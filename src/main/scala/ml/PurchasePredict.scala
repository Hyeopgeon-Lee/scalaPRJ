package ml

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.ml.PipelineModel

/**
 * PurchasePredict
 *
 * 목적:
 *  - 학습된 Spark ML 모델(PipelineModel)을 로드
 *  - ETL 결과 데이터를 이용해 구매 여부 예측
 *  - 예측 결과를 출력/저장
 *
 * 특징:
 *  - 학습 과정 없음 (fit X)
 *  - 오직 model.load + transform 만 수행
 *  - 실무 Inference 구조와 동일
 */
object PurchasePredict {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark ML - Purchase Predict")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // -------------------------
    // 1) 입력 경로
    // -------------------------
    val inputPath =
      if (args.length > 0) args(0)
      else "hdfs://192.168.133.131:8020/spark_data/clean_orders_parquet"

    // -------------------------
    // 2) 모델 경로
    // -------------------------
    val modelPath =
      if (args.length > 1) args(1)
      else "hdfs://192.168.133.131:8020/spark_models/purchase_lr"

    // -------------------------
    // 3) 예측 결과 저장 경로
    // -------------------------
    val outputPath =
      if (args.length > 2) args(2)
      else "hdfs://192.168.133.131:8020/spark_predictions/purchase_pred"

    // -------------------------
    // 4) 데이터 로드 (ETL 결과)
    // -------------------------
    val orders = spark.read.parquet(inputPath)

    // -------------------------
    // 5) 사용자 단위 Feature 생성
    // ※ Training과 "동일한 로직"이어야 함
    // -------------------------
    val userAgg = orders
      .filter($"user_id".isNotNull && $"amount".isNotNull && $"qty".isNotNull)
      .groupBy($"user_id")
      .agg(
        F.countDistinct($"order_id").as("order_cnt"),
        F.sum($"amount").as("total_amount"),
        F.avg($"amount").as("avg_amount"),
        F.avg($"qty").as("avg_qty"),
        F.countDistinct($"product").as("distinct_product_cnt")
      )
      .na.fill(0.0, Seq("total_amount", "avg_amount", "avg_qty"))
      .na.fill(0, Seq("order_cnt", "distinct_product_cnt"))

    println(s"[INFO] Prediction 대상 사용자 수 = ${userAgg.count()}")

    // -------------------------
    // 6) 학습된 모델 로드
    // -------------------------
    val model = PipelineModel.load(modelPath)

    // -------------------------
    // 7) 예측 수행
    // -------------------------
    val pred = model.transform(userAgg)

    // -------------------------
    // 8) 예측 결과 가공
    // -------------------------
    val result = pred.select(
      $"user_id",
      $"order_cnt",
      $"total_amount",
      $"avg_amount",
      $"avg_qty",
      $"distinct_product_cnt",
      $"probability",
      $"prediction"
    )

    // -------------------------
    // 9) 결과 확인
    // -------------------------
    println("[INFO] Prediction result (sample)")
    result.orderBy(F.desc("total_amount")).show(50, truncate = false)

    // -------------------------
    // 10) 결과 저장 (Parquet)
    // -------------------------
    result.write
      .mode("overwrite")
      .parquet(outputPath)

    println(s"[INFO] Prediction result saved to: $outputPath")

    spark.stop()
  }
}
