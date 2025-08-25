<template>
  <div ref="headerRef" :class="['header']">
    <h1>{{ title }}</h1>

    <ElDropdown trigger="click" placement="bottom-end">
      <span class="earth-icon" role="button" aria-haspopup="true" aria-expanded="false" aria-label="切换语言">🌍</span>

      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-for="lang in languages"
            :key="lang.code"
            @click="switchLanguage(lang.code)"
            :class="{ 'is-active': currentLanguage === lang.code }"
          >
            {{ lang.label }}
            <template v-if="currentLanguage === lang.code">
              <ElIcon class="ml-2"><Check /></ElIcon>
            </template>
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>

  </div>
</template>

<script setup>
// 恢复导入你原有的 useLanguageSwitch composable
import { useLanguageSwitch } from '@/composables/useLanguageSwitch';
import { ref, onMounted, onUnmounted } from 'vue';
// 导入 Element Plus 的下拉菜单相关组件
import {
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon
} from 'element-plus';
import { Check } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';

defineOptions({ name: 'AppHeader' });
const { t } = useI18n();

// 定义组件接收的 props
const props = defineProps({ title: String });

// 调用 useLanguageSwitch 获取语言切换函数和当前语言
const { switchLanguage, currentLanguage } = useLanguageSwitch();

// 定义支持的语言列表
const languages = [
  { code: 'en-US', label: 'English' },
  { code: 'zh-CL', label: '中文' }
];

// 监听滚动事件，添加滚动效果
const headerRef = ref(null);

const handleScroll = () => {
  if (headerRef.value) {
    if (window.scrollY > 50) {
      headerRef.value.classList.add('scrolled');
    } else {
      headerRef.value.classList.remove('scrolled');
    }
  }
};

// 生命周期钩子
onMounted(() => {
  window.addEventListener('scroll', handleScroll);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<style scoped>
/* 头部容器的基础样式 */
.header {
  display: flex; /* 使用 Flexbox 布局 */
  justify-content: space-between; /* 子元素两端对齐 */
  align-items: center; /* 子元素垂直居中 */
  padding: 1rem 1.5rem; /* 内边距 */
  background: linear-gradient(135deg, rgba(255,255,255,0.8) 0%, rgba(250,250,250,0.9) 100%); /* 渐变背景 */
  backdrop-filter: blur(10px); /* 毛玻璃效果 */
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05); /* 底部阴影 */
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed); /* 底部边框 */
  position: sticky; /* 粘性定位，使其在滚动时保持在顶部 */
  top: 0; /* 距离顶部0 */
  z-index: 100; /* 确保在其他内容之上 */
  transition: all 0.3s ease; /* 所有属性的过渡效果 */
}

/* 滚动时的效果 */
.header.scrolled {
  padding: 0.8rem 1.5rem; /* 减小滚动时的内边距 */
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08); /* 增加阴影 */
}

