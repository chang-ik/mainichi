import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { NICKNAME_MAX, errorMessage, validateNickname } from '../utils/nickname';
import { useToast } from './Toast';

interface Props {
  onClose: () => void;
}

function timezoneOptions(current: string): string[] {
  const zones = Intl.supportedValuesOf('timeZone');
  return zones.includes(current) ? zones : [current, ...zones];
}

export function ProfileModal({ onClose }: Props) {
  const { user, updateProfile, deleteAccount } = useAuth();
  const toast = useToast();

  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [timezone, setTimezone] = useState(user?.timezone ?? 'Asia/Seoul');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const zones = useMemo(() => timezoneOptions(user?.timezone ?? 'Asia/Seoul'), [user?.timezone]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  if (!user) return null;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const invalid = validateNickname(nickname);
    if (invalid) return setError(invalid);

    // 바뀐 항목만 보낸다 (PATCH — null/생략은 변경 없음)
    const changes: { nickname?: string; timezone?: string } = {};
    if (nickname.trim() !== user.nickname) changes.nickname = nickname.trim();
    if (timezone !== user.timezone) changes.timezone = timezone;
    if (Object.keys(changes).length === 0) return onClose();

    setError('');
    setBusy(true);
    try {
      await updateProfile(changes);
      toast('내 정보를 저장했어요.');
      onClose();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const onDelete = async () => {
    setError('');
    setBusy(true);
    try {
      await deleteAccount(); // 성공하면 세션이 끝나 로그인 화면으로 이동한다.
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
        <h2 id="profile-title">내 정보 변경</h2>
        <p className="sub">{user.email ?? '카카오 계정'}으로 로그인 중이에요.</p>

        <form onSubmit={onSubmit}>
          <label className="field">
            <span>닉네임</span>
            <input
              className="input"
              value={nickname}
              maxLength={NICKNAME_MAX}
              autoFocus
              onChange={(e) => setNickname(e.target.value)}
            />
          </label>
          <label className="field">
            <span>시간대</span>
            <select className="input" value={timezone} onChange={(e) => setTimezone(e.target.value)}>
              {zones.map((z) => (
                <option key={z} value={z}>
                  {z}
                </option>
              ))}
            </select>
            <p className="hint">하루가 시작되는 기준 시각에 사용돼요.</p>
          </label>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}

          <div className="row">
            <button type="button" className="btn outline" onClick={onClose} disabled={busy}>
              취소
            </button>
            <button type="submit" className="btn" disabled={busy}>
              저장
            </button>
          </div>
        </form>

        <hr className="divider" />

        <div className="danger-zone">
          {confirmingDelete ? (
            <>
              <p className="error">탈퇴하면 계정과 학습 기록이 삭제되며 되돌릴 수 없어요. 정말 탈퇴할까요?</p>
              <div className="row">
                <button type="button" className="btn outline" onClick={() => setConfirmingDelete(false)} disabled={busy}>
                  아니요
                </button>
                <button type="button" className="btn accent" onClick={onDelete} disabled={busy}>
                  탈퇴하기
                </button>
              </div>
            </>
          ) : (
            <button type="button" className="btn danger" onClick={() => setConfirmingDelete(true)} disabled={busy}>
              회원 탈퇴
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
