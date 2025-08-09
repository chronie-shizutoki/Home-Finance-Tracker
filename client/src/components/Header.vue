<template>
  <div :class="['header']">
    <h1>{{ title }}</h1>

    <!-- 新增反馈按钮 -->
    <ElDropdown trigger="click" class="notification-dropdown">
      <span class="notification-icon" role="button" aria-haspopup="true" aria-expanded="false" aria-label="通知">
        <ElBadge :value="unreadCount" v-if="unreadCount > 0" class="notification-badge">
          🔔
        </ElBadge>
        <span v-else>🔔</span>
      </span>
      <template #dropdown>
        <ElDropdownMenu>
          <div class="notification-header">
            <span>{{ t('header.notificationCenter') }}</span>
            <ElButton size="small" @click="markAllAsRead" v-if="unreadCount > 0">
              {{ t('header.markAllAsRead') }}
            </ElButton>
          </div>
          <ElDropdownItem v-for="notification in notifications.slice(0, 5)" :key="notification.id" class="notification-item">
            <span :class="{ 'unread-notification': !notification.read }">{{ truncateText(notification.content, 10) }}</span>
          </ElDropdownItem>
          <ElDropdownItem v-if="notifications.length === 0" disabled>
            {{ t('header.noNotifications') }}
          </ElDropdownItem>
          <div class="notification-divider"></div>
          <ElDropdownItem @click="showAllNotificationsModal = true">
            <i class="el-icon-menu"></i> {{ t('header.more') }}
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>

    <ElDropdown trigger="click">
      <span class="earth-icon" role="button" aria-haspopup="true" aria-expanded="false" aria-label="切换语言">🌍</span>

      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-for="lang in languages"
            :key="lang.code"
            @click="switchLanguage(lang.code)"
          >
            {{ lang.label }}
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>

  </div>

  <!-- 添加通知弹窗 -->
  <ElDialog v-model="showAddNotificationModal" :title="t('header.addNotificationTitle')" width="70%"
    ></ElDialog>

    <!-- 所有通知弹窗 -->
    <ElDialog v-model="showAllNotificationsModal" :title="t('header.allNotifications')" width="70%">
      <div class="all-notifications-container">
        <div class="notification-controls">
          <ElButton size="small" @click="markAllAsRead" v-if="unreadCount > 0">
            {{ t('header.markAllAsRead') }}
          </ElButton>
        </div>
        <div class="notification-list">
          <div v-for="notification in notifications" :key="notification.id" class="notification-item">
            <span :class="{ 'unread-notification': !notification.read }">{{ notification.content }}</span>
            <div class="notification-time">{{ formatNotificationTime(notification.id) }}</div>
          </div>
          <div v-if="notifications.length === 0" class="no-notifications">
            {{ t('header.noNotifications') }}
          </div>
        </div>
      </div>
    <ElInput
      v-model="newNotificationContent"
      :placeholder="t('header.addNotificationContent')"
      type="textarea"
      :rows="4"
    />
    <template #footer>
      <ElButton @click="showAddNotificationModal = false">{{ t('header.cancel') }}</ElButton>
      <ElButton type="primary" @click="addNotification">{{ t('header.confirmAdd') }}</ElButton>
    </template>
  </ElDialog>
</template>

<script setup>
// 恢复导入你原有的 useLanguageSwitch composable
import { useLanguageSwitch } from '@/composables/useLanguageSwitch';
import { computed, ref, onMounted } from 'vue';
// 导入 Element Plus 的下拉菜单相关组件（合并重复导入）
import {
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElButton,
  ElMessage,
  ElBadge,
  ElDialog,
  ElInput
} from 'element-plus';
import axios from 'axios';
import { useI18n } from 'vue-i18n';
defineOptions({ name: 'AppHeader' });
const { t } = useI18n();

// 定义组件接收的 props
defineProps({
  title: String // 接收一个字符串类型的标题
});

// 调用 useLanguageSwitch 获取语言切换函数
const { switchLanguage } = useLanguageSwitch();