/* 标题样式 */
.header h1 {
  font-size: 1.8rem; /* 标题字体大小 */
  color: var(--el-text-color-primary, #303133); /* 标题文本颜色 */
  margin: 0; /* 移除默认外边距 */
  flex-grow: 1; /* 允许标题占据可用空间 */
  text-align: left; /* 文本左对齐 */
  font-weight: 600; /* 字体粗细 */
  background: linear-gradient(90deg, var(--el-color-primary, #409eff), #7928ca); /* 文本渐变背景 */
  -webkit-background-clip: text; /* 背景裁剪到文本 */
  background-clip: text;
  -webkit-text-fill-color: transparent; /* 文本填充透明，显示背景渐变 */
  letter-spacing: -0.02em; /* 字母间距 */
  transition: all 0.3s ease; /* 过渡效果 */
  position: relative;
  z-index: 1;
}

/* 地球图标样式 */
.earth-icon {
  font-size: 28px; /* 图标字体大小 */
  cursor: pointer; /* 鼠标悬停时显示手型光标 */
  color: var(--el-color-primary, #409eff); /* 图标颜色，使用 Element Plus 主题色 */
  margin-left: 1rem; /* 左侧外边距 */
  display: flex; /* 使用 flex 布局确保图标居中 */
  align-items: center; /* 垂直居中 */
  justify-content: center; /* 水平居中 */
  width: 45px; /* 固定宽度 */
  height: 45px; /* 固定高度 */
  border-radius: 50%; /* 圆形 */
  background-color: transparent; /* 背景色 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); /* 阴影效果 */
  position: relative; /* 相对定位，用于伪元素 */
}

/* 地球图标悬停效果 */
.earth-icon:hover {
  color: var(--el-color-primary-light-3, #79bbff); /* 悬停时颜色变亮 */
  transform: scale(1.1) rotate(5deg); /* 悬停时放大并旋转 */
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1); /* 增加阴影 */
  background-color: white; /* 背景色变白 */
}

/* 地球图标动画 */
.earth-icon::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 60px;
  height: 60px;
  background: radial-gradient(circle, rgba(64,158,255,0.2) 0%, rgba(64,158,255,0) 70%);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  z-index: -1;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.earth-icon:hover::after {
  opacity: 1;
  animation: pulse 2s infinite ease-in-out; /* 脉冲动画 - 更柔和 */
}

/* 脉冲动画 */
@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(0.9);
    opacity: 0.4;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.05);
    opacity: 0.2;
  }
  100% {
    transform: translate(-50%, -50%) scale(0.9);
    opacity: 0.4;
  }
}

/* 针对 Element Plus 下拉菜单的样式覆盖 */
/* 使用 :deep() 穿透作用域样式，修改 Element Plus 组件内部样式 */
.header :deep(.el-dropdown__popper) {
  /* 明亮模式默认值 */
  --dropdown-bg: var(--el-bg-color-overlay, #ffffff);
  --dropdown-text: var(--el-text-color-regular, #606266);
  --dropdown-hover-bg: var(--el-fill-color-light, #f5f5f5);
  --dropdown-border: var(--el-border-color-light, #e4e7ed);

  background: var(--dropdown-bg) !important;
  border: 1px solid var(--dropdown-border) !important;
  border-radius: var(--el-border-radius-base, 8px) !important; /* 更大的圆角 */
  box-shadow: 0 12px 32px 4px rgba(0, 0, 0, 0.06), 0 4px 16px rgba(0, 0, 0, 0.04) !important; /* 增强阴影 */
  padding: 8px 0 !important; /* 增加内边距 */
  overflow: hidden; /* 隐藏溢出内容 */
}

.header :deep(.el-dropdown-menu .el-dropdown-menu__item) {
  padding: 10px 20px !important; /* 增加内边距 */
  color: var(--dropdown-text) !important;
  transition: all 0.3s ease;
  font-size: var(--el-font-size-base, 14px);
  line-height: 1.5 !important;
  border-radius: 6px !important; /* 圆角 */
  margin: 0 8px !important; /* 外边距 */
}

.header :deep(.el-dropdown-menu__item:hover) {
  background: var(--dropdown-hover-bg) !important;
  color: var(--el-color-primary) !important;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); /* 增加悬停阴影 */
}

/* 动画效果 */
.header :deep(.el-dropdown-menu) {
  transform: translateY(-10px);
  opacity: 0;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 400px; /* 最大高度 */
  overflow-y: auto; /* 超出部分添加滚动条 */
}

.header :deep(.el-dropdown-menu.el-dropdown-menu--show) {
  transform: translateY(0);
  opacity: 1;
  animation: fadeIn 0.3s ease; /* 淡入动画 */
}

/* 淡入动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    padding: 0.8rem 1rem; /* 减小移动端内边距 */
  }

  .header h1 {
    font-size: 1.3rem; /* 减小移动端标题字体大小 */
  }

  .earth-icon {
    font-size: 24px; /* 减小移动端图标大小 */
    width: 40px;
    height: 40px;
  }
}

@media (max-width: 480px) {
  .header h1 {
    font-size: 1.2rem; /* 进一步减小极小屏幕的标题字体大小 */
  }

  /* 移动端下拉菜单宽度调整 */
  .header :deep(.el-dropdown-menu) {
    min-width: 200px !important;
    max-width: 90vw !important;
  }
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .header {
    background: linear-gradient(135deg, rgba(30,30,30,0.8) 0%, rgba(24,24,24,0.9) 100%); /* 深色渐变背景 */
    border-bottom: 1px solid #333;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  }

  .header h1 {
    color: #e0e0e0;
    background: linear-gradient(90deg, #79bbff, #a78bfa); /* 深色模式下的文本渐变 */
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .earth-icon {
    color: #79bbff;
    background-color: #333;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }

  .header :deep(.el-dropdown__popper) {
    --dropdown-bg: #333;
    --dropdown-text: #e0e0e0;
    --dropdown-hover-bg: #444;
    --dropdown-border: #555;
  }

  .header :deep(.el-dropdown-menu__item:hover) {
    color: #79bbff !important;
  }
}

</style>
