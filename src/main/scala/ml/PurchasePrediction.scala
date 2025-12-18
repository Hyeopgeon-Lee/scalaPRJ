package ml

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{VectorAssembler, StandardScaler}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator

/**
 * PurchasePrediction
 * - clean_orders_parquet(ETL 결과)로부터 사용자별 Feature 생성
 * - 규칙 기반 Label 생성(실습용)
 * - Logistic Regression으로 구매 여부(0/1) 예측
 */
object PurchasePrediction {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark ML - Purchase Prediction (Binary Classification)")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // 1) 입력 경로: ETL로 만든 clean_orders_parquet
    // 예: hdfs://192.168.133.131:8020/spark_data/clean_orders_parquet
    val inputPath =
      if (args.length > 0) args(0)
      else "hdfs://192.168.133.131:8020/spark_data/clean_orders_parquet"

    // 2) Parquet 로드 (ETL 결과는 day 파티션이 있어도 spark.read.parquet로 자동 인식)
    val orders = spark.read.parquet(inputPath)

    // 기대 컬럼 예:
    // order_id, user_id, category, product, price, qty, ts, amount, day
    // (ETL 코드에서 amount/day 생성)

    // 3) 사용자 단위 Feature 생성 (집계)
    // - order_cnt: 주문 수
    // - total_amount: 총 구매 금액
    // - avg_amount: 주문당 평균 금액
    // - avg_qty: 평균 수량
    // - distinct_product_cnt: 구매 상품 종류 수
    val userFeatures = orders
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

    // 4) 실습용 Label 정의 (규칙 기반)
    // - 예: total_amount >= threshold 이면 구매 의도가 높은 사용자(1), 아니면 0
    // ※ 데이터 크기/분포에 따라 threshold 조정 권장
    val threshold = if (args.length > 1) args(1).toDouble else 200.0

    val labeled = userFeatures
      .withColumn("label", F.when($"total_amount" >= threshold, 1.0).otherwise(0.0))

    println(s"[INFO] Label threshold(total_amount) = $threshold")
    labeled.select("user_id", "order_cnt", "total_amount", "label").orderBy(F.desc("total_amount")).show(20, truncate = false)

    // 5) Feature Vector 구성
    val featureCols = Array("order_cnt", "total_amount", "avg_amount", "avg_qty", "distinct_product_cnt")

    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features_raw")

    // (선택) 스케일링: Logistic Regression에서 성능/수렴 안정성에 도움
    val scaler = new StandardScaler()
      .setInputCol("features_raw")
      .setOutputCol("features")
      .setWithStd(true)
      .setWithMean(false)

    // 6) 분류 모델: Logistic Regression
    val lr = new LogisticRegression()
      .setFeaturesCol("features")
      .setLabelCol("label")
      .setMaxIter(50)
      .setRegParam(0.0) // 실습용: 규제 약하게(또는 0)
      .setElasticNetParam(0) // 0=L2, 1=L1

    val pipeline = new Pipeline().setStages(Array(assembler, scaler, lr))

    // 7) 학습/평가 데이터 분리
    val Array(train, test) = labeled.randomSplit(Array(0.8, 0.2), seed = 42)

    println(s"[INFO] train count = ${train.count()}, test count = ${test.count()}")

    // 8) 학습
    val model = pipeline.fit(train)

    // 9) 예측
    val pred = model.transform(test)

    // 10) 평가 (AUC)
    val evaluator = new BinaryClassificationEvaluator()
      .setLabelCol("label")
      .setRawPredictionCol("rawPrediction") // LogisticRegression 기본 출력
      .setMetricName("areaUnderROC")

    val auc = evaluator.evaluate(pred)
    println(f"[RESULT] AUC = $auc%.4f")

    // 11) 예측 결과 확인
    // - probability: (클래스0 확률, 클래스1 확률)
    // - prediction: 최종 예측(0/1)
    pred.select(
        $"user_id",
        $"order_cnt",
        $"total_amount",
        $"avg_amount",
        $"avg_qty",
        $"distinct_product_cnt",
        $"label",
        $"probability",
        $"prediction"
      )
      .orderBy(F.desc("total_amount"))
      .show(50, truncate = false)

    spark.stop()
  }
}
