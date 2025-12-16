import json
import google.generativeai as genai
from pdf2image import convert_from_path
import config

# 1. API 키 설정
genai.configure(api_key=config.GOOGLE_API_KEY)

def process_receipt_with_llm(image):
    """
    이미지를 LLM(Gemini)에게 전송하여 구조화된 JSON 데이터를 받아옵니다.
    """
    # 비용 효율적인 Flash 모델 사용 (복잡한 문서는 Pro 권장)
    model = genai.GenerativeModel(config.GEMINI_MODEL_NAME)

    # 2. 프롬프트 엔지니어링 (가장 중요한 부분)
    # AI에게 수행할 역할과 출력 형식을 명확히 지정합니다.
    prompt = config.RECEIPT_PROMPT

    try:
        # 이미지와 프롬프트를 함께 전송
        response = model.generate_content([prompt, image])
        
        # 응답 텍스트 정리 (혹시 모를 공백이나 마크다운 제거)
        json_text = response.text.strip().replace("```json", "").replace("```", "")
        
        return json.loads(json_text)
    
    except Exception as e:
        print(f"LLM 처리 중 오류 발생: {e}")
        # 오류 발생 시 기본값 반환
        return {"store_name": "Error", "total_amount": 0, "error": str(e)}

def extract_data_from_pdf(pdf_path):
    # PDF를 이미지로 변환
    # Poppler 경로가 설정되어 있으면 사용, 아니면 PATH에서 찾음
    images = convert_from_path(pdf_path, poppler_path=config.POPPLER_PATH)
    results = []

    print(f"총 {len(images)}장의 영수증을 AI가 분석합니다...")

    for i, image in enumerate(images):
        print(f"[{i+1}/{len(images)}] 분석 중...")
        
        # LLM 호출
        data = process_receipt_with_llm(image)
        
        # 리스트로 반환된 경우 첫 번째 항목 사용 (또는 병합 로직 필요 시 수정)
        if isinstance(data, list):
            if data:
                data = data[0]
            else:
                data = {"store_name": "Error", "total_amount": 0, "error": "Empty list returned"}

        # ID 부여
        if isinstance(data, dict):
            data['id'] = i + 1
            results.append(data)
        else:
            print(f"  - 경고: 예상치 못한 데이터 형식 ({type(data)})")
            results.append({"id": i+1, "store_name": "Error", "total_amount": 0, "error": f"Invalid format: {type(data)}"})

    return results

# --- 실행부 ---
if __name__ == "__main__":
    input_pdf = config.INPUT_PDF_PATH
    output_json = config.OUTPUT_JSON_PATH

    final_data = extract_data_from_pdf(input_pdf)

    # 결과 저장
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(final_data, f, ensure_ascii=False, indent=2)

    print(f"\n완료! {output_json} 파일이 생성되었습니다.")
    
    # 결과 미리보기
    for item in final_data:
        store = item.get('store_name', 'Unknown')
        amount = item.get('total_amount')
        
        # 금액이 숫자형이 아니거나 None일 경우 처리
        if isinstance(amount, (int, float)):
            print(f"상호: {store} | 금액: {amount:,}원")
        else:
            print(f"상호: {store} | 금액: {amount}원")