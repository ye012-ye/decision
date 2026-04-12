import { render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';

import AppShell from './AppShell.vue';
import WorkspaceView from '@/views/WorkspaceView.vue';

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/workspace', component: WorkspaceView, name: 'workspace' }],
});

describe('AppShell', () => {
  it('renders top bar and sidebar nav', async () => {
    router.push('/workspace');
    await router.isReady();
    render(AppShell, { global: { plugins: [createPinia(), router] } });
    expect(screen.getByTestId('top-bar')).toBeInTheDocument();
    expect(screen.getByTestId('sidebar-nav')).toBeInTheDocument();
  });
});
