import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { CONTENT_MAX, TITLE_MAX, boardApi } from '../api/board';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { BOARD_LEVELS } from '../utils/board';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';

/** 글쓰기(/board/write?level=) 와 수정(/board/posts/:postId/edit). 수정할 때는 단계를 바꾸지 않는다. */
export function PostWritePage() {
  const { postId } = useParams();
  const editing = !!postId;
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [level, setLevel] = useState(Number(params.get('level') ?? user?.currentLevel ?? 5));
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [loaded, setLoaded] = useState(!editing);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!postId) return;
    boardApi.get(postId).then(
      (p) => {
        if (!p.mine) {
          navigate(`/board/posts/${p.id}`, { replace: true });
          return;
        }
        setLevel(p.level);
        setTitle(p.title);
        setContent(p.content);
        setLoaded(true);
      },
      (e) => setError(errorMessage(e)),
    );
  }, [postId, navigate]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim() || busy) return;
    setBusy(true);
    setError('');
    try {
      const saved = editing
        ? await boardApi.edit(postId, title, content)
        : await boardApi.write(level, title, content);
      navigate(`/board/posts/${saved.id}`, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="study">
      <div className="study-inner">
        <PageHeader back={editing ? `/board/posts/${postId}` : `/board?level=${level}`} />

        <form className="study-panel post-form" onSubmit={submit}>
          <p className="study-eyebrow">{editing ? '글 수정' : '글쓰기'}</p>
          <h1>{levelName(level)} 게시판</h1>

          {!editing && (
            <label className="field">
              <span className="setting-label">게시판</span>
              <select className="input" value={level} onChange={(e) => setLevel(Number(e.target.value))}>
                {BOARD_LEVELS.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.name}
                    {l.value === user?.currentLevel ? ' (내 단계)' : ''}
                  </option>
                ))}
              </select>
            </label>
          )}

          <label className="field">
            <span className="setting-label">제목</span>
            <input
              className="input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={TITLE_MAX}
              disabled={!loaded}
              required
            />
          </label>

          <label className="field">
            <span className="setting-label">
              내용 <span className="hint">{content.length.toLocaleString()} / {CONTENT_MAX.toLocaleString()}</span>
            </span>
            <textarea
              className="input post-textarea"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              maxLength={CONTENT_MAX}
              rows={12}
              disabled={!loaded}
              required
            />
          </label>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="btn accent block" disabled={busy || !loaded || !title.trim() || !content.trim()}>
            {editing ? '수정하기' : '올리기'}
          </button>
        </form>
      </div>
    </div>
  );
}
