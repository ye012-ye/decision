<template>
  <AppProviders>
    <RouterView />
  </AppProviders>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';
import AppProviders from '@/providers/AppProviders.vue';
</script>
