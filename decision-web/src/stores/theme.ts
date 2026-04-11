import { defineStore } from 'pinia';

export type ThemeMode = 'light' | 'dark' | 'auto';

const STORAGE_KEY = 'theme';

function readPersisted(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark' || stored === 'auto') {
    return stored;
  }
  return 'auto';
}

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: readPersisted() as ThemeMode,
    _systemDark: systemPrefersDark(),
  }),
  getters: {
    resolved(state): 'light' | 'dark' {
      if (state.mode === 'auto') {
        return state._systemDark ? 'dark' : 'light';
      }
      return state.mode;
    },
  },
  actions: {
    setMode(mode: ThemeMode) {
      this.mode = mode;
      localStorage.setItem(STORAGE_KEY, mode);
      document.documentElement.dataset.theme = this.resolved;
    },
    init() {
      document.documentElement.dataset.theme = this.resolved;
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      media.addEventListener('change', (e: { matches: boolean }) => {
        this._systemDark = e.matches;
        if (this.mode === 'auto') {
          document.documentElement.dataset.theme = this.resolved;
        }
      });
    },
  },
});
