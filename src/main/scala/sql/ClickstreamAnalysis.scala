package sql

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types._

object ClickstreamAnalysis {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - Clickstream Analysis")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val inputPath = if (args.nonEmpty) args(0) else "hdfs://192.168.133.131:8020/spark/clickstream.json"

    // 예시 스키마: user_id, event_time, page, event_type
    val schema = StructType(Seq(
      StructField("user_id", StringType, true),
      StructField("event_time", StringType, true),
      StructField("page", StringType, true),
      StructField("event_type", StringType, true)
    ))

    val raw = spark.read.schema(schema).option("multiline", "true").json(inputPath)

    val df = raw
      .withColumn("ts",
        F.coalesce(
          F.to_timestamp($"event_time"),
          F.to_timestamp($"event_time", "yyyy-MM-dd HH:mm:ss")
        )
      )
      .drop("event_time")
      .filter($"user_id".isNotNull && $"ts".isNotNull && $"page".isNotNull)

    // (A) 페이지 전환(Transition) 분석: prev_page -> page
    val w = Window.partitionBy("user_id").orderBy("ts")

    val transitions = df
      .withColumn("prev_page", F.lag($"page", 1).over(w))
      .filter($"prev_page".isNotNull)
      .groupBy($"prev_page", $"page")
      .agg(F.count(F.lit(1)).as("cnt"))
      .orderBy(F.desc("cnt"))

    // (B) 세션화(Sessionization): 30분 이상 공백이면 새 세션
    val sessionGapSec = 30 * 60

    val withDiff = df
      .withColumn("prev_ts", F.lag($"ts", 1).over(w))
      .withColumn("diff_sec", F.col("ts").cast("long") - F.col("prev_ts").cast("long"))
      .withColumn("is_new_session", F.when($"prev_ts".isNull || $"diff_sec" > sessionGapSec, 1).otherwise(0))

    val withSessionId = withDiff
      .withColumn("session_id", F.sum($"is_new_session").over(w))

    withSessionId.createOrReplaceTempView("clicks")

    val sessionAgg = spark.sql(
      """
      SELECT user_id, session_id,
             MIN(ts) AS session_start,
             MAX(ts) AS session_end,
             COUNT(*) AS events
      FROM clicks
      GROUP BY user_id, session_id
      ORDER BY user_id, session_id
    """)

    val pageViews = spark.sql(
      """
      SELECT page, COUNT(*) AS pv
      FROM clicks
      GROUP BY page
      ORDER BY pv DESC
      LIMIT 20
    """)

    println("=== [1] Page Transitions (Top) ===")
    transitions.show(50, truncate = false)

    println("=== [2] Session Summary ===")
    sessionAgg.show(50, truncate = false)

    println("=== [3] Top Page Views ===")
    pageViews.show(20, truncate = false)

    spark.stop()
  }
}
