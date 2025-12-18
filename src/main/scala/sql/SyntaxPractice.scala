package sql

import org.apache.spark.sql.SparkSession

object SyntaxPractice {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("Spark SQL - Syntax Practice")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")

    // 예: 1-1 웹로그 데이터를 그대로 재사용한다고 가정
    val inputPath = if (args.nonEmpty) args(0) else "hdfs://192.168.133.131:8020/spark/weblog.json"

    val df = spark.read.option("multiline", "true").json(inputPath)

    // View 등록
    df.createOrReplaceTempView("t")

    // 1) SELECT / WHERE / LIMIT
    spark.sql(
      """
      SELECT ip, url, status
      FROM t
      WHERE status >= 400
      LIMIT 20
    """).show(truncate = false)

    // 2) GROUP BY / HAVING
    spark.sql(
      """
      SELECT url, COUNT(*) AS cnt
      FROM t
      GROUP BY url
      HAVING cnt >= 10
      ORDER BY cnt DESC
      LIMIT 20
    """).show(truncate = false)

    // 3) CASE WHEN
    spark.sql(
      """
      SELECT
        CASE
          WHEN status BETWEEN 200 AND 299 THEN '2xx'
          WHEN status BETWEEN 300 AND 399 THEN '3xx'
          WHEN status BETWEEN 400 AND 499 THEN '4xx'
          WHEN status BETWEEN 500 AND 599 THEN '5xx'
          ELSE 'other'
        END AS status_group,
        COUNT(*) AS cnt
      FROM t
      GROUP BY
        CASE
          WHEN status BETWEEN 200 AND 299 THEN '2xx'
          WHEN status BETWEEN 300 AND 399 THEN '3xx'
          WHEN status BETWEEN 400 AND 499 THEN '4xx'
          WHEN status BETWEEN 500 AND 599 THEN '5xx'
          ELSE 'other'
        END
      ORDER BY cnt DESC
    """).show(truncate = false)

    // 4) 실행 계획 확인
    spark.sql("""SELECT url, COUNT(*) cnt FROM t GROUP BY url""").explain(true)

    spark.stop()
  }
}
