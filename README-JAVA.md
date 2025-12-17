# Receipt Extractor - Java Version

ExtReceipt.ipynb의 Java 구현 버전입니다. Gemini API를 사용하여 영수증 PDF에서 데이터를 추출합니다.

## 요구사항

- Java 17 이상
- Maven 3.6 이상
- Gemini API Key

## 프로젝트 구조

```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── verifyslip/
│   │           ├── ReceiptExtractor.java  # 메인 클래스
│   │           └── Config.java            # 설정 클래스
│   └── resources/
├── test/
│   └── java/
pom.xml                                    # Maven 설정 파일
```

## 설치 및 실행

### 1. 환경 변수 설정

`.env` 파일에 Gemini API Key를 설정합니다:

```bash
GEMINI_API_KEY=your_api_key_here
```

### 2. 의존성 설치 및 빌드

```bash
mvn clean install
```

### 3. 실행

#### 방법 1: Maven을 통한 실행

```bash
mvn exec:java -Dexec.mainClass="com.verifyslip.ReceiptExtractor"
```

#### 방법 2: JAR 파일 실행

```bash
# JAR 파일 생성
mvn package

# 실행
java -jar target/receipt-extractor-1.0.0.jar
```

## 설정 수정

`src/main/java/com/verifyslip/Config.java` 파일에서 다음 설정을 수정할 수 있습니다:

- `PDF_PATH`: 입력 PDF 파일 경로
- `JSON_PATH`: 출력 JSON 파일 경로
- `MODEL_NAME`: Gemini 모델 이름 (기본: gemini-2.5-pro)

## 주요 기능

1. **PDF 파일 업로드**
   - 한글 파일명 처리를 위한 임시 파일 생성
   - Gemini File API를 통한 업로드

2. **영수증 데이터 추출**
   - 사전 정의된 프롬프트를 사용하여 구조화된 데이터 추출
   - 기본 결제 정보, 가맹점 정보, 상세 결제 내역, 품목 상세 추출

3. **JSON 응답 처리**
   - 마크다운 코드 블록 제거
   - JSON 파싱 및 파일 저장

4. **실행 시간 측정**
   - 전체 프로세스의 실행 시간을 HH:MM:SS.mmm 형식으로 출력

## Python 버전과의 차이점

- **의존성 관리**: pip → Maven
- **환경 변수**: python-dotenv → dotenv-java
- **JSON 처리**: json 모듈 → Gson
- **HTTP 클라이언트**: requests → OkHttp3
- **로깅**: print → SLF4J

## 의존성

- `com.google.code.gson:gson` - JSON 처리
- `io.github.cdimascio:dotenv-java` - 환경 변수 관리
- `com.squareup.okhttp3:okhttp` - HTTP 클라이언트
- `org.slf4j:slf4j-api` & `slf4j-simple` - 로깅

## 문제 해결

### API 키 오류
```
GEMINI_API_KEY not found in environment variables
```
→ `.env` 파일이 프로젝트 루트에 있는지 확인하고, `GEMINI_API_KEY`가 설정되어 있는지 확인하세요.

### 파일 업로드 실패
```
파일 업로드 실패: 404
```
→ API 키가 유효한지, PDF 파일 경로가 올바른지 확인하세요.

### 빌드 오류
```
[ERROR] Source option 17 is no longer supported
```
→ Java 17 이상이 설치되어 있는지 확인하세요.

## 라이선스

이 프로젝트는 원본 Python 버전과 동일한 라이선스를 따릅니다.
