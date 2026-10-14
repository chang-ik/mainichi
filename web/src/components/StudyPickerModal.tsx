import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { quizApi } from '../api/quiz';
import { studyApi } from '../api/study';
import type { StudyArea } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { levelName } from '../utils/level';
import { selectedAreas } from '../utils/studyArea';

interface Props {
  onClose: () => void;
}

/**
 * 오늘 이 영역을 끝냈는지.
 * 단어·문법: 지금 공부할 카드가 남지 않음 (홈의 "오늘 할 카드를 모두 끝냈어요"와 같은 기준)
 * 장문·듣기: 오늘 한 세트 이상 채점까지 끝냄
 */
function isDone(area: StudyArea): Promise<boolean> {
  switch (area) {
    case 'WORD':
      return studyApi.queue('words').then((q) => q.card === null);
    case 'GRAMMAR':
      return studyApi.queue('grammar').then((q) => q.card === null);
    case 'READING':
      return quizApi.today('reading').then((t) => t.solvedToday > 0);
    case 'LISTENING':
      return quizApi.today('listening').then((t) => t.solvedToday > 0);
  }
}

/** 목업 "무엇을 학습할까요?" — 학습 변경에서 고른 영역 중 하나를 골라 들어간다. 오늘 끝낸 영역은 연초록으로 표시한다. */
export function StudyPickerModal({ onClose }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [done, setDone] = useState<Partial<Record<StudyArea, boolean>>>({});

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // 영역마다 따로 불러온다. 하나가 실패해도 나머지는 표시되고, 실패한 영역은 표시 없이 둔다.
  useEffect(() => {
    if (!user) return;
    let alive = true;
    selectedAreas(user.studyAreas)
      .filter((a) => a.path)
      .forEach((a) =>
        isDone(a.key).then(
          (d) => alive && setDone((prev) => ({ ...prev, [a.key]: d })),
          () => {},
        ),
      );
    return () => {
      alive = false;
    };
  }, [user]);

  if (!user) return null;

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="picker-title">
        <h2 id="picker-title">무엇을 학습할까요?</h2>
        <p className="sub">{levelName(user.currentLevel)} 단계 · 학습 변경에서 고른 영역이에요.</p>

        <div className="picker-list">
          {selectedAreas(user.studyAreas).map((a) => {
            const finished = !!a.path && done[a.key] === true;
            const badge = !a.path ? '준비 중' : null;
            return (
              <button
                key={a.key}
                type="button"
                className={`picker-item${finished ? ' done' : ''}`}
                onClick={() => a.path && navigate(a.path)}
                disabled={badge !== null}
              >
                <span className="area-glyph">{a.glyph}</span>
                <span className="picker-text">
                  <span className="picker-name">{a.name}</span>
                  <span className="picker-desc">{a.desc}</span>
                </span>
                {badge && <span className="picker-badge">{badge}</span>}
                {finished && <span className="picker-badge done">✓ 오늘 완료</span>}
              </button>
            );
          })}
        </div>

        <button type="button" className="btn outline block picker-close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  );
}
