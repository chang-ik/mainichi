import { tokenStorage } from '../auth/tokenStorage';
import type { RefreshResponse } from './types';

// 백엔드를 브라우저에서 직접 호출한다 (CORS). 백엔드에서 이 프론트 origin 을 허용해야 한다.
const BASE_URL = (
  (import.meta.env.VITE_API_BASE_URL as string | undefined) || 'http://localhost:8080'
).replace(/\/+$/, '');

/** 백엔드 공통 에러 포맷 { error: { code, message } } 를 담는 에러 */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** false 면 Authorization 헤더를 붙이지 않고 401 시 토큰 갱신도 시도하지 않는다. */
  auth?: boolean;
}

// ---- 세션 만료 알림 -------------------------------------------------------
// 리프레시 토큰까지 무효가 되면 AuthContext 가 로그인 화면으로 돌려보낼 수 있게 알린다.
type Listener = () => void;
const sessionExpiredListeners = new Set<Listener>();
export function onSessionExpired(listener: Listener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

// ---- 토큰 갱신 (single-flight) ---------------------------------------------
// 백엔드는 refresh 시 기존 리프레시 토큰을 폐기(rotation)한다.
// 동시에 여러 요청이 만료를 만나도 갱신은 한 번만 보내야 나머지가 폐기된 토큰으로 실패하지 않는다.
let refreshInFlight: Promise<boolean> | null = null;

/** 리프레시 토큰 쿠키로 액세스 토큰을 새로 받는다. 앱 시작 시 세션 복원에도 쓴다. */
export function refreshTokens(): Promise<boolean> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function doRefresh(): Promise<boolean> {
  try {
    // 리프레시 토큰은 httpOnly 쿠키에 있어 브라우저가 알아서 붙인다.
    const res = await send('/auth/refresh', { method: 'POST', auth: false });
    if (res.ok) {
      const data = (await res.json()) as RefreshResponse;
      tokenStorage.setAccess(data.accessToken);
      return true;
    }
    // 서버가 명시적으로 거절(만료/폐기)한 경우에만 세션을 끝낸다.
    if (res.status === 401 || res.status === 400) {
      tokenStorage.clear();
      sessionExpiredListeners.forEach((l) => l());
    }
    return false;
  } catch {
    // 네트워크 오류 등 — 토큰은 그대로 두고 다음 기회에 재시도
    return false;
  }
}

// ---- 요청 -----------------------------------------------------------------
function send(path: string, { method = 'GET', body, auth = true }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const access = tokenStorage.getAccess();
    if (access) headers.Authorization = `Bearer ${access}`;
  }
  return fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    // 다른 origin(백엔드)으로 리프레시 토큰 쿠키를 주고받으려면 필요하다.
    credentials: 'include',
  });
}

async function toApiError(res: Response): Promise<ApiError> {
  let message = `요청에 실패했습니다. (${res.status})`;
  let code: string | undefined;
  try {
    const data = await res.json();
    if (data?.error?.message) {
      message = data.error.message;
      code = data.error.code;
    } else if (typeof data?.message === 'string' && data.message) {
      message = data.message;
    }
  } catch {
    // 본문이 없거나 JSON 이 아님
  }
  return new ApiError(res.status, message, code);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const useAuth = options.auth !== false;
  let res = await send(path, options);

  // 백엔드는 인증 실패 시 401 이 아니라 403(Spring Security 기본 진입점)을 돌려줄 수 있고,
  // 역할 기반 권한이 없으므로 403 도 "액세스 토큰 만료"로 보고 한 번 갱신 후 재시도한다.
  // 액세스 토큰이 없으면 애초에 로그인 상태가 아니므로 갱신하지 않는다.
  if (useAuth && (res.status === 401 || res.status === 403) && tokenStorage.getAccess()) {
    if (await refreshTokens()) {
      res = await send(path, options);
    }
  }

  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
