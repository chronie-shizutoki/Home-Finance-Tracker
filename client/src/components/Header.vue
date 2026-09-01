<template>
  <div :class="['header', { 'has-back': showBack }]" v-liquid-glass="{ disabled: true }">
    <!-- Circular glass back button (for secondary pages: charts, membership, recycle-bin, ...) -->
    <button
      v-if="showBack"
      class="back-circle-btn"
      type="button"
      @click="handleBackClick"
      :aria-label="resolvedBackLabel"
      :title="resolvedBackLabel"
    >
      <FontAwesomeIcon icon="chevron-left" />
    </button>

    <h1>{{ title }}</h1>

    <div class="header-spacer" aria-hidden="true"></div>

    <div class="header-right">
      <div class="language-dropdown" ref="languageDropdownRef">
        <GlassButton
          class="language-toggle"
          @click.stop="toggleDropdown"
          :aria-label="`Current language: ${currentLanguageLabel}`"
          aria-haspopup="true"
          :aria-expanded="isDropdownOpen"
        >
        <svg class="language-icon-current" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <circle cx="12" cy="12" r="4"></circle>
                <line x1="21.17" y1="8" x2="12" y2="8"></line>
                <line x1="3.95" y1="6.06" x2="8.54" y2="14"></line>
                <line x1="10.88" y1="21.94" x2="15.46" y2="14"></line>
              </svg>
          <span>{{ currentLanguageShortLabel }}</span>
          <svg class="dropdown-arrow" :class="{ 'rotated': isDropdownOpen }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 9l6 6 6-6"></path>
          </svg>
        </GlassButton>

        <Transition name="dropdown">
          <div
            v-show="isDropdownOpen"
            class="language-dropdown-menu"
            role="menu"
            aria-label="Language selection"
          >
            <button
              v-for="lang in languages"
              :key="lang.code"
              @click.stop="selectLanguage(lang.code)"
              :class="['language-dropdown-item', { 'active': currentLang.value === lang.code }]"
              :role="currentLang.value === lang.code ? 'menuitemradio' : 'menuitem'"
              :aria-checked="currentLang.value === lang.code"
            >
            <svg class="language-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <circle cx="12" cy="12" r="4"></circle>
                <line x1="21.17" y1="8" x2="12" y2="8"></line>
                <line x1="3.95" y1="6.06" x2="8.54" y2="14"></line>
                <line x1="10.88" y1="21.94" x2="15.46" y2="14"></line>
              </svg>
              <span class="language-label">{{ lang.label }}</span>
              <span v-if="currentLang.value === lang.code" class="check-icon">✓</span>
            </button>
          </div>
        </Transition>
      </div>

      <div class="header-actions">
        <!-- Avatar display -->
        <div class="user-avatar-container" @click="goToMembership">
          <img :src="avatarUrl" alt="User Avatar" class="user-avatar" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useLanguageSwitch } from '@/composables/useLanguageSwitch';
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

defineOptions({ name: 'AppHeader' });
const { t } = useI18n();

// Define props received by the component
const props = defineProps({
  title: { type: String, default: '' },
  /** Whether to show the circular back button (for secondary pages) */
  showBack: { type: Boolean, default: false },
  /** Router path to go back to when back button is clicked. Defaults to "/" (home). */
  backRoute: { type: String, default: '/' },
  /** Accessible label for the back button. Defaults to a localized "Back to home". */
  backLabel: { type: String, default: '' },
});

// Emits: allow parent to override back behavior if needed
const emit = defineEmits(['back']);

// Initialize the router
const router = useRouter();

// Resolve the final accessible label for the back button
const resolvedBackLabel = computed(() => props.backLabel || t('recycleBin.backToHome') || 'Back');

// Back click: prefer emit override, else navigate to backRoute via router
const handleBackClick = () => {
  emit('back');
  // If no parent handler cancels default via event, also do standard navigation
  try {
    router.push(props.backRoute || '/').catch(() => { /* ignore duplicate nav */ });
  } catch (e) {
    console.warn('[Header] back navigation failed:', e);
  }
};

// Navigate to the membership page when the avatar is clicked
const goToMembership = () => {
  router.push('/membership');
};

// Call useLanguageSwitch to obtain the language switch function and current language
const { switchLanguage: originalSwitchLanguage, currentLang } = useLanguageSwitch();

// Enhanced language switch function with added logging
const switchLanguage = (langCode) => {
  console.log('Language switch requested:', { from: currentLang.value, to: langCode });
  try {
    originalSwitchLanguage(langCode);
    console.log('Language switch successful:', { currentLanguage: langCode });
  } catch (error) {
    console.error('Language switch failed:', { error: error.message, requestedLang: langCode });
  }
};

