import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';
import './styles/reset.css';
import './styles/tokens.css';
import './styles/glass.css';
import './styles/layout.css';
import { useAuthStore } from './stores/auth';
import { useThemeStore } from './stores/theme';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

useThemeStore().init();

const auth = useAuthStore();
if (auth.isAuthenticated && !auth.user) {
  auth.loadCurrentUser().catch(() => auth.logout());
}

app.mount('#app');
