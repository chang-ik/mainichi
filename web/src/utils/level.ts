// 백엔드 JlptLevel: 0=기초(가나), 1~5=N1~N5
export interface LevelOption {
  value: number;
  name: string;
  desc: string;
}

// 목업 "학습 변경" 순서 그대로 (N1 → N5 → 기초)
export const LEVELS: LevelOption[] = [
  { value: 1, name: 'N1', desc: '고급 어휘' },
  { value: 2, name: 'N2', desc: '장문 · 청해' },
  { value: 3, name: 'N3', desc: '한자 · 독해' },
  { value: 4, name: 'N4', desc: '일상 회화' },
  { value: 5, name: 'N5', desc: '기초 단어 · 문법' },
  { value: 0, name: '기초', desc: '가나부터' },
];

export function levelName(level: number | undefined): string {
  return LEVELS.find((l) => l.value === level)?.name ?? 'N5';
}
