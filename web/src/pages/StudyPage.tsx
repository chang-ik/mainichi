import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { studyApi } from '../api/study';
import type { Rating, StudyCard, StudyKind, StudyQueue } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Furigana, formatInterval, stripFurigana, toReading } from '../utils/furigana';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';

const LONG_PRESS_MS = 420;

// 목업의 ✕ △ ○ ◎ 버튼. 키보드 1~4 로도 누를 수 있다.
const GRADES: { rating: Rating; mark: string; label: string; key: keyof StudyCard['intervals']; variant: string }[] = [
  { rating: 1, mark: '✕', label: '다시', key: 'again', variant: 'again' },
  { rating: 2, mark: '△', label: '어려움', key: 'hard', variant: 'hard' },
  { rating: 3, mark: '○', label: '알맞음', key: 'good', variant: 'good' },
  { rating: 4, mark: '◎', label: '쉬움', key: 'easy', variant: 'easy' },
];

const PHASE_LABEL: Record<StudyCard['phase'], string> = {
  NEW: '새 카드',
  LEARNING: '학습 중',
  REVIEW: '복습',
  RELEARNING: '다시 학습',
};

const KIND_TEXT: Record<StudyKind, { title: string; newLabel: string }> = {
  words: { title: '필수 단어', newLabel: '새 단어' },
  grammar: { title: '문법', newLabel: '새 문법' },
};

interface Props {
  kind: StudyKind;
}

