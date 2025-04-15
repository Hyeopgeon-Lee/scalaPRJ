// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// SparkSession 사용을 위한 import

import org.apache.spark.sql.SparkSession

// 객체 정의 - 애플리케이션의 진입점
object ReadFileBasic {

  // main 함수 - Scala 애플리케이션의 시작 지점
  def main(args: Array[String]): Unit = {

    // SparkSession 생성
    // SparkSession은 DataFrame, Dataset, SQL 등을 포함해 Spark 기능을 사용하기 위한 핵심 진입점
    // .appName() : Spark UI나 로그에서 보일 애플리케이션 이름 지정
    // .master("local[*]") : 로컬 환경에서 모든 CPU 코어 사용 (YARN 환경이면 "yarn"으로 지정 가능)
    val spark = SparkSession.builder()
      .appName("Read Comedies File") // 애플리케이션 이름 설정
      .master("local[*]") // 로컬에서 실행 시 사용 (클러스터 환경은 "yarn")
      .getOrCreate() // 기존 SparkSession이 있으면 재사용, 없으면 새로 생성

    // SparkContext 생성
    // RDD 기반 API를 사용하기 위해 SparkSession에서 SparkContext를 얻음
    val sc = spark.sparkContext

    // Java 17 이상에서 RDD 사용 시 JVM 옵션 필요
    // JVM 실행 시 아래 옵션을 추가해야 Kryo 직렬화 오류 방지 가능
    // --add-opens java.base/sun.nio.ch=ALL-UNNAMED
    // --add-opens java.base/java.nio=ALL-UNNAMED
    // --add-opens java.base/java.lang.invoke=ALL-UNNAMED
    // --add-opens java.base/java.util=ALL-UNNAMED

    // HDFS에 저장된 텍스트 파일 읽기
    // textFile(): 각 줄을 RDD의 한 요소로 읽어들임
    // "hdfs://IP:포트/파일경로" 형식으로 지정
    val rdd = sc.textFile("hdfs://192.168.133.131:8020/comedies")

    // 읽어온 데이터 출력
    println("[comedies 파일 내용 출력]")
    rdd.foreach(println) // 각 줄을 출력

    // SparkSession 종료
    // 리소스 해제 및 애플리케이션 종료
    spark.stop()
  }

}
