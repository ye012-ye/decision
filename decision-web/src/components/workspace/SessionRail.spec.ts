import { fireEvent, render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import SessionRail from './SessionRail.vue';

const sessions = [
  { id: 's1', title: '会话一', messages: [] },
  { id: 's2', title: '会话二', messages: [] },
];

describe('SessionRail', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('renders sessions and emits select', async () => {
    const view = render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    expect(screen.getByText('会话一')).toBeInTheDocument();
    await fireEvent.click(screen.getByText('会话二'));
    expect(view.emitted('select')).toEqual([['s2']]);
  });

  it('emits create when 新对话 clicked', async () => {
    const view = render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    await fireEvent.click(screen.getByText('新对话'));
    expect(view.emitted('create')).toEqual([[]]);
  });

  it('renders a theme toggle', () => {
    render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    expect(screen.getByTestId('theme-toggle')).toBeInTheDocument();
  });
});
