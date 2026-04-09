import { expect, test } from '@playwright/test';

test('renders the decision shell', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('link', { name: '工作台' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
});
