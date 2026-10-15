import { LEVELS } from './level';

/** 게시판 단계 탭 순서: 쉬운 단계부터 (기초 → N5 → … → N1) */
export const BOARD_LEVELS = [...LEVELS].reverse();

/** "2026-10-15T09:30:00" → 오늘이면 "09:30", 올해면 "10/15", 아니면 "2025/10/15" */
export function formatPostDate(iso: string, now = new Date()): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  if (d.toDateString() === now.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}/${d.getDate()}`;
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
}

export const authorName = (name: string | null) => name || '알 수 없음';
