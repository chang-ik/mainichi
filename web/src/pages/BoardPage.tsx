import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { boardApi } from '../api/board';
import type { PostPage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { BOARD_LEVELS, authorName, formatPostDate } from '../utils/board';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';

/** 학습 단계별 게시판 목록. 처음엔 내 학습 단계가 열리고, 탭으로 다른 단계도 본다. 상태는 주소(?level=&page=)에 둔다. */
export function BoardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const level = Number(params.get('level') ?? user?.currentLevel ?? 5);
  const page = Math.max(0, Number(params.get('page') ?? 0));

  const [data, setData] = useState<PostPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    boardApi
      .list(level, page)
      .then(setData, (e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [level, page]);

  const go = (nextLevel: number, nextPage = 0) =>
    setParams({ level: String(nextLevel), page: String(nextPage) });

  return (
    <div className="study">
      <div className="study-inner">
        <PageHeader />

        <div className="board-head">
          <div>
            <p className="study-eyebrow">같은 단계를 공부하는 사람들</p>
            <h1>{levelName(level)} 게시판</h1>
          </div>
          <button type="button" className="btn accent" onClick={() => navigate(`/board/write?level=${level}`)}>
            글쓰기
          </button>
        </div>

        <div className="board-tabs" role="tablist" aria-label="단계">
          {BOARD_LEVELS.map((l) => (
            <button
              key={l.value}
              type="button"
              role="tab"
              aria-selected={l.value === level}
              className={l.value === level ? 'on' : ''}
              onClick={() => go(l.value)}
            >
              {l.name}
              {l.value === user?.currentLevel && <span className="board-tab-mine">내 단계</span>}
            </button>
          ))}
        </div>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <section className={`board-list${loading ? ' refreshing' : ''}`}>
          {data && data.posts.length === 0 && (
            <div className="board-empty">
              <p>아직 {levelName(level)} 게시판에 글이 없어요.</p>
              <p className="sub">첫 글을 남겨 보세요.</p>
            </div>
          )}
          {data?.posts.map((p) => (
            <Link key={p.id} to={`/board/posts/${p.id}`} className="board-row">
              <span className="board-title">
                {p.title}
                {p.commentCount > 0 && <span className="board-count">[{p.commentCount}]</span>}
              </span>
              <span className="board-meta">
                {authorName(p.author)} · {formatPostDate(p.createdAt)}
              </span>
            </Link>
          ))}
        </section>

        {data && data.totalPages > 1 && (
          <nav className="board-pager" aria-label="페이지">
            <button type="button" className="btn outline" disabled={page === 0} onClick={() => go(level, page - 1)}>
              이전
            </button>
            <span>
              {page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              className="btn outline"
              disabled={page + 1 >= data.totalPages}
              onClick={() => go(level, page + 1)}
            >
              다음
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}
