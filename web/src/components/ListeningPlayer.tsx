import { useEffect, useRef, useState } from 'react';
import { pickVoices, speechSupported, utteranceFor, type JapaneseVoices, type SpeechPart } from '../utils/speech';

const SPEEDS = [0.75, 1, 1.25];

interface Props {
  /** 차례대로 읽을 문장 (상황 설명 → 대화) */
  parts: SpeechPart[];
  level: number;
  /** 지금 읽고 있는 문장 번호 (-1 = 멈춤). 스크립트 강조에 쓴다 */
  onProgress?: (index: number) => void;
}

/** 청해 대화를 브라우저 음성으로 한 문장씩 읽어 준다. */
export function ListeningPlayer({ parts, level, onProgress }: Props) {
  const [voices, setVoices] = useState<JapaneseVoices>(() => pickVoices());
  const [playing, setPlaying] = useState(false);
  const [index, setIndex] = useState(-1);
  const [plays, setPlays] = useState(0);
  const [speed, setSpeed] = useState(1);
  // 멈춘 뒤에 늦게 도착한 onend 가 다음 문장을 읽지 않도록 재생마다 번호를 붙인다
  const runId = useRef(0);

  useEffect(() => {
    if (!speechSupported()) return;
    const update = () => setVoices(pickVoices());
    window.speechSynthesis.addEventListener('voiceschanged', update);
    // 목록이 이미 채워진 뒤라 voiceschanged 가 다시 오지 않을 수 있다
    update();
    return () => {
      window.speechSynthesis.removeEventListener('voiceschanged', update);
      runId.current++;
      window.speechSynthesis.cancel();
    };
  }, []);

  useEffect(() => {
    onProgress?.(index);
  }, [index, onProgress]);

  const stop = () => {
    runId.current++;
    window.speechSynthesis.cancel();
    setPlaying(false);
    setIndex(-1);
  };

  const play = () => {
    const synth = window.speechSynthesis;
    synth.cancel();
    const current = voices.narrator ? voices : pickVoices();
    if (current !== voices) setVoices(current);
    const id = ++runId.current;
    setPlaying(true);
    setPlays((n) => n + 1);

    const speakAt = (i: number) => {
      if (runId.current !== id) return;
      if (i >= parts.length) {
        setPlaying(false);
        setIndex(-1);
        return;
      }
      setIndex(i);
      const u = utteranceFor(parts[i], current, level, speed);
      // 문장 사이에 짧게 쉰다
      u.onend = () => window.setTimeout(() => speakAt(i + 1), 450);
      u.onerror = () => {
        if (runId.current === id) {
          setPlaying(false);
          setIndex(-1);
        }
      };
      synth.speak(u);
    };
    speakAt(0);
  };

  if (!speechSupported()) {
    return <p className="error">이 브라우저는 음성 재생을 지원하지 않아요. 크롬이나 사파리에서 열어 주세요.</p>;
  }

  const noJapanese = !voices.narrator;

  return (
    <div className="player">
      <button
        type="button"
        className={`player-btn${playing ? ' playing' : ''}`}
        onClick={playing ? stop : play}
        aria-label={playing ? '멈춤' : '듣기'}
      >
        {playing ? '■' : '▶'}
      </button>
      <div className="player-body">
        <div className="player-track" aria-hidden="true">
          {parts.map((_, i) => (
            <span key={i} className={`player-dot${i === index ? ' on' : i < index ? ' done' : ''}`} />
          ))}
        </div>
        <p className="player-meta">
          {playing ? `${index + 1} / ${parts.length}` : plays === 0 ? '눌러서 대화를 들어 보세요' : `${plays}번 들었어요`}
          {noJapanese && ' · 일본어 음성이 없어 기본 음성으로 읽어요'}
        </p>
      </div>
      <div className="player-speed" role="group" aria-label="재생 속도">
        {SPEEDS.map((s) => (
          <button
            key={s}
            type="button"
            className={s === speed ? 'on' : ''}
            onClick={() => setSpeed(s)}
            disabled={playing}
          >
            {s}×
          </button>
        ))}
      </div>
    </div>
  );
}
