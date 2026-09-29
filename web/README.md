# 毎日 web

`mainichi/api` (Spring Boot) 백엔드용 React + TypeScript 프론트엔드. (Vite)

## 실행

```bash
npm install
cp .env.example .env    # VITE_KAKAO_REST_API_KEY 입력
npm run dev             # http://localhost:5173
```

백엔드는 `http://localhost:8080` 에서 떠 있어야 하고, 프론트가 브라우저에서 직접 호출한다(CORS).
따라서 **백엔드가 `http://localhost:5173` origin 을 허용**해야 한다 (아래 "백엔드 CORS" 참고).
주소를 바꾸려면 `.env` 의 `VITE_API_BASE_URL` 을 사용한다.

## 카카오 로그인 설정

카카오 developers 콘솔에서:

1. 카카오 로그인 활성화
2. Redirect URI 등록: `http://localhost:5173/auth/kakao/callback`
3. 동의항목에서 이메일(선택) 설정 — 없어도 로그인은 된다 (백엔드에서 email 은 nullable)
4. Client Secret 을 켰다면 **백엔드** 환경변수 `KAKAO_CLIENT_SECRET` 에 넣는다 (프론트는 secret 을 다루지 않음)

흐름 (인가 코드 방식): 프론트가 카카오 인가 페이지로 이동 → 카카오가 `?code=...&state=...` 로 되돌려 보냄
→ 프론트가 state 검증 후 `POST /auth/login {provider:"kakao", code}` → 백엔드가 카카오와 code 를 교환하고
사용자를 조회해 서비스 access/refresh 토큰 발급 → 프론트가 저장.

**Redirect URI 는 카카오 콘솔 / 프론트(`VITE_KAKAO_REDIRECT_URI`) / 백엔드(`KAKAO_REDIRECT_URI`) 세 곳이 완전히 같아야 한다.**
백엔드가 `code_verifier` 를 보내지 않으므로 PKCE 는 쓰지 않는다.

백엔드 실행에는 환경변수 `KAKAO_CLIENT_ID`(= REST API 키)가 필수다. 없으면 백엔드가 뜨지 않는다.

## 구현된 화면 (백엔드 API 기준)

| 경로 | 화면 | API |
|---|---|---|
| `/login` | 카카오 로그인 | `POST /auth/login` |
| `/auth/kakao/callback` | 카카오 콜백 처리 | — |
| `/welcome` | 최초 로그인 시 닉네임 설정 (닉네임이 null 인 계정) | `PATCH /user/me` |
| `/` | 메인(학습하기/통계/게시판 — 아직 "준비 중") | `GET /user/me` |
| 모달 | 내 정보(닉네임·시간대) 변경, 로그아웃, 회원 탈퇴 | `PATCH/DELETE /user/me`, `POST /auth/logout` |

학습·통계·게시판은 백엔드에 API 가 생기면 연결한다.

## 토큰 처리 (`src/api/http.ts`)

- access 토큰은 메모리(`src/auth/tokenStorage.ts`)에만 둔다. 새로고침하면 사라지므로 앱 시작 시 `/auth/refresh` 로 다시 받는다.
- refresh 토큰은 백엔드가 httpOnly 쿠키(`Path=/auth`)로 주고받는다. JS 에서는 읽을 수 없고, 모든 요청은 `credentials: 'include'` 로 보낸다.
- 인증 요청이 401/403 이면 `/auth/refresh` 로 갱신 후 원 요청을 1회 재시도.
  (백엔드가 인증 실패에 403 을 줄 수 있어 둘 다 처리)
- 백엔드가 refresh 시 기존 토큰을 폐기(rotation)하므로 갱신은 동시에 하나만 전송(single-flight).
- 리프레시까지 실패하면 세션을 정리하고 로그인 화면으로 이동.

## 백엔드 CORS

백엔드(`SecurityConfig`)에 아래 설정이 필요하다. 없으면 브라우저가 프리플라이트(OPTIONS)를 403 으로 받아 모든 API 호출이 실패한다.

```java
http.cors(Customizer.withDefaults()) // authorizeHttpRequests 앞에 추가

@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of("http://localhost:5173")); // 운영 도메인도 추가
    c.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    c.setAllowCredentials(true); // 리프레시 토큰 쿠키 전송에 필요
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
}
```

## 운영 배포

`VITE_API_BASE_URL` 에 운영 백엔드 전체 URL 을 지정해 빌드하고, 백엔드 CORS 허용 origin 에 프론트 도메인을 추가한다.

리프레시 토큰 쿠키는 기본 `SameSite=Strict`, `Secure` 라서 프론트와 백엔드가 **같은 사이트**(예: `app.example.com` 과 `api.example.com`)이고 HTTPS 여야 전송된다. 서로 다른 도메인에 배포하면 백엔드 `REFRESH_COOKIE_SAME_SITE=None` 으로 바꾸고 CSRF 대비를 따로 해야 한다.
