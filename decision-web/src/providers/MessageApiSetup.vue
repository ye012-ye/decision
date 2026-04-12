<script setup lang="ts">
import { onMounted } from 'vue';
import { useDialog, useLoadingBar, useMessage, useNotification } from 'naive-ui';

const message = useMessage();
const dialog = useDialog();
const notification = useNotification();
const loadingBar = useLoadingBar();

onMounted(() => {
  window.$message = message;
  window.$dialog = dialog;
  window.$notification = notification;
  window.$loadingBar = loadingBar;
});
</script>

<template>
  <!-- pure side-effect component -->
</template>
