// 백엔드 UpdateProfileRequest: @Size(min = 2, max = 20)
export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 20;

/** 문제가 있으면 에러 메시지, 없으면 null */
export function validateNickname(value: string): string | null {
  const len = value.trim().length;
  if (len < NICKNAME_MIN || len > NICKNAME_MAX) {
    return `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자로 입력해 주세요.`;
  }
  return null;
}

export function errorMessage(e: unknown, fallback = '문제가 발생했습니다. 잠시 후 다시 시도해 주세요.'): string {
  return e instanceof Error && e.message ? e.message : fallback;
}
