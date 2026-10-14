// 청해 음성: 브라우저 음성 합성(Web Speech API)으로 일본어를 읽는다.
// 한자는 오독하기 쉬우므로 후리가나(대괄호) 읽기로 바꿔서 읽힌다.
import { toReading } from './furigana';

export type Speaker = 'MALE' | 'FEMALE' | 'NARRATOR';

export interface SpeechPart {
  speaker: Speaker;
  /** 후리가나 대괄호 표기 */
  text: string;
}

export const speechSupported = () => typeof window !== 'undefined' && 'speechSynthesis' in window;

// 브라우저·OS 마다 일본어 음성 이름이 다르다. 이름으로 성별을 짐작한다.
const MALE_HINT = /male|男|ichiro|keita|otoya|daichi|naoki|takumi|hattori/i;
const FEMALE_HINT = /female|女|haruka|ayumi|kyoko|nanami|sayaka|mizuki|o-ren|aoi|shiori|mayu/i;

export interface JapaneseVoices {
  male: SpeechSynthesisVoice | null;
  female: SpeechSynthesisVoice | null;
  narrator: SpeechSynthesisVoice | null;
}

/** 쓸 수 있는 일본어 음성. 목록은 비동기로 채워지므로 voiceschanged 뒤에 다시 부른다. */
export function pickVoices(): JapaneseVoices {
  if (!speechSupported()) return { male: null, female: null, narrator: null };
  const ja = window.speechSynthesis.getVoices().filter((v) => v.lang.replace('_', '-').toLowerCase().startsWith('ja'));
  // "Female" 에도 male 이 들어 있으므로 여성 판별을 먼저 한다
  const female = ja.find((v) => FEMALE_HINT.test(v.name)) ?? null;
  const male = ja.find((v) => !FEMALE_HINT.test(v.name) && MALE_HINT.test(v.name)) ?? null;
  const fallback = ja.find((v) => v.default) ?? ja[0] ?? null;
  // 시험처럼 상황 설명은 대화하는 두 사람과 다른 목소리로 (남는 음성이 없으면 여성 음성)
  const narrator = ja.find((v) => v !== female && v !== male && FEMALE_HINT.test(v.name)) ?? female ?? fallback;
  return {
    male: male ?? fallback,
    female: female ?? fallback,
    narrator,
  };
}

/** 단계별 기본 속도 (기초가 가장 느리다) */
const LEVEL_RATE: Record<number, number> = { 0: 0.8, 5: 0.85, 4: 0.9, 3: 0.95, 2: 1.0, 1: 1.05 };

export function utteranceFor(part: SpeechPart, voices: JapaneseVoices, level: number, speed: number) {
  const u = new SpeechSynthesisUtterance(toReading(part.text));
  u.lang = 'ja-JP';
  u.rate = (LEVEL_RATE[level] ?? 1) * speed;
  const voice = part.speaker === 'MALE' ? voices.male : part.speaker === 'FEMALE' ? voices.female : voices.narrator;
  if (voice) u.voice = voice;
  // 남녀 목소리가 같은 음성이면 높낮이로 구분한다
  const sameVoice = voices.male === voices.female;
  if (sameVoice && part.speaker === 'MALE') u.pitch = 0.7;
  if (sameVoice && part.speaker === 'FEMALE') u.pitch = 1.3;
  return u;
}
