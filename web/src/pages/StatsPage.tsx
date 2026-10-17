import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { statsApi } from '../api/stats';
import type { Stats, StatsDays, StatsDeck, StatsHistogram } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CalendarHeatmap, ChartCard, ColumnChart, SERIES_COLOR, SINGLE_COLOR, type Column, type Series } from '../components/charts';
import { levelName } from '../utils/level';
import { errorMessage } from '../utils/nickname';

const DECKS: { value: StatsDeck; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'words', label: '단어' },
  { value: 'grammar', label: '문법' },
];

const PERIODS: { value: StatsDays; label: string }[] = [
  { value: 30, label: '1개월' },
  { value: 90, label: '3개월' },
  { value: 365, label: '1년' },
];

const REVIEW_SERIES: Series[] = [
  { key: 'learning', name: '학습', color: SERIES_COLOR.learning },
  { key: 'review', name: '복습', color: SERIES_COLOR.review },
  { key: 'relearning', name: '재학습', color: SERIES_COLOR.relearning },
];

const BUTTON_GROUP = { LEARNING: '학습 중', YOUNG: '어린 카드', MATURE: '성숙 카드' } as const;
const BUTTON_NAMES = ['✕ 다시', '△ 어려움', '○ 알맞음', '◎ 쉬움'];

