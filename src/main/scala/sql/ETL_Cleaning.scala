package sql

import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * ETL_Cleaning
 *
 * 목적:
 *  - dirty_orders.csv (의도적으로 더러운 데이터) 를
 *  - Spark SQL 분석/ML 학습에 바로 쓸 수 있는 clean_orders_parquet 로 정제
 *
 * 처리 내용(핵심):
 *  - 문자열 공백 제거(trim)
 *  - price/qty/is_purchase 타입 변환
 *  - order_time 다중 포맷 Timestamp 파싱
 *  - NULL/빈문자/중복/이상치 제거
 *  - 파생 컬럼(amount, day) 생성
 *  - Parquet 저장 + day 파티션
 */
object ETL_Cleaning {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - ETL Cleaning")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // -------------------------
    // 1) 경로 설정 (args 우선)
    // -------------------------
    val inputPath =
      if (args.length > 0) args(0)
      else "hdfs://192.168.133.131:8020/spark_data/dirty_orders.csv"

    val outputPath =
      if (args.length > 1) args(1)
      else "hdfs://192.168.133.131:8020/spark_data/clean_orders_parquet"

    // 세션 구분과 다르게 ETL은 보통 파라미터화가 유용함
    val showBadSamples = if (args.length > 2) args(2).toBoolean else true

    // -------------------------
    // 2) Extract: CSV 로드
    // -------------------------
    val raw = spark.read
      .option("header", "true")
      .option("mode", "PERMISSIVE")
      .csv(inputPath)

    val rawCount = raw.count()

    // -------------------------
    // 3) Transform: Trim (공백 제거)
    // -------------------------
    val trimmed = raw.select(raw.columns.map(c => F.trim(F.col(c)).as(c)): _*)

    // 빈문자("")를 NULL처럼 다루기 위한 보조 함수
    def emptyToNull(colName: String) =
      F.when(F.col(colName).isNull || F.length(F.col(colName)) === 0, F.lit(null)).otherwise(F.col(colName))

    val standardized = trimmed
      // 빈문자 -> null 로 통일 (category/product/user_id 등)
      .withColumn("user_id", emptyToNull("user_id"))
      .withColumn("order_time", emptyToNull("order_time"))
      .withColumn("category", emptyToNull("category"))
      .withColumn("product", emptyToNull("product"))
      .withColumn("price", emptyToNull("price"))
      .withColumn("qty", emptyToNull("qty"))
      .withColumn("is_purchase", emptyToNull("is_purchase"))

    // -------------------------
    // 4) Transform: 타입 변환 + 시간 파싱(다중 포맷)
    // -------------------------
    val casted = standardized
      // price/qty/is_purchase: 문자열 -> 숫자
      // (free, NaN 같은 값은 cast 실패 -> null)
      .withColumn("price_num", F.col("price").cast("double"))
      .withColumn("qty_num", F.col("qty").cast("int"))
      .withColumn("is_purchase_num", F.col("is_purchase").cast("int"))

      // 시간 포맷 혼재 대응:
      // - yyyy-MM-dd HH:mm:ss
      // - yyyy/MM/dd HH:mm:ss
      // - yyyy-MM-dd'T'HH:mm:ss
      // - (마지막) Spark 기본 파서
      .withColumn(
        "ts",
        F.coalesce(
          F.to_timestamp($"order_time", "yyyy-MM-dd HH:mm:ss"),
          F.to_timestamp($"order_time", "yyyy/MM/dd HH:mm:ss"),
          F.to_timestamp($"order_time", "yyyy-MM-dd'T'HH:mm:ss"),
          F.to_timestamp($"order_time")
        )
      )

      // 원본 문자열 컬럼은 유지해도 되지만, 실습에서는 표준 컬럼만 남기는 게 깔끔
      .drop("price", "qty", "is_purchase", "order_time")

      // 표준 컬럼명으로 정리
      .withColumnRenamed("price_num", "price")
      .withColumnRenamed("qty_num", "qty")
      .withColumnRenamed("is_purchase_num", "is_purchase")

    // -------------------------
    // 5) Transform: 결측/이상치/라벨 검증
    // -------------------------
    // category/product가 null이면 Unknown으로 채움 (분석/피처 구성 편의)
    val filled = casted
      .na.fill("Unknown", Seq("category", "product"))

    // “정상 행” 조건:
    // - ts 존재 (시간 파싱 성공)
    // - user_id 존재
    // - price/qty 존재
    // - is_purchase 존재 (ML label)
    // - qty > 0, price >= 0
    // - is_purchase는 0/1만 허용 (실습 안정성)
    val filtered = filled
      .filter(
        $"user_id".isNotNull &&
          $"ts".isNotNull &&
          $"price".isNotNull &&
          $"qty".isNotNull &&
          $"is_purchase".isNotNull &&
          $"qty" > 0 &&
          $"price" >= 0 &&
          $"is_purchase".isin(0, 1)
      )

    // -------------------------
    // 6) Transform: 중복 제거 (order_id 기준)
    // -------------------------
    val dedup = filtered.dropDuplicates("order_id")

    // -------------------------
    // 7) Transform: 파생 컬럼 생성
    // -------------------------
    val cleaned = dedup
      .withColumn("amount", F.round($"price" * $"qty", 2))
      .withColumn("day", F.date_format($"ts", "yyyy-MM-dd"))

    val cleanedCount = cleaned.count()

    // -------------------------
    // 8) 정제 결과 요약 출력
    // -------------------------
    println(s"[ETL] inputPath  = $inputPath")
    println(s"[ETL] outputPath = $outputPath")
    println(s"[ETL] rawCount   = $rawCount")
    println(s"[ETL] cleaned    = $cleanedCount")
    val ratio = if (rawCount == 0) 0.0 else cleanedCount.toDouble / rawCount.toDouble * 100.0
    println(f"[ETL] keep ratio = $ratio%.2f%%")

    // 라벨 분포 확인 (ML 실습에서 매우 중요)
    println("[ETL] label distribution (is_purchase):")
    cleaned.groupBy($"is_purchase").count().orderBy($"is_purchase").show(truncate = false)

    // 잘려나간(불량) 샘플 몇 개 보기 (수업 효과 큼)
    if (showBadSamples) {
      val bad = filled.except(filtered).limit(20)
      println("[ETL] bad rows samples (dropped):")
      bad.show(20, truncate = false)
    }

    // -------------------------
    // 9) Load: Parquet 저장 + day 파티션
    // -------------------------
    cleaned.write
      .mode("overwrite")
      .partitionBy("day")
      .parquet(outputPath)

    println(s"[ETL] Done. Saved to: $outputPath")

    // 샘플 확인
    cleaned.orderBy(F.desc("ts")).show(20, truncate = false)

    spark.stop()
  }
}