// 定义支持的语言列表
const languages = [
  { code: 'en-US', label: 'English' },
  { code: 'zh-CL', label: '中文' }
];

// 通知系统相关数据和方法
const notifications = ref([]);

const unreadCount = computed(() => {
  if (!Array.isArray(notifications.value)) return 0;
  return notifications.value.filter(notification => !notification.read).length;
});

const markAllAsRead = async () => {
  notifications.value.forEach(notification => {
    notification.read = true;
  });
  await saveNotifications();
};

// 添加通知相关状态
const showAddNotificationModal = ref(false);
const showAllNotificationsModal = ref(false);
const newNotificationContent = ref('');

// 加载通知数据
const loadNotifications = async () => {
  try {
    const response = await axios.get('/api/json-files/notifications');
    if (response.data.success) {
      notifications.value = Array.isArray(response.data.data) ? response.data.data.map(notification => ({ ...notification, content: notification.content.replace(/\\n/g, '\n') })) : [];
    }
  } catch (error) {
    ElMessage.error(t('header.loadNotificationFailed') + ': ' + (error.response?.data?.error || error.message));
    console.error('加载通知错误:', error);
  }
};

// 保存通知数据
const saveNotifications = async () => {
  try {
    await axios.post('/api/json-files/notifications', notifications.value);
  } catch (error) {
    ElMessage.error(t('header.saveNotificationFailed') + ': ' + (error.response?.data?.error || error.message));
    console.error('保存通知错误:', error);
  }
};

// 组件挂载时加载通知
onMounted(() => {
  loadNotifications();
});

const truncateText = (text, maxLength) => {
  if (typeof text !== 'string' || text.length <= maxLength) return text;
  return text.slice(0, maxLength) + '...';
};

const formatNotificationTime = (timestamp) => {
  const date = new Date(timestamp);
  return date.toLocaleString();
};

const addNotification = async () => {
  if (newNotificationContent.value.trim()) {
    if (Array.isArray(notifications.value)) {
      notifications.value.push({
        id: Date.now(),
        content: newNotificationContent.value,
        read: false
      });
      newNotificationContent.value = '';
      showAddNotificationModal.value = false;
      await saveNotifications();
      ElMessage.success(t('header.addSuccess'));
    } else {
      ElMessage.warning(t('header.pleaseEnterNotificationContent'));
    }
  }
};
</script>

<style scoped>
/* 头部容器的基础样式 */
.header {
  display: flex; /* 使用 Flexbox 布局 */
  justify-content: space-between; /* 子元素两端对齐 */
  align-items: center; /* 子元素垂直居中 */
  padding: 1rem; /* 内边距 */
  background-color: transparent; /* 透明背景，显示樱花效果 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); /* 底部阴影 */
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed); /* 底部边框 */
  position: sticky; /* 粘性定位，使其在滚动时保持在顶部 */
  top: 0; /* 距离顶部0 */
  z-index: 100; /* 确保在其他内容之上 */
}

