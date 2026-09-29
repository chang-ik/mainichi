// 액세스 토큰은 메모리에만 둔다. localStorage 에 두면 XSS 스크립트가 그대로 꺼내 갈 수 있다.
// 새로고침하면 사라지므로, 앱 시작 시 httpOnly 쿠키의 리프레시 토큰으로 다시 발급받는다.
// 리프레시 토큰은 서버가 httpOnly 쿠키로만 주고받아 JS 에서는 다루지 않는다.
let accessToken: string | null = null;

// 예전 버전이 localStorage 에 남긴 토큰은 노출 위험만 있으므로 지운다.
try {
  localStorage.removeItem('mainichi.accessToken');
  localStorage.removeItem('mainichi.refreshToken');
} catch {
  // 저장소를 쓸 수 없는 환경이면 지울 것도 없다
}

export const tokenStorage = {
  getAccess: () => accessToken,
  setAccess(token: string) {
    accessToken = token;
  },
  clear() {
    accessToken = null;
  },
};
