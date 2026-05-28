# main.py
from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import numpy as np

app = FastAPI(title="AI Schedule Manager Backend")


# 안드로이드의 NlpRequest 구조와 100% 매핑되는 Pydantic 모델 선언
class NlpRequest(BaseModel):
    text: str


# 서버 시작 시 머신러닝 가중치 로드
try:
    ml_model = joblib.load("best_timetable_model.pkl")
    print("🤖 [AI 모델 갱신] 랜덤 포레스트 가중치 파일 로드 성공!")
except Exception as e:
    ml_model = None
    print(
        f"⚠️ [경고] 학습 모델 파일을 찾을 수 없습니다. 기본값으로 구동됩니다. 에러: {e}"
    )


@app.post("/nlp-analyze")
def analyze_natural_language(request: NlpRequest):
    """
    안드로이드 인박스에서 전송한 자연어 문장을 파싱하고
    과거 로그 기반 머신러닝 스코어를 결합하여 추천 시간대를 반환합니다.
    """
    raw_text = request.text.strip()
    print(f"📩 안드로이드 수신 문장: {raw_text}")

    # 1. 자연어 전처리 파이프라인 (Rule-based NLP Feature Extraction)
    parsed_date = "오늘"
    if "내일" in raw_text:
        parsed_date = "내일"
    elif "모레" in raw_text:
        parsed_date = "모레"

    # 카테고리 및 기본 학습 피처 가중치 매핑
    category_id = 4
    parsed_category = "잡일"
    duration_mock = 60  # 기본 소요 시간

    if any(w in raw_text for w in ["공부", "과제", "시험", "알고리즘"]):
        category_id = 1
        parsed_category = "공부"
        duration_mock = 90
    elif any(w in raw_text for w in ["운동", "헬스", "러닝", "스쿼트"]):
        category_id = 2
        parsed_category = "운동"
        duration_mock = 60
    elif any(w in raw_text for w in ["약속", "영화", "커피", "데이트", "쉬기"]):
        category_id = 3
        parsed_category = "여가"
        duration_mock = 120
    elif any(w in raw_text for w in ["회의", "팀플", "미팅"]):
        category_id = 4
        parsed_category = "회의"
        duration_mock = 60

    # 순수 제목 추출을 위해 시점 및 조사 키워드 마스킹 제거
    cleaned_title = raw_text
    for filter_word in ["내일", "오늘", "모레", "에", "하고", "하기", "해야지"]:
        cleaned_title = cleaned_title.replace(filter_word, "")
    cleaned_title = cleaned_title.strip()
    if not cleaned_title:
        cleaned_title = "새로운 일정"

    # 2. 머신러닝(ML) 최적 슬롯 추론 (Inference)
    # 기본 fallback 값: 오후 2시 (840분)
    recommended_minutes = 840

    if ml_model is not None:
        # 학습 피처 매핑 구조: [[category_id, duration_minutes, title_length]]
        features = np.array([[category_id, duration_mock, len(cleaned_title)]])

        # 모델 예측 실행
        prediction = ml_model.predict(features)

        # 예측된 분(Minutes) 단위를 타임테이블의 15분 블록 시스템 단위로 보정 처리
        predicted_raw_min = int(prediction[0])
        recommended_minutes = (predicted_raw_min // 15) * 15

        # 06:00 ~ 24:00 예외 스케일 차단 처리
        recommended_minutes = max(360, min(recommended_minutes, 1425))

    print(
        f"🤖 [AI 분석 완료] 제목: {cleaned_title} | 카테고리: {parsed_category} | 추천 시간(분): {recommended_minutes}"
    )

    # 안드로이드 NlpResponse DTO 구조와 형식을 맞춰 반환
    return {
        "parsedTitle": cleaned_title,
        "parsedDate": parsed_date,
        "parsedCategory": parsed_category,
        "recommendedTimeSlot": recommended_minutes,
    }


# 앱 측 하위 호환성을 위한 범용 predict 엔드포인트 유지
@app.post("/predict")
def predict_fallback():
    return {"recommended_day": 1, "recommended_time_slot": 840, "is_ai": True}
