import { fireEvent, render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';

import ComposerBar from './ComposerBar.vue';

describe('ComposerBar', () => {
  it('shows idle helper text, send button, and helper association for textarea', () => {
    render(ComposerBar, {
      props: {
        busy: false,
      },
    });

    const input = screen.getByPlaceholderText('输入客户诉求或问题...');
    expect(input).toHaveAttribute('aria-describedby', 'composer-helper-text');
    expect(screen.getByText('发送后将以流式方式持续返回结果')).toHaveAttribute(
      'id',
      'composer-helper-text'
    );
    expect(screen.getByText('发送后将以流式方式持续返回结果')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '发送' })).toBeInTheDocument();
    expect(screen.getByTestId('composer-input')).toBeInTheDocument();
    expect(screen.getByTestId('composer-submit')).toBeInTheDocument();
  });

  it('shows busy helper and busy button label', () => {
    render(ComposerBar, {
      props: {
        busy: true,
      },
    });

    const button = screen.getByRole('button', { name: '生成中…' });
    expect(button).toBeDisabled();
    expect(screen.getByText('正在整理回复，请稍候…')).toBeInTheDocument();
  });

  it('emits trimmed message on submit and clears the field', async () => {
    const view = render(ComposerBar, {
      props: {
        busy: false,
      },
    });

    const input = screen.getByPlaceholderText('输入客户诉求或问题...');
    await fireEvent.update(input, '  客户想改签收货时间  ');

    await fireEvent.submit(screen.getByRole('button', { name: '发送' }));

    expect(view.emitted('submit')).toEqual([['客户想改签收货时间']]);
    expect(screen.getByPlaceholderText('输入客户诉求或问题...')).toHaveValue('');
  });

  it('does not emit submit for whitespace-only input', async () => {
    const view = render(ComposerBar, {
      props: {
        busy: false,
      },
    });

    const input = screen.getByPlaceholderText('输入客户诉求或问题...');
    await fireEvent.update(input, '   ');
    await fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(view.emitted('submit')).toBeUndefined();
  });

  it('does not emit submit while busy', async () => {
    const view = render(ComposerBar, {
      props: {
        busy: true,
      },
    });

    const input = screen.getByPlaceholderText('输入客户诉求或问题...');
    await fireEvent.update(input, '请帮我处理退款');
    await fireEvent.click(screen.getByRole('button', { name: '生成中…' }));

    expect(view.emitted('submit')).toBeUndefined();
  });
});
