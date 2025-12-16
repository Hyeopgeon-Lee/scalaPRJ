// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// 필요한 Spark SQL 관련 클래스 import

import org.apache.spark.sql.{SparkSession, functions => F}

// MaxMind GeoIP2 라이브러리 import (IP → 국가 정보 조회용)
import com.maxmind.geoip2.DatabaseReader
import java.io.File
import java.net.InetAddress
import scala.util.Try // 예외 처리를 위해 사용

// GeoIPCountryAnalysis 객체 정의 - 애플리케이션 실행 진입점
object GeoIPCountryAnalysis {
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    //    - Spark SQL, DataFrame 등을 사용하기 위한 진입점
    //    - .master("local[*]")는 로컬에서 사용 가능한 모든 CPU 코어 사용
    val spark = SparkSession.builder()
      .appName("GeoIP Country Analysis")
      .master("local[*]")
      .getOrCreate()

    // 불필요한 로그를 줄이기 위해 로그 레벨을 ERROR로 설정
    spark.sparkContext.setLogLevel("ERROR")

    // 스파크 SQL 함수 사용 및 DataFrame to Dataset 변환 위해 암시적 import
    import spark.implicits._

    // 2. GeoIP 데이터베이스 파일 경로 가져오기
    //    - `resources` 폴더에 위치한 GeoLite2-Country.mmdb 파일 경로를 가져옴
    val geoIPFilePath = this.getClass.getResource("/GeoLite2-Country.mmdb").getPath

    // 3. IP 주소를 국가 이름으로 변환하는 함수 정의
    //    - 각 호출 시마다 DatabaseReader를 새로 생성 → 비효율적이지만 구조는 단순
    //    - reader는 thread-safe하지 않으므로 이 방식은 안전
    //    - 예외 발생 시 "Unknown" 반환
    def ipToCountry(ip: String): String = {
      Try {
        val dbFile = new File(geoIPFilePath)
        val reader = new DatabaseReader.Builder(dbFile).build()
        val country = reader.country(InetAddress.getByName(ip)).getCountry.getName
        reader.close() // 리소스 해제
        country
      }.getOrElse("Unknown") // 예외 발생 시 fallback
    }

    // 4. 위 함수를 UDF로 등록하여 Spark SQL에서 사용 가능하게 만듦
    val ipToCountryUDF = F.udf(ipToCountry _)

    // 5. Apache 로그 JSON 파일 읽기
    //    - HDFS에 저장된 JSON 파일을 DataFrame으로 불러옴
    //    - multiline: JSON 배열 형태라면 true 설정 필요
    val df = spark.read
      .option("multiline", value = true)
      .json("hdfs://192.168.133.131:8020/spark_data/apache_log_bot_detection.json")

    // 6. 국가 컬럼 추가 및 국가별 요청 수 집계
    //    - withColumn("country", ...): IP → 국가 변환
    //    - groupBy + count: 국가별 요청 횟수 집계
    //    - orderBy: 요청 수 기준 내림차순 정렬
    val resultDF = df
      .withColumn("country", ipToCountryUDF($"ip"))
      .groupBy("country")
      .count()
      .orderBy(F.desc("count"))

    // 7. 최종 결과 출력 (최대 100개, 값 자르지 않음)
    println("📊 국가별 요청 수:")
    resultDF.show(100, truncate = false)

    // 8. Spark 세션 종료 (리소스 반환)
    spark.stop()
  }
}
