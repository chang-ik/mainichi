import { request } from './http';
import type { Rating, StudyKind, StudyQueue, StudySettings } from './types';

export const studyApi = {
  /** 지금 공부할 카드와 오늘 남은 개수 */
  queue: (kind: StudyKind) => request<StudyQueue>(`/study/${kind}`),
  /** 카드를 평가하고 같은 종류의 다음 카드를 받는다. durationMs: 카드를 보고 평가하기까지 걸린 시간(통계용) */
  review: (kind: StudyKind, cardId: string, rating: Rating, durationMs?: number) =>
    request<StudyQueue>(`/study/${kind}/${cardId}/review`, { method: 'POST', body: { rating, durationMs } }),
  /** 하루 학습량 */
  settings: () => request<StudySettings>('/study/settings'),
  updateSettings: (newCardsPerDay: number) =>
    request<StudySettings>('/study/settings', { method: 'PATCH', body: { newCardsPerDay } }),
};
