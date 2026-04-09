<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{ submit: [message: string] }>();

const value = ref('');

function submit() {
  const message = value.value.trim();
  if (!message || props.busy) {
    return;
  }

  emit('submit', message);
  value.value = '';
}
</script>

<template>
  <form class="composer" @submit.prevent="submit">
    <label class="composer__field">
      <span class="composer__label">输入客户诉求</span>
      <textarea
        v-model="value"
        class="composer__input"
        rows="3"
        placeholder="输入客户诉求或问题..."
      />
    </label>
    <button class="composer__button" :disabled="busy" type="submit">
      {{ busy ? '处理中...' : '发送' }}
    </button>
  </form>
</template>
