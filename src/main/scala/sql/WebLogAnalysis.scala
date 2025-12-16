package sql

// SparkSession: Spark SQL 애플리케이션의 진입점
// functions => F : Spark SQL 내장 함수(to_timestamp, date_format 등) 사용
import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.types._

/**
 * WebLogAnalysis
 * - Apache 웹 로그(JSON)를 Spark SQL로 분석하는 예제
 * - 상태코드 분포, 시간대별 트래픽, Top URL 분석
 */
object WebLogAnalysis {
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // - Spark SQL, DataFrame, SQL 실행을 위한 핵심 객체
    // - local[*] : 로컬 머신의 모든 CPU 코어 사용
    val spark = SparkSession.builder()
      .appName("Spark SQL - Web Log Analysis")
      .master("local[*]")
      .getOrCreate()

    // 불필요한 로그를 줄여 실습 시 출력 가독성 확보
    spark.sparkContext.setLogLevel("ERROR")

    // DataFrame DSL 사용을 위한 암시적 변환
    import spark.implicits._

    // 2. 입력 JSON 파일의 실제 구조에 맞춘 스키마 정의
    // - Apache 로그 JSON은 구조화된 로그이므로 스키마를 명시하는 것이 바람직
    // - timestamp 포맷 예: 11/Apr/2025:15:03:18 +0900
    val schema = StructType(Seq(
      StructField("ip", StringType, true),          // 클라이언트 IP
      StructField("timestamp", StringType, true),   // 요청 발생 시각 (문자열)
      StructField("method", StringType, true),      // HTTP 메서드 (GET, POST 등)
      StructField("path", StringType, true),        // 요청 URL 경로
      StructField("status", IntegerType, true),     // HTTP 상태 코드
      StructField("bytes", LongType, true),         // 응답 바이트 수
      StructField("userAgent", StringType, true),   // User-Agent 정보
      StructField("referrer", StringType, true)     // Referrer URL
    ))

    // 3. HDFS에 저장된 Apache 로그 JSON 파일 경로
    val inputPath =
      "hdfs://192.168.133.131:8020/spark_data/apache_log_bot_detection.json"

    // 4. JSON 파일 로드
    // - schema 명시 → 데이터 타입 안정성 확보
    // - multiline=true → JSON 배열 형태일 경우 필요
    val df = spark.read
      .schema(schema)
      .option("multiline", "true")
      .json(inputPath)

    // 5. 컬럼 표준화 및 전처리
    val weblog = df
      // timestamp 문자열을 Timestamp 타입으로 변환
      // Apache 로그 포맷: "dd/MMM/yyyy:HH:mm:ss Z"
      .withColumn(
        "event_time",
        F.to_timestamp($"timestamp", "dd/MMM/yyyy:HH:mm:ss Z")
      )

      // 분석 가독성을 위해 path → url 컬럼명 변경
      .withColumnRenamed("path", "url")

      // 원본 timestamp 컬럼 제거 (event_time으로 대체)
      .drop("timestamp")

      // 분석에 필요한 최소 조건 필터링
      // - 시간 파싱 실패(null) 제거
      // - URL이 없는 로그 제거
      .filter($"event_time".isNotNull && $"url".isNotNull)

    // 6. 디버깅용 데이터 건수 확인
    // - raw count: 원본 로그 건수
    // - parsed count: timestamp 파싱 및 필터링 후 건수
    println(s"raw count = ${df.count()}, parsed count = ${weblog.count()}")

    // 7. Spark SQL에서 사용하기 위해 임시 뷰 등록
    weblog.createOrReplaceTempView("weblog")

    // 8. 상태 코드별 요청 수 집계
    val statusAgg = spark.sql("""
      SELECT status, COUNT(*) AS cnt
      FROM weblog
      GROUP BY status
      ORDER BY cnt DESC
    """)

    // 9. 시간대별 트래픽 분석 (시간 단위)
    val hourlyAgg = spark.sql("""
      SELECT date_format(event_time, 'yyyy-MM-dd HH:00') AS hour_bucket,
             COUNT(*) AS requests
      FROM weblog
      GROUP BY date_format(event_time, 'yyyy-MM-dd HH:00')
      ORDER BY hour_bucket
    """)

    // 10. 요청이 가장 많은 URL Top 20
    val topUrl = spark.sql("""
      SELECT url, COUNT(*) AS cnt
      FROM weblog
      GROUP BY url
      ORDER BY cnt DESC
      LIMIT 20
    """)

    // 11. 결과 출력
    println("=== [1] Status Code Distribution ===")
    statusAgg.show(truncate = false)

    println("=== [2] Hourly Traffic ===")
    hourlyAgg.show(truncate = false)

    println("=== [3] Top URL ===")
    topUrl.show(truncate = false)

    // 12. Spark 세션 종료 (리소스 해제)
    spark.stop()
  }
}
