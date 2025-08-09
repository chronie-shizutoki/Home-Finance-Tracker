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
    transition: background-color 0.2s ease, color 0.2s ease;
  }

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  #app {
    color: #e0e0e0;
    background: #121212;
  }

  html, body {
    background: #121212;
    color: #e0e0e0;
    transition: background-color 0.3s ease, color 0.3s ease;
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

  button:hover {
    background-color: #444;
  }

  button:active {
    background-color: #222;
  }

  button:disabled {
    background-color: #2d2d2d;
    color: #666666;
    cursor: not-allowed;
  }

  /* 通用div和span元素深色模式适配 */
  div {
    color: #e0e0e0;
    background-color: transparent;
  }

  span {
    color: #e0e0e0;
  }
/* ===== 深色模式日期选择器优化方案 ===== */
input[type="date"] {
  /* 基础样式 */
  background-color: #1e1e1e !important;
  color: #e0e0e0 !important;
  border: 1px solid #333333 !important;
  border-radius: 4px;
  padding: 8px 12px;
  
  /* 关键修复：移除默认外观 */
  -webkit-appearance: none !important;
  appearance: none !important;
  
  /* 过渡效果 */
  transition: all 0.2s ease;
  outline: none;
}

/* 修复焦点状态 */
input[type="date"]:focus {
  border-color: #4d90fe !important;  /* 更明显的蓝色 */
  box-shadow: 0 0 0 2px rgba(77, 144, 254, 0.5) !important;
}

/* ===== 日历图标适配方案 ===== */
input[type="date"]::-webkit-calendar-picker-indicator {
  /* 方案1：颜色反转（推荐） */
  filter: invert(0.8) brightness(1.2) contrast(1.5) !important;
  
  /* 方案2：自定义SVG图标（备用） */
  background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="%23e0e0e0" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>') !important;
  background-repeat: no-repeat;
  background-position: center;
  
  /* 统一尺寸 */
  width: 20px;
  height: 20px;
  cursor: pointer;
}

/* ===== 文本字段修复 ===== */
input[type="date"]::-webkit-datetime-edit {
  color: #e0e0e0 !important;
  padding: 2px 0; /* 垂直居中 */
}
input[type="date"]::-webkit-datetime-edit-fields-wrapper {
  background: transparent !important; /* 移除背景 */
}
input[type="date"]::-webkit-datetime-edit-text {
  color: #999 !important;
  padding: 0 2px; /* 分隔符间距 */
}

/* ===== 下拉日历深色模式强制启用 ===== */
  /* 触发Chrome内部深色模式 */
  input[type="date"] {
    color-scheme: dark !important;
  }

  /* Element Plus对话框组件深色模式增强 */
  .el-dialog {
    background-color: #1e1e1e !important;
    border-color: #333 !important;
  }

  .el-dialog__title {
    color: #e0e0e0 !important;
  }

  .el-dialog__body {
    color: #e0e0e0 !important;
    background-color: #1e1e1e !important;
  }
        /* Element Plus 下拉菜单深色模式适配 - 增强版 */
        .el-select {
          --el-select-bg-color: #1e1e1e !important;
          --el-select-text-color: #e0e0e0 !important;
          --el-select-border-color: #333333 !important;
          background-color: var(--el-select-bg-color) !important;
        }

        .el-select__dropdown {
          background-color: var(--el-select-bg-color) !important;
          border-color: var(--el-select-border-color) !important;
          color: var(--el-select-text-color) !important;
        }

        /* 确保下拉菜单内容区域背景色正确 */
        .el-select__dropdown .el-select-dropdown__list {
          background-color: var(--el-select-bg-color) !important;
        }

        /* 修复下拉菜单可能的白色背景问题 */
        .el-popper {
          background-color: #1e1e1e !important;
          border-color: #333333 !important;
        }

        .el-option {
          color: var(--el-select-text-color) !important;
          background-color: var(--el-select-bg-color) !important;
        }

        .el-option:hover {
          background-color: #2d2d2d !important;
          color: #ffffff !important;
        }

        .el-option.is-selected {
          background-color: #3366cc !important;
          color: #ffffff !important;
        }

        .el-select__placeholder {
          color: #999999 !important;
        }
        /* 输入框组件深色模式适配 */
        .el-input__wrapper {
          background-color: #1e1e1e !important;
          --el-input-bg-color: #1e1e1e !important;
          --el-input-text-color: #e0e0e0 !important;
          --el-input-border-color: #333333 !important;
        }
        .el-input__inner {
          background-color: #1e1e1e !important;
          color: #e0e0e0 !important;
        }
        /* 输入框占位符颜色 */
        .el-input__inner::placeholder {
          color: #999999 !important;
        }
        /* Removed duplicate date picker styles - consolidated above */
        .el-input__wrapper.is-focus {
          border-color: #3366cc !important;
        }
        .el-input-group__append {
          background-color: #333 !important;
        }
        .el-input-group__prepend {
          background-color: #333 !important;
        }
        .el-select__wrapper .el-tooltip__trigger .el-tooltip__content {
          background-color: #333 !important;
          color: #e0e0e0 !important;
          border: 1px solid #444 !important;
        }
        /* 完善下拉选择器的其他元素样式 */
        .el-select__wrapper {          
          background-color: #1e1e1e !important;          
          border-color: #333333 !important;        
        }

        /* 下拉菜单focus状态样式 */
        .el-select__wrapper.is-focus {
          background-color: #1e1e1e !important;
          border-color: #3366cc !important;
        }
        .el-select__placeholder {
          color: #999999 !important;
        }
        /* 下拉菜单滚动条样式 */
        .el-select__dropdown .el-scrollbar__bar {
          background-color: #444444 !important;
        }
        .el-select__dropdown .el-scrollbar__thumb {
          background-color: #666666 !important;
        }
        /* 下拉菜单分隔线样式 */
        .el-select__dropdown .el-divider {
          background-color: #333333 !important;
        }
        /* 多选下拉菜单标签样式 */
        .el-select__tags {
          background-color: #1e1e1e !important;
        }
        .el-select__tag {
          background-color: #333333 !important;
          color: #e0e0e0 !important;
          border-color: #444444 !important;
        }
        .el-select__tag-close {
          color: #999999 !important;
        }
        .el-select__tag-close:hover {
          color: #e0e0e0 !important;
        }

        /* Element Plus 表单组件深色模式适配 */
        .el-form-item__label {
          color: #e0e0e0 !important;
        }

        /* Fix the white background highlighting issue when mouse hovers over the pop-up menu of .el-select__wrapper */
        .el-popper .el-select-dropdown__item:hover {
          background-color: #2d2d2d !important;
        }
/* Fix the background color of el-dropdown popper in dark mode */
.el-dropdown__popper.el-popper {
  background-color: #1e1e1e !important;
  border-color: #333333 !important;
}

.el-dropdown-menu {
  background-color: #1e1e1e !important;
}

.el-dropdown-menu__item {
  color: #e0e0e0 !important;
  background-color: #1e1e1e !important;
}

.el-dropdown-menu__item:hover {
  background-color: #2d2d2d !important;
}
/* Element Plus textarea component dark mode adaptation */
.el-textarea__inner {
  background-color: #1e1e1e !important;
  color: #e0e0e0 !important;
  border-color: #333333 !important;
}

.el-textarea__inner::placeholder {
  color: #999999 !important;
}

.el-textarea__wrapper.is-focus .el-textarea__inner {
  border-color: #3366cc !important;
  box-shadow: 0 0 0 2px rgba(51, 102, 204, 0.2) !important;
}

        /* Element Plus 单选按钮组深色模式适配 */
        .el-radio-button__inner {
          background-color: #1e1e1e !important;
          color: #e0e0e0 !important;
          border-color: #333333 !important;
        }

        .el-radio-button__inner:hover {
          background-color: #2d2d2d !important;
        }

        .el-radio-button.is-active .el-radio-button__inner {
          background-color: #3366cc !important;
          color: #ffffff !important;
          border-color: #3366cc !important;
        }

        /* Element Plus 按钮组件增强 */
        .el-button {
          --el-button-bg-color: #333333 !important;
          --el-button-text-color: #e0e0e0 !important;
          --el-button-border-color: #444444 !important;
          transition: all 0.2s ease !important;
        }

        .el-button:hover {
          --el-button-bg-color: #444444 !important;
          --el-button-border-color: #555555 !important;
        }

        .el-button:active {
          --el-button-bg-color: #222222 !important;
          --el-button-border-color: #333333 !important;
        }

        .el-button.is-disabled {
          --el-button-bg-color: #2d2d2d !important;
          --el-button-text-color: #666666 !important;
          --el-button-border-color: #444444 !important;
          cursor: not-allowed !important;
        }

        .el-button--primary {
          --el-button-bg-color: #3366cc !important;
          --el-button-text-color: #ffffff !important;
          --el-button-border-color: #3366cc !important;
        }

        .el-button--primary:hover {
          --el-button-bg-color: #4477dd !important;
          --el-button-border-color: #4477dd !important;
        }

        .el-button--primary:active {
          --el-button-bg-color: #2255bb !important;
          --el-button-border-color: #2255bb !important;
        }

        /* Element Plus 输入框聚焦状态 */
        .el-input__wrapper.is-focus {
          border-color: #3366cc !important;
          box-shadow: 0 0 0 2px rgba(51, 102, 204, 0.2) !important;
        }
      }
</style>
