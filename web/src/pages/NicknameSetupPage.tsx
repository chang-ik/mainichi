import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Logo } from '../components/Logo';
import { NICKNAME_MAX, errorMessage, validateNickname } from '../utils/nickname';

/** 신규 가입자는 닉네임이 비어 있으므로 첫 로그인 직후 한 번 설정하게 한다. */
export function NicknameSetupPage() {
  const { user, updateProfile, logout } = useAuth();
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // 이미 닉네임이 있으면 이 화면은 필요 없다.
  if (user?.nickname) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const invalid = validateNickname(nickname);
    if (invalid) return setError(invalid);

    setError('');
    setBusy(true);
    try {
      await updateProfile({ nickname: nickname.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="center-page">
      <div className="form-box">
        <Logo size={60} />
        <h2>닉네임을 정해 주세요</h2>
        <p className="sub">커뮤니티 글에 표시되는 이름이에요. 나중에 바꿀 수 있어요.</p>

        <form onSubmit={onSubmit}>
          <label className="field">
            <span>닉네임</span>
            <input
              className="input"
              value={nickname}
              maxLength={NICKNAME_MAX}
              placeholder="2~20자"
              autoFocus
              onChange={(e) => setNickname(e.target.value)}
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn accent block" disabled={busy}>
            시작하기
          </button>
          <button type="button" className="link-btn" onClick={logout}>
            로그아웃
          </button>
        </form>
      </div>
    </div>
  );
}
