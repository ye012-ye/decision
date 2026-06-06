<script setup lang="ts">
import { ref } from 'vue';
import { NButton, NForm, NFormItem, NInput, NModal, type FormInst, type FormRules } from 'naive-ui';

import { useKnowledgeStore } from '@/stores/knowledge';
import type { KnowledgeBaseCreateInput } from '@/types/knowledge';

const store = useKnowledgeStore();

const show = ref(false);
const submitting = ref(false);
const formRef = ref<FormInst | null>(null);

function emptyForm(): KnowledgeBaseCreateInput {
  return { kbCode: '', kbName: '', description: '', owner: 'console' };
}

const model = ref<KnowledgeBaseCreateInput>(emptyForm());

const rules: FormRules = {
  kbCode: [
    { required: true, message: '请输入知识库编码', trigger: ['input', 'blur'] },
    {
      pattern: /^[a-zA-Z0-9_-]+$/,
      message: '只允许字母、数字、下划线、连字符',
      trigger: ['input', 'blur'],
    },
  ],
  kbName: [{ required: true, message: '请输入知识库名称', trigger: ['input', 'blur'] }],
  owner: [{ required: true, message: '请输入所有者', trigger: ['input', 'blur'] }],
};

function open() {
  model.value = emptyForm();
  show.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return; // 校验未通过，错误已内联显示
  }

  submitting.value = true;
  try {
    const created = await store.createBase({ ...model.value });
    if (created) {
      show.value = false;
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <NButton type="primary" secondary round size="small" @click="open">＋ 新建知识库</NButton>

  <NModal
    v-model:show="show"
    preset="card"
    title="新建知识库"
    :bordered="false"
    class="knowledge-create-modal"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="top">
      <NFormItem label="知识库编码" path="kbCode">
        <NInput v-model:value="model.kbCode" placeholder="字母 / 数字 / 下划线 / 连字符，全局唯一" />
      </NFormItem>
      <NFormItem label="名称" path="kbName">
        <NInput v-model:value="model.kbName" placeholder="知识库显示名称" />
      </NFormItem>
      <NFormItem label="所有者" path="owner">
        <NInput v-model:value="model.owner" placeholder="owner" />
      </NFormItem>
      <NFormItem label="描述" path="description">
        <NInput
          v-model:value="model.description"
          type="textarea"
          placeholder="可选，最多 512 字"
          :autosize="{ minRows: 2, maxRows: 4 }"
        />
      </NFormItem>
    </NForm>

    <template #footer>
      <div class="knowledge-create-modal__footer">
        <NButton :disabled="submitting" @click="show = false">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="submit">创建</NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.knowledge-create-modal {
  width: min(480px, 92vw);
}

.knowledge-create-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
