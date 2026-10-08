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
