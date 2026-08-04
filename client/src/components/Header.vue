<template>
  <div :class="['header']" v-liquid-glass>
    <h1>{{ title }}</h1>

    <div class="header-right">
      <div class="language-dropdown" ref="languageDropdownRef">
        <GlassButton
          class="language-toggle"
          @click.stop="toggleDropdown"
          :aria-label="`当前语言: ${currentLanguageLabel}`"
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
            aria-label="语言选择"
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

defineOptions({ name: 'AppHeader' });
useI18n();

// Define props received by the component
const props = defineProps({ title: String });

// Initialize the router
const router = useRouter();

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
  // If no avatar is set, return a default blank avatar SVG
  return avatar || 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40" fill="none"><circle cx="20" cy="20" r="20" fill="%23e0e0e0"/><circle cx="20" cy="15" r="5" fill="%23999999"/><path d="M10 30C10 28.3431 11.3431 27 13 27H27C28.6569 27 30 28.3431 30 30V32C30 33.6569 28.6569 35 27 35H13C11.3431 35 10 33.6569 10 32V30Z" fill="%23999999"/></svg>';
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
    console.error('获取用户信息失败:', error);
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

<style scoped>
/* Header container. The glass blur is rendered by the WebGL engine through
   the v-liquid-glass directive on the .header element. */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: linear-gradient(135deg, rgba(255,255,255,0.8) 0%, rgba(250,250,250,0.9) 100%);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  border-bottom: 1px solid #e4e7ed;
  width: 95%;
  max-width: 1200px;
  margin: 0 auto;
  position: fixed; /* Stays pinned to the top while scrolling */
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  transition: all 0.3s ease;
  box-sizing: border-box;
  border-radius: 0 0 var(--border-radius-lg) var(--border-radius-lg);
}

/* Effect applied while scrolling */
.header.scrolled {
  padding: 0.8rem 1.5rem; /* Reduce padding while scrolling */
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08); /* Stronger shadow */
}

/* Title styles */
.header h1 {
  font-size: 1.8rem; /* Title font size */
  color: #303133; /* Title text color */
  margin: 0; /* Remove default margin */
  flex-shrink: 0; /* Prevent the title from shrinking */
  text-align: left; /* Left-align text */
  font-weight: 600; /* Font weight */
  background: linear-gradient(90deg, #409eff, #7928ca); /* Gradient text background */
  -webkit-background-clip: text; /* Clip background to the text */
  background-clip: text;
  -webkit-text-fill-color: transparent; /* Transparent text fill so the gradient shows through */
  letter-spacing: -0.02em; /* Letter spacing */
  transition: all 0.3s ease; /* Transition effect */
  position: relative;
  z-index: 1;
  white-space: nowrap; /* Prevent the title from wrapping */
  overflow: hidden;
  text-overflow: ellipsis; /* Show an ellipsis when text overflows */
  max-width: 50%; /* Maximum width limit */
}

/* Right-side area container */
.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-shrink: 0; /* Prevent shrinking */
}

/* Header actions area */
.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* User avatar container */
.user-avatar-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* User avatar styles */
.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.user-avatar:hover {
  transform: scale(1.1);
}

.language-dropdown {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.language-toggle {
  padding: 8px 12px;
  font-size: 14px;
  font-weight: 500;
  gap: 8px;
  white-space: nowrap;
  align-items: center;
}

.dropdown-arrow {
  width: 16px;
  height: 16px;
  transition: transform 0.3s ease;
  flex-shrink: 0;
}

.dropdown-arrow.rotated {
  transform: rotate(180deg);
}

.language-dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 140px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.12);
  padding: 6px;
  z-index: 1000;
  overflow: hidden;
}

.language-dropdown-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: #303133;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
  text-align: left;
  white-space: nowrap;
  gap: 8px;
}

.language-icon-current{
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  opacity: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.language-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  opacity: 0.6;
}

.language-dropdown-item:hover .language-icon .language-icon-current,
.language-dropdown-item.active .language-icon .language-icon-current {
  opacity: 1;
}

.language-dropdown-item:hover {
  background: rgba(255, 255, 255, 0.6);
}

.language-dropdown-item.active {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}

.language-label {
  flex: 1;
}

.check-icon {
  margin-left: 4px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .header {
    padding: 0.8rem 1rem;
  }

  .header h1 {
    font-size: 1.4rem;
    max-width: 40%;
  }

  .header-right {
    gap: 0.5rem;
  }

  .language-toggle {
    padding: 6px 10px;
    font-size: 12px;
  }

  .dropdown-arrow {
    width: 14px;
    height: 14px;
  }

  .language-dropdown-menu {
    min-width: 120px;
    padding: 4px;
  }

  .language-dropdown-item {
    padding: 8px 10px;
    font-size: 12px;
  }

  .language-icon {
    width: 12px;
    height: 12px;
  }

  .user-avatar {
    width: 36px;
    height: 36px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 0.6rem 0.8rem;
  }

  .header h1 {
    font-size: 1.1rem;
    max-width: 35%;
  }

  .language-toggle {
    padding: 4px 8px;
    font-size: 11px;
  }

  .dropdown-arrow {
    width: 12px;
    height: 12px;
  }

  .language-dropdown-menu {
    min-width: 110px;
    padding: 4px;
  }

  .language-dropdown-item {
    padding: 7px 8px;
    font-size: 11px;
    gap: 6px;
  }

  .language-icon {
    width: 11px;
    height: 11px;
  }

  .user-avatar {
    width: 32px;
    height: 32px;
  }
}

@media (prefers-color-scheme: dark) {
  .header {
    background: linear-gradient(135deg, rgba(30,30,30,0.8) 0%, rgba(24,24,24,0.9) 100%);
    border-bottom: 1px solid #333;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  }

  .header h1 {
    color: #e0e0e0;
    background: linear-gradient(90deg, #79bbff, #a78bfa);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .language-dropdown-menu {
    background: rgba(30, 30, 30, 0.9);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  }

  .language-dropdown-item {
    color: #e0e0e0;
  }

  .language-dropdown-item:hover {
    background: rgba(255, 255, 255, 0.08);
  }

  .language-dropdown-item.active {
    background: rgba(64, 158, 255, 0.2);
    color: #79bbff;
  }
}

.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.25s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.95);
}

</style>
