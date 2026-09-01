<template>
  <div class="error-page">
    <h1 class="error-code">404 {{ $t('app.notFound') }}</h1>
    <p class="error-message">{{ $t('app.pageNotFound') }}</p>
    <router-link
      class="home-link"
      :to="{ name: 'home' }"
    >
      ← {{ $t('app.backToHome') }}
    </router-link>
  </div>
</template>

<script>
/**
 * Global error page component
 * @displayName Error Page
 */
export default {
  name: 'NotFoundView',
  metaInfo() {
    return {
      title: this.$t('app.notFound'),
      description: this.$t('app.pageNotFound'),
    }
  }
};
</script>

<style scoped src="../styles/views/NotFoundView.css"></style>
