import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { quizApi } from '../api/quiz';
import type { Quiz, QuizKind, TodayQuiz } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ListeningPlayer } from '../components/ListeningPlayer';
import { Furigana, stripFurigana } from '../utils/furigana';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';
import type { SpeechPart } from '../utils/speech';

const CHOICE_MARKS = ['①', '②', '③', '④'];

const KIND_TEXT: Record<QuizKind, { eyebrow: string; title: string; glyph: string; intro: string; making: string; retry: string }> = {
  reading: {
    eyebrow: '오늘의 장문',
    title: '독해',
    glyph: '読解',
    intro: '으로 JLPT 형식 지문과 문제 2개를 만들어요.',
    making: '오늘 배운 단어로 지문을 쓰고 있어요.',
    retry: '해설을 보고 지문을 다시 읽어 보세요.',
  },
  listening: {
    eyebrow: '오늘의 청해',
    title: '청해',
    glyph: '聴解',
    intro: '을 섞어 단계에 맞는 JLPT 형식 대화와 문제 2개를 만들어요.',
    making: '단계에 맞는 대화를 쓰고 있어요.',
    retry: '스크립트를 보면서 대화를 다시 들어 보세요.',
  },
};

const SPEAKER_LABEL = { MALE: '男', FEMALE: '女' } as const;

/** 후리가나 표시를 끄면 대괄호 읽기를 뺀 원문만 보여준다. */
function Jp({ text, furigana }: { text: string; furigana: boolean }) {
  return furigana ? <Furigana text={text} /> : <>{stripFurigana(text)}</>;
}

interface Props {
  kind: QuizKind;
}

/**
 * Claude 가 만든 JLPT 형식 문제 1세트 (문제 2개).
 * 독해: 오늘 새로 배운 단어·문법으로 쓴 지문. 청해: 단계에 맞는 대화를 브라우저 음성으로 듣는다.
 */
