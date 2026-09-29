import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ProfileModal } from '../components/ProfileModal';

// 회의록 기준 3개 메뉴. 아직 백엔드 API 가 없어서 모두 "준비 중"으로 둔다.
const MENUS = [
  { glyph: '学', title: '학습하기', desc: '단어 카드로 오늘의 복습을 시작해요', variant: 'primary' },
  { glyph: '録', title: '통계', desc: '연속 학습일과 학습 기록을 확인해요', variant: 'tinted' },
  { glyph: '話', title: '게시판', desc: '같은 단계를 공부하는 사람들과 이야기해요', variant: '' },
] as const;

export function HubPage() {
  const { user, logout } = useAuth();
  const [profileOpen, setProfileOpen] = useState(false);

  return (
    <div className="hub">
      <div className="hub-inner">
        <div className="hub-top">
          <span className="logo">毎日</span>
          <div className="hub-actions">
            <button type="button" className="pill" onClick={() => setProfileOpen(true)}>
              내 정보 변경
            </button>
            <button type="button" className="pill" onClick={logout}>
              로그아웃
            </button>
          </div>
        </div>

        <h1>{user?.nickname}님, 오늘도 한 장부터 시작해요.</h1>

        <div className="cards">
          {MENUS.map((m) => (
            <button key={m.title} type="button" className={`card ${m.variant}`} disabled>
              <span className="badge">준비 중</span>
              <span className="glyph">{m.glyph}</span>
              <span className="title">{m.title}</span>
              <span className="desc">{m.desc}</span>
            </button>
          ))}
        </div>
      </div>

      {profileOpen && <ProfileModal onClose={() => setProfileOpen(false)} />}
    </div>
  );
}
