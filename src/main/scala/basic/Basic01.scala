object Basic01 {

  def main(args: Array[String]): Unit = {

    // val 사용 예시 (불변 변수)
    val appName: String = "Spark Program"
    println(appName)

    // appName = "Changed"  //오류 발생 (val은 재할당 불가)

    // var 사용 예시 (가변 변수)
    var counter: Int = 0
    counter = counter + 1
    println(s"현재 카운터 값: $counter")
  }

}

