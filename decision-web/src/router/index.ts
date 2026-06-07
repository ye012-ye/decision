import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import ChatLayout from '@/layouts/ChatLayout.vue';
import NotFoundView from '@/views/NotFoundView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/', component: ChatLayout, meta: { title: '智能助手' } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '未找到页面' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
