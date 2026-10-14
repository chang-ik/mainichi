import { request } from './http';
import type { Quiz, QuizKind, TodayQuiz } from './types';

export const quizApi = {
  /** 오늘 새로 배운 카드 수와 오늘 만든 최근 문제 */
  today: (kind: QuizKind) => request<TodayQuiz>(`/quiz/${kind}/today`),
  /** 새 문제를 만든다 (수십 초 걸릴 수 있다). 안 푼 문제가 있으면 그 문제를 준다 */
  create: (kind: QuizKind) => request<Quiz>(`/quiz/${kind}`, { method: 'POST' }),
  /** 답을 내고 정답·해설·해석을 받는다 */
  submit: (kind: QuizKind, quizId: string, answers: number[]) =>
    request<Quiz>(`/quiz/${kind}/${quizId}/answers`, { method: 'POST', body: { answers } }),
};
