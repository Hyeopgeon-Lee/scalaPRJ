# Scala + Spark 실습 프로젝트

Scala 기초 문법부터 Spark Core/SQL/ML 예제까지 한 번에 정리한 학습용 프로젝트입니다. 
로컬/클러스터(HDFS) 환경에서 바로 실행할 수 있도록 예제별 입력 경로를 코드에 명시했습니다.

## 목차
- [프로젝트 개요](#프로젝트-개요)
- [개발 환경](#개발-환경)
- [디렉터리 구조](#디렉터리-구조)
- [실행 방법](#실행-방법)
- [예제 목록](#예제-목록)
  - [Scala 기초 문법](#scala-기초-문법)
  - [Spark Core/SQL](#spark-coresql)
  - [Spark SQL 분석](#spark-sql-분석)
  - [Spark ML](#spark-ml)
- [샘플 데이터](#샘플-데이터)
- [참고 사항](#참고-사항)

## 프로젝트 개요
- **Scala 기초 문법 학습**: 변수, 조건문, 반복문, 함수, 컬렉션, Option/예외 처리
- **Spark Core**: RDD 기반 파일 읽기/집계
- **Spark SQL**: DataFrame/SQL 분석 시나리오
- **Spark ML**: 구매 예측 파이프라인(학습/추론)과 ETL

## 개발 환경
- Scala: **2.13.16**
- Spark: **3.5.3** (spark-core, spark-sql, spark-mllib)
- Java: **17 권장**
- Build Tool: **sbt**

> Java 17에서 RDD API 사용 시 JVM 옵션이 필요할 수 있습니다.
> `--add-opens java.base/sun.nio.ch=ALL-UNNAMED` 등은 `build.sbt`에 설정되어 있습니다.

## 디렉터리 구조
```
.
├── build.sbt
├── data/                     # 샘플 데이터
├── src/
│   └── main/
│       ├── resources/         # GeoIP DB
│       └── scala/
│           ├── basic/          # Scala 기초 문법 예제
│           ├── spark/          # Spark Core 예제
│           ├── sql/            # Spark SQL 분석 예제
│           └── ml/             # Spark ML/ETL 예제
└── README.md
```

## 실행 방법
예제는 모두 `object`의 `main` 실행형입니다. `sbt`에서 `runMain`으로 실행합니다.

```bash
sbt "runMain basic.Basic01"
```

Spark 예제는 HDFS 경로를 사용합니다. 필요 시 코드 내 경로를 수정하거나, HDFS에 데이터를 적재해 주세요.

```bash
sbt "runMain spark.WordCount"
```

## 예제 목록

### Scala 기초 문법
- `basic/Basic01.scala`: `val`/`var` 차이와 기본 출력
- `basic/Basic02.scala`: 타입 선언, 타입 추론, Tuple, null/Unit
- `basic/Basic03.scala`: 조건문/표현식
- `basic/Basic04.scala`: for 반복, 조건 필터, yield, foreach
- `basic/BasicCollection.scala`: List/Seq/Array/Map, mutable 컬렉션
- `basic/BasicImport.scala`: import 선택/별칭/제외
- `basic/BasicFunction.scala`: 함수/익명함수 기초
- `basic/BasicFucntion2.scala`: map/filter/reduce 예제
- `basic/BasicOption.scala`: Option, try/catch/finally

### Spark Core/SQL
- `spark/HelloSpark.scala`: SparkSession + 간단한 DataFrame 출력
- `spark/ReadFileBasic.scala`: HDFS 텍스트 파일 RDD 읽기
- `spark/WordCount.scala`: 소설 텍스트 WordCount (RDD)
- `spark/IPCount.scala`: JSON 로그를 IP별 집계(DataFrame)
- `spark/IPCountTxt.scala`: 텍스트 로그를 IP별 집계(RDD)
- `spark/BotDetection.scala`: 분당 요청 수 기반 봇 탐지
- `spark/GeoIPCountryAnalysis.scala`: GeoIP UDF 기반 국가별 집계
- `spark/GeoIPCountryAnalysis_MapPartitions.scala`: mapPartitions로 GeoIP 성능 최적화

### Spark SQL 분석
- `sql/SyntaxPractice.scala`: SQL 기본 구문 실습
- `sql/WebLogAnalysis.scala`: 웹 로그 분석(상태코드/시간대/Top URL)
- `sql/ClickstreamAnalysis.scala`: 페이지 전환/세션화/페이지뷰 분석
- `sql/SalesAnalysis.scala`: 주문 데이터 매출 분석(일/월/카테고리/상품)
- `sql/ETL_Cleaning.scala`: dirty_orders.csv 정제 후 Parquet 저장

### Spark ML
- `ml/PurchasePrediction.scala`: 규칙 기반 라벨링 + 구매 예측 모델 학습
- `ml/PurchaseTrain.scala`: 학습 전용 파이프라인 + 모델 저장
- `ml/PurchasePredict.scala`: 저장된 모델로 구매 예측(추론)
- `ml/MakePredictParquet.scala`: 예측용 CSV 정제 Parquet 생성

## 샘플 데이터
`data/` 디렉터리에 실습용 파일이 포함되어 있습니다.
- `novel.txt`, `apache_log.txt/json`, `clickstream.json`, `orders.csv`, `dirty_orders.csv` 등

## 참고 사항
- 코드 내 기본 입력 경로는 **HDFS** 기준입니다. 로컬 파일을 사용할 경우 경로를 수정하세요.
- GeoIP 분석 예제는 `src/main/resources/GeoLite2-Country.mmdb`를 사용합니다.
