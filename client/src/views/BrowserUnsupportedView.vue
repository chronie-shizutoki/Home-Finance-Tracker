<template>
  <div class="error-page">
    <h1 class="error-code">{{ $t('app.browserUnsupported') }}</h1>
    <p class="error-message">{{ $t('app.browserUnsupportedMessage') }}</p>
    <a
      class="home-link"
      href="https://browsehappy.com/"
      target="_blank"
      rel="noopener noreferrer"
    >
      {{ $t('app.upgradeBrowser') }} →
    </a>
  </div>
</template>

<script>
/**
 * Browser incompatibility notice page.
 * Shown when the runtime cannot support the modern feature set the
 * Vite(esnext) + Vue 3 bundle relies on.
 *
 * NOTE: For legacy engines that cannot even parse the ESNext bundle, the
 * real fallback is injected by the inline ES5 guard in index.html. This
 * component is the in-app (modern-browser) variant, reachable at
 * /unsupported-browser.
 * @displayName Browser Unsupported
 */
export default {
  name: 'BrowserUnsupportedView',
  metaInfo() {
    return {
      title: this.$t('app.browserUnsupported'),
      description: this.$t('app.browserUnsupportedMessage')
    };
  }
};
</script>

<style scoped src="../styles/views/BrowserUnsupportedView.css"></style>
