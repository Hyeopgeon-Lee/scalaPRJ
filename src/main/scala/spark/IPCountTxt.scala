// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// 필요한 Spark 클래스 import

import org.apache.spark.sql.SparkSession

object IPCountTxt {

  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // appName: Spark UI나 로그에서 보여질 이름
    // master: 로컬 실행 시 모든 CPU 코어를 사용 ("local[*]") / 클러스터에서는 "yarn" 등 사용
    val spark = SparkSession.builder()
      .appName("Apache Log TXT - IP별 요청 수")
      .master("local[*]")
      .getOrCreate()

    // 2. 불필요한 로그 억제를 위해 로그 레벨을 ERROR로 설정
    spark.sparkContext.setLogLevel("ERROR")

    // 3. SparkContext를 통해 텍스트 파일(RDD 형태) 읽기
    // RDD는 각 줄(로그 한 줄)이 하나의 요소로 구성됨
    val sc = spark.sparkContext
    val rdd = sc.textFile("hdfs://192.168.133.131:8020/spark/apache_log.txt")

    // 4. 각 로그 줄에서 IP만 추출
    // 로그의 첫 번째 단어는 IP이므로 split(" ")(0)으로 가져옴
    val ipRDD = rdd.map(line => line.split(" ")(0))

    // 5. (IP, 1)로 매핑 후 reduceByKey로 집계
    // 결과: (IP, 요청 횟수)
    val ipCountRDD = ipRDD.map(ip => (ip, 1))
      .reduceByKey(_ + _)

    // 6. 요청 수 기준으로 내림차순 정렬
    val sortedRDD = ipCountRDD.sortBy({ case (_, count) => count }, ascending = false)

    // 7. 상위 50개 출력
    println("[IP별 요청 수 상위 50개]")
    sortedRDD.take(50).foreach { case (ip, count) =>
      println("ip : " + ip + " count : " + count)
    }

    // 8. Spark 종료
    spark.stop()
  }
}
