object BasicFucntion2 {

  def main(args: Array[String]): Unit = {

    // 정수 리스트를 선언합니다.
    val nums = List(1, 2, 3, 4, 5)

    // 각 요소를 제곱하여 새로운 리스트를 반환합니다.
    // x => x * x 는 각 요소를 제곱하는 익명 함수입니다.
    val squares = nums.map(x => x * x)
    println(squares) // 출력: List(1, 4, 9, 16, 25)

    // 짝수인 값만 걸러냅니다.
    // _ % 2 == 0 은 각 요소가 짝수인지 판단합니다.
    val evens = nums.filter(_ % 2 == 0)
    println(evens) // 출력: List(2, 4)

    // 덧셈 누적
    val totalSum = nums.reduce(_ + _)
    println(totalSum) // 출력: 15

    // 곱셈 누적
    val totalProduct = nums.reduce(_ * _)
    println(totalProduct) // 출력: 120

    // 정수 리스트를 생성합니다.
    val nums2 = List(10, 5, 2)

    // reduceLeft는 왼쪽에서 오른쪽으로 순차적으로 값을 처리합니다.
    // 이 경우, 연산은 다음과 같이 수행됩니다:
    // 1단계: 10 - 5 = 5
    // 2단계: 5 - 2 = 3
    // 최종 결과: 3

    val result = nums2.reduceLeft(_ - _)

    // 결과 출력: 최종 계산된 값 3이 출력됩니다.
    println(result) // 출력: 3 → ((10 - 5) - 2)

    // reduceRight는 오른쪽에서 왼쪽으로 연산을 수행합니다.
    // 연산 순서는 다음과 같습니다:
    //
    // 1단계: 5 - 2 = 3
    // 2단계: 10 - 3 = 7
    //
    // 즉, 수식은 다음과 같이 평가됩니다:
    // 10 - (5 - 2) = 10 - 3 = 7

    val result2 = nums2.reduceRight(_ - _)

    // 결과 출력: 계산된 값 7이 출력됩니다.
    println(result) // 출력: 7 → (10 - (5 - 2))
  }
}

