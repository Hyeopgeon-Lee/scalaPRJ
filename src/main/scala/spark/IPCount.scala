// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// 필요한 Spark SQL 클래스 import
import org.apache.spark.sql.SparkSession // SparkSession은 Spark 기능 사용의 진입점
import org.apache.spark.sql.functions._ // 집계 함수(count, desc 등)를 사용하기 위한 import

// 객체 정의 - 프로그램의 진입점이 되는 main 함수를 포함
object IPCount {

  def main(args: Array[String]): Unit = {

    // SparkSession 생성
    // - appName: Spark UI나 로그에 표시될 이름
    // - master: 로컬에서 실행 시 모든 CPU 코어 사용 ("local[*]") / 클러스터 환경에서는 "yarn" 등 사용
    val spark = SparkSession.builder()
      .appName("Apache Log - IP별 요청 수")
      .master("local[*]")
      .getOrCreate() // 이미 존재하는 SparkSession이 있으면 재사용하고, 없으면 새로 생성

    // 로그 레벨을 ERROR로 설정하여 불필요한 INFO 로그 출력 억제
    spark.sparkContext.setLogLevel("ERROR")

    // HDFS에 저장된 apache_log.json 파일을 읽어 DataFrame으로 로드
    // - JSON 형식이므로 자동으로 스키마 추론 및 파싱됨
    val df = spark.read.json("hdfs://192.168.133.131:8020/spark/apache_log.json")

    // [예상되는 컬럼 예시] ip, timestamp, method, uri, status, userAgent 등
    // - ip 컬럼을 기준으로 그룹화(groupBy)
    // - 각 IP에 대해 요청 건수를 count
    // - count 기준으로 내림차순 정렬(desc)
    val result = df.groupBy("ip")
      .count()
      .orderBy(desc("count"))

    // 결과 출력
    // - show(50, false): 최대 50개 출력, truncate 없이 전체 문자열 표시
    println("IP별 요청 수 상위 순위:")
    result.show(50, truncate = false)

    // SparkSession 종료 - 리소스 해제 및 애플리케이션 종료
    spark.stop()
  }
}
