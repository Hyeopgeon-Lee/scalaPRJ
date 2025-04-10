object BasicCollection {

  def main(args: Array[String]): Unit = {

    // List는 불변(immutable) 컬렉션입니다.
    // 데이터를 추가하거나 수정하면 새로운 List가 생성됩니다.
    val fruits = List("apple", "banana", "cherry")

    // foreach를 사용해 리스트의 모든 요소를 출력합니다.
    // f는 리스트의 각 요소를 의미합니다.
    fruits.foreach(f => println(s"과일: $f")) // 출력: 과일: apple ...

    // Array는 고정된 크기의 자료구조로, 값 수정이 가능합니다.
    val arr = Array(1, 2, 3)

    // for문을 통해 배열의 요소들을 반복 출력합니다.
    for (i <- arr) {
      println(s"배열 요소: $i") // 출력: 배열 요소: 1 ...
    }

    // Map은 키-값 쌍으로 이루어진 불변 컬렉션입니다.
    // 여기서는 국가와 수도 정보를 담고 있습니다.
    val capital = Map("Korea" -> "Seoul", "Japan" -> "Tokyo")

    // foreach문에서 case 문법을 사용해 (key, value) 쌍을 꺼냅니다.
    capital.foreach { case (country, city) =>
      println(s"${country}의 수도는 ${city}입니다.")
    }
    // 출력:
    // Korea의 수도는 Seoul입니다.
    // Japan의 수도는 Tokyo입니다.

    // scala.collection.mutable 패키지에서 ListBuffer를 가져옵니다.
    // ListBuffer는 가변 리스트로, 값 변경이 가능합니다.
    import scala.collection.mutable.ListBuffer

    // 초기 점수 2개를 가진 가변 리스트 생성
    val scores = ListBuffer(80, 90)

    // += 연산자를 사용해 리스트에 값을 추가합니다.
    scores += 100

    // ListBuffer는 값이 변경되므로, 새로운 컬렉션이 생성되지 않습니다.
    println(s"점수 목록: $scores") // 출력: ListBuffer(80, 90, 100)

  }
}


