import type { StudyArea, StudyKind } from '../api/types';

export interface StudyAreaOption {
  key: StudyArea;
  name: string;
  glyph: string;
  desc: string;
  /** 카드 학습 화면이 있는 영역. 없으면 "준비 중" */
  kind?: StudyKind;
}

// 목업 STUDY_TYPES 와 같은 순서·글자 (대화는 제외)
export const STUDY_AREAS: StudyAreaOption[] = [
  { key: 'WORD', name: '단어', glyph: '語', desc: '단어 카드 뒤집기', kind: 'words' },
  { key: 'GRAMMAR', name: '문법', glyph: '文', desc: '예문으로 익히는 문형 카드', kind: 'grammar' },
  { key: 'READING', name: '장문', glyph: '読', desc: '짧은 글 읽고 이해하기' },
  { key: 'LISTENING', name: '듣기', glyph: '聴', desc: '음성 듣고 뜻 맞히기' },
];

/** 학습 화면 경로 */
export const studyPath = (kind: StudyKind) => (kind === 'words' ? '/study' : `/study/${kind}`);

/** 유저가 고른 영역을 목업 순서대로. 단어는 항상 포함된다. */
export function selectedAreas(areas: StudyArea[] | undefined): StudyAreaOption[] {
  const picked = new Set<StudyArea>(['WORD', ...(areas ?? [])]);
  return STUDY_AREAS.filter((a) => picked.has(a.key));
}
