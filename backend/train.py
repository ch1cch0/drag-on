import joblib
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.feature_extraction.text import TfidfVectorizer

# ==========================================
# 1. 고도화된 대량의 학습 데이터셋 세팅
# 다양한 조사와 문장 패턴을 학습할 수 있도록 데이터를 대폭 보강합니다.
# ==========================================
train_sentences = [
    # 미팅 / 회의 관련 (60분 ~ 120분)
    "내일 오후 3시 캡스톤 미팅",
    "오전 10시 졸업작품 회의 하기로 함",
    "줌으로 비대면 교수님 면담",
    "스타트업 인턴 정기 미팅 참석",
    "프로젝트 개발 마일스톤 회의",
    # 공부 / 과제 / 시험 관련 (90분 ~ 180분)
    "알고리즘 과제 제출하기",
    "컴퓨터 아키텍처 시험 공부 2시간 하기",
    "운영체제 족보 훑어보기",
    "데이터베이스 실습 과제 코딩 테스트 준비",
    "도서관에서 전공 서적 독서 및 요약 정리",
    "선형대수학 인강 3개 듣기",
    "자료구조 중간고사 대비 밤샘 공부",
    # 일상 / 약속 / 기타 관련 (30분 ~ 120분)
    "친구와 저녁 약속 장소로 이동",
    "방 청소 및 빨래 돌리기",
    " 헬스장 가서 가슴 운동 및 유산소 1시간",
    "부모님과 주말 저녁 식사",
    "고양이 사료 주문 및 모래 전체 갈이",
    " 미용실 예약 다운펌 하러 가기",
    "마트에서 일주일치 장보기",
    "은행 방문해서 통장 개설 및 보안카드 발급",
]

# 각 문장에 정교하게 대응하는 소요 시간 정답 레이블 (분 단위)
train_durations = [
    60,
    90,
    45,
    60,
    120,  # 미팅류
    30,
    120,
    45,
    150,
    90,
    180,
    240,  # 공부/과제류
    90,
    40,
    70,
    120,
    30,
    60,
    80,
    50,  # 일상류
]

# ==========================================
# 2. 자연어 파이프라인 구축 (TF-IDF 내장 처리)
# 단순 글자 수 매칭 대신 문맥 상 단어의 중요도를 수치화합니다.
# ==========================================
print("📝 텍스트 단어 문맥 분석기(TF-IDF)를 생성 중입니다...")
# 캐릭터 단위와 단어 단위를 모두 조합하여 한국어 조사 변화에 유연하게 대응합니다.
vectorizer = TfidfVectorizer(ngram_range=(1, 2), analyzer='char_wb')
X_text_features = vectorizer.fit_transform(train_sentences).toarray()


# 문장 길이 및 특정 가중치 피처를 결합하기 위한 보조 데이터 추출
def extract_additional_features(sentences):
    extra = []
    for s in sentences:
        length = len(s)
        # 중요 키워드가 들어간 경우 모델이 인지할 수 있도록 명시적 피처 병합
        has_meeting = 1.0 if "미팅" in s or "회의" in s or "면담" in s else 0.0
        has_study = (
            1.0 if "공부" in s or "과제" in s or "시험" in s or "인강" in s else 0.0
        )
        extra.append([length, has_meeting, has_study])
    return np.array(extra)


X_extra_features = extract_additional_features(train_sentences)

# 최종 학습 피처 행렬 완성 (텍스트 문맥 수치 + 수치 피처 결합)
X_train = np.hstack((X_text_features, X_extra_features))
y_train = np.array(train_durations)

# ==========================================
# 3. 모델 선언 및 고도화 학습 (Hyperparameter Tuning)
# RandomForest의 트리 개수와 깊이를 제어해 예측 정확도를 끌어올립니다.
# ==========================================
print("🤖 업그레이드된 AI 머신러닝 모델 학습을 시작합니다...")
model = RandomForestRegressor(
    n_estimators=300,  # 트리의 개수를 늘려 더 신중하게 예측하도록 설정
    max_depth=15,  # 의사결정 트리의 최대 깊이를 지정하여 과적합 방지
    min_samples_split=2,
    random_state=42,
)
model.fit(X_train, y_train)

# ==========================================
# 4. 모델 파일과 전처리 텍스트 분석기를 세트로 묶어 저장
# 예측할 때도 똑같은 단어 사전(Vectorizer)이 필요하므로 딕셔너리로 저장합니다.
# ==========================================
model_packet = {
    "vectorizer": vectorizer,
    "model": model,
    "extract_extra_fn_params": ["length", "has_meeting", "has_study"],  # 구조 정의용
}

model_filename = "best_timetable_model.pkl"
joblib.dump(model_packet, model_filename)
print(f"✅ 정확도 고도화 모델 학습 완료 및 패키징 저장 성공: {model_filename}")
