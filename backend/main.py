import datetime
import os
import re
import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="Schedule Manager AI Backend")

# 안드로이드 앱 및 로컬 테스트 환경과의 통신을 위한 CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==========================================
# 1. 고도화된 머신러닝 AI 모델 패키지 로드
# ==========================================
MODEL_PATH = "best_timetable_model.pkl"

if os.path.exists(MODEL_PATH):
    try:
        model_packet = joblib.load(MODEL_PATH)
        ml_model = model_packet["model"]
        vectorizer = model_packet["vectorizer"]
        print(
            "✨ [AI 모델] 단어 문맥 분석기(TF-IDF)를 포함한 고도화 AI 모델 로드 완료!"
        )
    except Exception as e:
        print(f"⚠️ [AI 모델] 모델 파일 로드 중 오류 발생: {e}")
        ml_model = None
        vectorizer = None
else:
    print(
        f"⚠️ [AI 모델] '{MODEL_PATH}' 파일이 존재하지 않습니다. 규칙 기반 기본값으로 작동합니다."
    )
    ml_model = None
    vectorizer = None


# ==========================================
# 2. 요청 및 응답 데이터 규격 정의 (Pydantic)
# ==========================================
class NlpRequest(BaseModel):
    text: str


class NlpResponse(BaseModel):
    title: str
    scheduled_date: int  # 안드로이드 LocalDate.ofEpochDay() 연동용 정수형 일수
    start_time_minutes: int  # 하루 중 시작 시간 (분 단위)
    duration_minutes: int  # AI 모델이 예측한 소요 시간 (분 단위)


# ==========================================
# 3. 자연어 일정 분석 엔드포인트
# ==========================================
@app.post("/nlp-analyze", response_model=NlpResponse)
async def analyze_natural_language(request: NlpRequest):
    sentence = request.text.strip()
    if not sentence:
        raise HTTPException(status_code=422, detail="입력 문장이 비어 있습니다.")

    print(f"📥 [분석 요청 수신]: '{sentence}'")

    # ------------------------------------------
    # 파트 A: 핵심 제목(Title) 추출 및 정제
    # ------------------------------------------
    # 날짜, 시간 키워드를 제거하여 순수 일정 제목만 남깁니다.
    cleaned_title = sentence
    time_keywords = [
        "오늘",
        "내일",
        "오후",
        "오전",
        "낮",
        "밤",
        "새벽",
        r"\d+\s*시",
        r"\d+\s*분",
        "일곱시",
        "세시",
        "한시",
        "두시",
    ]
    for kw in time_keywords:
        cleaned_title = re.sub(kw, "", cleaned_title)

    # 불필요한 공백 제거
    cleaned_title = re.sub(r'\s+', ' ', cleaned_title).strip()
    if not cleaned_title:
        cleaned_title = "AI 분석 일정"

    # ------------------------------------------
    # 파트 B: 날짜(Scheduled Date) 분석 -> 정확한 Epoch Day 일수 계산
    # ------------------------------------------
    today = datetime.date.today()
    epoch_start = datetime.date(1970, 1, 1)

    if "내일" in sentence:
        target_date = today + datetime.timedelta(days=1)
    elif "오늘" in sentence:
        target_date = today
    else:
        # 문장에 날짜 명시가 없으면 기본적으로 오늘 날짜 지정
        target_date = today

    # ⭐ 안드로이드 수신 규격 불일치 해결의 핵심: 정수형 Epoch Day 일수(Int) 계산
    scheduled_date_epoch = int((target_date - epoch_start).days)

    # ------------------------------------------
    # 파트 C: 시작 시간(Start Time Minutes) 분석
    # ------------------------------------------
    start_time_minutes = 540  # 분석 실패 시 기본값 오전 9시 (9 * 60)

    # 정규식을 이용해 "7시", "19시" 같은 숫자 형태 추출
    time_match = re.search(r"(\d+)\s*시", sentence)

    if time_match:
        hour = int(time_match.group(1))
        # "오후"가 들어가 있고 시간이 12보다 작으면 12를 더해 24시간제로 변환
        if "오후" in sentence and hour < 12:
            hour += 12
        # "오전"이나 "오후" 표시가 없더라도 문맥상 '저녁'이 들어가면 야간 시간으로 보정
        elif "저녁" in sentence and hour < 12:
            hour += 12

        start_time_minutes = hour * 60
    else:
        # 한글 텍스트 시간 패턴 예외 처리
        if "3시" in sentence or "세시" in sentence:
            start_time_minutes = (
                15 * 60 if "오후" in sentence or "미팅" in sentence else 3 * 60
            )
        elif "7시" in sentence or "일곱시" in sentence:
            start_time_minutes = (
                19 * 60 if "오후" in sentence or "저녁" in sentence else 7 * 60
            )

    # ------------------------------------------
    # 파트 D: AI 머신러닝 모델 기반 소요 시간(Duration) 예측
    # ------------------------------------------
    duration_minutes = 60  # AI 로드 실패 시 기본 지속 시간 (1 hours)

    if ml_model is not None and vectorizer is not None:
        try:
            # 1. train.py와 동일한 방식으로 단어 임베딩 변환
            text_feat = vectorizer.transform([sentence]).toarray()

            # 2. 추가 구조적 피처 결합
            length = len(sentence)
            has_meeting = (
                1.0 if any(k in sentence for k in ["미팅", "회의", "면담"]) else 0.0
            )
            has_study = (
                1.0
                if any(k in sentence for k in ["공부", "과제", "시험", "인강"])
                else 0.0
            )
            extra_feat = np.array([[length, has_meeting, has_study]])

            # 3. 피처 병합 및 앙상블 트리 예측 진행
            final_features = np.hstack((text_feat, extra_feat))
            prediction = ml_model.predict(final_features)

            # 예측 결과를 정수형 분 단위로 캐스팅
            duration_minutes = max(10, int(prediction[0]))  # 최소 10분 보장
            print(f"🤖 [AI 모델 예측 완료]: 소요시간 {duration_minutes}분 소요 예상")
        except Exception as e:
            print(
                f"⚠️ [AI 모델 예측 실패]: 예외 발생으로 기본값(60분) 대체 / 원인: {e}"
            )

    # ------------------------------------------
    # 파트 E: 최종 호환성 규격 JSON 반환
    # ------------------------------------------
    response_data = NlpResponse(
        title=cleaned_title,
        scheduled_date=scheduled_date_epoch,
        start_time_minutes=start_time_minutes,
        duration_minutes=duration_minutes,
    )

    print(f"📤 [분석 완료 및 응답 전송]: {response_data.dict()}")
    return response_data


# 로컬 테스트 실행용 진입점
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main.py:app", host="0.0.0.0", port=8000, reload=True)
