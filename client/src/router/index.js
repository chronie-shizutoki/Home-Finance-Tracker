import { createRouter, createWebHistory } from 'vue-router';
import i18n from '@/locales/i18n';
import HomeView from '@/views/HomeView.vue';
import NotFoundView from '@/views/NotFoundView.vue';

/**
 * Application router configuration
 * @module router
 * @desc Vue Router configuration for application routing, including history mode and route guards
 */

import ChartsView from '@/views/ChartsView.vue';
import MembershipView from '@/views/MembershipView.vue';

const router = createRouter({
  history: createWebHistory(), // Enable history mode (remove # from URL)
  routes: [
    {
      path: '/',
      name: 'home',
      meta: { title: 'app.title' }, // Route meta: title key in i118n
      component: HomeView
    },
    {
      path: '/charts',
      name: 'charts',
      meta: { title: 'chart.title' },
      component: ChartsView
    },
    {
      path: '/membership',
      name: 'membership',
      meta: { title: 'membership.title' },
      component: MembershipView
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView
    }
  ],
  scrollBehavior (_to, _from, savedPosition) {
    return savedPosition || { top: 0 };
  }
});

// Global before each guard: unified guard for all route controls
router.beforeEach(async (to, _from, next) => {
  try {
    const userAgent = navigator.userAgent || 'unknown';
    // Update page title
    if (to.meta.title) {
      document.title = i18n.global.t(to.meta.title);
    }

    // Record route access log
    console.log(`[Route Access] ${to.name} route accessed - User-Agent: ${userAgent}`);

    next();
  } catch (error) {
    console.error('Route guard error:', error);
    next();
  }
});

export default router;
