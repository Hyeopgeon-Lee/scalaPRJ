package sql

// Spark SQL 실행을 위한 핵심 클래스
// - SparkSession : Spark SQL / DataFrame / SQL의 시작점
// - functions => F : Spark SQL 내장 함수 모음
import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.types._

/**
 * WebLogAnalysis
 *
 * Apache 웹 로그(JSON)를 Spark SQL로 분석하는 예제
 *  - 상태 코드 분포
 *  - 시간대별 트래픽
 *  - 요청이 많은 URL 분석
 *
 * 목적:
 *  - Spark SQL 기반 로그 분석 흐름 이해
 *  - Timestamp 파싱과 전처리의 중요성 학습
 */
object WebLogAnalysis {
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // Spark SQL 애플리케이션의 진입점
    // local[*] : 로컬 환경에서 사용 가능한 모든 CPU 코어 사용
    val spark = SparkSession.builder()
      .appName("Spark SQL - Web Log Analysis")
      .master("local[*]")
      .getOrCreate()

    // 실습 시 불필요한 INFO/WARN 로그 제거
    spark.sparkContext.setLogLevel("ERROR")

    // $"컬럼명" 과 같은 DataFrame DSL 사용을 위한 설정
    import spark.implicits._

    // 2. Apache 로그 JSON 구조에 맞춘 스키마 정의
    // 로그 분석에서는 컬럼 구조를 명확히 아는 것이 가장 중요
    // timestamp 예시: 11/Apr/2025:15:03:18 +0900
    val schema = StructType(Seq(
      StructField("ip", StringType, true),          // 요청한 클라이언트 IP
      StructField("timestamp", StringType, true),   // 요청 시각 (문자열)
      StructField("method", StringType, true),      // HTTP Method
      StructField("path", StringType, true),        // 요청 URL 경로
      StructField("status", IntegerType, true),     // HTTP 상태 코드
      StructField("bytes", LongType, true),         // 응답 크기(Byte)
      StructField("userAgent", StringType, true),   // User-Agent 정보
      StructField("referrer", StringType, true)     // 이전 페이지 정보
    ))

    schema.printTreeString()

    // 3. HDFS에 저장된 Apache 로그 JSON 파일 로드
    // - schema 명시 : 타입 안정성 확보
    // - multiline=true : JSON 배열 형태 로그 처리
    val df = spark.read
      .schema(schema)
      .option("multiline", "true")
      .json("hdfs://192.168.133.131:8020/spark_data/apache_log_bot_detection.json")

    // 4. 분석을 위한 전처리 및 컬럼 표준화
    val weblog = df
      // 문자열 timestamp → Timestamp 타입 변환
      // Apache 로그 시간 포맷을 반드시 명시해야 함
      .withColumn(
        "event_time",
        F.to_timestamp($"timestamp", "dd/MMM/yyyy:HH:mm:ss Z")
      )

      // SQL 분석 가독성을 위해 path 컬럼명을 url로 변경
      .withColumnRenamed("path", "url")

      // 원본 timestamp 컬럼 제거
      .drop("timestamp")

      // 분석에 필요한 최소 조건만 남김
      // - 시간 파싱 실패한 로그 제거
      // - URL 정보가 없는 로그 제거
      .filter($"event_time".isNotNull && $"url".isNotNull)

    weblog.show()

    // 5. 전처리 결과 확인
    // raw count    : 원본 로그 수
    // parsed count : 시간 파싱 및 필터링 후 로그 수
    println(s"raw count = ${df.count()}, parsed count = ${weblog.count()}")

    // 6. Spark SQL에서 사용하기 위해 임시 뷰 생성
    // 이후 모든 분석은 SQL로 수행
    weblog.createOrReplaceTempView("weblog")

    // 7. 상태 코드별 요청 수 분석
    val statusAgg = spark.sql("""
      SELECT status, COUNT(*) AS cnt
      FROM weblog
      GROUP BY status
      ORDER BY cnt DESC
    """)

    // 8. 시간대별 트래픽 분석 (시간 단위 집계)
    val hourlyAgg = spark.sql("""
      SELECT date_format(event_time, 'yyyy-MM-dd HH:00') AS hour_bucket,
             COUNT(*) AS requests
      FROM weblog
      GROUP BY date_format(event_time, 'yyyy-MM-dd HH:00')
      ORDER BY hour_bucket
    """)

    // 9. 요청이 가장 많은 URL Top 20
    val topUrl = spark.sql("""
      SELECT url, COUNT(*) AS cnt
      FROM weblog
      GROUP BY url
      ORDER BY cnt DESC
      LIMIT 20
    """)

    // 10. 분석 결과 출력
    println("=== [1] Status Code Distribution ===")
    statusAgg.show(truncate = false)

    println("=== [2] Hourly Traffic ===")
    hourlyAgg.show(truncate = false)

    println("=== [3] Top URL ===")
    topUrl.show(truncate = false)

    // 11. Spark 세션 종료
    // 사용한 리소스 정리
    spark.stop()
  }
}

