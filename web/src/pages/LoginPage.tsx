import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { kakaoConfigured, startKakaoLogin } from '../auth/kakao';
import { Logo } from '../components/Logo';
import { errorMessage } from '../utils/nickname';

const FEATURES = [
  { glyph: '語', label: '단어 카드' },
  { glyph: '復', label: '복습 알림' },
  { glyph: '話', label: '커뮤니티' },
];

export function LoginPage() {
  const { status } = useAuth();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const onKakao = () => {
    setError('');
    setBusy(true);
    try {
      startKakaoLogin(); // 카카오 인가 페이지로 이동
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="center-page">
      <div className="login-box">
        <Logo size={76} />
        <p className="tagline">하루 한 장, 부담 없이 이어가는 일본어 학습</p>

        <div className="feature-grid">
          {FEATURES.map((f) => (
            <div className="feature" key={f.glyph}>
              <span className="glyph">{f.glyph}</span>
              <span className="label">{f.label}</span>
            </div>
          ))}
        </div>

        <div className="stack">
          <button type="button" className="btn kakao block" onClick={onKakao} disabled={busy || !kakaoConfigured}>
            카카오로 로그인
          </button>
          {/* 백엔드가 아직 kakao provider 만 지원한다 (AuthService.login) */}
          <button type="button" className="btn outline block" disabled title="준비 중입니다">
            구글 로그인 (준비 중)
          </button>
          {!kakaoConfigured && (
            <p className="hint">
              .env 에 VITE_KAKAO_REST_API_KEY 를 설정하면 카카오 로그인을 사용할 수 있어요.
            </p>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
