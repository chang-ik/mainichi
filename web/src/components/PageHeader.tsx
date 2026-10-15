import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/** 학습·게시판 화면 위쪽: ⟵ 뒤로 / 毎日 로고(홈) / 닉네임 */
export function PageHeader({ back = '/' }: { back?: string }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  return (
    <header className="study-header">
      <button type="button" className="study-back" onClick={() => navigate(back)} title="뒤로" aria-label="뒤로">
        ⟵
      </button>
      <button type="button" className="study-logo" onClick={() => navigate('/')}>
        毎日
      </button>
      <span className="study-nick">{user?.nickname}</span>
    </header>
  );
}