/** 초 → "12분", "1시간 5분" */
function duration(seconds: number) {
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}분`;
  return `${Math.floor(minutes / 60)}시간${minutes % 60 ? ` ${minutes % 60}분` : ''}`;
}

const pct = (v: number | null) => (v == null ? '–' : `${v}%`);
const md = (iso: string) => {
  const [, m, d] = iso.split('-');
  return `${Number(m)}/${Number(d)}`;
};
const addDays = (iso: string, n: number) => {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  return `${d.getMonth() + 1}/${d.getDate()}`;
};

/** n 개 칸 중 대략 5곳에만 축 글자를 단다 */
const tickEvery = (n: number) => Math.max(1, Math.ceil(n / 5));

/** 1년 보기는 하루 막대가 너무 가늘어 주 단위로 묶는다 */
function bin<T>(items: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

function histogramColumns(h: StatsHistogram): Column[] {
  return h.buckets.map((b) => ({ label: b.label, tick: b.label, values: { count: b.count } }));
}

/** Anki 통계 화면을 본뜬 학습 통계 */
export function StatsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [deck, setDeck] = useState<StatsDeck>('all');
  const [days, setDays] = useState<StatsDays>(30);
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    statsApi
      .get(deck, days)
      .then(setStats, (e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [deck, days]);

  const weekly = days === 365;
  const binSize = weekly ? 7 : 1;

  const reviewColumns: Column[] = stats
    ? bin(stats.reviews, binSize).map((group, i, all) => {
        const first = group[0].date;
        const last = group[group.length - 1].date;
        const sum = (k: 'learning' | 'review' | 'relearning' | 'newCards' | 'seconds') =>
          group.reduce((s, d) => s + d[k], 0);
        return {
          label: weekly ? `${md(first)}–${md(last)}` : md(first),
          tick: i % tickEvery(all.length) === 0 || i === all.length - 1 ? md(first) : undefined,
          values: { learning: sum('learning'), review: sum('review'), relearning: sum('relearning') },
          note: `새 카드 ${sum('newCards')}장 · ${duration(sum('seconds'))}`,
        };
      })
    : [];

  const forecastColumns: Column[] = stats
    ? bin(stats.forecast.due, binSize).map((group, i, all) => {
        const start = i * binSize;
        const label = start === 0 ? (weekly ? '이번 주' : '오늘') : addDays(stats.today, start);
        return {
          label: weekly && start > 0 ? `${label}부터 1주` : label,
          tick: i % tickEvery(all.length) === 0 ? (start === 0 ? '오늘' : `+${start}일`) : undefined,
          values: { count: group.reduce((s, n) => s + n, 0) },
        };
      })
    : [];

  const hourColumns: Column[] = stats
    ? stats.hours.map((h) => ({
        label: `${h.hour}시`,
        tick: h.hour % 6 === 0 ? `${h.hour}시` : undefined,
        values: { count: h.count },
        note: h.correctRate == null ? undefined : `정답률 ${h.correctRate}%`,
      }))
    : [];

  const single: Series[] = [{ key: 'count', name: '카드', color: SINGLE_COLOR }];
  const t = stats?.todayStats;

  return (
    <div className="study">
      <div className="study-inner stats-inner">
        <header className="study-header">
          <button type="button" className="study-back" onClick={() => navigate('/')} title="홈" aria-label="홈으로">
            ⟵
          </button>
          <button type="button" className="study-logo" onClick={() => navigate('/')}>
            毎日
          </button>
          <span className="study-nick">{user?.nickname}</span>
        </header>

        <div className="stats-head">
          <div>
            <p className="study-eyebrow">학습 기록</p>
            <h1>통계</h1>
          </div>
          {/* 필터는 차트 위 한 줄에. 아래 모든 숫자가 같은 범위를 쓴다 */}
          <div className="stats-filters">
            <div className="segmented" role="group" aria-label="카드 종류">
              {DECKS.map((d) => (
                <button key={d.value} type="button" className={deck === d.value ? 'on' : ''} onClick={() => setDeck(d.value)}>
                  {d.label}
                </button>
              ))}
            </div>
            <div className="segmented" role="group" aria-label="기간">
              {PERIODS.map((p) => (
                <button key={p.value} type="button" className={days === p.value ? 'on' : ''} onClick={() => setDays(p.value)}>
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {!stats && !error && <p className="status-msg study-loading">불러오는 중…</p>}

        {stats && t && (
          <div className={`stats-body${loading ? ' refreshing' : ''}`}>
            <div className="stat-tiles">
              <div className="stat-tile">
                <p className="stat-label">연속 학습일</p>
                <p className="stat-value hero">{stats.streak.current}일</p>
                <p className="stat-sub">최장 {stats.streak.longest}일</p>
              </div>
              <div className="stat-tile">
                <p className="stat-label">오늘 공부한 카드</p>
                <p className="stat-value">{t.reviews}장</p>
                <p className="stat-sub">{duration(t.seconds)}</p>
              </div>
              <div className="stat-tile">
                <p className="stat-label">오늘 정답률</p>
                <p className="stat-value">{pct(t.correctRate)}</p>
                <p className="stat-sub">다시 {t.again}번</p>
              </div>
              <div className="stat-tile">
                <p className="stat-label">내일 예정</p>
                <p className="stat-value">{stats.forecast.dueTomorrow}장</p>
                <p className="stat-sub">밀린 카드 {stats.forecast.overdue}장</p>
              </div>
            </div>

            <ChartCard title="오늘">
              {t.reviews === 0 ? (
                <p className="chart-text">오늘은 아직 공부한 카드가 없어요.</p>
              ) : (
                <p className="chart-text">
                  오늘 <strong>{t.reviews}장</strong>을 {duration(t.seconds)} 동안 공부했어요
                  {t.seconds > 0 && ` (장당 ${Math.round(t.seconds / t.reviews)}초)`}.
                  <br />
                  다시 {t.again}번 · 정답률 {pct(t.correctRate)}
                  <br />
                  새 카드 {t.newCards} · 학습 {t.learning} · 복습 {t.review} · 재학습 {t.relearning}
                  {t.matureReviews > 0 && (
                    <>
                      <br />
                      성숙 카드 정답률 {pct(t.matureCorrectRate)} ({t.matureReviews}장)
                    </>
                  )}
                </p>
              )}
            </ChartCard>

            <ChartCard
              title="달력"
              sub="하루에 복습한 횟수 (최근 1년, 좁은 화면에서는 최근 몇 달)"
              summary={
                <>
                  <span>
                    {PERIODS.find((p) => p.value === days)?.label} 중 공부한 날 <strong>{stats.streak.studiedDays}</strong> /{' '}
                    {stats.streak.periodDays}일 (
                    {Math.round((stats.streak.studiedDays / stats.streak.periodDays) * 100)}%)
                  </span>
                  <span>
                    최장 연속 <strong>{stats.streak.longest}일</strong>
                  </span>
                  <span>
                    현재 연속 <strong>{stats.streak.current}일</strong>
                  </span>
                </>
              }
              table={{ head: ['날짜', '복습'], rows: [...stats.calendar].reverse().map((c) => [c.date, c.count]) }}
            >
              <CalendarHeatmap today={stats.today} counts={stats.calendar} />
            </ChartCard>

            <ChartCard
              title="앞으로 볼 카드"
              sub={`앞으로 ${PERIODS.find((p) => p.value === days)?.label} 동안 복습 예정${weekly ? ' (주 단위)' : ''}`}
              summary={
                <>
                  <span>
                    내일 <strong>{stats.forecast.dueTomorrow}장</strong>
                  </span>
                  <span>
                    밀린 카드 <strong>{stats.forecast.overdue}장</strong>
                  </span>
                  <span>
                    하루 평균 부담 <strong>{stats.forecast.dailyLoad}장</strong>
                  </span>
                </>
              }
              table={{ head: ['날', '카드'], rows: forecastColumns.map((c) => [c.label, c.values.count]) }}
            >
              <ColumnChart columns={forecastColumns} series={single} unit="장" empty="예정된 복습이 없어요" />
            </ChartCard>

            <ChartCard
              title="복습"
              sub={`복습 전 단계로 나눈 복습 횟수${weekly ? ' (주 단위)' : ''}`}
              summary={
                <>
                  <span>
                    공부한 날 <strong>{stats.reviewSummary.daysStudied}</strong> / {days}일
                  </span>
                  <span>
                    총 <strong>{stats.reviewSummary.total.toLocaleString('ko-KR')}회</strong>
                  </span>
                  <span>
                    공부한 날 평균 <strong>{stats.reviewSummary.averagePerStudiedDay}회</strong>
                  </span>
                  <span>
                    공부 시간 <strong>{duration(stats.reviewSummary.seconds)}</strong>
                  </span>
                </>
              }
              table={{
                head: ['날짜', '학습', '복습', '재학습', '새 카드', '시간'],
                rows: stats.reviews
                  .filter((d) => d.learning + d.review + d.relearning > 0)
                  .reverse()
                  .map((d) => [d.date, d.learning, d.review, d.relearning, d.newCards, duration(d.seconds)]),
              }}
            >
              <ColumnChart columns={reviewColumns} series={REVIEW_SERIES} unit="회" />
            </ChartCard>

            <ChartCard
              title="카드 수"
              sub={`새 카드는 지금 단계(${levelName(user?.currentLevel)}) 덱에서 아직 안 본 카드`}
              table={{
                head: ['종류', '카드'],
                rows: [
                  ['새 카드', stats.cardCounts.newCards],
                  ['학습 중', stats.cardCounts.learning],
                  ['재학습', stats.cardCounts.relearning],
                  ['어린 카드 (간격 21일 미만)', stats.cardCounts.young],
                  ['성숙 카드 (간격 21일 이상)', stats.cardCounts.mature],
                ],
              }}
            >
              <CardCountBars counts={stats.cardCounts} />
            </ChartCard>

            <HistogramCard title="복습 간격" sub="복습 단계 카드의 다음 복습까지 간격" unit="일" h={stats.intervals} />
            <HistogramCard title="안정도" sub="기억률이 90%로 떨어지기까지 걸리는 날 (FSRS)" unit="일" h={stats.stability} />
            <HistogramCard title="난이도" sub="카드가 얼마나 어려운지 (FSRS, 높을수록 어려움)" unit="%" h={stats.difficulty} />
            <HistogramCard title="지금 기억할 확률" sub="복습 단계 카드를 지금 보면 기억할 확률 (FSRS)" unit="%" h={stats.retrievability} />

            <ChartCard
              title="시간대"
              sub="몇 시에 공부했는지 (기간 내)"
              table={{
                head: ['시', '복습', '정답률'],
                rows: stats.hours.filter((h) => h.count > 0).map((h) => [`${h.hour}시`, h.count, pct(h.correctRate)]),
              }}
            >
              <ColumnChart columns={hourColumns} series={[{ key: 'count', name: '복습', color: SINGLE_COLOR }]} unit="회" />
            </ChartCard>

            <ChartCard title="평가 버튼" sub="카드 단계별로 누른 버튼 (기간 내). 정답률 = ✕ 가 아닌 비율">
              <div className="table-scroll">
                <table className="stats-table">
                  <thead>
                    <tr>
                      <th />
                      {BUTTON_NAMES.map((n) => (
                        <th key={n}>{n}</th>
                      ))}
                      <th>정답률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.buttons.map((b) => (
                      <tr key={b.group}>
                        <th>{BUTTON_GROUP[b.group]}</th>
                        {b.counts.map((c, i) => (
                          <td key={i}>{c.toLocaleString('ko-KR')}</td>
                        ))}
                        <td>
                          <strong>{pct(b.correctRate)}</strong>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </ChartCard>

            <ChartCard title="실제 기억률" sub="복습 단계 카드를 기억해 낸 비율 (✕ 가 아닌 비율). 어린/성숙은 지난 간격 21일 기준">
              <div className="table-scroll">
                <table className="stats-table">
                  <thead>
                    <tr>
                      <th />
                      <th>어린 카드</th>
                      <th>성숙 카드</th>
                      <th>전체</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.retention.map((r) => {
                      const cell = (pass: number, fail: number) =>
                        pass + fail === 0 ? '–' : `${Math.round((pass / (pass + fail)) * 1000) / 10}% (${pass}/${pass + fail})`;
                      return (
                        <tr key={r.label}>
                          <th>{r.label}</th>
                          <td>{cell(r.youngPass, r.youngFail)}</td>
                          <td>{cell(r.maturePass, r.matureFail)}</td>
                          <td>
                            <strong>{cell(r.youngPass + r.maturePass, r.youngFail + r.matureFail)}</strong>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </ChartCard>
          </div>
        )}
      </div>
    </div>
  );
}

function HistogramCard({ title, sub, unit, h }: { title: string; sub: string; unit: string; h: StatsHistogram }) {
  return (
    <ChartCard
      title={title}
      sub={sub}
      summary={
        h.average != null && (
          <>
            <span>
              평균 <strong>{h.average}{unit}</strong>
            </span>
            {h.max != null && (
              <span>
                최대 <strong>{h.max}{unit}</strong>
              </span>
            )}
          </>
        )
      }
      table={{ head: ['구간', '카드'], rows: h.buckets.map((b) => [b.label, b.count]) }}
    >
      <ColumnChart
        columns={histogramColumns(h)}
        series={[{ key: 'count', name: '카드', color: SINGLE_COLOR }]}
        unit="장"
        empty="복습 단계 카드가 아직 없어요"
      />
    </ChartCard>
  );
}

/** 카드 수: 한 계열 가로 막대 (값은 막대 끝에) */
function CardCountBars({ counts }: { counts: Stats['cardCounts'] }) {
  const rows = [
    { name: '새 카드', value: counts.newCards },
    { name: '학습 중', value: counts.learning },
    { name: '재학습', value: counts.relearning },
    { name: '어린 카드', value: counts.young },
    { name: '성숙 카드', value: counts.mature },
  ];
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <ul className="hbars">
      {rows.map((r) => (
        <li key={r.name}>
          <span className="hbar-name">{r.name}</span>
          <span className="hbar-track">
            {/* 값 글자 자리(64px)를 남기고 막대를 늘린다 */}
            {r.value > 0 && <span className="hbar" style={{ width: `calc((100% - 64px) * ${r.value / max})` }} />}
            <span className="hbar-value">{r.value.toLocaleString('ko-KR')}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}
