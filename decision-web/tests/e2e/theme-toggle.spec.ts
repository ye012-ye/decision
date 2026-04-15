import { expect, test } from '@playwright/test';

test.describe('theme toggle', () => {
  test('cycles light → dark → auto and persists choice', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');
    await expect(toggle).toBeVisible();

    // Default is 'auto' — system preference decides resolved theme
    const html = page.locator('html');

    // Click 1: auto → light
    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'light');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('light');

    // Click 2: light → dark
    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'dark');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');

    // Click 3: dark → auto
    await toggle.click();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');

    // Reload — persisted mode should stick
    await page.reload();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');
  });

  test('persists dark mode across navigation', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');

    // Set to dark: auto → light → dark (2 clicks)
    await toggle.click();
    await toggle.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    // Navigate to another route
    await page.getByRole('link', { name: '知识库' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    // Navigate back
    await page.getByRole('link', { name: '工作台' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });
});
