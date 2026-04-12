<script setup lang="ts">
import { computed } from 'vue';
import {
  NConfigProvider,
  NDialogProvider,
  NLoadingBarProvider,
  NMessageProvider,
  NNotificationProvider,
  darkTheme,
  lightTheme,
} from 'naive-ui';

import { useThemeStore } from '@/stores/theme';
import { darkOverrides, lightOverrides } from '@/theme';
import MessageApiSetup from './MessageApiSetup.vue';

const theme = useThemeStore();
const naiveTheme = computed(() => (theme.resolved === 'dark' ? darkTheme : lightTheme));
const overrides = computed(() => (theme.resolved === 'dark' ? darkOverrides : lightOverrides));
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="overrides">
    <NLoadingBarProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <NMessageProvider>
            <MessageApiSetup />
            <slot />
          </NMessageProvider>
        </NNotificationProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
