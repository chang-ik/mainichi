import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Splash } from './components/Logo';
import { ToastProvider } from './components/Toast';
import { BoardPage } from './pages/BoardPage';
import { HubPage } from './pages/HubPage';
import { KakaoCallbackPage } from './pages/KakaoCallbackPage';
import { LoginPage } from './pages/LoginPage';
import { NicknameSetupPage } from './pages/NicknameSetupPage';
import { PostPage } from './pages/PostPage';
import { PostWritePage } from './pages/PostWritePage';
import { QuizPage } from './pages/QuizPage';
import { StatsPage } from './pages/StatsPage';
import { StudyPage } from './pages/StudyPage';

/** 로그인한 사용자만 통과. 닉네임이 없는 신규 가입자는 닉네임 설정으로 보낸다. */
function RequireAuth({ needsNickname = true }: { needsNickname?: boolean }) {
  const { status, user } = useAuth();

  if (status === 'loading') return <Splash />;
  if (status === 'anonymous') return <Navigate to="/login" replace />;
  if (needsNickname && !user?.nickname) return <Navigate to="/welcome" replace />;
  return <Outlet />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />

            <Route element={<RequireAuth needsNickname={false} />}>
              <Route path="/welcome" element={<NicknameSetupPage />} />
            </Route>

            <Route element={<RequireAuth />}>
              <Route path="/" element={<HubPage />} />
              <Route path="/study" element={<StudyPage key="words" kind="words" />} />
              <Route path="/study/grammar" element={<StudyPage key="grammar" kind="grammar" />} />
              <Route path="/study/reading" element={<QuizPage key="reading" kind="reading" />} />
              <Route path="/study/listening" element={<QuizPage key="listening" kind="listening" />} />
              <Route path="/stats" element={<StatsPage />} />
              <Route path="/board" element={<BoardPage />} />
              <Route path="/board/write" element={<PostWritePage key="write" />} />
              <Route path="/board/posts/:postId" element={<PostPage />} />
              <Route path="/board/posts/:postId/edit" element={<PostWritePage key="edit" />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
