// 백엔드 DTO (LoginResponse / RefreshResponse / UserResponse) 와 1:1 대응

export interface User {
  id: string;
  nickname: string | null;
  email: string | null;
  provider: string;
  currentLevel: number;
  timezone: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  isNewUser: boolean;
  user: User;
}

export interface RefreshResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface UpdateProfileRequest {
  nickname?: string;
  timezone?: string;
}
