object BasicFunction {

  def main(args: Array[String]): Unit = {

    def greet(name: String): String = {
      s"Hello, $name!" // s 문자열: 값 삽입 가능
    }

    println(greet("Scala")) // 출력: Hello, Scala!

    // 정수 두 개를 더한 결과를 반환
    def add(x: Int, y: Int): Int = {
      x + y
    }

    println(add(3, 5)) // 출력: 8

    // 정수를 제곱하는 익명 함수(Lambda 함수)를 정의합니다.
    // 함수 이름 없이 변수(square)에 직접 함수를 저장합니다.
    // x: Int는 입력 매개변수, x * x는 반환 값입니다.
    val square = (x: Int) => x * x

    // square 함수에 정수 4를 전달하여 제곱 결과를 출력합니다.
    println(square(4)) // 출력: 16

    // 두 개의 정수를 더하는 익명 함수를 정의합니다.
    // (x: Int, y: Int)는 두 개의 입력 매개변수이며,
    // x + y는 두 숫자의 합을 반환합니다.
    val sum = (x: Int, y: Int) => x + y

    // sum 함수에 5와 7을 전달하여 결과(12)를 출력합니다.
    println(sum(5, 7)) // 출력: 12

  }
}

