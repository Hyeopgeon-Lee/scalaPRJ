// 패키지 선언 - 이 파일은 spark 패키지에 포함됨
package spark

// SparkSession을 사용하기 위해 필요한 import 문

import org.apache.spark.sql.SparkSession

// HelloSpark 객체 정의 - Scala 애플리케이션의 시작점
object HelloSpark {

  // main 함수: 프로그램이 실행되는 진입점
  def main(args: Array[String]): Unit = {

    // SparkSession 생성: Spark 애플리케이션을 위한 핵심 객체
    // .appName(): 실행될 Spark 애플리케이션의 이름 지정
    // .master("local[*]"): 로컬 머신의 모든 CPU 코어 사용
    val spark = SparkSession.builder()
      .appName("HelloSparkApp")
      .master("local[*]") // local mode에서 실행
      .getOrCreate() // 이미 존재하면 재사용하고, 없으면 새로 생성

    // DataFrame 생성에 필요한 암시적(implicit) 변환 함수들을 불러옴
    import spark.implicits._ // toDF(), $"컬럼명" 등을 사용 가능하게 함

    // 간단한 예제 데이터 생성: (이름, 나이) 형태의 튜플을 Seq로 구성
    val data = Seq(
      ("홍길동", 25),
      ("이몽룡", 30),
      ("성춘향", 28)
    )

    // 시퀀스를 DataFrame으로 변환하고 컬럼 이름 지정
    val df = data.toDF("name", "age")

    // 생성된 DataFrame의 내용을 콘솔에 출력
    df.show()

    /*
      출력 결과 예시:
      +--------+---+
      |    name|age|
      +--------+---+
      |  홍길동| 25|
      |  이몽룡| 30|
      |  성춘향| 28|
      +--------+---+
    */

    // 작업 완료 후 Spark 세션 종료 (리소스 정리)
    spark.stop()
  }
}
