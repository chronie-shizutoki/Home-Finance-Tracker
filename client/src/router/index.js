import { createRouter, createWebHistory } from 'vue-router';
import { ref } from 'vue';
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
import RecycleBinView from '@/views/RecycleBinView.vue';

/**
 * Page transition direction.
 * - 'slide-left' : entering a deeper (secondary) page — new content slides in from the right
 * - 'slide-right': returning to a shallower (primary) page — new content slides in from the left
 * @type {import('vue').Ref<'slide-left' | 'slide-right'>}
 */
export const transitionName = ref('slide-left');

const router = createRouter({
  history: createWebHistory(), // Enable history mode (remove # from URL)
  routes: [
    {
      path: '/',
      name: 'home',
      meta: { title: 'app.title', depth: 0 }, // Primary page — depth 0
      component: HomeView
    },
    {
      path: '/charts',
      name: 'charts',
      meta: { title: 'chart.title', depth: 1 }, // Secondary page — depth 1
      component: ChartsView
    },
    {
      path: '/membership',
      name: 'membership',
      meta: { title: 'membership.title', depth: 1 }, // Secondary page — depth 1
      component: MembershipView
    },
    {
      path: '/recycle-bin',
      name: 'recycle-bin',
      meta: { title: 'recycleBin.title', depth: 1 }, // Secondary page — depth 1
      component: RecycleBinView
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      meta: { depth: 0 },
      component: NotFoundView
    }
  ],
  scrollBehavior (_to, _from, savedPosition) {
    return savedPosition || { top: 0 };
  }
});

// Global before each guard:
// 1. Determine horizontal transition direction based on route depth.
// 2. Update document title via i18n.
// 3. Log route access.
// NOTE: `next()` callback is deprecated since vue-router 4.10+. Use return-based navigation instead.
router.beforeEach(async (to, from) => {
  try {
    // --- Direction detection (for horizontal slide animation) ---
    // Default to `slide-left` (forward / into a secondary page).
    const toDepth = typeof to.meta?.depth === 'number' ? to.meta.depth : 0;
    const fromDepth = typeof from.meta?.depth === 'number' ? from.meta.depth : -1;

    if (toDepth < fromDepth) {
      // Going back to a shallower page (secondary -> home).
      transitionName.value = 'slide-right';
    } else if (toDepth > fromDepth) {
      // Going forward into a deeper page (home -> secondary).
      transitionName.value = 'slide-left';
    } else {
      // Same depth: secondary <-> secondary.  Use browser-history direction heuristic
      // (POP = back/forward button in history, else treat as forward push).
      const navType = router.currentRoute.value.href === '' ? 'PUSH' : (router.options.history.state?.back ? 'POP_BACK' : 'PUSH');
      // `window.history.length` alone isn't reliable; prefer explicit depth first.
      // For same-depth navigation we default to slide-left so animation is always visible.
      transitionName.value = navType === 'POP_BACK' ? 'slide-right' : 'slide-left';
    }

    // Update page title
    if (to.meta.title) {
      document.title = i18n.global.t(to.meta.title);
    }

    // Record route access log
    const userAgent = navigator.userAgent || 'unknown';
    console.log(`[Route Access] ${to.name} route accessed (${transitionName.value}) - User-Agent: ${userAgent}`);
  } catch (error) {
    console.error('Route guard error:', error);
  }
  // Always allow navigation (return true / undefined both work; explicit return for clarity)
  return true;
});

export default router;
