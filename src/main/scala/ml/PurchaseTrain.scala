package ml

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.{StandardScaler, VectorAssembler}
import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * PurchaseTrain
 *
 * 목적:
 *  - ETL 결과(clean_orders_parquet_v3)를 이용해
 *  - 사용자 단위 Feature 생성
 *  - 구매 여부(is_purchase) 예측 모델 학습
 *  - 학습된 모델을 HDFS에 저장
 *
 * 특징:
 *  - Training 전용 (fit + evaluate + save)
 *  - Inference(예측)는 별도 프로그램에서 수행
 */
object PurchaseTrain {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark ML - Purchase Train")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // -------------------------
    // 1) 입력 / 출력 경로
    // -------------------------
    val inputPath =
      if (args.length > 0) args(0)
      else "hdfs://192.168.133.131:8020/spark_data/clean_orders_parquet"

    val modelPath =
      if (args.length > 1) args(1)
      else "hdfs://192.168.133.131:8020/spark_models/purchase_lr"

    // -------------------------
    // 2) ETL 결과 로드
    // -------------------------
    val orders = spark.read.parquet(inputPath)

    // 기대 컬럼:
    // order_id, user_id, category, product,
    // price, qty, ts, amount, is_purchase, day

    // -------------------------
    // 3) 사용자 단위 Feature 생성
    // -------------------------
    val userAgg = orders
      .filter($"user_id".isNotNull && $"amount".isNotNull && $"qty".isNotNull)
      .groupBy($"user_id")
      .agg(
        F.countDistinct($"order_id").as("order_cnt"),
        F.sum($"amount").as("total_amount"),
        F.avg($"amount").as("avg_amount"),
        F.avg($"qty").as("avg_qty"),
        F.countDistinct($"product").as("distinct_product_cnt"),
        F.max($"is_purchase").as("label") // 사용자 기준 구매 여부 라벨
      )
      .na.fill(0.0, Seq("total_amount", "avg_amount", "avg_qty"))
      .na.fill(0, Seq("order_cnt", "distinct_product_cnt", "label"))

    println(s"[INFO] User samples = ${userAgg.count()}")

    // 라벨 분포 확인 (불균형 여부)
    println("[INFO] Label distribution:")
    userAgg.groupBy($"label").count().orderBy($"label").show(truncate = false)

    // -------------------------
    // 4) Feature Vector 구성
    // -------------------------
    val featureCols = Array(
      "order_cnt",
      "total_amount",
      "avg_amount",
      "avg_qty",
      "distinct_product_cnt"
    )

    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features_raw")

    val scaler = new StandardScaler()
      .setInputCol("features_raw")
      .setOutputCol("features")
      .setWithStd(true)
      .setWithMean(false)

    // -------------------------
    // 5) 분류 모델 정의
    // -------------------------
    val lr = new LogisticRegression()
      .setFeaturesCol("features")
      .setLabelCol("label")
      .setMaxIter(50)
      .setRegParam(0.01) // 약한 규제 (과적합 방지)
      .setElasticNetParam(0) // L2

    val pipeline = new Pipeline()
      .setStages(Array(assembler, scaler, lr))

    // -------------------------
    // 6) Train / Test 분리
    // -------------------------
    val Array(train, test) =
      userAgg.randomSplit(Array(0.8, 0.2), seed = 42)

    println(s"[INFO] Train = ${train.count()}, Test = ${test.count()}")

    // -------------------------
    // 7) 모델 학습
    // -------------------------
    val model = pipeline.fit(train)

    // -------------------------
    // 8) 평가 (AUC)
    // -------------------------
    val pred = model.transform(test)

    val evaluator = new BinaryClassificationEvaluator()
      .setLabelCol("label")
      .setRawPredictionCol("rawPrediction")
      .setMetricName("areaUnderROC")

    val auc = evaluator.evaluate(pred)
    println(f"[RESULT] Validation AUC = $auc%.4f")

    // -------------------------
    // 9) 모델 저장 (중요)
    // -------------------------
    model.write
      .overwrite()
      .save(modelPath)

    println(s"[INFO] Model saved to: $modelPath")

    spark.stop()
  }
}
