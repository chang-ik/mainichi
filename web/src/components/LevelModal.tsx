import { useEffect, useState } from 'react';
import { studyApi } from '../api/study';
import type { StudyArea, StudySettings, UpdateProfileRequest } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { LEVELS } from '../utils/level';
import { errorMessage } from '../utils/nickname';
import { STUDY_AREAS } from '../utils/studyArea';
import { useToast } from './Toast';

// 백엔드 StudySettingsRequest: @Min(1) @Max(100)
const GOAL_MIN = 1;
const GOAL_MAX = 100;

interface Props {
  onClose: () => void;
  /** 저장 후 (남은 카드 수 등을 다시 불러오도록) */
  onSaved?: () => void;
}

function sameAreas(a: StudyArea[], b: StudyArea[]): boolean {
  return a.length === b.length && a.every((k) => b.includes(k));
}

export function LevelModal({ onClose, onSaved }: Props) {
  const { user, updateProfile } = useAuth();
  const toast = useToast();

  const [level, setLevel] = useState(user?.currentLevel ?? 5);
  // 단어는 항상 포함이라 따로 고르지 않는다.
  const [areas, setAreas] = useState<StudyArea[]>(() => (user?.studyAreas ?? []).filter((k) => k !== 'WORD'));
  const [settings, setSettings] = useState<StudySettings | null>(null);
  const [goalInput, setGoalInput] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    studyApi.settings().then(
      (s) => {
        setSettings(s);
        setGoalInput(String(s.newCardsPerDay));
      },
      (err) => setError(errorMessage(err)),
    );
  }, []);

  if (!user) return null;

  const toggleArea = (key: StudyArea) =>
    setAreas((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]));

  const goal = Number(goalInput);
  const isDefaultGoal = settings !== null && goal === settings.defaultNewCardsPerDay;

  const onSave = async () => {
    if (settings && (!Number.isInteger(goal) || goal < GOAL_MIN || goal > GOAL_MAX)) {
      return setError(`하루 학습량은 ${GOAL_MIN}~${GOAL_MAX}장으로 입력해 주세요.`);
    }

    const profile: UpdateProfileRequest = {};
    if (level !== user.currentLevel) profile.currentLevel = level;
    const savedAreas = user.studyAreas.filter((k) => k !== 'WORD');
    if (!sameAreas(areas, savedAreas)) profile.studyAreas = areas;
    const goalChanged = settings !== null && goal !== settings.newCardsPerDay;

    if (Object.keys(profile).length === 0 && !goalChanged) return onClose();

    setError('');
    setBusy(true);
    try {
      if (Object.keys(profile).length > 0) await updateProfile(profile);
      if (goalChanged) setSettings(await studyApi.updateSettings(goal));
      toast('학습 설정을 저장했어요.');
      onSaved?.();
      onClose();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="level-title">
        <h2 id="level-title">학습 변경</h2>
        <p className="sub">한 번에 한 단계씩 학습합니다. 언제든 바꿀 수 있어요.</p>

        <div className="level-grid" role="radiogroup" aria-label="학습 단계">
          {LEVELS.map((l) => (
            <button
              key={l.value}
              type="button"
              role="radio"
              aria-checked={l.value === level}
              className={`level-option${l.value === level ? ' selected' : ''}`}
              onClick={() => setLevel(l.value)}
              disabled={busy}
            >
              <span className="level-name">{l.name}</span>
              <span className="level-desc">{l.desc}</span>
            </button>
          ))}
        </div>

        <p className="setting-label">학습 영역 (단어는 항상 포함, 추가 선택 가능)</p>
        <div className="level-grid">
          {STUDY_AREAS.filter((a) => a.key !== 'WORD').map((a) => {
            const on = areas.includes(a.key);
            return (
              <button
                key={a.key}
                type="button"
                aria-pressed={on}
                className={`level-option area-option${on ? ' selected' : ''}`}
                onClick={() => toggleArea(a.key)}
                disabled={busy}
              >
                <span className="area-glyph">{a.glyph}</span>
                <span className="area-name">{a.name}</span>
              </button>
            );
          })}
        </div>

        <p className="setting-label">하루 학습량</p>
        <div className="goal-row">
          <input
            className="input goal-input"
            type="text"
            inputMode="numeric"
            aria-label="하루 학습량 (장)"
            placeholder={settings ? String(settings.defaultNewCardsPerDay) : ''}
            value={goalInput}
            onChange={(e) => setGoalInput(e.target.value.replace(/[^0-9]/g, '').slice(0, 3))}
            onKeyDown={(e) => e.key === 'Enter' && onSave()}
            disabled={busy || !settings}
          />
          <span className="goal-unit">장</span>
          <button
            type="button"
            className={`goal-default${isDefaultGoal ? ' active' : ''}`}
            onClick={() => settings && setGoalInput(String(settings.defaultNewCardsPerDay))}
            disabled={busy || !settings}
          >
            기본값
          </button>
        </div>
        <p className="hint goal-hint">단어·문법 각각 하루에 새로 볼 카드 수예요. 복습 카드는 따로 나와요.</p>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <div className="row">
          <button type="button" className="btn outline" onClick={onClose} disabled={busy}>
            닫기
          </button>
          <button type="button" className="btn accent" onClick={onSave} disabled={busy}>
            저장
          </button>
        </div>
      </div>
    </div>
  );
}
