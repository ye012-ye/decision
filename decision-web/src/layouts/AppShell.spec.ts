import { render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import { describe, expect, it } from 'vitest';

import AppShell from './AppShell.vue';
import { routes } from '../router';

describe('AppShell', () => {
  it('renders the three primary navigation links', async () => {
    setActivePinia(createPinia());
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });

    router.push('/workspace');
    await router.isReady();

    render(AppShell, {
      global: {
        plugins: [router],
      },
    });

    expect(screen.getByRole('link', { name: '工作台' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '知识库' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '工单' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '工作台' })).toBeInTheDocument();
    expect(screen.getByText('智能客服')).toBeInTheDocument();
  });
});
