import { request } from './http';
import type { LoginResponse } from './types';

export const authApi = {
  /** 카카오 인가 코드를 백엔드에 넘기면, 백엔드가 카카오와 교환해 우리 서비스 토큰을 발급한다. */
  loginWithKakao: (code: string) =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: { provider: 'kakao', code },
    }),

  /** 백엔드는 로그아웃도 Bearer 인증이 필요하다 (만료 시 http 계층이 갱신 후 재시도). */
  /** 리프레시 토큰은 쿠키로 전송되고, 서버가 폐기 후 쿠키를 지운다. */
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
};
