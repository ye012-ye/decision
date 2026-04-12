import { expect, test, type Page, type Route } from '@playwright/test';

const activeTicket = {
  orderNo: 'WO20260409001',
  type: 'LOGISTICS',
  priority: 'HIGH',
  status: 'PROCESSING',
  title: '物流延迟',
  description: '三天未更新',
  customerId: '13800001111',
  assignee: '物流专员',
  assigneeGroup: '物流组',
  resolution: null,
  sessionId: 'session-1',
  createdAt: '2026-04-09T10:00:00',
  updatedAt: '2026-04-09T10:05:00',
  resolvedAt: null,
};

const createdTicket = {
  ...activeTicket,
  orderNo: 'WO20260409088',
  title: '控制台联动工单',
  description: '用于验证工作台手动建单流程',
  status: 'PENDING',
  assignee: null,
  assigneeGroup: null,
  resolution: null,
};

const knowledgeBase = {
  kbCode: 'product-docs',
  kbName: '产品文档库',
  description: '产品说明',
  owner: 'tech-team',
  status: 1,
};

const knowledgeDocument = {
  docId: 'doc-1',
  fileName: 'guide.pdf',
  status: 'PROCESSING',
};

const completedDocument = {
  ...knowledgeDocument,
  status: 'COMPLETED',
};

async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({
      code: 200,
      msg: 'ok',
      data,
    }),
  });
}

async function mockConsoleApi(page: Page) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();

    if (!url.pathname.startsWith('/api/')) {
      await route.continue();
      return;
    }

    if (method === 'POST' && url.pathname === '/api/chat/stream') {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream; charset=utf-8',
        body: [
          'event:thought\ndata:已接收客户诉求\n\n',
          'event:action\ndata:已创建工单 WO20260409001\n\n',
          'event:answer\ndata:当前工单 WO20260409001 已\n\n',
          'event:answer\ndata:进入处理流\n\n',
          'event:done\ndata:流程结束\n\n',
        ].join(''),
      });
      return;
    }

    if (method === 'GET' && url.pathname === '/api/kb') {
      await fulfillJson(route, [knowledgeBase]);
      return;
    }

    if (method === 'GET' && url.pathname === '/api/kb/product-docs/documents') {
      await fulfillJson(route, { records: [knowledgeDocument] });
      return;
    }

    if (method === 'GET' && url.pathname === '/api/kb/product-docs/documents/doc-1/status') {
      await fulfillJson(route, completedDocument);
      return;
    }

    if (method === 'GET' && url.pathname === '/api/work-orders') {
      await fulfillJson(route, [activeTicket]);
      return;
    }

    if (method === 'GET' && url.pathname === '/api/work-orders/WO20260409001') {
      await fulfillJson(route, activeTicket);
      return;
    }

    if (method === 'GET' && url.pathname === '/api/work-orders/WO20260409001/logs') {
      await fulfillJson(route, [
        {
          action: '创建工单',
          operator: 'console',
          content: '工单已进入处理队列',
          createdAt: '2026-04-09T10:01:00',
        },
      ]);
      return;
    }

    if (method === 'PATCH' && url.pathname === '/api/work-orders/WO20260409001/status') {
      await fulfillJson(route, { ...activeTicket, status: 'PROCESSING', updatedAt: '2026-04-09T10:06:00' });
      return;
    }

    if (method === 'POST' && url.pathname === '/api/work-orders/WO20260409001/close') {
      await fulfillJson(route, { ...activeTicket, status: 'CLOSED', resolvedAt: '2026-04-09T10:10:00' });
      return;
    }

    if (method === 'POST' && url.pathname === '/api/work-orders') {
      await fulfillJson(route, createdTicket);
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify({
        code: 404,
        msg: `Unhandled mock route: ${method} ${url.pathname}`,
        data: null,
      }),
    });
  });
}

test.describe('decision console', () => {
  test('keeps the main console flow visible on desktop', async ({ page }) => {
    await mockConsoleApi(page);

    await page.goto('/');
    await expect(page).toHaveURL(/\/workspace$/);

    await expect(page.getByRole('link', { name: '工作台' })).toBeVisible();
    await expect(page.getByRole('link', { name: '知识库' })).toBeVisible();
    await expect(page.getByRole('link', { name: '工单' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '最近会话' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '工单面板' })).toBeVisible();
    await expect(page.getByLabel('输入客户诉求')).toBeVisible();

    await page.getByLabel('输入客户诉求').fill('客户反馈物流延迟，请帮我跟进。');
    await page.getByRole('button', { name: '发送' }).click();

    await expect(
      page.getByTestId('chat-message-user').last().getByTestId('chat-message-content')
    ).toHaveText('客户反馈物流延迟，请帮我跟进。');
    await expect(page.getByText('当前工单：WO20260409001')).toBeVisible();
    await expect(
      page.getByTestId('chat-message-assistant').last().getByTestId('chat-message-content')
    ).toHaveText('当前工单 WO20260409001 已进入处理流');
    await expect(page.getByRole('button', { name: '展开过程' })).toBeVisible();
    await page.getByRole('button', { name: '展开过程' }).click();
    await expect(page.getByRole('button', { name: '收起过程' })).toBeVisible();
    await expect(page.getByTestId('chat-process-row')).toHaveCount(2);
    await expect(page.getByText('thought')).toBeVisible();
    await expect(page.getByText('已接收客户诉求')).toBeVisible();
    await expect(page.getByText('action')).toBeVisible();
    await expect(page.getByText('已创建工单 WO20260409001')).toBeVisible();
    await expect(page.getByText('当前工单：WO20260409001')).toBeVisible();

    await page.getByLabel('客户 ID').fill('13800001111');
    await page.getByLabel('工单标题').fill('控制台联动工单');
    await page.getByLabel('工单描述').fill('用于验证工作台手动建单流程');
    await page.getByRole('button', { name: '手动创建工单' }).click();

    await expect(page.getByText('当前工单：WO20260409088')).toBeVisible();

    await page.getByRole('link', { name: '工单' }).click();
    await expect(page.getByRole('heading', { name: '工单管理' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '工单清单' })).toBeVisible();
    await expect(page.getByRole('button', { name: /WO20260409001/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'WO20260409001' })).toBeVisible();

    await page.getByRole('link', { name: '知识库' }).click();
    await expect(page.getByRole('heading', { name: '知识库管理' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '库列表' })).toBeVisible();
    await expect(page.getByText('文档面板')).toBeVisible();
    await expect(page.getByText('guide.pdf')).toBeVisible();
  });

  test.describe('mobile', () => {
    test.use({
      viewport: { width: 390, height: 844 },
      hasTouch: true,
      isMobile: true,
    });

    test('keeps the console usable on a narrow screen', async ({ page }) => {
      await mockConsoleApi(page);

      await page.goto('/');
      await expect(page).toHaveURL(/\/workspace$/);

      await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();

      // On mobile the sidebar lives inside a drawer; open it via the hamburger button
      await page.getByRole('button', { name: '打开导航' }).click();
      await expect(page.getByTestId('sidebar-nav')).toBeVisible();
      await page.getByRole('link', { name: '工单' }).click();
      await expect(page.getByRole('heading', { name: '工单管理' })).toBeVisible();

      await page.getByRole('button', { name: '打开导航' }).click();
      await page.getByRole('link', { name: '知识库' }).click();
      await expect(page.getByRole('heading', { name: '知识库管理' })).toBeVisible();
    });
  });
});
