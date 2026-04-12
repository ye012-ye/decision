<script setup lang="ts">
import { computed, h } from 'vue';
import { NAvatar, NBadge, NButton, NDropdown, NIcon, NInput, NTooltip } from 'naive-ui';
import type { DropdownOption } from 'naive-ui';

import {
  AutoIcon,
  BellIcon,
  MenuIcon,
  MoonIcon,
  SearchIcon,
  SunIcon,
  UserIcon,
} from '@/theme/icons';
import { useThemeStore } from '@/stores/theme';

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void;
  (e: 'open-search'): void;
}>();

const theme = useThemeStore();

const themeIcon = computed(() => {
  if (theme.mode === 'light') return SunIcon;
  if (theme.mode === 'dark') return MoonIcon;
  return AutoIcon;
});

const themeLabel = computed(() => {
  if (theme.mode === 'light') return '亮色';
  if (theme.mode === 'dark') return '暗色';
  return '跟随系统';
});

function cycleTheme() {
  const next = theme.mode === 'light' ? 'dark' : theme.mode === 'dark' ? 'auto' : 'light';
  theme.setMode(next);
}

const userOptions: DropdownOption[] = [
  { key: 'profile', label: '个人资料' },
  { key: 'settings', label: '设置' },
  { type: 'divider', key: 'd1' },
  { key: 'logout', label: '退出登录' },
];

function onUserSelect(key: string) {
  window.$message?.info(`${key}（占位）`);
}

function renderIcon(icon: unknown) {
  return () => h(NIcon, null, { default: () => h(icon as never) });
}
</script>

<template>
  <header class="top-bar" data-testid="top-bar">
    <div class="top-bar__left">
      <NButton
        class="top-bar__menu"
        quaternary
        circle
        :render-icon="renderIcon(MenuIcon)"
        aria-label="打开导航"
        @click="emit('toggle-sidebar')"
      />
      <span class="top-bar__brand">决策中心</span>
    </div>

    <div class="top-bar__center">
      <NInput
        class="top-bar__search"
        placeholder="搜索会话、工单、知识…  (Ctrl/Cmd + K)"
        readonly
        @click="emit('open-search')"
      >
        <template #prefix>
          <NIcon :component="SearchIcon" />
        </template>
      </NInput>
    </div>

    <div class="top-bar__right">
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton
            quaternary
            circle
            :render-icon="renderIcon(themeIcon)"
            :aria-label="`切换主题（当前：${themeLabel}）`"
            data-testid="theme-toggle"
            @click="cycleTheme"
          />
        </template>
        当前：{{ themeLabel }}
      </NTooltip>

      <NBadge :value="0" :show="false">
        <NButton quaternary circle :render-icon="renderIcon(BellIcon)" aria-label="通知" />
      </NBadge>

      <NDropdown :options="userOptions" trigger="click" @select="onUserSelect">
        <NAvatar round :size="32" color="var(--color-primary)">
          <NIcon :component="UserIcon" />
        </NAvatar>
      </NDropdown>
    </div>
  </header>
</template>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  height: 56px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.top-bar__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.top-bar__menu {
  display: none;
}

.top-bar__brand {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text);
}

.top-bar__center {
  display: flex;
  justify-content: center;
}

.top-bar__search {
  max-width: 520px;
}

.top-bar__right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 980px) {
  .top-bar__menu {
    display: inline-flex;
  }
  .top-bar__search {
    display: none;
  }
}
</style>
