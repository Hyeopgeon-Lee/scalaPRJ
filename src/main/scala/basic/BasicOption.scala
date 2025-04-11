object BasicOption {

  def main(args: Array[String]): Unit = {

    // 값이 존재하는 경우를 Option으로 표현
    val name: Option[String] = Some("Spark")
    // getOrElse는 Option이 Some일 경우 해당 값을 반환하고,
    // None일 경우 괄호 안의 기본값("Unknown")을 반환함
    println(name.getOrElse("Unknown")) // 출력: Spark

    // 값이 존재하지 않는 경우를 Option으로 표현
    val nickname: Option[String] = None
    // nickname은 None이므로 getOrElse에 지정한 기본값 "익명"이 반환됨
    println(nickname.getOrElse("익명")) // 출력: 익명

    try {
      // 예외가 발생할 수 있는 코드 블록
      // 여기서는 10을 0으로 나누는 연산이므로 ArithmeticException 발생
      val result = 10 / 0
    } catch {
      // 위 try 블록에서 예외가 발생하면 catch 블록이 실행됨
      // ArithmeticException 타입의 예외가 발생한 경우에만 이 블록이 실행됨
      case e: ArithmeticException =>
        println("0으로 나눌 수 없습니다.") // 예외 메시지 출력
    } finally {
      // 예외 발생 여부와 관계없이 항상 실행되는 블록
      println("항상 실행됩니다.")
    }

  }
}

