import type { ReactNode } from 'react';

// 회의록(2026-09-07) 규칙: 한자 바로 뒤 대괄호에 읽는 법을 붙인다. 예) 今日[きょう]は忙[いそが]しい
const RUBY = /(\p{Script=Han}+)\[([^\]]+)\]/gu;

/** 대괄호 표기를 <ruby> 로 그린다. */
export function Furigana({ text }: { text: string }) {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const m of text.matchAll(RUBY)) {
    const start = m.index ?? 0;
    if (start > last) parts.push(text.slice(last, start));
    parts.push(
      <ruby key={start}>
        {m[1]}
        <rt>{m[2]}</rt>
      </ruby>,
    );
    last = start + m[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <>{parts}</>;
}

/** 읽는 법만 (食[た]べる → たべる) */
export const toReading = (text: string) => text.replace(RUBY, '$2');

/** 후리가나를 뺀 원문 (食[た]べる → 食べる) */
export const stripFurigana = (text: string) => text.replace(RUBY, '$1');

/** 다음 복습까지 간격 안내 (예: 1분, 6분, 3일, 1.5개월) */
export function formatInterval(seconds: number): string {
  const minutes = seconds / 60;
  if (minutes < 60) return `${Math.max(1, Math.round(minutes))}분`;
  const hours = minutes / 60;
  if (hours < 24) return `${Math.round(hours)}시간`;
  const days = hours / 24;
  if (days < 30) return `${Math.round(days)}일`;
  if (days < 365) return `${trim(days / 30)}개월`;
  return `${trim(days / 365)}년`;
}

const trim = (n: number) => n.toFixed(1).replace(/\.0$/, '');
