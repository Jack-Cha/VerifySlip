import os
from dotenv import load_dotenv

load_dotenv()

# API Configuration
# 환경 변수에서 가져오기 (시스템 환경 변수 또는 .env 파일)
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
if not GOOGLE_API_KEY:
    raise ValueError("GOOGLE_API_KEY 환경 변수가 설정되지 않았습니다. 시스템 환경 변수 또는 .env 파일에 설정하세요.")

# Model Configuration
# 최신 안정 버전 모델 사용
# gemini-2.5-flash: 최신 고속 모델 (권장)
# gemini-2.5-pro: 최고 성능 모델
GEMINI_MODEL_NAME = 'gemini-2.5-flash'

# File Paths
INPUT_PDF_PATH = r".\data\영수증_PDF\1. 2025-11_지출결의영수증_지수현.pdf"
OUTPUT_JSON_PATH = r".\output\1. 2025-11_지출결의영수증_지수현.json"

# Poppler Configuration (Windows 사용자 필수)
# Poppler 설치 경로의 bin 폴더를 지정하세요. (예: r"C:\Program Files\poppler-24.02.0\Library\bin")
# 시스템 PATH에 등록되어 있다면 None으로 두어도 됩니다.
POPPLER_PATH = None

# Prompts
RECEIPT_PROMPT = """
# 영수증 필수 추출 항목 (Extract Items)

`Verify-Rule.md`에 정의된 검증 규칙을 수행하기 위해 OCR(광학 문자 인식) 또는 파싱을 통해 영수증 이미지/파일에서 반드시 추출해야 하는 데이터 항목들입니다.
다음 항목을 추출하여 JSON 형식으로 반환해 주세요.

## 1. 기본 결제 정보 (Basic Transaction Info)
가장 핵심적인 검증 대상이며, 지출 내역 매칭에 사용됩니다.

- **거래 일자 (Date)**
  - *형식:* YYYY-MM-DD
  - *용도:* 신청 내역 날짜 비교, 주말/공휴일 사용 여부 확인
- **거래 시간 (Time)**
  - *형식:* HH:MM:SS (또는 HH:MM)
  - *용도:* 심야 시간 사용 여부, 분할 결제(시간차 공격) 탐지
- **합계 금액 (Total Amount)**
  - *형식:* 숫자 (원화)
  - *용도:* 신청 금액 일치 여부 확인
- **승인 번호 (Approval Code / Authorization No)**
  - *형식:* 문자열/숫자
  - *용도:* 중복 제출 방지, 유니크 키 식별, 법인카드 승인 내역 대조

## 2. 가맹점 정보 (Vendor Info)
지출처의 적격성과 실제 방문 여부를 판단합니다.

- **가맹점 명 (Vendor Name / Merchant Name)**
  - *용도:* 신청 내역의 사용처와 비교
- **사업자 등록 번호 (Tax ID / Business Registration No)**
  - *형식:* 000-00-00000
  - *용도:* 세무 증빙 유효성 확인, 휴폐업 조회
- **가맹점 주소 (Vendor Address)**
  - *용도:* 실제 가맹점 위치 확인 (선택적)
- **전화 번호 (Phone Number)**
  - *용도:* 가맹점 식별 보조 정보 (선택적)

## 3. 상세 결제 내역 (Payment Details)
금액의 구성 요소와 결제 수단을 검증합니다.

- **공급가액 (Supply Value / Net Amount)**
  - *용도:* 금액 무결성 검증 (공급가액 + 부가세 = 합계)
- **부가세 (VAT / Tax)**
  - *용도:* 금액 무결성 검증, 매입세액 공제 확인
- **봉사료 (Service Charge)**
  - *용도:* 금액 합산 검증 (있을 경우)
- **카드 번호 (Card Number)**
  - *형식:* 마스킹 된 번호 (예: 1234-****-****-5678)
  - *용도:* 법인카드 사용 여부 확인

## 4. 품목 상세 (Line Items)
구매한 물품이 회사 규정에 부합하는지 확인합니다.

- **품목 명 (Item Name)**
  - *용도:* 금지 품목(주류, 담배 등) 키워드 검색
- **단가 및 수량 (Unit Price & Quantity)**
  - *용도:* 상세 금액 계산 검증
- **품목별 금액 (Item Total)**
  - *용도:* 품목 합계가 총 합계와 일치하는지 확인

## 5. 메타 데이터 (Meta Data)
- **OCR 신뢰도 (Confidence Score)**
  - *용도:* 데이터의 정확성 판단, 수동 검토 필요 여부 플래그
    """
