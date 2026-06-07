import { expect, test } from '@playwright/test';

test('renders the chat workspace', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('session-rail')).toBeVisible();
  await expect(page.getByTestId('composer-input')).toBeVisible();
  await expect(page.getByRole('button', { name: '新对话' })).toBeVisible();
});
