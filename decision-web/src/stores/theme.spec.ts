import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useThemeStore } from './theme';

function mockMatchMedia(darkPrefers: boolean) {
  const listeners: Array<(e: { matches: boolean }) => void> = [];
  vi.stubGlobal('matchMedia', vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('dark') ? darkPrefers : false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn((_evt: string, cb: (e: { matches: boolean }) => void) => listeners.push(cb)),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  return {
    trigger(matches: boolean) {
      listeners.forEach((cb) => cb({ matches }));
    },
  };
}

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('defaults to auto and resolves using system preference', () => {
    mockMatchMedia(true);
    const store = useThemeStore();
    expect(store.mode).toBe('auto');
    expect(store.resolved).toBe('dark');
  });

  it('init() writes data-theme to <html>', () => {
    mockMatchMedia(false);
    const store = useThemeStore();
    store.init();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('setMode persists and updates html data-theme', () => {
    mockMatchMedia(true);
    const store = useThemeStore();
    store.setMode('light');
    expect(localStorage.getItem('theme')).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(store.resolved).toBe('light');
  });

  it('setMode("auto") follows system preference change', () => {
    const mm = mockMatchMedia(false);
    const store = useThemeStore();
    store.init();
    store.setMode('auto');
    expect(document.documentElement.dataset.theme).toBe('light');
    mm.trigger(true);
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('reads persisted mode from localStorage on creation', () => {
    localStorage.setItem('theme', 'dark');
    mockMatchMedia(false);
    const store = useThemeStore();
    expect(store.mode).toBe('dark');
    expect(store.resolved).toBe('dark');
  });
});
