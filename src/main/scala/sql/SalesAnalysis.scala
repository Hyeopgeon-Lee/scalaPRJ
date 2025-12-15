package sql

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.types._

object SalesAnalysis {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - Sales Analysis")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    // CSV 예시: order_id,user_id,order_time,category,product,price,qty
    val inputPath = if (args.nonEmpty) args(0) else "hdfs://192.168.133.131:8020/spark/orders.csv"

    val schema = StructType(Seq(
      StructField("order_id", StringType, true),
      StructField("user_id", StringType, true),
      StructField("order_time", StringType, true),
      StructField("category", StringType, true),
      StructField("product", StringType, true),
      StructField("price", DoubleType, true),
      StructField("qty", IntegerType, true)
    ))

    val raw = spark.read
      .option("header", "true")
      .schema(schema)
      .csv(inputPath)

    val df = raw
      .withColumn("ts",
        F.coalesce(
          F.to_timestamp($"order_time"),
          F.to_timestamp($"order_time", "yyyy-MM-dd HH:mm:ss")
        )
      )
      .drop("order_time")
      .filter($"ts".isNotNull && $"price".isNotNull && $"qty".isNotNull)
      .withColumn("amount", $"price" * $"qty")

    df.createOrReplaceTempView("orders")

    // (A) 일별 매출
    val daily = spark.sql(
      """
      SELECT date_format(ts, 'yyyy-MM-dd') AS day,
             SUM(amount) AS sales,
             COUNT(DISTINCT order_id) AS orders,
             COUNT(DISTINCT user_id) AS users
      FROM orders
      GROUP BY date_format(ts, 'yyyy-MM-dd')
      ORDER BY day
    """)

    // (B) 월별 매출
    val monthly = spark.sql(
      """
      SELECT date_format(ts, 'yyyy-MM') AS month,
             SUM(amount) AS sales,
             COUNT(DISTINCT order_id) AS orders
      FROM orders
      GROUP BY date_format(ts, 'yyyy-MM')
      ORDER BY month
    """)

    // (C) 카테고리별 매출 Top
    val byCategory = spark.sql(
      """
      SELECT category, SUM(amount) AS sales
      FROM orders
      GROUP BY category
      ORDER BY sales DESC
    """)

    // (D) 상위 상품(매출 기준)
    val topProducts = spark.sql(
      """
      SELECT product, SUM(amount) AS sales, SUM(qty) AS units
      FROM orders
      GROUP BY product
      ORDER BY sales DESC
      LIMIT 20
    """)

    println("=== [1] Daily Sales ===")
    daily.show(60, truncate = false)

    println("=== [2] Monthly Sales ===")
    monthly.show(24, truncate = false)

    println("=== [3] Sales by Category ===")
    byCategory.show(50, truncate = false)

    println("=== [4] Top Products ===")
    topProducts.show(20, truncate = false)

    spark.stop()
  }
}
