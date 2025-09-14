import { createRouter, createWebHistory } from 'vue-router';
import i18n from '@/locales/i18n';
import { ElMessage } from 'element-plus';
import HomeView from '@/views/HomeView.vue';
import NotFoundView from '@/views/NotFoundView.vue';
import TodoView from '@/views/TodoView.vue';

/**
 * 应用路由配置
 * @module router
 * @desc 使用Vue Router管理前端路由，包含历史模式配置和路由守卫
 */

import DonationView from '@/views/DonationView.vue';
import DebtView from '@/views/DebtView.vue';
import ChartsView from '@/views/ChartsView.vue';

const router = createRouter({
  history: createWebHistory(), // 启用History模式（去除URL中的#号）
  routes: [
    {
      path: '/',
      name: 'home',
      meta: { title: 'app.title' }, // 路由元信息：对应i18n的键
      component: HomeView
    },
    {
      path: '/todo',
      name: 'todo',
      meta: { title: 'todo.title' },
      component: TodoView
    },
    {
      path: '/donation',
      name: 'donation',
      meta: { title: 'donation.title' },
      component: DonationView
    },
    {
      path: '/debts',
      name: 'debts',
      meta: { title: 'debt.title' },
      component: DebtView
    },
    {
      path: '/charts',
      name: 'charts',
      meta: { title: 'chart.title' },
      component: ChartsView
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView
    }
  ],
  scrollBehavior (to, from, savedPosition) {
    return savedPosition || { top: 0 };
  }
});

// 全局前置守卫：整合所有路由控制逻辑
router.beforeEach(async (to, from, next) => {
  try {
    const userAgent = navigator.userAgent || 'unknown';
    // 更新页面标题
    if (to.meta.title) {
      document.title = i18n.global.t(to.meta.title);
    }

    // 记录路由访问日志
    console.log(`[Route Access] ${to.name} route accessed - User-Agent: ${userAgent}`);

    next();
  } catch (error) {
    console.error('路由守卫错误:', error);
    next();
  }
});

export default router;
