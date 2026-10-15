import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { studyApi } from '../api/study';
import type { StudyQueue } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { LevelModal } from '../components/LevelModal';
import { ProfileModal } from '../components/ProfileModal';
import { StudyPickerModal } from '../components/StudyPickerModal';
import { levelName } from '../utils/level';
import { selectedAreas } from '../utils/studyArea';

// 회의록 기준 3개 메뉴.
const OTHER_MENUS = [
  { glyph: '録', title: '통계', desc: '연속 학습일과 학습 기록을 확인해요', variant: 'tinted', path: '/stats' },
  { glyph: '話', title: '게시판', desc: '같은 단계를 공부하는 사람들과 이야기해요', variant: '', path: '/board' },
] as const;

function studyDesc(queue: StudyQueue | null): string {
  if (!queue) return '단어 카드로 오늘의 복습을 시작해요';
  const { newCards, learning, review } = queue.counts;
  if (newCards + learning + review === 0) return '오늘 할 카드를 모두 끝냈어요';
  return `복습 ${review + learning}장 · 새 단어 ${newCards}장`;
}

export function HubPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [profileOpen, setProfileOpen] = useState(false);
  const [levelOpen, setLevelOpen] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [queue, setQueue] = useState<StudyQueue | null>(null);

  // 남은 카드 수 안내용. 실패해도 메뉴는 그대로 쓸 수 있다.
  const loadQueue = useCallback(() => {
    studyApi.queue('words').then(setQueue, () => {});
  }, []);

  useEffect(loadQueue, [loadQueue]);

  // 단어 외 영역도 골랐으면 목업처럼 무엇을 학습할지 먼저 고른다.
  const startStudy = () => {
    if (selectedAreas(user?.studyAreas).length > 1) setPickerOpen(true);
    else navigate('/study');
  };

  return (
    <div className="hub">
      <div className="hub-inner">
        <div className="hub-top">
          <span className="logo">毎日</span>
          <div className="hub-actions">
            <button type="button" className="pill dark" onClick={() => setLevelOpen(true)}>
              학습 변경 <span className="pill-level">{levelName(user?.currentLevel)}</span>
            </button>
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
          <button type="button" className="card primary" onClick={startStudy}>
            <span className="glyph">学</span>
            <span className="title">{levelName(user?.currentLevel)} 학습하기</span>
            <span className="desc">{studyDesc(queue)}</span>
          </button>
          {OTHER_MENUS.map((m) => (
            <button
              key={m.title}
              type="button"
              className={`card ${m.variant}`}
              disabled={!m.path}
              onClick={() => m.path && navigate(m.path)}
            >
              {!m.path && <span className="badge">준비 중</span>}
              <span className="glyph">{m.glyph}</span>
              <span className="title">{m.title}</span>
              <span className="desc">{m.desc}</span>
            </button>
          ))}
        </div>
      </div>

      {profileOpen && <ProfileModal onClose={() => setProfileOpen(false)} />}
      {levelOpen && <LevelModal onClose={() => setLevelOpen(false)} onSaved={loadQueue} />}
      {pickerOpen && <StudyPickerModal onClose={() => setPickerOpen(false)} />}
    </div>
  );
}
