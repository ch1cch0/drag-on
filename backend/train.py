import joblib
import numpy as np
from sklearn.ensemble import RandomForestRegressor

# 1. 간단한 학습 데이터셋 준비 (문장 텍스트 -> 실제 예상 소요시간 분 단위)
# 학습 데이터를 추가할수록 모델이 더 정교해집니다.
train_sentences = [
    "내일 오후 3시 캡스톤 미팅",
    "알고리즘 과제 제출하기",
    "컴퓨터 아키텍처 시험 공부 2시간 하기",
    "운영체제 족보 훑어보기",
    "친구와 저녁 약속",
    "방 청소 및 빨래 돌리기",
]
# 각 문장에 대응하는 소요 시간(분) 정답 레이블
train_durations = [60, 30, 120, 45, 90, 40]


# 2. 텍스트 문장을 수치형 피처(Feature)로 변환하는 함수
def extract_features(sentences):
    features = []
    for s in sentences:
        length = len(s)
        has_meeting = 1 if "미팅" in s or "회의" in s else 0
        has_study = 1 if "공부" in s or "과제" in s or "시험" in s else 0
        has_hour = 1 if "시간" in s else 0
        features.append([length, has_meeting, has_study, has_hour])
    return np.array(features)


X_train = extract_features(train_sentences)
y_train = np.array(train_durations)

# 3. 모델 선언 및 학습 (RandomForestRegressor)
print("🤖 AI 모델 학습을 시작합니다...")
model = RandomForestRegressor(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

# 4. 학습된 모델 파일(.pkl)로 저장하기
model_filename = "best_timetable_model.pkl"
joblib.dump(model, model_filename)
print(f"✅ 모델 학습 완료 및 저장 성공: {model_filename}")
