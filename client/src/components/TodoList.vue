<template>
  <div class="todo-list-container">
    <div class="todo-header">
      <h2 class="todo-title">{{ $t('todo.title') }}</h2>
      <div class="todo-stats">
        <el-tag type="info" size="small">
          {{ $t('todo.total') }}: {{ todoStore.totalTodos }}
        </el-tag>
        <el-tag type="success" size="small">
          {{ $t('todo.completed') }}: {{ todoStore.completedCount }}
        </el-tag>
        <el-tag type="warning" size="small">
          {{ $t('todo.pending') }}: {{ todoStore.pendingTodos.length }}
        </el-tag>
      </div>
    </div>

    <!-- 添加新待办事项 -->
    <div class="todo-input-section">
      <el-input
        v-model="newTodoText"
        :placeholder="$t('todo.placeholder')"
        @keyup.enter="addTodo"
        size="large"
        class="todo-input"
      >
        <template #append>
          <el-button
            @click="addTodo"
            type="primary"
            :icon="Plus"
            :disabled="!newTodoText.trim()"
          >
            {{ $t('todo.add') }}
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- 过滤器 -->
    <div class="todo-filters">
      <el-radio-group v-model="currentFilter" size="small">
        <el-radio-button label="all">{{ $t('todo.filter.all') }}</el-radio-button>
        <el-radio-button label="pending">{{ $t('todo.filter.pending') }}</el-radio-button>
        <el-radio-button label="completed">{{ $t('todo.filter.completed') }}</el-radio-button>
      </el-radio-group>

      <el-button
        v-if="todoStore.completedCount > 0"
        @click="clearCompleted"
        size="small"
        type="danger"
        plain
        :icon="Delete"
        class="clear-completed-btn"
      >
        {{ $t('todo.clearCompleted') }}
      </el-button>
    </div>

    <!-- 待办事项列表 -->
    <div class="todo-list" v-if="filteredTodos.length > 0">
      <TransitionGroup name="todo-list" tag="div">
        <TodoItem
          v-for="todo in filteredTodos"
          :key="todo.id"
          :todo="todo"
        />
      </TransitionGroup>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <el-empty :description="getEmptyDescription()">
        <template #image>
          <el-icon size="64" color="#c0c4cc">
            <Document />
          </el-icon>
        </template>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { useTodoStore } from '../stores/todo.js';
import { useI18n } from 'vue-i18n';
import TodoItem from './TodoItem.vue';

const { t } = useI18n();
const todoStore = useTodoStore();

const newTodoText = ref('');
const currentFilter = ref('all');

// 计算过滤后的待办事项
const filteredTodos = computed(() => {
  switch (currentFilter.value) {
  case 'pending':
    return todoStore.pendingTodos;
  case 'completed':
    return todoStore.completedTodos;
  default:
    return todoStore.todos;
  }
});

// 添加新待办事项
const addTodo = () => {
  if (newTodoText.value.trim()) {
    todoStore.addTodo(newTodoText.value);
    newTodoText.value = '';
  }
};

// 清除已完成的待办事项
const clearCompleted = () => {
  ElMessageBox.confirm(
    t('todo.confirmClearCompleted'),
    t('todo.warning'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    }
  ).then(() => {
    todoStore.clearCompleted();
    ElMessage.success(t('todo.clearedSuccess'));
  }).catch(() => {
    // 用户取消操作
  });
};

// 获取空状态描述
const getEmptyDescription = () => {
  switch (currentFilter.value) {
  case 'pending':
    return t('todo.empty.pending');
  case 'completed':
    return t('todo.empty.completed');
  default:
    return t('todo.empty.all');
  }
};

// 组件挂载时加载待办事项
onMounted(() => {
  todoStore.loadTodos();
});
</script>

<style scoped>
.todo-list-container {
  max-width: 75%;
  margin: 0 auto;
  padding: 20px;
}

.todo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.todo-title {
  margin: 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.todo-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.todo-input-section {
  margin-bottom: 20px;
}

.todo-input {
  width: 100%;
}

.todo-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.clear-completed-btn {
  margin-left: auto;
}

.todo-list {
  min-height: 200px;
}

.empty-state {
  margin-top: 40px;
}

/* 过渡动画 */
.todo-list-enter-active,
.todo-list-leave-active {
  transition: all 0.3s ease;
}

.todo-list-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.todo-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.todo-list-move {
  transition: transform 0.3s ease;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .todo-list-container {
    background-color: rgba(30, 30, 30, 0.7);
    border-radius: 8px;
  }

  .todo-title {
    color: #e0e0e0;
  }

  .todo-stats .el-tag {
    background-color: #333;
    color: #e0e0e0;
    border-color: #555;
  }

  .todo-filters .el-radio-button__inner {
    background-color: #333;
    color: #e0e0e0;
    border-color: #555;
  }

  .todo-filters .el-radio-button__inner:hover {
    background-color: #444;
  }

  .todo-filters .el-radio-button.is-active .el-radio-button__inner {
    background-color: #4073d2;
    color: white;
    border-color: #4073d2;
  }

  .clear-completed-btn {
    --el-button-bg-color: #333;
    --el-button-text-color: #e0e0e0;
    --el-button-border-color: #555;
  }

  .empty-state .el-empty__description {
    color: #999;
  }

  /* Element Plus 输入框组件深色模式适配 */
  :deep(.el-input__wrapper) {
    background-color: #333;
    border-color: #555;
  }

  :deep(.el-input__inner) {
    color: #e0e0e0;
  }
  :deep(.el-input-group__append) {
    background-color: #333;
  }
  /* Element Plus 按钮组件深色模式适配 */
  :deep(.el-button--primary) {
    --el-button-bg-color: #4073d2 !important;
    --el-button-text-color: white !important;
    --el-button-border-color: #4073d2 !important;
  }
}


/* 响应式设计 */
@media (max-width: 768px) {
  .todo-list-container {
    padding: 16px;
    max-width: 85%;
  }

  .todo-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .todo-stats {
    width: 100%;
    justify-content: flex-start;
  }

  .todo-filters {
    flex-direction: column;
    align-items: flex-start;
  }

  .clear-completed-btn {
    margin-left: 0;
    align-self: flex-end;
  }

  .todo-title {
    font-size: 20px;
  }
}

@media (max-width: 480px) {
  .todo-list-container {
    padding: 12px;
  }

  .todo-stats {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
