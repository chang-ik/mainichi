import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import { onSessionExpired, refreshTokens } from '../api/http';
import type { LoginResponse, UpdateProfileRequest, User } from '../api/types';
import { userApi } from '../api/user';
import { tokenStorage } from './tokenStorage';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: Status;
  user: User | null;
  /** 카카오 인가 코드로 로그인 */
  loginWithKakaoCode: (code: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  updateProfile: (input: UpdateProfileRequest) => Promise<void>;
  deleteAccount: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  // 리프레시 토큰은 httpOnly 쿠키라 JS 에서 존재 여부를 알 수 없으므로, 항상 복원을 시도한다.
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<User | null>(null);

  // 앱 시작 시 리프레시 토큰 쿠키로 액세스 토큰을 받아 세션을 복원한다.
  useEffect(() => {
    if (status !== 'loading') return;
    let cancelled = false;
    refreshTokens()
      .then((ok) => {
        if (!ok) throw new Error('no session');
        return userApi.me();
      })
      .then((me) => {
        if (cancelled) return;
        setUser(me);
        setStatus('authenticated');
      })
      .catch(() => {
        if (cancelled) return;
        // 네트워크 오류로 실패했을 수도 있지만, 복원할 수 없으면 로그인 화면으로 보낸다.
        tokenStorage.clear();
        setStatus('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, [status]);

  // 리프레시 토큰이 만료/폐기되면 로그인 화면으로
  useEffect(
    () =>
      onSessionExpired(() => {
        setUser(null);
        setStatus('anonymous');
      }),
    [],
  );

  const loginWithKakaoCode = useCallback(async (code: string) => {
    const res = await authApi.loginWithKakao(code);
    tokenStorage.setAccess(res.accessToken);
    setUser(res.user);
    setStatus('authenticated');
    return res;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // 서버 폐기에 실패해도 클라이언트 세션은 반드시 끝낸다.
    }
    tokenStorage.clear();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const updateProfile = useCallback(async (input: UpdateProfileRequest) => {
    setUser(await userApi.update(input));
  }, []);

  const deleteAccount = useCallback(async () => {
    await userApi.remove();
    tokenStorage.clear();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const value = useMemo(
    () => ({ status, user, loginWithKakaoCode, logout, updateProfile, deleteAccount }),
    [status, user, loginWithKakaoCode, logout, updateProfile, deleteAccount],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
