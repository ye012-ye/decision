import { defineStore } from 'pinia';

import { fetchMe, login as loginApi } from '@/api/auth';
import { clearToken, getToken, setToken } from '@/api/token';
import type { AuthUser } from '@/types/auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() as string | null,
    user: null as AuthUser | null,
  }),
  getters: {
    isAuthenticated: (state): boolean => Boolean(state.token),
  },
  actions: {
    async login(username: string, password: string) {
      const result = await loginApi(username, password);
      setToken(result.token);
      this.token = result.token;
      this.user = { username: result.username, nickname: result.nickname };
    },
    async loadCurrentUser() {
      this.user = await fetchMe();
    },
    logout() {
      clearToken();
      this.token = null;
      this.user = null;
    },
  },
});
