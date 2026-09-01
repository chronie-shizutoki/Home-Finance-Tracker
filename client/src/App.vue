<template>
  <div class="app-root">
    <router-view v-slot="{ Component, route }">
      <!-- NOTE: Outer wrapper must be a single real DOM element per:
             1) <Transition> — needs an element (not Fragment/Component) root to animate transforms.
             2) <Suspense>   — default slot requires a single root node.
           Each route-level view (HomeView / ChartsView / ...) has multiple top-level nodes
           (Header + .container), which is Fragment.  Wrapping in a single <div> here gives
           both <Transition> and <Suspense> the single element root they require. -->
      <Transition :name="transitionName" mode="out-in" appear>
        <div class="page-root" :key="route.fullPath">
          <Suspense>
            <component :is="Component" />
            <template #fallback>
              <div class="loading-alert">{{ t('app.loading') }}</div>
            </template>
          </Suspense>
        </div>
      </Transition>
    </router-view>
  </div>
</template>

<script setup>
import { watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';
import router, { transitionName } from '@/router';

const { t } = useI18n();

watchEffect(() => {
  // Read transitionName so this watchEffect re-runs when route direction changes
  // (keeps the binding live and helps DevTools reactivity tracking).
  // eslint-disable-next-line no-unused-expressions
  transitionName.value;
  if (router.currentRoute.value.meta?.title) {
    document.title = t(router.currentRoute.value.meta.title);
  } else {
    document.title = t('app.title');
  }
});
</script>

<style scoped>
/* Make the transition container fill the viewport so the slide animation
   spans the full width and never reveals the page background between frames. */
.app-root {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
}

/* Single-element wrapper that <Transition> actually animates.
   Must be full-width so translateX() moves the whole page rather than a shrink-wrapped box. */
.page-root {
  position: relative;
  width: 100%;
  min-height: 100vh;
}

/* <Suspense> fallback shown while route-level views are loading. */
.loading-alert {
  padding: 10px;
  margin-bottom: 15px;
  border-radius: 4px;
  text-align: center;
}
</style>