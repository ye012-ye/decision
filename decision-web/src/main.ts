import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';
import './styles/reset.css';
import './styles/tokens.css';
import './styles/layout.css';
import './styles/theme.css';
import { useThemeStore } from './stores/theme';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

useThemeStore().init();

app.mount('#app');
