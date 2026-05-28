# train.py
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
import joblib

print("🎲 1. 가상 가중치 학습 데이터셋 시뮬레이션 생성 중...")

# 학부생 수준 프로젝트 증명을 위한 가상 사용자 행동 패턴 데이터 150줄 생성
# 카테고리 규격: 1=공부, 2=운동, 3=여가, 4=회의/잡일
np.random.seed(42)
data_size = 150

categories = np.random.choice([1, 2, 3, 4], size=data_size)
durations = []
titles_length = np.random.randint(4, 25, size=data_size)
target_minutes = []

for cat in categories:
    if cat == 1:  # 공부: 주로 아침~오후 (소요 시간 길음)
        durations.append(np.random.choice([90, 120, 180]))
        target_minutes.append(np.random.randint(540, 960))  # 09:00 ~ 16:00
    elif cat == 2:  # 운동: 주로 퇴근/하교 후 저녁
        durations.append(np.random.choice([60, 90]))
        target_minutes.append(np.random.randint(1020, 1260))  # 17:00 ~ 21:00
    elif cat == 3:  # 여가: 주로 주말이나 늦은 저녁
        durations.append(np.random.choice([60, 120]))
        target_minutes.append(np.random.randint(1140, 1380))  # 19:00 ~ 23:00
    else:  # 회의/잡일: 주로 낮 시간대
        durations.append(np.random.choice([30, 60]))
        target_minutes.append(np.random.randint(660, 1080))  # 11:00 ~ 18:00

# 데이터프레임 구조화
df = pd.DataFrame(
    {
        'category_id': categories,
        'duration_minutes': durations,
        'title_length': titles_length,
        'target_minutes': target_minutes,
    }
)

X = df[['category_id', 'duration_minutes', 'title_length']]
y = df['target_minutes']

print("🤖 2. 랜덤 포레스트(Random Forest Regressor) 모델 생성 및 지도 학습 개시...")
# 시간대 수치 예측을 위한 회귀 알고리즘 튜닝
model = RandomForestRegressor(n_estimators=100, random_state=42)
model.fit(X, y)

# 가중치 파일로 디스크 저장 고정
model_filename = "best_timetable_model.pkl"
joblib.dump(model, model_filename)
print(f"✅ 3. 머신러닝 학습 완료! 가중치 파일 저장 성공 ➡️ {model_filename}")
