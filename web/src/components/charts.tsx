// 통계 화면용 SVG 차트. 라이브러리 없이 막대(누적 포함)·히트맵만 그린다.
// 규칙: 막대 ≤24px·윗끝 4px 둥글게·막대 사이 2px 틈, 격자는 1px 실선, 글자는 데이터 색을 쓰지 않는다.
// 모든 차트는 마우스/키보드 툴팁과 "표로 보기"를 함께 둔다.
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';

/** dataviz 검증기 통과 (밝은 배경 #fffdf8): 학습 → 복습 → 재학습 순서로 인접 배치 */
export const SERIES_COLOR = {
  learning: '#3c6fb0',
  review: '#3a9e6e',
  relearning: '#b8402c',
} as const;

/** 한 계열만 그리는 차트의 막대 색 */
export const SINGLE_COLOR = SERIES_COLOR.learning;

export interface Series {
  key: string;
  name: string;
  color: string;
}

export interface Column {
  /** 툴팁 제목 (날짜, 구간 이름 등) */
  label: string;
  /** 축 아래에 짧게 쓸 글자. 없으면 축에 표시하지 않는다 */
  tick?: string;
  values: Record<string, number>;
  /** 툴팁 맨 아래에 덧붙일 줄 */
  note?: string;
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(el.clientWidth);
    const ro = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/** 깔끔한 눈금 (0, 5, 10 / 0, 50, 100 …) */
function niceMax(max: number): { top: number; step: number } {
  if (max <= 0) return { top: 4, step: 1 };
  const rough = max / 3;
  const pow = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= rough) ?? 10 * pow;
  return { top: Math.ceil(max / step) * step, step };
}