export function QuizPage({ kind }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const text = KIND_TEXT[kind];
  const listening = kind === 'listening';

  const [today, setToday] = useState<TodayQuiz | null>(null);
  const [answers, setAnswers] = useState<(number | null)[]>([]);
  const [furigana, setFurigana] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  /** 지금 읽고 있는 문장 (0 = 상황 설명, 1~ = 대사) */
  const [speaking, setSpeaking] = useState(-1);

  const quiz = today?.quiz ?? null;

  useEffect(() => {
    quizApi.today(kind).then(setToday, (e) => setError(errorMessage(e)));
  }, [kind]);

  // 문제가 바뀌면 고른 답을 비운다
  useEffect(() => {
    setAnswers(quiz && !quiz.submitted ? quiz.questions.map(() => null) : []);
  }, [quiz?.id, quiz?.submitted]); // quiz 객체 자체는 남은 횟수 갱신 때도 바뀌므로 id·제출 여부만 본다

  const speechParts = useMemo<SpeechPart[]>(
    () => (quiz && listening ? [{ speaker: 'NARRATOR', text: quiz.passage }, ...quiz.script] : []),
    [quiz, listening],
  );
  const onProgress = useCallback((i: number) => setSpeaking(i), []);

  const showQuiz = (next: Quiz) => {
    setToday((t) => (t ? { ...t, quiz: next } : t));
    // 남은 횟수 등 갱신. 실패해도 문제는 그대로 보인다.
    quizApi.today(kind).then(setToday, () => {});
  };

  const create = async () => {
    setGenerating(true);
    setError('');
    try {
      showQuiz(await quizApi.create(kind));
      window.scrollTo({ top: 0 });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setGenerating(false);
    }
  };

  const submit = async () => {
    if (!quiz || answers.some((a) => a === null)) return;
    setBusy(true);
    setError('');
    try {
      showQuiz(await quizApi.submit(kind, quiz.id, answers as number[]));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const choose = (qi: number, ci: number) => {
    if (!quiz || quiz.submitted) return;
    setAnswers((prev) => prev.map((a, i) => (i === qi ? ci : a)));
  };

  const canCreate = !!today && today.newCardCount >= today.minNewCards && today.remainingToday > 0;
  const lacking = today ? Math.max(0, today.minNewCards - today.newCardCount) : 0;

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
              <p className="study-eyebrow">{text.eyebrow}</p>
              <h1>
                {levelName(quiz?.level ?? user?.currentLevel)} {text.title}
              </h1>
            </div>
            {quiz && (
              <label className="furigana-toggle">
                <input type="checkbox" checked={furigana} onChange={(e) => setFurigana(e.target.checked)} />
                후리가나
              </label>
            )}
          </div>

          {error && (
            <p className="error" role="alert" style={{ marginTop: 16 }}>
              {error}
            </p>
          )}

          {!today && !error && <p className="status-msg study-loading">불러오는 중…</p>}

          {generating && (
            <div className="reading-generating" role="status">
              <span className="reading-spinner" aria-hidden="true" />
              <p>{text.making}</p>
              <p className="sub">30초~1분 정도 걸려요.</p>
            </div>
          )}

          {today && !quiz && !generating && (
            <div className="study-done">
              <p className="otsukare">{text.glyph}</p>
              <p className="sub">
                오늘 새로 배운 카드 <strong>{today.newCardCount}장</strong>
                {text.intro}
                {lacking > 0 && <><br />단어·문법 카드를 {lacking}장 더 공부하면 만들 수 있어요.</>}
                {lacking === 0 && today.remainingToday === 0 && <><br />오늘은 더 만들 수 없어요. 내일 다시 도전해 주세요.</>}
              </p>
              {lacking > 0 ? (
                <button type="button" className="btn" onClick={() => navigate('/study')}>
                  단어 공부하러 가기
                </button>
              ) : (
                <button type="button" className="btn accent" onClick={create} disabled={!canCreate}>
                  문제 만들기
                </button>
              )}
            </div>
          )}

          {quiz && !generating && (
            <>
              {listening ? (
                <>
                  <h2 className="passage-title listening-title">
                    <Jp text={quiz.title} furigana={furigana} />
                  </h2>
                  <ListeningPlayer parts={speechParts} level={quiz.level} onProgress={onProgress} />
                  {quiz.submitted ? (
                    <article className="passage script">
                      <p className={`script-situation${speaking === 0 ? ' speaking' : ''}`}>
                        <Jp text={quiz.passage} furigana={furigana} />
                      </p>
                      {quiz.script.map((line, i) => (
                        <p key={i} className={`script-line${speaking === i + 1 ? ' speaking' : ''}`}>
                          <span className={`speaker ${line.speaker.toLowerCase()}`}>{SPEAKER_LABEL[line.speaker]}</span>
                          <span>
                            <Jp text={line.text} furigana={furigana} />
                          </span>
                        </p>
                      ))}
                    </article>
                  ) : (
                    <p className="hint study-hint">대화를 듣고 문제를 풀어 보세요. 스크립트는 채점한 뒤에 보여요.</p>
                  )}
                </>
              ) : (
                <article className="passage">
                  <h2 className="passage-title">
                    <Jp text={quiz.title} furigana={furigana} />
                  </h2>
                  {quiz.passage.split(/\n\s*\n/).map((para, i) => (
                    <p key={i}>
                      <Jp text={para} furigana={furigana} />
                    </p>
                  ))}
                </article>
              )}

              {quiz.translation && (
                <details className="translation">
                  <summary>해석 보기</summary>
                  {quiz.translation.split(listening ? /\n/ : /\n\s*\n/).map((para, i) => (
                    <p key={i}>{para}</p>
                  ))}
                </details>
              )}

              <ol className="questions">
                {quiz.questions.map((q, qi) => {
                  const picked = quiz.submitted ? q.selectedIndex : answers[qi];
                  const correct = quiz.submitted && q.selectedIndex === q.answerIndex;
                  return (
                    <li key={qi} className="question">
                      <p className="question-text">
                        <span className="question-no">問{qi + 1}</span>
                        <Jp text={q.question} furigana={furigana} />
                        {quiz.submitted && (
                          <span className={`question-result ${correct ? 'ok' : 'ng'}`}>{correct ? '정답' : '오답'}</span>
                        )}
                      </p>
                      <div className="choices" role="radiogroup" aria-label={`問${qi + 1} 보기`}>
                        {q.choices.map((c, ci) => {
                          const state = quiz.submitted
                            ? ci === q.answerIndex
                              ? ' answer'
                              : ci === picked
                                ? ' wrong'
                                : ''
                            : ci === picked
                              ? ' picked'
                              : '';
                          return (
                            <button
                              key={ci}
                              type="button"
                              role="radio"
                              aria-checked={ci === picked}
                              className={`choice${state}`}
                              onClick={() => choose(qi, ci)}
                              disabled={quiz.submitted}
                            >
                              <span className="choice-mark">{CHOICE_MARKS[ci]}</span>
                              <span>
                                <Jp text={c} furigana={furigana} />
                              </span>
                            </button>
                          );
                        })}
                      </div>
                      {quiz.submitted && q.explanation && <p className="explanation">{q.explanation}</p>}
                    </li>
                  );
                })}
              </ol>

              {!quiz.submitted ? (
                <button
                  type="button"
                  className="btn accent block reading-submit"
                  onClick={submit}
                  disabled={busy || answers.some((a) => a === null)}
                >
                  채점하기
                </button>
              ) : (
                <div className="study-done">
                  <p className="otsukare">
                    {quiz.correctCount} / {quiz.questions.length}
                  </p>
                  <p className="sub">
                    {quiz.correctCount === quiz.questions.length ? '모두 맞혔어요!' : text.retry}
                    {today && today.remainingToday > 0 && ` 오늘 ${today.remainingToday}번 더 만들 수 있어요.`}
                  </p>
                  <div className="reading-actions">
                    {today && today.remainingToday > 0 && (
                      <button type="button" className="btn accent" onClick={create}>
                        새 문제 만들기
                      </button>
                    )}
                    <button type="button" className="btn outline" onClick={() => navigate('/')}>
                      홈으로
                    </button>
                  </div>
                </div>
              )}

              {quiz.sourceCards.length > 0 && (
                <div className="source-cards">
                  <p className="setting-label">오늘 배운 표현</p>
                  <ul>
                    {quiz.sourceCards.map((c) => (
                      <li key={c.front}>
                        <span className="source-front">
                          <Furigana text={c.front} />
                        </span>
                        <span className="source-meaning">{c.meaning}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
