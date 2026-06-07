<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NCard, NForm, NFormItem, NInput } from 'naive-ui';
import type { FormInst, FormRules } from 'naive-ui';

import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const model = ref({ username: '', password: '' });

const rules: FormRules = {
  username: { required: true, message: '请输入用户名', trigger: ['blur', 'input'] },
  password: { required: true, message: '请输入密码', trigger: ['blur', 'input'] },
};

async function onSubmit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  loading.value = true;
  try {
    await auth.login(model.value.username, model.value.password);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    await router.replace(redirect);
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败';
    window.$message?.error(message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login">
    <NCard class="login__card" :bordered="false">
      <div class="login__brand">决策中心</div>
      <p class="login__subtitle">登录以继续</p>

      <NForm ref="formRef" :model="model" :rules="rules" @keyup.enter="onSubmit">
        <NFormItem path="username" label="用户名">
          <NInput v-model:value="model.username" placeholder="请输入用户名" />
        </NFormItem>
        <NFormItem path="password" label="密码">
          <NInput
            v-model:value="model.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
          />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="onSubmit">登录</NButton>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-4);
  background: var(--color-surface);
}

.login__card {
  width: 100%;
  max-width: 360px;
  padding: var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.12);
}

.login__brand {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text);
}

.login__subtitle {
  margin: var(--space-1) 0 var(--space-4);
  color: var(--color-text);
  opacity: 0.7;
  font-size: 14px;
}
</style>
