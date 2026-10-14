import { request } from './http';
import type { Stats, StatsDays, StatsDeck } from './types';

export const statsApi = {
  get: (deck: StatsDeck, days: StatsDays) => request<Stats>(`/stats?deck=${deck}&days=${days}`),
};
