// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// GeoIP2 라이브러리 import - MaxMind의 GeoLite2 DB 사용
import com.maxmind.geoip2.DatabaseReader

// SparkSession 및 필요한 클래스 import
import org.apache.spark.sql.SparkSession
import java.io.File
import java.net.InetAddress
import scala.util.Try // 예외 처리에 사용

// 객체 선언 - 프로그램 진입점
object GeoIPCountryAnalysis_MapPartitions {
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성 - Spark SQL 및 DataFrame 사용을 위한 기본 엔트리
    val spark = SparkSession.builder()
      .appName("GeoIP Country Analysis - mapPartitions") // Spark UI에서 확인할 앱 이름
      .master("local[*]") // 로컬 실행 시 모든 CPU 코어 사용
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR") // Spark 로그 레벨을 ERROR로 설정해 불필요한 로그 줄이기
    import spark.implicits._ // DataFrame/Dataset 관련 암시적 변환

    // 2. 리소스 경로에서 GeoLite2 Country DB 파일 경로를 가져옴
    val geoIPFilePath = this.getClass.getResource("/GeoLite2-Country.mmdb").getPath

    // 3. JSON 로그 파일 읽기
    //    - multiline: JSON 배열 형식이 여러 줄로 구성된 경우 true 설정 필요
    //    - select($"ip") : 필요한 컬럼(IP 주소)만 선택하여 메모리 사용 최소화
    val df = spark.read
      .option("multiline", value = true)
      .json("hdfs://192.168.133.131:8020/spark/apache_log.json")
      .select($"ip")

    // 4. DataFrame을 RDD로 변환 후 mapPartitions을 통해 변환 수행
    //    - mapPartitions은 각 파티션마다 1번만 reader를 생성하여 효율적
    //    - DataFrame 기반 UDF 방식보다 성능 우수
    val countryRDD = df.rdd.mapPartitions { partitionIter =>
      // 각 파티션별로 단 1번만 DatabaseReader 생성
      val reader = new DatabaseReader.Builder(new File(geoIPFilePath)).build()

      // 각 레코드(IP)에 대해 국가명을 추출하여 튜플(country, 1)로 반환
      val resultIter = partitionIter.map { row =>
        val ip = row.getAs[String]("ip")
        val country = Try {
          val response = reader.country(InetAddress.getByName(ip))
          response.getCountry.getName
        }.getOrElse("Unknown") // 예외 발생 시 "Unknown" 처리

        (country, 1) // (국가명, 카운트 1)
      }

      resultIter
    }

    // 5. RDD → DataFrame 변환
    //    - toDF(): 튜플을 "country", "count"라는 컬럼명으로 변환
    //    - groupBy + sum: 국가별 요청 횟수 집계
    //    - withColumnRenamed: 컬럼명 변경
    val countryDF = countryRDD.toDF("country", "count")
      .groupBy("country")
      .sum("count")
      .withColumnRenamed("sum(count)", "request_count")
      .orderBy($"request_count".desc)

    // 6. 최종 결과 출력
    println("[국가별 요청 수 - mapPartitions 최적화]")
    countryDF.show(100, truncate = false)

    // 7. Spark 세션 종료
    spark.stop()
  }
}