/** 윗끝만 둥근 막대 경로 */
function topRounded(x: number, y: number, w: number, h: number, r: number) {
  const rr = Math.max(0, Math.min(r, w / 2, h));
  return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`;
}

const fmt = (n: number) => n.toLocaleString('ko-KR');

interface TipState {
  x: number;
  y: number;
  title: string;
  rows: { color?: string; name: string; value: string }[];
  note?: string;
}

function Tooltip({ tip }: { tip: TipState | null }) {
  if (!tip) return null;
  return (
    <div className="chart-tip" style={{ left: tip.x, top: tip.y }} role="status">
      <p className="chart-tip-title">{tip.title}</p>
      {tip.rows.map((r) => (
        <p key={r.name} className="chart-tip-row">
          {r.color && <span className="line-key" style={{ background: r.color }} />}
          <strong>{r.value}</strong>
          <span>{r.name}</span>
        </p>
      ))}
      {tip.note && <p className="chart-tip-note">{tip.note}</p>}
    </div>
  );
}

const PLOT_H = 160;
const PAD = { top: 10, right: 4, bottom: 22, left: 34 };

/** 세로 막대 (계열이 여럿이면 누적). 계열이 하나면 범례를 그리지 않는다. */
export function ColumnChart({
  columns,
  series,
  unit = '',
  empty = '아직 기록이 없어요',
}: {
  columns: Column[];
  series: Series[];
  unit?: string;
  empty?: string;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [tip, setTip] = useState<TipState | null>(null);
  const [hover, setHover] = useState(-1);

  const totals = columns.map((c) => series.reduce((s, k) => s + (c.values[k.key] ?? 0), 0));
  const max = Math.max(0, ...totals);
  const { top, step } = niceMax(max);
  const plotW = Math.max(0, width - PAD.left - PAD.right);
  const slot = columns.length ? plotW / columns.length : 0;
  const gap = slot > 6 ? 2 : 0;
  const barW = Math.max(1, Math.min(24, slot - gap));
  const y = (v: number) => PAD.top + PLOT_H - (v / top) * PLOT_H;
  const ticks = Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);

  const show = (i: number, x: number) => {
    const c = columns[i];
    setHover(i);
    setTip({
      x: Math.min(Math.max(x, 70), width - 70),
      y: Math.max(0, y(totals[i]) - 8),
      title: c.label,
      rows: series.map((s) => ({
        color: series.length > 1 ? s.color : undefined,
        name: s.name,
        value: `${fmt(c.values[s.key] ?? 0)}${unit}`,
      })),
      note: c.note,
    });
  };
  const hide = () => {
    setHover(-1);
    setTip(null);
  };

  return (
    <div className="chart" ref={ref}>
      {series.length > 1 && (
        <ul className="chart-legend">
          {series.map((s) => (
            <li key={s.key}>
              <span className="swatch" style={{ background: s.color }} />
              {s.name}
            </li>
          ))}
        </ul>
      )}
      {max === 0 && <p className="chart-empty">{empty}</p>}
      {width > 0 && (
        <svg width={width} height={PAD.top + PLOT_H + PAD.bottom} role="img" aria-label="막대 그래프" onPointerLeave={hide}>
          {ticks.map((t) => (
            <g key={t}>
              <line className="grid" x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} />
              <text className="axis" x={PAD.left - 6} y={y(t)} dy="0.32em" textAnchor="end">
                {fmt(t)}
              </text>
            </g>
          ))}
          {columns.map((c, i) => {
            const cx = PAD.left + i * slot + slot / 2;
            let acc = 0;
            const segments = series
              .map((s) => ({ s, v: c.values[s.key] ?? 0 }))
              .filter((seg) => seg.v > 0);
            return (
              <g key={i} className={hover === i ? 'col hover' : 'col'}>
                {segments.map((seg, si) => {
                  const y0 = y(acc);
                  acc += seg.v;
                  const y1 = y(acc);
                  // 누적 막대 사이 2px 틈
                  const h = Math.max(0, y0 - y1 - (si > 0 ? 2 : 0));
                  const last = si === segments.length - 1;
                  return last ? (
                    <path key={seg.s.key} d={topRounded(cx - barW / 2, y1, barW, h, 4)} fill={seg.s.color} />
                  ) : (
                    <rect key={seg.s.key} x={cx - barW / 2} y={y1} width={barW} height={h} fill={seg.s.color} />
                  );
                })}
                {/* 칸 전체가 마우스·키보드 대상 */}
                <rect
                  className="hit"
                  x={PAD.left + i * slot}
                  y={PAD.top}
                  width={slot}
                  height={PLOT_H}
                  tabIndex={columns.length <= 60 ? 0 : -1}
                  onPointerEnter={() => show(i, cx)}
                  onFocus={() => show(i, cx)}
                  onBlur={hide}
                />
                {c.tick && (
                  <text className="axis" x={cx} y={PAD.top + PLOT_H + 15} textAnchor="middle">
                    {c.tick}
                  </text>
                )}
              </g>
            );
          })}
          <line className="baseline" x1={PAD.left} x2={width - PAD.right} y1={y(0)} y2={y(0)} />
        </svg>
      )}
      <Tooltip tip={tip} />
    </div>
  );
}

/** 최근 1년 달력 히트맵 (한 칸 = 하루, 세로 = 요일). 진하기 = 복습 수 */
export function CalendarHeatmap({ today, counts }: { today: string; counts: { date: string; count: number }[] }) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [tip, setTip] = useState<TipState | null>(null);

  const end = new Date(`${today}T00:00:00`);
  const byDate = new Map(counts.map((c) => [c.date, c.count]));
  const max = Math.max(1, ...counts.map((c) => c.count));
  // 1년(53주)을 그리되, 좁은 화면에서는 칸이 8px 아래로 작아지지 않도록 최근 몇 주만 그린다
  const left = 18;
  const weeks = Math.max(1, Math.min(53, Math.floor((width - left) / 10)));
  const cell = Math.max(8, Math.min(12, Math.floor((width - left) / weeks) - 2));
  const pitch = cell + 2;
  const start = new Date(end);
  start.setDate(start.getDate() - end.getDay() - (weeks - 1) * 7);
  const days: { date: Date; key: string }[] = [];
  for (const d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    days.push({ date: new Date(d), key: isoDate(d) });
  }
  // 월 이름은 앞 이름과 충분히 떨어져 있고 오른쪽 끝을 넘지 않을 때만
  let lastLabelX = -Infinity;
  const monthLabel = (x: number) => {
    if (x - lastLabelX < 30 || x + 24 > width) return false;
    lastLabelX = x;
    return true;
  };

  // 같은 초록의 4단계 (옅음 → 진함), 기록 없는 날은 바탕보다 한 단계 진한 회색
  const level = (n: number) => (n === 0 ? 0 : Math.min(4, Math.ceil((n / max) * 4)));
  const fill = ['var(--chart-empty)', '#c9e6d5', '#8fcaa8', '#5aae80', SERIES_COLOR.review];

  return (
    <div className="chart" ref={ref}>
      {width > 0 && (
        <svg width={width} height={pitch * 7 + 18} role="img" aria-label="최근 1년 학습 달력">
          {['일', '', '화', '', '목', '', '토'].map((d, i) =>
            d ? (
              <text key={i} className="axis" x={0} y={18 + i * pitch + cell / 2} dy="0.32em">
                {d}
              </text>
            ) : null,
          )}
          {days.map(({ date, key }, i) => {
            const col = Math.floor(i / 7);
            const n = byDate.get(key) ?? 0;
            const x = left + col * pitch;
            const yy = 18 + date.getDay() * pitch;
            return (
              <g key={key}>
                {date.getDate() === 1 && monthLabel(x) && (
                  <text className="axis" x={x} y={10}>
                    {date.getMonth() + 1}월
                  </text>
                )}
                <rect
                  className="heat"
                  x={x}
                  y={yy}
                  width={cell}
                  height={cell}
                  rx={2}
                  fill={fill[level(n)]}
                  onPointerEnter={() =>
                    setTip({
                      x: Math.min(Math.max(x, 70), width - 70),
                      y: yy - 6,
                      title: `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`,
                      rows: [{ name: '복습', value: `${n}회` }],
                    })
                  }
                  onPointerLeave={() => setTip(null)}
                />
              </g>
            );
          })}
        </svg>
      )}
      <div className="heat-legend" aria-hidden="true">
        적음
        {fill.map((f, i) => (
          <span key={i} className="heat-swatch" style={{ background: f }} />
        ))}
        많음
      </div>
      <Tooltip tip={tip} />
    </div>
  );
}

export function isoDate(d: Date) {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** 차트 카드: 제목·설명·요약 + 차트 + 표로 보기 */
export function ChartCard({
  title,
  sub,
  summary,
  table,
  children,
}: {
  title: string;
  sub?: string;
  summary?: ReactNode;
  table?: { head: string[]; rows: (string | number)[][] };
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  useEffect(() => setOpen(false), [table?.rows.length]);
  return (
    <section className="chart-card">
      <h2>{title}</h2>
      {sub && <p className="chart-sub">{sub}</p>}
      {children}
      {summary && <div className="chart-summary">{summary}</div>}
      {table && (
        <details className="chart-table" open={open} onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
          <summary>표로 보기</summary>
          {open && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    {table.head.map((h) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {table.rows.map((r, i) => (
                    <tr key={i}>
                      {r.map((v, j) => (
                        <td key={j}>{typeof v === 'number' ? fmt(v) : v}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </details>
      )}
    </section>
  );
}
