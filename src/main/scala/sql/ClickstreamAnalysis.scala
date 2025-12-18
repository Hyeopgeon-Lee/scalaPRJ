package sql

// Spark SQL 핵심 클래스
// - SparkSession : Spark SQL 애플리케이션의 시작점
// - functions => F : Spark SQL 내장 함수 모음
// - Window : 사용자 행동 분석을 위한 Window Function

import org.apache.spark.sql.{SparkSession, functions => F}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types._

/**
 * ClickstreamAnalysis
 *
 * 사용자 행동 로그(Clickstream)를 Spark SQL로 분석하는 예제
 *
 * 분석 내용
 * 1) 페이지 전환 분석 (이전 페이지 → 현재 페이지)
 * 2) 세션(Session) 단위 사용자 행동 요약
 * 3) 페이지별 방문 수(Page View)
 *
 * 핵심 학습 포인트
 *  - Window Function(lag)
 *  - 사용자 기준 정렬
 *  - 세션 구분 로직(Sessionization)
 */
object ClickstreamAnalysis {
  def main(args: Array[String]): Unit = {

    // 1. SparkSession 생성
    // local[*] : 로컬 환경에서 사용 가능한 모든 CPU 코어 사용
    val spark = SparkSession.builder()
      .appName("Spark SQL - Clickstream Analysis")
      .master("local[*]")
      .getOrCreate()

    // 실습 시 불필요한 로그 제거
    spark.sparkContext.setLogLevel("ERROR")

    // DataFrame DSL($"컬럼명") 사용을 위한 설정
    import spark.implicits._

    // 2. Clickstream 데이터 스키마 정의
    // 하나의 Row = 하나의 사용자 행동 이벤트
    val schema = StructType(Seq(
      StructField("user_id", StringType, nullable = true), // 사용자 식별자
      StructField("event_time", StringType, nullable = true), // 이벤트 발생 시각 (문자열)
      StructField("page", StringType, nullable = true), // 접근/클릭한 페이지
      StructField("event_type", StringType, nullable = true) // 행동 유형 (click 등)
    ))

    // 3. JSON 로그 파일 로드
    val raw = spark.read
      .schema(schema)
      .option("multiline", "true") // JSON 배열 형태일 경우 필요
      .json("hdfs://192.168.133.131:8020/spark_data/clickstream.json")

    // 4. 분석을 위한 전처리
    val df = raw
      // event_time 문자열을 Timestamp 타입으로 변환
      // 다양한 시간 포맷을 대비해 coalesce 사용
      .withColumn(
        "ts",
        F.coalesce(
          F.to_timestamp($"event_time"),
          F.to_timestamp($"event_time", "yyyy-MM-dd HH:mm:ss")
        )
      )
      // 원본 event_time 컬럼 제거
      .drop("event_time")

      // 분석에 필요한 최소 조건 필터링
      // - 사용자 ID 없음 제거
      // - 시간 파싱 실패 제거
      // - 페이지 정보 없는 로그 제거
      .filter($"user_id".isNotNull && $"ts".isNotNull && $"page".isNotNull)

    df.show()

    // 5. 사용자 행동 분석을 위한 Window 정의
    // - user_id 기준으로 묶고
    // - 시간(ts) 기준으로 정렬
    val w = Window.partitionBy("user_id").orderBy("ts")

    // =========================================================
    // (A) 페이지 전환 분석 (Page Transition)
    // =========================================================

    // 이전 페이지(prev_page) → 현재 페이지(page) 전환 분석
    val transitions = df
      // 이전 페이지 컬럼 생성
      .withColumn("prev_page", F.lag($"page", 1).over(w))

      // 첫 이벤트(이전 페이지 없음) 제거
      .filter($"prev_page".isNotNull)

      // 페이지 전환 쌍 기준 집계
      .groupBy($"prev_page", $"page")
      .agg(F.count(F.lit(1)).as("cnt"))

      // 전환 횟수 기준 내림차순 정렬
      .orderBy(F.desc("cnt"))

    transitions.show()
    // =========================================================
    // (B) 세션화(Sessionization)
    // =========================================================

    // 세션 구분 기준: 30분(1800초) 이상 행동 공백 → 새 세션
    val sessionGapSec = 30 * 60

    val withDiff = df
      // 이전 이벤트 시각
      .withColumn("prev_ts", F.lag($"ts", 1).over(w))

      // 이전 이벤트와의 시간 차(초)
      .withColumn(
        "diff_sec",
        F.col("ts").cast("long") - F.col("prev_ts").cast("long")
      )

      // 새 세션 여부 판단
      // - 첫 이벤트
      // - 이전 이벤트와의 시간 차가 기준 초과
      .withColumn(
        "is_new_session",
        F.when($"prev_ts".isNull || $"diff_sec" > sessionGapSec, 1).otherwise(0)
      )

    // 사용자별 누적 합으로 session_id 생성
    val withSessionId = withDiff
      .withColumn("session_id", F.sum($"is_new_session").over(w))

    withSessionId.show()

    // Spark SQL 분석을 위해 임시 뷰 등록
    withSessionId.createOrReplaceTempView("clicks")

    // =========================================================
    // (C) 세션 단위 요약 분석
    // =========================================================

    val sessionAgg = spark.sql(
      """
      SELECT user_id,
             session_id,
             MIN(ts) AS session_start,
             MAX(ts) AS session_end,
             COUNT(*) AS events
      FROM clicks
      GROUP BY user_id, session_id
      ORDER BY user_id, session_id
    """)

    // =========================================================
    // (D) 페이지별 방문 수(Page View)
    // =========================================================

    val pageViews = spark.sql(
      """
      SELECT page, COUNT(*) AS pv
      FROM clicks
      GROUP BY page
      ORDER BY pv DESC
      LIMIT 20
    """)

    // =========================================================
    // 결과 출력
    // =========================================================

    println("=== [1] Page Transitions (Top) ===")
    transitions.show(50, truncate = false)

    println("=== [2] Session Summary ===")
    sessionAgg.show(50, truncate = false)

    println("=== [3] Top Page Views ===")
    pageViews.show(20, truncate = false)

    // 7. Spark 세션 종료
    spark.stop()
  }
}
