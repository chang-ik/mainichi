import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { consumeKakaoState } from '../auth/kakao';
import { Logo } from '../components/Logo';
import { errorMessage } from '../utils/nickname';

// 인가 코드는 1회용이다. StrictMode 가 effect 를 두 번 실행해도 로그인 요청은 한 번만 보내도록
// code 별로 진행 중인 Promise 를 모듈 수준에서 공유한다.
const inflight = new Map<string, Promise<unknown>>();

export function KakaoCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { loginWithKakaoCode } = useAuth();
  const [error, setError] = useState('');

  useEffect(() => {
    const code = params.get('code');

    if (params.get('error') || !code) {
      setError(params.get('error') === 'access_denied' ? '카카오 로그인이 취소되었습니다.' : '카카오 로그인에 실패했습니다.');
      return;
    }

    let task = inflight.get(code);
    if (!task) {
      // state 는 1회용이라 첫 실행에서만 검증한다 (StrictMode 재실행은 위 inflight 로 흡수).
      if (!consumeKakaoState(params.get('state'))) {
        setError('로그인 요청이 유효하지 않습니다. 처음부터 다시 시도해 주세요.');
        return;
      }
      task = loginWithKakaoCode(code);
      inflight.set(code, task);
    }
    task.then(
      () => navigate('/', { replace: true }),
      (e) => setError(errorMessage(e)),
    );
  }, [params, loginWithKakaoCode, navigate]);

  return (
    <div className="center-page">
      <div className="login-box">
        <Logo size={60} />
        <div className="stack" style={{ marginTop: 32 }}>
          {error ? (
            <>
              <p className="error" role="alert" style={{ textAlign: 'center' }}>
                {error}
              </p>
              <button type="button" className="btn block" onClick={() => navigate('/login', { replace: true })}>
                로그인으로 돌아가기
              </button>
            </>
          ) : (
            <p className="status-msg">로그인 중입니다…</p>
          )}
        </div>
      </div>
    </div>
  );
}
