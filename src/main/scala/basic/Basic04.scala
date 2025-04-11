object Basic04 {

  def main(args: Array[String]): Unit = {

    // 1부터 5까지의 숫자를 반복하면서 출력
    for (i <- 1 to 5) {
      // 문자열 보간(s-interpolation)을 사용하여 변수 i 값을 출력
      println(s"$i 번째 반복")
    }

    // 2중 for문: i는 1부터 2까지, j는 1부터 3까지 반복
    for (i <- 1 to 2; j <- 1 to 3) {
      // i와 j의 현재 값을 출력
      println(s"i=$i, j=$j")
    }

    // 1부터 10까지 반복하면서, 짝수(i % 2 == 0)인 경우만 출력
    for (i <- 1 to 10 if i % 2 == 0) {
      // 조건을 만족하는 값(i)을 출력
      println(s"짝수: $i")
    }

    // 1부터 5까지의 숫자를 순회하며 각 숫자의 제곱을 계산하여 새로운 컬렉션에 담음
    val squares = for (i <- 1 to 5) yield i * i

    // 결과 출력: Vector(1, 4, 9, 16, 25)
    // yield는 for문을 통해 생성된 값을 모아 새로운 컬렉션(Vector)을 생성함
    println(squares)


    // 문자열 요소를 가지는 리스트 생성
    val fruits = List("apple", "banana", "cherry")

    // foreach를 사용하여 리스트의 각 요소에 대해 작업 수행
    // fruit => println(fruit)는 람다식(익명 함수)으로, 각 요소를 출력함
    fruits.foreach(fruit => println(fruit))

    // 정수형 요소를 가지는 리스트 생성
    val numbers = List(1, 2, 3, 4, 5)

    // foreach 구문을 사용하여 리스트의 각 숫자를 출력
    // println은 각 요소를 그대로 출력하는 함수로, 람다식 없이 함수 이름만 전달해도 동작함
    numbers.foreach(println)


  }
}


