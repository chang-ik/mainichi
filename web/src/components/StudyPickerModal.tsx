import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { levelName } from '../utils/level';
import { selectedAreas, studyPath } from '../utils/studyArea';

interface Props {
  onClose: () => void;
}

/** 목업 "무엇을 학습할까요?" — 학습 변경에서 고른 영역 중 하나를 골라 들어간다. */
export function StudyPickerModal({ onClose }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  if (!user) return null;

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="picker-title">
        <h2 id="picker-title">무엇을 학습할까요?</h2>
        <p className="sub">{levelName(user.currentLevel)} 단계 · 학습 변경에서 고른 영역이에요.</p>

        <div className="picker-list">
          {selectedAreas(user.studyAreas).map((a) => {
            // 기초(가나) 단계에는 문법 카드가 없다.
            const noGrammar = a.kind === 'grammar' && user.currentLevel === 0;
            const badge = !a.kind ? '준비 중' : noGrammar ? 'N5부터' : null;
            return (
              <button
                key={a.key}
                type="button"
                className="picker-item"
                onClick={() => a.kind && navigate(studyPath(a.kind))}
                disabled={badge !== null}
              >
                <span className="area-glyph">{a.glyph}</span>
                <span className="picker-text">
                  <span className="picker-name">{a.name}</span>
                  <span className="picker-desc">{a.desc}</span>
                </span>
                {badge && <span className="picker-badge">{badge}</span>}
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
