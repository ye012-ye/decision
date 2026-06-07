import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import { getToken } from '@/api/token';
import ChatLayout from '@/layouts/ChatLayout.vue';
import LoginView from '@/views/LoginView.vue';
import NotFoundView from '@/views/NotFoundView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView, meta: { title: '登录', public: true } },
  { path: '/', component: ChatLayout, meta: { title: '智能助手' } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '未找到页面' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const isPublic = to.meta.public === true;
  const authenticated = Boolean(getToken());

  if (!isPublic && !authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && authenticated) {
    return { path: '/' };
  }
  return true;
});

export default router;
