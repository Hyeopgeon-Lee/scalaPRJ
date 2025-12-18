package ml

import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * MakePredictParquet
 *
 * 목적:
 *  - 예측 전용(라벨 없는) dirty 주문 CSV를
 *  - 예측에 바로 사용할 수 있는 clean Parquet로 변환
 *
 * 입력(예):
 *  - hdfs://.../spark_data/dirty_orders_predict_20000.csv
 *
 * 출력(예):
 *  - hdfs://.../spark_data/clean_orders_predict_parquet_v1
 *
 * 특징:
 *  - is_purchase 컬럼이 없음 (라벨 누수 방지)
 *  - order_time 다중 포맷 파싱
 *  - 공백/빈문자/결측/중복/이상치 제거
 *  - amount/day 생성
 */
object MakePredictParquet {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - Make Predict Parquet")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val inputPath =
      if (args.length > 0) args(0)
      else "hdfs://192.168.133.131:8020/spark_data/dirty_orders_predict_20000.csv"

    val outputPath =
      if (args.length > 1) args(1)
      else "hdfs://192.168.133.131:8020/spark_data/clean_orders_predict_parquet_v1"

    // 1) Extract
    val raw = spark.read
      .option("header", "true")
      .option("mode", "PERMISSIVE")
      .csv(inputPath)

    // 2) Transform: trim
    val trimmed = raw.select(raw.columns.map(c => F.trim(F.col(c)).as(c)): _*)

    // 빈문자("") -> null
    def emptyToNull(c: String) =
      F.when(F.col(c).isNull || F.length(F.col(c)) === 0, F.lit(null)).otherwise(F.col(c))

    val standardized = trimmed
      .withColumn("order_id", emptyToNull("order_id"))
      .withColumn("user_id", emptyToNull("user_id"))
      .withColumn("order_time", emptyToNull("order_time"))
      .withColumn("category", emptyToNull("category"))
      .withColumn("product", emptyToNull("product"))
      .withColumn("price", emptyToNull("price"))
      .withColumn("qty", emptyToNull("qty"))

    // 3) 타입 변환 + 시간 파싱(다중 포맷)
    val casted = standardized
      .withColumn("price_num", F.col("price").cast("double"))
      .withColumn("qty_num", F.col("qty").cast("int"))
      .withColumn(
        "ts",
        F.coalesce(
          F.to_timestamp($"order_time", "yyyy-MM-dd HH:mm:ss"),
          F.to_timestamp($"order_time", "yyyy/MM/dd HH:mm:ss"),
          F.to_timestamp($"order_time", "yyyy-MM-dd'T'HH:mm:ss"),
          F.to_timestamp($"order_time")
        )
      )
      .drop("price", "qty", "order_time")
      .withColumnRenamed("price_num", "price")
      .withColumnRenamed("qty_num", "qty")

    // 4) 결측/이상치 제거 + category/product 보정
    val cleaned = casted
      .na.fill("Unknown", Seq("category", "product"))
      .filter(
        $"order_id".isNotNull &&
          $"user_id".isNotNull &&
          $"ts".isNotNull &&
          $"price".isNotNull &&
          $"qty".isNotNull &&
          $"price" >= 0 &&
          $"qty" > 0
      )
      // 5) 중복 제거
      .dropDuplicates("order_id")
      // 6) 파생 컬럼
      .withColumn("amount", F.round($"price" * $"qty", 2))
      .withColumn("day", F.date_format($"ts", "yyyy-MM-dd"))

    println(s"[Predict-ETL] inputPath  = $inputPath")
    println(s"[Predict-ETL] outputPath = $outputPath")
    println(s"[Predict-ETL] cleanedCount = ${cleaned.count()}")

    // 7) Load: Parquet 저장 (day 파티션)
    cleaned.repartition($"day")
      .write
      .mode("overwrite")
      .partitionBy("day")
      .parquet(outputPath)

    println(s"[Predict-ETL] Done. Saved to: $outputPath")
    spark.stop()
  }
}
