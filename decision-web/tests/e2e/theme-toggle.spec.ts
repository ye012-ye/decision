import { expect, test } from '@playwright/test';

test.describe('theme toggle', () => {
  test('cycles auto → light → dark → auto and persists', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');
    await expect(toggle).toBeVisible();
    const html = page.locator('html');

    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'light');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('light');

    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'dark');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');

    await toggle.click();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');

    await page.reload();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');
  });

  test('persists dark mode across reload', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');
    await toggle.click();
    await toggle.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });
});
