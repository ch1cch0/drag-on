import datetime
import joblib
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional

app = FastAPI(title="AI Schedule Manager Backend")


# 안드로이드 앱의 NlpRequest 규격과 일치 (text 필드 사용)
class NlpRequest(BaseModel):
    text: str


# 안드로이드 앱의 NlpResponse 규격과 일치
class NlpResponse(BaseModel):
    title: str
    durationMinutes: Optional[int] = 60
    scheduledDate: Optional[int] = None
    startTimeMinutes: Optional[int] = None


# 학습시킨 머신러닝 모델 로드
try:
    ml_model = joblib.load("best_timetable_model.pkl")
    print("✨ 학습된 AI 머신러닝 모델 로드 완료!")
except:
    print("⚠️ 모델 파일이 없습니다. train.py를 먼저 실행해 주세요.")
    ml_model = None


# 학습 때와 동일한 규칙의 피처 추출 함수
def extract_single_feature(s: str):
    length = len(s)
    has_meeting = 1 if "미팅" in s or "회의" in s else 0
    has_study = 1 if "공부" in s or "과제" in s or "시험" in s else 0
    has_hour = 1 if "시간" in s else 0
    return np.array([[length, has_meeting, has_study, has_hour]])


@app.post("/nlp-analyze", response_model=NlpResponse)
async def analyze_natural_language(request: NlpRequest):
    sentence = request.text.strip()

    # 1. 조사 및 시간 키워드를 정제하여 일정 제목 생성
    filter_words = ["내일", "오늘", "오전", "오후", "시에", "에서", "에"]
    cleaned_title = sentence
    for word in filter_words:
        cleaned_title = cleaned_title.replace(word, "")
    cleaned_title = cleaned_title.strip()
    if not cleaned_title:
        cleaned_title = "새로운 AI 일정"

    # 2. 날짜 분석 (Epoch Day 형태 계산)
    scheduled_date = None
    today = datetime.date.today()
    if "내일" in sentence:
        tomorrow = today + datetime.timedelta(days=1)
        scheduled_date = (tomorrow - datetime.date(1970, 1, 1)).days
    elif "오늘" in sentence:
        scheduled_date = (today - datetime.date(1970, 1, 1)).days

    # 3. 시간 분석 (하루 중 분 단위 계산)
    start_time_minutes = None
    words = sentence.split()
    for word in words:
        if "시" in word:
            try:
                time_num = int("".join(filter(str.isdigit, word)))
                if "오후" in sentence and time_num < 12:
                    time_num += 12
                elif "오전" in sentence and time_num == 12:
                    time_num = 0
                start_time_minutes = time_num * 60
                break
            except ValueError:
                continue

    # 4. 내 학습 모델을 이용해 소요 시간(durationMinutes) 예측하기
    duration_result = 60  # 기본값
    if ml_model is not None:
        features = extract_single_feature(sentence)
        prediction = ml_model.predict(features)
        duration_result = int(prediction[0])

    return NlpResponse(
        title=cleaned_title,
        durationMinutes=duration_result,
        scheduledDate=scheduled_date,
        startTimeMinutes=start_time_minutes,
    )


if __name__ == "__main__":
    import uvicorn

    # 외부 배포(Render) 혹은 로컬 테스트를 위해 호스트와 포트 설정
    uvicorn.run(app, host="0.0.0.0", port=10000)
