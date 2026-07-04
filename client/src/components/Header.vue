<template>
  <div :class="['header']">
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
        <!-- 头像显示 -->
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

// 定义组件接收的 props
const props = defineProps({ title: String });

// 初始化路由
const router = useRouter();

// 点击头像导航到会员页面
const goToMembership = () => {
  router.push('/membership');
};

// 调用 useLanguageSwitch 获取语言切换函数和当前语言
const { switchLanguage: originalSwitchLanguage, currentLang } = useLanguageSwitch();

// 增强版语言切换函数，添加日志记录
const switchLanguage = (langCode) => {
  console.log('Language switch requested:', { from: currentLang.value, to: langCode });
  try {
    originalSwitchLanguage(langCode);
    console.log('Language switch successful:', { currentLanguage: langCode });
  } catch (error) {
    console.error('Language switch failed:', { error: error.message, requestedLang: langCode });
  }
};

// 定义支持的语言列表
const languages = [
  { code: 'en-US', label: 'English', shortLabel: 'EN' },
  { code: 'zh-CN', label: '简体中文', shortLabel: '简体' },
  { code: 'zh-TW', label: '繁體中文', shortLabel: '繁體' },
  { code: 'ja-JP', label: '日本語', shortLabel: '日本語' }
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

// 从本地存储获取用户名
const getUsername = () => {
  return localStorage.getItem('username') || '';
};

// 从本地存储获取当前用户头像
const getCurrentUserAvatar = () => {
  const username = getUsername();
  if (!username) return '';
  return localStorage.getItem('avatar-' + username) || '';
};

// 计算属性获取头像URL，没有头像时返回默认空白头像
const avatarUrl = computed(() => {
  const avatar = getCurrentUserAvatar();
  // 如果没有头像，返回一个默认的空白头像SVG
  return avatar || 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40" fill="none"><circle cx="20" cy="20" r="20" fill="%23e0e0e0"/><circle cx="20" cy="15" r="5" fill="%23999999"/><path d="M10 30C10 28.3431 11.3431 27 13 27H27C28.6569 27 30 28.3431 30 30V32C30 33.6569 28.6569 35 27 35H13C11.3431 35 10 33.6569 10 32V30Z" fill="%23999999"/></svg>';
});

// 从后端获取用户信息和头像
const fetchUserInfo = async () => {
  const username = getUsername();
  if (!username) return;
  
  try {
    // 从后端获取用户信息
    const userResponse = await fetch('/api/members/members/' + encodeURIComponent(username), {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    if (userResponse.ok) {
      const userData = await userResponse.json();
      const user = userData.data || userData;
      
      // 如果后端返回了头像，更新本地存储
      if (user && user.avatar) {
        localStorage.setItem('avatar-' + username, user.avatar);
      }
    }
  } catch (error) {
    console.error('获取用户信息失败:', error);
  }
};

// 生命周期钩子
onMounted(async () => {
  console.log('Header component mounted:', { initialLanguage: currentLang.value });
  // 从后端获取最新的用户信息和头像
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
/* 头部容器的基础样式 */
.header {
  display: flex; /* 使用 Flexbox 布局 */
  justify-content: space-between; /* 子元素两端对齐 */
  align-items: center; /* 子元素垂直居中 */
  padding: 1rem 2rem; /* 内边距 */
  background: linear-gradient(135deg, rgba(255,255,255,0.8) 0%, rgba(250,250,250,0.9) 100%); /* 渐变背景 */
  backdrop-filter: blur(10px); /* 毛玻璃效果 */
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05); /* 底部阴影 */
  border-bottom: 1px solid #e4e7ed; /* 底部边框 */
  width: 95%; /* 宽度95% */
  max-width: 1200px; /* 最大宽度限制 */
  margin: 0 auto; /* 水平居中 */
  position: fixed; /* 固定定位，使其在滚动时保持在顶部 */
  top: 0; /* 距离顶部0 */
  left: 50%; /* 水平居中定位 */
  transform: translateX(-50%); /* 水平居中修正 */
  z-index: 100; /* 确保在其他内容之上 */
  transition: all 0.3s ease; /* 所有属性的过渡效果 */
  box-sizing: border-box; /* 边框盒模型 */
}

/* 滚动时的效果 */
.header.scrolled {
  padding: 0.8rem 1.5rem; /* 减小滚动时的内边距 */
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08); /* 增加阴影 */
}

/* 标题样式 */
.header h1 {
  font-size: 1.8rem; /* 标题字体大小 */
  color: #303133; /* 标题文本颜色 */
  margin: 0; /* 移除默认外边距 */
  flex-shrink: 0; /* 防止标题被压缩 */
  text-align: left; /* 文本左对齐 */
  font-weight: 600; /* 字体粗细 */
  background: linear-gradient(90deg, #409eff, #7928ca); /* 文本渐变背景 */
  -webkit-background-clip: text; /* 背景裁剪到文本 */
  background-clip: text;
  -webkit-text-fill-color: transparent; /* 文本填充透明，显示背景渐变 */
  letter-spacing: -0.02em; /* 字母间距 */
  transition: all 0.3s ease; /* 过渡效果 */
  position: relative;
  z-index: 1;
  white-space: nowrap; /* 防止标题换行 */
  overflow: hidden;
  text-overflow: ellipsis; /* 超出显示省略号 */
  max-width: 50%; /* 最大宽度限制 */
}

/* 右侧区域容器 */
.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-shrink: 0; /* 防止被压缩 */
}

/* 头部操作区域 */
.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* 用户头像容器 */
.user-avatar-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 用户头像样式 */
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
  backdrop-filter: blur(12px);
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
