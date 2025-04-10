object Basic03 {

  def main(args: Array[String]): Unit = {

    val score = 85

    if (score >= 90) {
      println("A학점")
    } else if (score >= 80) {
      println("B학점")
    } else {
      println("C학점 이하")
    }

  }

  val score = 75
  val grade = if (score >= 80) "Good" else "Needs Improvement"

  println(grade) // 결과: "Needs Improvement"

}