/** 단어·문법 카드 학습 (FSRS). 카드를 눌러 뒤집고, 기억난 정도를 4단계로 평가한다. */
export function StudyPage({ kind }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [queue, setQueue] = useState<StudyQueue | null>(null);
  const [done, setDone] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [showReading, setShowReading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const pressTimer = useRef<number | undefined>(undefined);
  /** 지금 카드를 보여준 시각. 평가까지 걸린 시간을 통계로 보낸다 */
  const shownAt = useRef(Date.now());
  const longPressed = useRef(false);

  useEffect(() => {
    studyApi.queue(kind).then(setQueue, (e) => setError(errorMessage(e)));
    return () => window.clearTimeout(pressTimer.current);
  }, [kind]);

  const card = queue?.card ?? null;

  useEffect(() => {
    shownAt.current = Date.now();
  }, [card?.id]);

  const grade = useCallback(
    async (rating: Rating) => {
      if (!card || !flipped || busy) return;
      setBusy(true);
      setError('');
      try {
        const next = await studyApi.review(kind, card.id, rating, Date.now() - shownAt.current);
        setQueue(next);
        setDone((n) => n + 1);
        setFlipped(false);
      } catch (e) {
        setError(errorMessage(e));
      } finally {
        setBusy(false);
      }
    },
    [kind, card, flipped, busy],
  );

  // Space/Enter: 뒤집기, 1~4: 평가
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!card || e.repeat) return;
      if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        setFlipped((f) => !f);
      } else if (['1', '2', '3', '4'].includes(e.key)) {
        void grade(Number(e.key) as Rating);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [card, grade]);

  // 길게 누르면 앞면에서 읽는 법을 살짝 보여준다 (목업 동작)
  const pressStart = () => {
    longPressed.current = false;
    window.clearTimeout(pressTimer.current);
    pressTimer.current = window.setTimeout(() => {
      longPressed.current = true;
      setShowReading(true);
    }, LONG_PRESS_MS);
  };
  const pressEnd = () => {
    window.clearTimeout(pressTimer.current);
    setShowReading(false);
  };
  const onCardClick = () => {
    if (longPressed.current) {
      longPressed.current = false;
      return;
    }
    setFlipped((f) => !f);
  };

  const text = KIND_TEXT[kind];
  const isGrammar = kind === 'grammar';
  const remaining = queue ? queue.counts.newCards + queue.counts.learning + queue.counts.review : 0;
  const total = done + remaining;
  const pct = total === 0 ? (queue ? 100 : 0) : Math.round((done / total) * 100);

  return (
    <div className="study">
      <div className="study-inner">
        <header className="study-header">
          <button type="button" className="study-back" onClick={() => navigate('/')} title="홈" aria-label="홈으로">
            ⟵
          </button>
          <button type="button" className="study-logo" onClick={() => navigate('/')}>
            毎日
          </button>
          <span className="study-nick">{user?.nickname}</span>
        </header>

        <section className="study-panel">
          <div className="study-top">
            <div>
              <p className="study-eyebrow">오늘의 학습</p>
              <h1>
                {levelName(user?.currentLevel)} {text.title}
              </h1>
            </div>
            <p className="study-progress-label">
              {done} / {total}장
            </p>
          </div>
          <div className="progress">
            <div className="progress-bar" style={{ width: `${pct}%` }} />
          </div>

          {queue && (
            <div className="study-counts" aria-label="남은 카드">
              <span className="count new">
                {text.newLabel} {queue.counts.newCards}
              </span>
              <span className="count learning">학습 중 {queue.counts.learning}</span>
              <span className="count review">복습 {queue.counts.review}</span>
            </div>
          )}

          {error && (
            <p className="error" role="alert" style={{ marginTop: 16 }}>
              {error}
            </p>
          )}

          {!queue && !error && <p className="status-msg study-loading">카드를 불러오는 중…</p>}

          {card && (
            <>
              <div
                key={card.id}
                className="flip"
                role="button"
                tabIndex={0}
                aria-label={flipped ? '앞면 보기' : '뒷면 보기'}
                onClick={onCardClick}
                onMouseDown={pressStart}
                onMouseUp={pressEnd}
                onMouseLeave={pressEnd}
                onTouchStart={pressStart}
                onTouchEnd={pressEnd}
                onContextMenu={(e) => e.preventDefault()}
              >
                <div className={`flip-inner${flipped ? ' flipped' : ''}${isGrammar ? ' grammar' : ''}`}>
                  <div className="face front">
                    <span className="phase-tag">{PHASE_LABEL[card.phase]}</span>
                    <span className="word-wrap">
                      {showReading && <span className="reading-peek">{toReading(card.front)}</span>}
                      <span className={`word${isGrammar ? ' pattern' : ''}`}>{stripFurigana(card.front)}</span>
                    </span>
                    <span className="flip-hint">눌러서 뒤집기 · 길게 눌러 읽기</span>
                  </div>
                  <div className="face back">
                    {isGrammar ? (
                      <>
                        <span className="pattern-back">
                          <Furigana text={card.front} />
                        </span>
                        {card.usage && <span className="usage">{card.usage}</span>}
                      </>
                    ) : (
                      <span className="reading">{toReading(card.front)}</span>
                    )}
                    <span className="meaning">{card.meaning}</span>
                    <div className="example">
                      <span className="example-jp">
                        <Furigana text={card.example} />
                      </span>
                      <span className="example-ko">{card.exampleMeaning}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="grades">
                {GRADES.map((g) => (
                  <button
                    key={g.rating}
                    type="button"
                    className={`grade ${g.variant}`}
                    onClick={() => grade(g.rating)}
                    disabled={!flipped || busy}
                    title={`${g.label} (${g.rating})`}
                  >
                    <span className="mark">{g.mark}</span>
                    <span className="grade-sub">
                      <span className="grade-label">{g.label} · </span>
                      {formatInterval(card.intervals[g.key])}
                    </span>
                  </button>
                ))}
              </div>
              {!flipped && <p className="hint study-hint">카드를 뒤집은 뒤 기억난 정도를 골라 주세요.</p>}
            </>
          )}

          {queue && !card && (
            <div className="study-done">
              <p className="otsukare">お疲れさま</p>
              <p className="sub">
                {done > 0
                  ? `오늘 ${done}장을 공부했어요.`
                  : '지금은 공부할 카드가 없어요.'}
                {queue.nextDueInSeconds != null &&
                  ` ${formatInterval(queue.nextDueInSeconds)} 뒤에 다시 볼 카드가 있어요.`}
              </p>
              <button type="button" className="btn" onClick={() => navigate('/')}>
                홈으로
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
