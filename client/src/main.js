import { createApp } from 'vue';
import axios from 'axios';
import { setupAxiosInterceptors } from './utils/offlineDataSync.js';
import { initGlobalErrorMonitoring, tryReportFailedLogs, logUserAction, initConsoleLogging } from './utils/operationLogger.js';

import './styles/common.css'; // 导入公共样式文件
import './styles/fonts.css'; // 导入自定义字体
import 'element-plus/dist/index.css'; // 导入Element Plus基础样式

import router from './router';
import i18n from './locales/i18n.js';
import 'dayjs/locale/en'; // 正确命名导出i18n实例供其他模块使用
// 使用locales目录下已配置的i18n实例（包含完整语言包）
import { createPinia } from 'pinia';

import * as ElementPlusIconsVue from '@element-plus/icons-vue';

import App from './App.vue';
// 设置Axios离线拦截器
setupAxiosInterceptors(axios);

// 初始化全局错误监听
initGlobalErrorMonitoring();

// 尝试上报失败的日志
tryReportFailedLogs();

// 初始化控制台日志捕获
// 配置选项：
// - levels: 要捕获的日志级别
// - maxLength: 单个日志消息的最大长度，防止过大的日志数据
initConsoleLogging({
  levels: ['log', 'error', 'warn', 'info'],
  maxLength: 5000
});

export { i18n };
const pinia = createPinia();

const app = createApp({
  components: { App },
  template: `
    <Suspense>
      <App />
      <template #fallback>Loading...</template>
    </Suspense>
  `
});
app.use(pinia); // 安装Pinia实例
// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}
app.use(router);
app.use(i18n);
app.mount('#app');
console.log('[App Initialization] Application mounted successfully');

// 记录应用启动日志
// 移除应用启动日志记录，避免增加日志量

// 深色模式适配
const applyDarkMode = (isDark) => {
  if (isDark) {
    document.documentElement.classList.add('dark-mode');
  } else {
    document.documentElement.classList.remove('dark-mode');
  }
};

// 检测系统主题偏好
const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
applyDarkMode(mediaQuery.matches);

// 监听主题变化
mediaQuery.addEventListener('change', (e) => {
  applyDarkMode(e.matches);
});
