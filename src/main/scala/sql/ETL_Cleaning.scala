package sql

import org.apache.spark.sql.types._
import org.apache.spark.sql.{SparkSession, functions => F}

object ETL_Cleaning {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - ETL Cleaning")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // 예: dirty_orders.csv
    // 컬럼 예: order_id,user_id,order_time,category,product,price,qty
    val inputPath = if (args.length > 0) args(0) else "hdfs://192.168.133.131:8020/spark/dirty_orders.csv"
    val outputPath = if (args.length > 1) args(1) else "hdfs://192.168.133.131:8020/spark/clean_orders_parquet"

    val df = spark.read.option("header", "true").csv(inputPath)

    // 1) 컬럼 트리밍 / 표준화
    val trimmed = df.select(df.columns.map(c => F.trim(F.col(c)).as(c)): _*)

    // 2) 타입 캐스팅 + 파생 컬럼
    val casted = trimmed
      .withColumn("price", F.col("price").cast("double"))
      .withColumn("qty", F.col("qty").cast("int"))
      .withColumn("ts",
        F.coalesce(
          F.to_timestamp($"order_time"),
          F.to_timestamp($"order_time", "yyyy-MM-dd HH:mm:ss")
        )
      )
      .drop("order_time")

    // 3) 결측치 처리
    // - category/product가 없으면 "Unknown"
    // - qty/price가 null이면 제거(분석 불가)
    val filled = casted
      .na.fill("Unknown", Seq("category", "product"))
      .filter($"ts".isNotNull && $"price".isNotNull && $"qty".isNotNull)

    // 4) 중복 제거(주문ID 기준)
    val dedup = filled.dropDuplicates("order_id")

    // 5) 이상치 처리(예: qty <= 0 제거, price < 0 제거)
    val cleaned = dedup
      .filter($"qty" > 0 && $"price" >= 0)
      .withColumn("amount", $"price" * $"qty")
      .withColumn("day", F.date_format($"ts", "yyyy-MM-dd"))

    // 6) 저장: Parquet + 파티션(일자)
    cleaned.write
      .mode("overwrite")
      .partitionBy("day")
      .parquet(outputPath)

    println(s"ETL 완료. 저장 경로: $outputPath")
    cleaned.show(20, truncate = false)

    spark.stop()
  }
}
