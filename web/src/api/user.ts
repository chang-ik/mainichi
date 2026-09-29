import { request } from './http';
import type { UpdateProfileRequest, User } from './types';

export const userApi = {
  me: () => request<User>('/user/me'),
  update: (body: UpdateProfileRequest) => request<User>('/user/me', { method: 'PATCH', body }),
  remove: () => request<void>('/user/me', { method: 'DELETE' }),
};
