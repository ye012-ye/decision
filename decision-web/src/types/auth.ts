export interface AuthUser {
  username: string;
  nickname: string;
  role?: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  nickname: string;
}
