// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// 필요한 Spark SQL 라이브러리 import

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._ // 집계 함수, 시간 처리 함수 등 포함

// 객체 선언 - Scala 애플리케이션의 실행 진입점
object BotDetection {

  // main 함수 - 애플리케이션의 시작 지점
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // .appName(): Spark UI에서 보일 애플리케이션 이름
    // .master("local[*]"): 로컬 환경에서 모든 CPU 코어를 사용하여 실행
    val spark = SparkSession.builder()
      .appName("Bot Detection by IP Request Rate")
      .master("local[*]")
      .getOrCreate()

    // 로그 레벨을 ERROR로 설정하여 콘솔 출력 최소화
    spark.sparkContext.setLogLevel("ERROR")

    // 암시적 변환 사용을 위해 import
    import spark.implicits._

    // 2. JSON 로그 파일 로딩
    // option("multiline", true): JSON 배열 형식으로 여러 줄에 걸쳐 있을 경우 필요
    // 예: [{...}, {...}, {...}]
    val df = spark.read
      .option("multiline", value = true)
      .json("hdfs://192.168.133.131:8020/apache_log.json")

    // 3. timestamp 필드를 Spark의 Timestamp 형식으로 변환 및 시간 버킷 생성
    val parsedDF = df
      .withColumn(
        "parsed_time",
        to_timestamp($"timestamp", "dd/MMM/yyyy:HH:mm:ss Z")
      ) // Apache 로그 포맷 예시: 11/Apr/2025:13:52:40 +0900
      .withColumn(
        "minute_bucket",
        date_format($"parsed_time", "yyyy-MM-dd HH:mm")
      ) // 분 단위로 버킷팅 (yyyy-MM-dd HH:mm)

    // 4. IP + 분단위 조합으로 요청 수 계산 후 1분 내 10건 이상 요청한 IP 탐지
    val ipPerMinute = parsedDF
      .groupBy($"ip", $"minute_bucket")
      .agg(count("*").alias("request_count"))
      .filter($"request_count" >= 10) // 봇 의심 기준

    // 5. 결과 출력
    println("봇 의심 IP 탐지 결과 (1분 내 10건 이상 요청):")
    ipPerMinute
      .orderBy(desc("request_count")) // 요청 수 많은 순 정렬
      .show(100, truncate = false) // 100개까지 전체 출력

    // 6. 실행 계획(SQL 변환 결과 포함) 출력
    println("실행 계획 (SQL 변환 포함):")
    ipPerMinute.explain(true)

    // 7. Spark 세션 종료
    spark.stop()
  }
}
