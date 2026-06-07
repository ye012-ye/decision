import { expect, test, type Page } from '@playwright/test';

async function mockChat(page: Page) {
  await page.route('**/api/chat/stream', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream; charset=utf-8',
      body: [
        'event:thought\ndata:已接收客户诉求\n\n',
        'event:action\ndata:queryData | {"table":"orders"}\n\n',
        'event:observation\ndata:命中 1 条记录\n\n',
        'event:answer\ndata:已为你\n\n',
        'event:answer\ndata:处理完成\n\n',
        'event:done\ndata:流程结束\n\n',
      ].join(''),
    });
  });
}

test.describe('single-agent chat', () => {
  test('streams an answer and shows the process trace', async ({ page }) => {
    await mockChat(page);
    await page.goto('/');

    await expect(page.getByTestId('session-rail')).toBeVisible();
    await expect(page.getByTestId('composer-input')).toBeVisible();

    await page.getByTestId('composer-input').locator('textarea').fill('帮我查一下订单');
    await page.getByTestId('composer-submit').click();

    await expect(
      page.getByTestId('chat-message-user').last().getByTestId('chat-message-content')
    ).toHaveText('帮我查一下订单');
    await expect(
      page.getByTestId('chat-message-assistant').last().getByTestId('chat-message-content')
    ).toHaveText('已为你处理完成');

    await expect(page.getByTestId('chat-process-trace')).toBeVisible();
    await page.getByTestId('chat-process-trace').getByText('思考过程').click();
    await expect(page.getByText('已接收客户诉求')).toBeVisible();
  });

  test.describe('mobile', () => {
    test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });

    test('opens the session rail from the drawer', async ({ page }) => {
      await mockChat(page);
      await page.goto('/');

      await expect(page.getByTestId('composer-input')).toBeVisible();
      await page.getByRole('button', { name: '打开会话' }).click();
      await expect(page.getByTestId('session-rail')).toBeVisible();
      await expect(page.getByRole('button', { name: '新对话' })).toBeVisible();
    });
  });
});