/* 标题样式 */
.header h1 {
  font-size: 1.8rem; /* 标题字体大小 */
  color: var(--el-text-color-primary, #303133); /* 标题文本颜色 */
  margin: 0; /* 移除默认外边距 */
  flex-grow: 1; /* 允许标题占据可用空间 */
  text-align: left; /* 文本左对齐 */
}

/* 地球图标样式 */
.earth-icon {
  font-size: 28px; /* 图标字体大小 */
  cursor: pointer; /* 鼠标悬停时显示手型光标 */
  transition: all 0.3s ease; /* 所有属性的过渡效果 */
  color: var(--el-color-primary, #409eff); /* 图标颜色，使用 Element Plus 主题色 */
  margin-left: 1rem; /* 左侧外边距 */
  display: flex; /* 使用 flex 布局确保图标居中 */
  align-items: center; /* 垂直居中 */
  justify-content: center; /* 水平居中 */
  width: 40px; /* 固定宽度 */
  height: 40px; /* 固定高度 */
  border-radius: 50%; /* 圆形 */
  background-color: var(--el-fill-color-light, #f5f5f5); /* 背景色 */
}

/* 地球图标悬停效果 */
.earth-icon:hover {
  color: var(--el-color-primary-light-3, #79bbff); /* 悬停时颜色变亮 */
  transform: scale(1.1); /* 悬停时放大 */
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.1); /* 添加阴影 */
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
  border-radius: var(--el-border-radius-base, 4px) !important;
  box-shadow: var(--el-box-shadow-light, 0 12px 32px 4px rgba(0, 0, 0, 0.04)) !important;
  padding: 4px 0 !important;
}

/* 修复1: 强制覆盖Element Plus的nowrap设置 */
.header :deep(.el-dropdown-menu .el-dropdown-menu__item),
.header :deep(.el-dropdown-menu .notification-item) {
  padding: 8px 16px !important;
  color: var(--dropdown-text) !important;
  transition: all 0.2s ease;
  font-size: var(--el-font-size-base, 14px);
  line-height: 1.5 !important; /* 增加行高 */
  white-space: normal !important; /* 强制覆盖nowrap */
  word-break: break-word !important; /* 确保长单词换行 */
  max-width: 320px; /* 增加最大宽度 */
}

.header :deep(.el-dropdown-menu__item:hover) {
  background: var(--dropdown-hover-bg) !important;
  color: var(--el-color-primary) !important;
  transform: translateX(4px);
}

/* 通知相关样式 */
.notification-icon {
  font-size: 28px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: var(--el-color-primary, #409eff);
  margin-left: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: var(--el-fill-color-light, #f5f5f5);
}

.notification-icon:hover {
  color: var(--el-color-primary-light-3, #79bbff);
  transform: scale(1.1);
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.1);
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
}

/* 修复2: 确保通知内容正确换行 */
/* 通知项内容 - 关键修复 */
 .header :deep(.notification-item span) {
   display: -webkit-box !important;
   -webkit-line-clamp: 2 !important;
   line-clamp: 2 !important;
   -webkit-box-orient: vertical !important;
   overflow: hidden !important;
   text-overflow: ellipsis !important;
   width: 100% !important;
   min-height: 1.5em !important;
   word-break: break-word !important;
 }

/* 修复3: 确保通知内容span正确处理换行 */
/* 未读通知样式 */
.unread-notification {
  font-weight: bold;
  position: relative;
  padding-left: 12px;
}

.unread-notification::before {
  content: "";
   position: absolute;
  left: 0;
  top: 50%;
   transform: translateY(-50%);
   width: 6px;
   height: 6px;
   border-radius: 50%;
   background: var(--el-color-primary);
 }

.unread-notification {
  font-weight: bold;
}

.notification-divider {
  height: 1px;
  background-color: var(--el-border-color-light, #e4e7ed);
  margin: 4px 0;
}

.notification-badge {
  --el-badge-size: 18px;
  position: relative;
  top: -10px;
  right: 10px;
}

/* 动画效果 */
/* Element Plus 下拉菜单的默认动画，通常不需要额外修改 */
/* 为了确保动画效果，这里保留了原始的动画相关样式 */
.header :deep(.el-dropdown-menu) {
  transform: translateY(-10px);
  opacity: 0;
  transition: transform 0.3s ease, opacity 0.3s ease;
  max-height: 400px; /* 最大高度 */
  overflow-y: auto; /* 超出部分添加滚动条 */
  overflow-wrap: break-word;
  width: auto !important; /* 允许根据内容调整宽度 */
  min-width: 260px !important; /* 添加最小宽度 */
  max-width: 320px !important; /* 增加最大宽度 */
}

.header :deep(.el-dropdown-menu.el-dropdown-menu--show) {
  transform: translateY(0);
  opacity: 1;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    padding: 0.8rem; /* 减小移动端内边距 */
  }

  .header h1 {
    font-size: 1.5rem; /* 减小移动端标题字体大小 */
  }

  .earth-icon {
    font-size: 24px; /* 减小移动端图标大小 */
    width: 36px;
    height: 36px;
  }
}

@media (max-width: 480px) {
  .header h1 {
    font-size: 1.3rem; /* 进一步减小极小屏幕的标题字体大小 */
  }

  /* 移动端下拉菜单宽度调整 */
  .header :deep(.el-dropdown-menu) {
    min-width: 200px !important;
    max-width: 90vw !important;
  }
}

/* 新增反馈按钮样式 */
.feedback-btn {
  margin-left: 1rem;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.feedback-btn i {
  margin-right: 8px;
}

/* 调整下拉菜单间距 */
.header :deep(.el-dropdown) {
  margin-left: 1rem;
}

/* 所有通知弹窗样式 */
.all-notifications-container {
  max-height: 500px;
  overflow-y: auto;
  padding: 10px;
}

.notification-controls {
  margin-bottom: 15px;
  display: flex;
  justify-content: flex-end;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notification-item {
  padding: 10px;
  border-radius: 4px;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  transition: all 0.2s ease;
}

.notification-item:hover {
  background-color: var(--el-fill-color-light);
}

.notification-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 5px;
  text-align: right;
}

.no-notifications {
  text-align: center;
  padding: 20px;
  color: var(--el-text-color-secondary);
}

/* 新增移动端反馈弹窗适配 */
@media (max-width: 768px) {
  .header :deep(.el-dialog) {
    width: 90% !important;  /* 弹窗宽度调整为屏幕90% */
    max-width: 100vw;       /* 最大宽度不超过视口宽度 */
    margin-top: 4vh !important;  /* 调整顶部间距 */
  }

  .header :deep(.el-dialog__body) {
    padding: 15px;  /* 减少内边距 */
  }

  .header :deep(.el-form-item__label) {
    font-size: 14px;  /* 缩小标签字号 */
  }

  .header :deep(.el-select),
  .header :deep(.el-textarea) {
    font-size: 14px;  /* 统一输入控件字号 */
  }

  .header :deep(.el-dialog__footer) {
    padding: 10px 15px;  /* 调整底部按钮区域间距 */
    flex-direction: column;  /* 垂直排列按钮 */
    gap: 8px;  /* 按钮间距 */
  }

  .header :deep(.el-dialog__footer button) {
    width: 100%;  /* 按钮全宽显示 */
    margin: 0 !important;  /* 移除默认边距 */
  }
}

@media (max-width: 480px) {
  .header :deep(.el-dialog__header) {
    padding: 15px;  /* 缩小头部间距 */
  }

  .header :deep(.el-dialog__title) {
    font-size: 16px;  /* 调整标题字号 */
  }

  .header :deep(.el-form-item) {
    margin-bottom: 12px;  /* 减少表单项间距 */
  }
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .header {
    background-color: #1e1e1e;
    border-bottom: 1px solid #333;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .header h1 {
    color: #e0e0e0;
  }

  .earth-icon,
  .notification-icon {
    color: #79bbff;
    background-color: #333;
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

  .notification-header {
    border-bottom: 1px solid #555;
  }

  .notification-divider {
    background-color: #555;
  }

  .notification-item {
    background-color: #333;
    border: 1px solid #555;
  }

  .notification-item:hover {
    background-color: #444;
  }

  .notification-time {
    color: #aaa;
  }

  .no-notifications {
    color: #aaa;
  }

  .header :deep(.el-dialog) {
    --el-bg-color: #333;
    --el-text-color-primary: #e0e0e0;
    --el-border-color: #555;
  }

  .header :deep(.el-button) {
    --el-button-bg-color: #444;
    --el-button-text-color: #e0e0e0;
    --el-button-border-color: #555;
    --el-button-hover-bg-color: #555;
    --el-button-hover-text-color: #fff;
    --el-button-hover-border-color: #666;
  }

  .header :deep(.el-input__inner) {
    --el-input-bg-color: #444;
    --el-input-text-color: #e0e0e0;
    --el-input-border-color: #555;
  }
}

</style>
