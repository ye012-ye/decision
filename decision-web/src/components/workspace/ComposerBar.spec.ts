import { fireEvent, render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';

import ComposerBar from './ComposerBar.vue';

function mount(props: { busy: boolean }) {
  return render(ComposerBar, { props, global: { plugins: [createPinia()] } });
}

describe('ComposerBar', () => {
  it('renders send button and input when idle', () => {
    mount({ busy: false });
    expect(screen.getByTestId('composer-input')).toBeInTheDocument();
    expect(screen.getByTestId('composer-submit')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-stop')).not.toBeInTheDocument();
  });

  it('renders stop button and helper text when busy', () => {
    mount({ busy: true });
    expect(screen.getByText('正在整理回复…')).toBeInTheDocument();
    expect(screen.getByTestId('composer-stop')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-submit')).not.toBeInTheDocument();
  });

  it('emits trimmed message on click submit and clears field', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '  客户想改签  ');
    await fireEvent.click(screen.getByTestId('composer-submit'));
    expect(view.emitted('submit')).toEqual([['客户想改签']]);
    expect((screen.getByTestId('composer-input').querySelector('textarea')! as HTMLTextAreaElement).value).toBe('');
  });

  it('emits submit on Enter without shift', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '请帮我处理退款');
    await fireEvent.keyDown(input, { key: 'Enter' });
    expect(view.emitted('submit')).toEqual([['请帮我处理退款']]);
  });

  it('does not emit submit on Shift+Enter', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, 'line1');
    await fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });
    expect(view.emitted('submit')).toBeUndefined();
  });

  it('does not emit submit for whitespace only', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '   ');
    await fireEvent.click(screen.getByTestId('composer-submit'));
    expect(view.emitted('submit')).toBeUndefined();
  });

  it('emits stop when stop button clicked while busy', async () => {
    const view = mount({ busy: true });
    await fireEvent.click(screen.getByTestId('composer-stop'));
    expect(view.emitted('stop')).toEqual([[]]);
  });
});
