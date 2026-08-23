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
</style>

<style>
/* Global base styles */
#app {
  /* Ensure application content is not forced by popups */
  position: relative !important;
  z-index: 1 !important;
  -webkit-font-smoothing: antialiased !important;
  -moz-osx-font-smoothing: grayscale !important;
  transition: background-color var(--transition-time), color var(--transition-time) !important;
  position: relative !important;
  background: var(--bg-primary) !important;
}

/* Global scrollbar hide styles */
html, body {
  /* Hide scrollbar but keep functionality */
  -ms-overflow-style: none;  /* IE and Edge */
  scrollbar-width: none;     /* Firefox */
  overflow-x: hidden;        /* Prevent horizontal scrollbar */
}

/* Chrome, Safari, Opera */
html::-webkit-scrollbar,
body::-webkit-scrollbar,
.app-container::-webkit-scrollbar {
  display: none;
}

/* Ensure scrolling functionality still works */
html, body {
  -webkit-overflow-scrolling: touch;  /* iOS Safari smooth scrolling */
}

.contain {
  background-color: transparent !important;
}

.app-container {
  transition: background var(--transition-time) ease, color var(--transition-time) ease !important;
  min-height: 100vh !important;
  display: flex !important;
  flex-direction: column !important;
}

header {
  padding: 1rem !important;
  border-bottom: 1px solid #eee !important;
  transition: border-color var(--transition-time) ease !important;
}

main {
  padding: 2rem !important;
  flex: 1 !important;
  position: relative !important;
}

button {
  border-radius: var(--border-radius) !important;
  cursor: pointer !important;
  transition: all var(--transition-time) ease !important;
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #1e2028 !important;
    --border-light: rgba(255, 255, 255, 0.12) !important;
  }

  html, body {
    background: var(--bg-primary) !important;
    transition: background-color var(--transition-time) ease, color var(--transition-time) ease !important;
  }
  
  .app-container {
    background: #2a2d35 !important;
  }

  header {
    border-bottom: 1px solid var(--border-light) !important;
  }

  /* ===== Dark mode date picker optimization ===== */
  input[type="date"] {
    background-color: #2a2d35 !important;
    border: 1px solid rgba(255, 255, 255, 0.12) !important;
    border-radius: var(--border-radius) !important;
    padding: 8px 12px !important;
    -webkit-appearance: none !important;
    appearance: none !important;
    transition: all var(--transition-time) ease !important;
    outline: none !important;
    color-scheme: dark !important;
  }

  input[type="date"]:focus {
    border-color: #6366f1 !important;
    box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.4) !important;
  }

  input[type="date"]::-webkit-calendar-picker-indicator {
    filter: invert(0.85) brightness(1.15) contrast(1.4) !important;
    cursor: pointer !important;
    width: 20px !important;
    height: 20px !important;
  }

  input[type="date"]::-webkit-datetime-edit-fields-wrapper {
    background: transparent !important;
  }

  input[type="date"]::-webkit-datetime-edit-text {
    color: #9EA3AD !important;
    padding: 0 2px !important;
  }
}

.loading-alert {
  padding: 10px;
  margin-bottom: 15px;
  border-radius: 4px;
  text-align: center;
}
</style>