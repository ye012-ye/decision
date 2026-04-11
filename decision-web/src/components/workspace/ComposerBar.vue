<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{ submit: [message: string] }>();

const value = ref('');
const helperId = 'composer-helper-text';

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
        :aria-describedby="helperId"
        data-testid="composer-input"
      />
    </label>
    <div class="composer__footer">
      <p :id="helperId" class="composer__helper" role="status" aria-live="polite">
        {{ busy ? '正在整理回复，请稍候…' : '发送后将以流式方式持续返回结果' }}
      </p>
      <button class="composer__button" :disabled="busy" type="submit" data-testid="composer-submit">
        {{ busy ? '生成中…' : '发送' }}
      </button>
    </div>
  </form>
</template>
