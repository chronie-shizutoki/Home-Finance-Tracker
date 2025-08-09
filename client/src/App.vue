<template>
    <Suspense>
      <router-view />
      <template #fallback>
        <div class="loading">{{ t('app.loading') }}</div>
      </template>
    </Suspense>

</template>

<script setup>
import { watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

watchEffect(() => {
  document.title = t('app.title');
});
</script>

<style>
/* 增强适配兼容性，添加浏览器前缀以支持更多浏览器 */
#app {
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  transition: background-color 0.3s, color 0.3s;
  position: relative;
  background: transparent;
}

.contain{
  background-color: transparent !important;
}

.app-container {
  transition: background 0.3s ease, color 0.3s ease;
}

header {
  padding: 1rem;
  border-bottom: 1px solid #eee;
}

main {
  padding: 2rem;
  min-height: 100vh;
}

button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  #app {
    color: #e0e0e0;
    background: #121212;
  }

  .app-container {
    background: #1e1e1e;
    color: #e0e0e0;
  }

  header {
    border-bottom: 1px solid #333;
  }

  button {
    background-color: #333;
    color: #e0e0e0;
  }
}
</style>
