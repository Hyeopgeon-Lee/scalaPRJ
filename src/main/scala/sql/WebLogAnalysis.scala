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

    // ✔ 실제 JSON 구조에 맞춘 스키마
    val schema = StructType(Seq(
      StructField("ip", StringType, true),
      StructField("timestamp", StringType, true),
      StructField("method", StringType, true),
      StructField("path", StringType, true),
      StructField("status", IntegerType, true),
      StructField("bytes", LongType, true),
      StructField("userAgent", StringType, true),
      StructField("referrer", StringType, true)
    ))

    // ✔ 로컬 파일 경로
    val df = spark.read
      .schema(schema)
      .option("multiline", "true")
      .json("hdfs://192.168.133.131:8020/spark_data/apache_log_bot_detection.json")

    // ✔ 컬럼 표준화
    val weblog = df
      .withColumn("event_time", F.to_timestamp($"timestamp"))
      .withColumnRenamed("path", "url")
      .drop("timestamp")
      .filter($"event_time".isNotNull && $"url".isNotNull)

    weblog.createOrReplaceTempView("weblog")

    // 1️⃣ 상태코드 분포
    val statusAgg = spark.sql(
      """
      SELECT status, COUNT(*) AS cnt
      FROM weblog
      GROUP BY status
      ORDER BY cnt DESC
    """)

    // 2️⃣ 시간대별 트래픽
    val hourlyAgg = spark.sql(
      """
      SELECT date_format(event_time, 'yyyy-MM-dd HH:00') AS hour_bucket,
             COUNT(*) AS requests
      FROM weblog
      GROUP BY date_format(event_time, 'yyyy-MM-dd HH:00')
      ORDER BY hour_bucket
    """)

    // 3️⃣ Top URL
    val topUrl = spark.sql(
      """
      SELECT url, COUNT(*) AS cnt
      FROM weblog
      GROUP BY url
      ORDER BY cnt DESC
      LIMIT 20
    """)

    println("=== [1] Status Code Distribution ===")
    statusAgg.show(truncate = false)

    println("=== [2] Hourly Traffic ===")
    hourlyAgg.show(truncate = false)

    println("=== [3] Top URL ===")
    topUrl.show(truncate = false)

    spark.stop()
  }
}