// Define the list of supported languages
const languages = [
  { code: 'en-US', label: 'English', shortLabel: 'EN' },
  { code: 'id-ID', label: 'Indonesia', shortLabel: 'ID' },
  { code: 'ja-JP', label: '日本語', shortLabel: 'JP' },
  { code: 'ko-KR', label: '한국어', shortLabel: 'KO' },
  { code: 'ms-MY', label: 'Melayu', shortLabel: 'MS' },
  { code: 'th-TH', label: 'ไทย', shortLabel: 'TH' },
  { code: 'vi-VN', label: 'Tiếng Việt', shortLabel: 'VI' },
  { code: 'zh-CN', label: '简体中文', shortLabel: 'CN' },
  { code: 'zh-HK', label: '繁體中文(香港)', shortLabel: 'HK' },
  { code: 'zh-MO', label: '繁體中文(澳門)', shortLabel: 'MO' },
  { code: 'zh-SG', label: '简体中文(新加坡)', shortLabel: 'SG' },
  { code: 'zh-TW', label: '繁體中文(台灣)', shortLabel: 'TW' }
];

const isDropdownOpen = ref(false);
const languageDropdownRef = ref(null);

const currentLanguageLabel = computed(() => {
  const lang = languages.find(l => l.code === currentLang.value);
  return lang ? lang.label : '';
});

const currentLanguageShortLabel = computed(() => {
  const lang = languages.find(l => l.code === currentLang.value);
  return lang ? lang.shortLabel : '';
});

const toggleDropdown = () => {
  isDropdownOpen.value = !isDropdownOpen.value;
};

const selectLanguage = (langCode) => {
  switchLanguage(langCode);
  isDropdownOpen.value = false;
};

const handleClickOutside = (event) => {
  if (languageDropdownRef.value && !languageDropdownRef.value.contains(event.target)) {
    isDropdownOpen.value = false;
  }
};

const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    isDropdownOpen.value = false;
  }
};

// Retrieve the username from local storage
const getUsername = () => {
  return localStorage.getItem('username') || '';
};

// Retrieve the current user's avatar from local storage
const getCurrentUserAvatar = () => {
  const username = getUsername();
  if (!username) return '';
  return localStorage.getItem('avatar-' + username) || '';
};

// Computed property for the avatar URL; returns a default blank avatar when none is set
const avatarUrl = computed(() => {
  const avatar = getCurrentUserAvatar();
  // If no avatar is set, return a solid default SVG. A small inline SVG is
  // preferred over an external file so it works offline; URIs below use
  // explicit URL-encoded characters to avoid parsing issues.
  return (
    avatar ||
    // Light-gray placeholder: neutral gray circle + body. Stays visible on
    // both the current light-grey header background and dark mode variants.
    'data:image/svg+xml;charset=UTF-8,' +
      encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80">
          <defs>
            <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#a5b4fc" stop-opacity="0.95"/>
              <stop offset="100%" stop-color="#4361ee" stop-opacity="0.95"/>
            </linearGradient>
          </defs>
          <circle cx="40" cy="40" r="40" fill="url(#g)"/>
          <circle cx="40" cy="31" r="11" fill="white" fill-opacity="0.95"/>
          <path d="M18 62.5 C18 53.4 25.7 47 40 47 C54.3 47 62 53.4 62 62.5 V68 C62 74.2 57.2 78 51 78 H29 C22.8 78 18 74.2 18 68 V62.5 Z" fill="white" fill-opacity="0.95"/>
        </svg>`
      )
  );
});

// Fetch user info and avatar from the backend
const fetchUserInfo = async () => {
  const username = getUsername();
  if (!username) return;
  
  try {
    // Fetch user info from the backend
    const userResponse = await fetch('/api/members/members/' + encodeURIComponent(username), {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    if (userResponse.ok) {
      const userData = await userResponse.json();
      const user = userData.data || userData;
      
      // If the backend returned an avatar, update local storage
      if (user && user.avatar) {
        localStorage.setItem('avatar-' + username, user.avatar);
      }
    }
  } catch (error) {
    console.error('Failed to fetch user info:', error);
  }
};

// Lifecycle hooks
onMounted(async () => {
  console.log('Header component mounted:', { initialLanguage: currentLang.value });
  // Fetch the latest user info and avatar from the backend
  await fetchUserInfo();
  document.addEventListener('click', handleClickOutside);
  document.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped src="../styles/components/Header.css"></style>
