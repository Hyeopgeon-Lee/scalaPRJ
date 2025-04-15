// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// SparkSession 사용을 위한 import

import org.apache.spark.sql.SparkSession

// 객체 정의 - 이 객체는 애플리케이션의 진입점 역할을 함
object WordCount {

  // main 함수 - Scala 애플리케이션 실행 시 시작되는 함수
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // SparkSession은 DataFrame, Dataset, SQL 처리에 사용되는 핵심 객체
    // .appName(): 애플리케이션 이름 지정 (Spark UI 등에서 확인 가능)
    // .master("local[*]"): 로컬 머신의 모든 코어를 사용하여 실행
    val spark = SparkSession.builder()
      .appName("Word Count from Comedies File")
      .master("local[*]")
      .getOrCreate()

    // 2. 로그 출력 최소화 - INFO 이하 로그를 생략하여 콘솔 출력 간결하게 함
    spark.sparkContext.setLogLevel("ERROR")

    // 3. SparkContext 획득 - RDD API를 사용하기 위해 필요
    val sc = spark.sparkContext

    // JVM 관련 주의 사항
    // Java 17 이상에서는 RDD 처리 시 내부적으로 sun.nio.ch.DirectBuffer 등
    // JDK 내부 모듈을 접근하는 경우가 있어 JVM 실행 옵션이 필요함
    // 아래 옵션을 추가하지 않으면 IllegalAccessError 발생 가능
    //
    // --add-opens java.base/sun.nio.ch=ALL-UNNAMED
    // --add-opens java.base/java.nio=ALL-UNNAMED
    // --add-opens java.base/java.lang.invoke=ALL-UNNAMED
    // --add-opens java.base/java.util=ALL-UNNAMED

    // 4. 텍스트 파일 로드 (HDFS 경로에서 파일을 읽음)
    // textFile(): 한 줄씩 읽어 RDD[String] 형태로 반환
    val rdd = sc.textFile("hdfs://192.168.133.131:8020/comedies")

    // 5. 단어 분리 및 카운트 처리
    // flatMap: 각 줄을 단어로 나눠 평탄화
    // filter: 공백 제거 (빈 문자열 제외)
    // map: (단어, 1) 형태로 변환
    // reduceByKey: 동일 단어를 그룹핑하여 출현 횟수 누적
    val wordCounts = rdd
      .flatMap(_.split("\\W+")) // 구두점 제거하고 단어 분리 (정규표현식 사용)
      .filter(_.nonEmpty) // 빈 문자열 제외
      .map(_.toLowerCase) // 대소문자 통일
      .map(word => (word, 1)) // (단어, 1) 튜플 생성
      .reduceByKey(_ + _) // 같은 단어끼리 더함

    // 6. 결과 출력
    // sortBy: 출현 횟수를 기준으로 내림차순 정렬
    // take(50): 상위 50개 단어만 가져옴
    // foreach: 콘솔에 출력
    println("[Word Count 결과 - 상위 50개 단어]")
    wordCounts
      .sortBy({ case (_, count) => -count }) // count 기준으로 내림차순 정렬
      .take(50)
      .foreach { case (word, count) =>
        println("word : " + word + " count : " + count)
      }

    // 7. Spark 세션 종료
    // 실행 후 자원 정리 및 메모리 해제
    spark.stop()
  }
}
