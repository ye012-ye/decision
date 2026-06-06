// ⚠️ Colors here MUST match src/styles/tokens.css. Keep in sync on any edit.
import type { GlobalThemeOverrides } from 'naive-ui';

const radii = { small: '8px', medium: '12px', large: '16px' };
const fontFamily =
  '"Noto Sans SC","PingFang SC","Microsoft YaHei",system-ui,-apple-system,sans-serif';

const light = {
  primary: '#6d5efc',
  primaryHover: '#5a49e6',
  primaryPressed: '#4836c9',
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#ef4444',
  text: '#1f2329',
  textMuted: '#6b7280',
  border: '#e5e7eb',
  surface: '#ffffff',
};

const dark = {
  primary: '#a78bfa',
  primaryHover: '#b9a3ff',
  primaryPressed: '#9374f2',
  success: '#40c2ad',
  warning: '#f0aa52',
  danger: '#f07863',
  text: '#e6ebf2',
  textMuted: '#8a94a6',
  border: '#232a36',
  surface: '#141a24',
};

function makeOverrides(c: typeof light): GlobalThemeOverrides {
  return {
    common: {
      primaryColor: c.primary,
      primaryColorHover: c.primaryHover,
      primaryColorPressed: c.primaryPressed,
      primaryColorSuppl: c.primaryHover,
      successColor: c.success,
      warningColor: c.warning,
      errorColor: c.danger,
      textColorBase: c.text,
      borderRadius: radii.medium,
      borderRadiusSmall: radii.small,
      fontFamily,
    },
    Button: {
      borderRadiusTiny: radii.small,
      borderRadiusSmall: radii.small,
      borderRadiusMedium: radii.medium,
      borderRadiusLarge: radii.medium,
    },
    Card: {
      borderRadius: radii.large,
      paddingMedium: '20px 24px',
    },
    Input: {
      borderRadius: radii.medium,
    },
    Menu: {
      itemHeight: '40px',
      borderRadius: radii.medium,
    },
    Tag: {
      borderRadius: radii.small,
    },
  };
}

export const lightOverrides = makeOverrides(light);
export const darkOverrides = makeOverrides(dark);
