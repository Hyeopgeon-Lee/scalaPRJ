object Basic02 {

  def main(args: Array[String]): Unit = {

    // 타입을 명시한 변수 선언
    val year: Int = 2025
    val temperature: Double = 36.5
    val isScalaFun: Boolean = true

    println(s"올해는 ${year}년입니다.")
    println(s"현재 체온은 ${temperature}도입니다.")
    println(s"Scala는 재미있는가요? ${isScalaFun}")

    // 타입을 명시한 가변 변수
    var score: Int = 90
    println(s"현재 점수는 ${score}점입니다.")
    score = 95
    println(s"변경된 점수는 ${score}점입니다.")

    // 타입 추론
    val message = "Hello, Scala!"
    val pi = 3.14159
    val items = 42
    val isEven = (items % 2 == 0)

    println(message)
    println(s"원주율은 ${pi}입니다.")
    println(s"아이템 수는 ${items}개이고, 짝수인가요? ${isEven}")

    // 여러 변수 동시 선언 (Tuple unpacking)
    val (name, age, isStudent) = ("Alice", 23, true)
    println(s"${name}의 나이는 ${age}세이며 학생 여부: ${isStudent}")

    // val과 var의 차이
    val country = "Korea"
    // country = "USA" // 오류: val은 값을 변경할 수 없음

    var city = "Seoul"
    println(s"현재 도시는 ${city}입니다.")
    city = "Busan"
    println(s"변경된 도시는 ${city}입니다.")

    // Null, Unit 예제
    val nothing: String = null
    println(s"null 할당: ${nothing}")

    def sayHello(): Unit = println("Hi from function!")

    sayHello()
  }

}
