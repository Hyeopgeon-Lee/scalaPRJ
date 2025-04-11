object BasicImport {

  def main(args: Array[String]): Unit = {

    // scala.collection.mutable 패키지에서 ListBuffer와 Map만 선택적으로 import합니다.
    import scala.collection.mutable.{ListBuffer, Map}

    // 이제 ListBuffer, Map 클래스를 바로 사용할 수 있습니다.
    val list = ListBuffer(1, 2, 3)
    val map = Map("a" -> 1, "b" -> 2)

    // java.util 패키지의 Date 클래스를 UtilDate라는 별칭으로 import합니다.
    import java.util.{Date => UtilDate}

    // 별칭을 사용해 Date 객체를 생성합니다.
    val today: UtilDate = new UtilDate()
    println(s"오늘 날짜: $today")

    // scala.collection.mutable 패키지에서 Map은 제외하고 나머지 모두 import합니다.
    import scala.collection.mutable.{Map => _, _}

    // ListBuffer는 정상적으로 사용할 수 있습니다.
    val buffer = ListBuffer(10, 20, 30)
    // Map은 import되지 않았기 때문에 사용 시 오류가 발생합니다.
    // val map = Map("a" -> 1)  // 오류 발생
  }
}

