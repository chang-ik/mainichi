// 백엔드 DTO (LoginResponse / RefreshResponse / UserResponse) 와 1:1 대응

/** 학습 영역. WORD(단어)는 항상 포함된다. */
export type StudyArea = 'WORD' | 'GRAMMAR' | 'READING' | 'LISTENING';

/** 카드로 공부하는 종류 = API 경로 /study/{kind} */
export type StudyKind = 'words' | 'grammar';

export interface User {
  id: string;
  nickname: string | null;
  email: string | null;
  provider: string;
  /** 0=기초, 1~5=N1~N5 */
  currentLevel: number;
  timezone: string;
  /** 단어를 포함한 학습 영역 (WORD 가 맨 앞) */
  studyAreas: StudyArea[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  isNewUser: boolean;
  user: User;
}

export interface RefreshResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface UpdateProfileRequest {
  nickname?: string;
  timezone?: string;
  /** 0=기초, 1~5=N1~N5 */
  currentLevel?: number;
  /** WORD 는 보내지 않아도 항상 포함된다 */
  studyAreas?: StudyArea[];
}

// ---- 학습 설정 (StudySettingsResponse) ----

export interface StudySettings {
  /** 하루 학습량 = 하루에 새로 꺼낼 카드 수 (1~100) */
  newCardsPerDay: number;
  defaultNewCardsPerDay: number;
}

// ---- 카드 학습: 단어·문법 (StudyQueueResponse / StudyCardResponse) ----

export type CardPhase = 'NEW' | 'LEARNING' | 'REVIEW' | 'RELEARNING';

/** 1=다시(✕) 2=어려움(△) 3=알맞음(○) 4=쉬움(◎) */
export type Rating = 1 | 2 | 3 | 4;

export interface StudyCard {
  id: string;
  phase: CardPhase;
  /** 후리가나 대괄호 표기. 예) 食[た]べる */
  front: string;
  meaning: string;
  example: string;
  exampleMeaning: string;
  /** 문법 카드의 접속 형태 (예: 동사 て형 + もいい). 단어 카드는 null */
  usage: string | null;
  /** 각 버튼을 눌렀을 때 다음 복습까지 간격(초) */
  intervals: { again: number; hard: number; good: number; easy: number };
}

export interface StudyQueue {
  counts: { newCards: number; learning: number; review: number };
  card: StudyCard | null;
  /** card 가 null 인데 오늘 다시 볼 학습 중 카드가 있으면 그때까지 남은 초 */
  nextDueInSeconds: number | null;
}

// ---- 독해·청해: Claude 가 만든 JLPT 형식 문제 (QuizResponse / TodayQuizResponse) ----

/** API 경로 /quiz/{kind} */
export type QuizKind = 'reading' | 'listening';

export interface QuizSourceCard {
  /** 후리가나 대괄호 표기 */
  front: string;
  meaning: string;
  usage: string | null;
  grammar: boolean;
}

export interface ListeningLine {
  speaker: 'MALE' | 'FEMALE';
  /** 후리가나 대괄호 표기 */
  text: string;
}

export interface QuizQuestion {
  /** 후리가나 대괄호 표기 */
  question: string;
  choices: string[];
  /** 아래 셋은 풀기 전에는 null */
  selectedIndex: number | null;
  answerIndex: number | null;
  explanation: string | null;
}

export interface Quiz {
  id: string;
  kind: 'READING' | 'LISTENING';
  level: number;
  title: string;
  /** 독해 지문 / 청해 상황 설명. 후리가나 대괄호 표기, 문단은 빈 줄로 구분 */
  passage: string;
  /** 청해 대화 (독해는 빈 배열). 소리로 읽어야 해서 풀기 전에도 오지만 화면에는 풀고 나서 보여준다 */
  script: ListeningLine[];
  /** 한국어 해석. 풀기 전에는 null */
  translation: string | null;
  sourceCards: QuizSourceCard[];
  questions: QuizQuestion[];
  submitted: boolean;
  correctCount: number | null;
}

export interface TodayQuiz {
  /** 오늘 새로 배운 카드 수 (단어+문법) */
  newCardCount: number;
  /** 문제를 만들 수 있는 최소 카드 수 (청해는 0) */
  minNewCards: number;
  /** 오늘 더 만들 수 있는 문제 수 */
  remainingToday: number;
  /** 오늘 채점까지 끝낸 문제 수 */
  solvedToday: number;
  quiz: Quiz | null;
}

// ---- 통계 (StatsResponse): 날짜는 유저 시간대 기준 YYYY-MM-DD ----

export type StatsDeck = 'all' | 'words' | 'grammar';
export type StatsDays = 30 | 90 | 365;

export interface StatsBucket {
  label: string;
  count: number;
}

export interface StatsHistogram {
  buckets: StatsBucket[];
  average: number | null;
  max: number | null;
}

export interface Stats {
  deck: StatsDeck;
  days: StatsDays;
  today: string;
  todayStats: {
    reviews: number;
    seconds: number;
    newCards: number;
    learning: number;
    review: number;
    relearning: number;
    again: number;
    correctRate: number | null;
    matureReviews: number;
    matureCorrectRate: number | null;
  };
  streak: { current: number; longest: number; studiedDays: number; periodDays: number };
  /** 최근 1년 중 복습한 날만 */
  calendar: { date: string; count: number }[];
  /** 기간의 하루하루 (오래된 날부터) */
  reviews: { date: string; learning: number; review: number; relearning: number; newCards: number; seconds: number }[];
  reviewSummary: { total: number; daysStudied: number; averagePerStudiedDay: number; averagePerDay: number; seconds: number };
  /** due[0] = 오늘(밀린 카드 포함) */
  forecast: { due: number[]; dueTomorrow: number; overdue: number; dailyLoad: number };
  cardCounts: { newCards: number; learning: number; relearning: number; young: number; mature: number };
  /** 일 */
  intervals: StatsHistogram;
  /** 일 */
  stability: StatsHistogram;
  /** % */
  difficulty: StatsHistogram;
  /** % */
  retrievability: StatsHistogram;
  hours: { hour: number; count: number; correctRate: number | null }[];
  /** counts[0]=다시 … counts[3]=쉬움 */
  buttons: { group: 'LEARNING' | 'YOUNG' | 'MATURE'; counts: number[]; correctRate: number | null }[];
  retention: { label: string; youngPass: number; youngFail: number; maturePass: number; matureFail: number }[];
}

// ---- 게시판 (PostPageResponse / PostDetailResponse): 학습 단계별 ----

export interface PostSummary {
  id: string;
  title: string;
  /** 작성자 닉네임 */
  author: string | null;
  /** 서버 시각 (YYYY-MM-DDTHH:mm:ss) */
  createdAt: string;
  commentCount: number;
}

export interface PostPage {
  /** 0=기초, 1~5=N1~N5 */
  level: number;
  posts: PostSummary[];
  /** 0부터 */
  page: number;
  totalPages: number;
  totalPosts: number;
}

export interface BoardComment {
  id: string;
  content: string;
  author: string | null;
  createdAt: string;
  /** 내가 쓴 댓글 (삭제 버튼 표시) */
  mine: boolean;
}

export interface PostDetail {
  id: string;
  level: number;
  title: string;
  content: string;
  author: string | null;
  createdAt: string;
  /** 쓴 뒤 고쳤는지 */
  edited: boolean;
  /** 내가 쓴 글 (수정·삭제 버튼 표시) */
  mine: boolean;
  comments: BoardComment[];
}
