import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { COMMENT_MAX, boardApi } from '../api/board';
import type { PostDetail } from '../api/types';
import { PageHeader } from '../components/PageHeader';
import { useToast } from '../components/Toast';
import { authorName, formatPostDate } from '../utils/board';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';

/** 글 상세 + 댓글. 내 글이면 수정·삭제, 내 댓글이면 삭제 버튼이 보인다. */
export function PostPage() {
  const { postId = '' } = useParams();
  const navigate = useNavigate();
  const toast = useToast();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    boardApi.get(postId).then(setPost, (e) => setError(errorMessage(e)));
  }, [postId]);

  const backToList = post ? `/board?level=${post.level}` : '/board';

  const removePost = async () => {
    if (!post || !window.confirm('이 글을 지울까요? 달린 댓글도 함께 지워져요.')) return;
    try {
      await boardApi.remove(post.id);
      toast('글을 지웠어요.');
      navigate(backToList, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const submitComment = async (e: FormEvent) => {
    e.preventDefault();
    if (!post || !comment.trim() || busy) return;
    setBusy(true);
    setError('');
    try {
      setPost(await boardApi.comment(post.id, comment));
      setComment('');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const removeComment = async (commentId: string) => {
    if (!post || !window.confirm('이 댓글을 지울까요?')) return;
    try {
      await boardApi.removeComment(commentId);
      setPost({ ...post, comments: post.comments.filter((c) => c.id !== commentId) });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="study">
      <div className="study-inner">
        <PageHeader back={backToList} />

        {error && (
          <p className="error" role="alert" style={{ marginTop: 20 }}>
            {error}
          </p>
        )}
        {!post && !error && <p className="status-msg study-loading">불러오는 중…</p>}

        {post && (
          <>
            <article className="post">
              <p className="study-eyebrow">{levelName(post.level)} 게시판</p>
              <h1 className="post-title">{post.title}</h1>
              <p className="post-meta">
                {authorName(post.author)} · {formatPostDate(post.createdAt)}
                {post.edited && ' · 수정됨'}
              </p>
              {/* 줄바꿈은 CSS(pre-wrap)로 살린다. 글은 텍스트로만 넣어 HTML 로 해석되지 않는다 */}
              <div className="post-content">{post.content}</div>
              {post.mine && (
                <div className="post-actions">
                  <button type="button" className="btn outline" onClick={() => navigate(`/board/posts/${post.id}/edit`)}>
                    수정
                  </button>
                  <button type="button" className="btn danger" onClick={removePost}>
                    삭제
                  </button>
                </div>
              )}
            </article>

            <section className="comments">
              <h2>댓글 {post.comments.length}</h2>
              <ul>
                {post.comments.map((c) => (
                  <li key={c.id} className="comment">
                    <p className="comment-meta">
                      <strong>{authorName(c.author)}</strong> · {formatPostDate(c.createdAt)}
                      {c.mine && (
                        <button type="button" className="link-btn comment-remove" onClick={() => removeComment(c.id)}>
                          삭제
                        </button>
                      )}
                    </p>
                    <p className="comment-content">{c.content}</p>
                  </li>
                ))}
              </ul>

              <form className="comment-form" onSubmit={submitComment}>
                <textarea
                  className="input"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  maxLength={COMMENT_MAX}
                  rows={3}
                  placeholder="댓글을 남겨 보세요"
                  aria-label="댓글"
                />
                <button type="submit" className="btn accent" disabled={busy || !comment.trim()}>
                  등록
                </button>
              </form>
            </section>
          </>
        )}
      </div>
    </div>
  );
}
