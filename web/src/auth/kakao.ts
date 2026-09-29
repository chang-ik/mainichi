// 카카오 로그인: 인가 코드 방식 (SDK 없이)
//   1) startKakaoLogin()  -> 카카오 인가 페이지로 이동
//   2) 카카오가 REDIRECT_URI?code=...&state=... 로 돌려보냄
//   3) consumeKakaoState() 로 state 검증 -> code 를 백엔드 POST /auth/login 에 전달
//      (카카오 토큰 교환과 사용자 조회는 백엔드가 한다)
//
// 주의: REDIRECT_URI 는 카카오 콘솔에 등록한 값, 백엔드 kakao.redirect-uri 와 완전히 같아야 한다.
//       백엔드가 code_verifier 를 보내지 않으므로 PKCE(code_challenge)는 사용하지 않는다.

const REST_API_KEY = import.meta.env.VITE_KAKAO_REST_API_KEY as string | undefined;
const REDIRECT_URI =
  (import.meta.env.VITE_KAKAO_REDIRECT_URI as string | undefined) ||
  `${window.location.origin}/auth/kakao/callback`;

const STATE_KEY = 'mainichi.kakao.state';

export const kakaoConfigured = Boolean(REST_API_KEY);

function randomState(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function startKakaoLogin(): void {
  if (!REST_API_KEY) throw new Error('VITE_KAKAO_REST_API_KEY 가 설정되지 않았습니다.');

  const state = randomState();
  sessionStorage.setItem(STATE_KEY, state);

  const params = new URLSearchParams({
    client_id: REST_API_KEY,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    state,
  });
  window.location.assign(`https://kauth.kakao.com/oauth/authorize?${params}`);
}

/** 콜백으로 돌아온 state 가 로그인 시작 때 저장한 값과 같은지 확인한다. (1회용) */
export function consumeKakaoState(state: string | null): boolean {
  const expected = sessionStorage.getItem(STATE_KEY);
  sessionStorage.removeItem(STATE_KEY);
  return Boolean(expected) && state === expected;
}
