import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import KnowledgeView from '@/views/KnowledgeView.vue';
import NotFoundView from '@/views/NotFoundView.vue';
import TicketsView from '@/views/TicketsView.vue';
import WorkspaceView from '@/views/WorkspaceView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/workspace' },
  { path: '/workspace', component: WorkspaceView, meta: { title: '工作台' } },
  { path: '/knowledge', component: KnowledgeView, meta: { title: '知识库' } },
  { path: '/tickets', component: TicketsView, meta: { title: '工单' } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '未找到页面' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
