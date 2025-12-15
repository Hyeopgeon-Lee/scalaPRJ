package sql

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.types._

object WebLogAnalysis {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - Web Log Analysis")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // 1) 입력 경로 (필요 시 수정)
    // 예: HDFS: hdfs://192.168.133.131:8020/data/weblog.json
    // 예: Local: file:///C:/data/weblog.json
    val inputPath = if (args.nonEmpty) args(0) else "hdfs://192.168.133.131:8020/spark/weblog.json"

    // 2) 웹 로그 스키마(예시)
    // ts: ISO8601 또는 "yyyy-MM-dd HH:mm:ss" 형태
    val schema = StructType(Seq(
      StructField("ts", StringType, nullable = true),
      StructField("ip", StringType, nullable = true),
      StructField("method", StringType, nullable = true),
      StructField("url", StringType, nullable = true),
      StructField("status", IntegerType, nullable = true),
      StructField("bytes", LongType, nullable = true),
      StructField("userAgent", StringType, nullable = true),
      StructField("referrer", StringType, nullable = true)
    ))

    // 3) JSON 로드 → 표준 컬럼 정규화
    val raw = spark.read
      .schema(schema)
      .option("multiline", "true")
      .json(inputPath)

    val df = raw
      .withColumn("event_time",
        F.coalesce(
          F.to_timestamp($"ts"),
          F.to_timestamp($"ts", "yyyy-MM-dd HH:mm:ss")
        )
      )
      .drop("ts")
      .filter($"event_time".isNotNull && $"url".isNotNull)

    // 4) Temp View 등록
    df.createOrReplaceTempView("weblog")

    // 5) 상태코드 분포
    val statusAgg = spark.sql(
      """
      SELECT status, COUNT(*) AS cnt
      FROM weblog
      GROUP BY status
      ORDER BY cnt DESC
    """)

    // 6) 시간대별 트래픽(시간 단위)
    val hourlyAgg = spark.sql(
      """
      SELECT date_format(event_time, 'yyyy-MM-dd HH:00') AS hour_bucket,
             COUNT(*) AS requests
      FROM weblog
      GROUP BY date_format(event_time, 'yyyy-MM-dd HH:00')
      ORDER BY hour_bucket
    """)

    // 7) Top URL
    val topUrl = spark.sql(
      """
      SELECT url, COUNT(*) AS cnt
      FROM weblog
      GROUP BY url
      ORDER BY cnt DESC
      LIMIT 20
    """)

    // 8) 출력
    println("=== [1] Status Code Distribution ===")
    statusAgg.show(50, truncate = false)

    println("=== [2] Hourly Traffic ===")
    hourlyAgg.show(48, truncate = false)

    println("=== [3] Top 20 URL ===")
    topUrl.show(20, truncate = false)

    spark.stop()
  }
}
