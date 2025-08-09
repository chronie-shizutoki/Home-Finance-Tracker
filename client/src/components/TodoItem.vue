<template>
  <div class="todo-item" :class="{ 'completed': todo.completed }">
    <div class="todo-content">
      <el-checkbox
        :model-value="todo.completed"
        @change="toggleTodo"
        class="todo-checkbox"
      />

      <div class="todo-text-container" @dblclick="startEdit" v-if="!isEditing">
        <span class="todo-text" :class="{ 'completed-text': todo.completed }">
          {{ todo.text }}
        </span>
      </div>

      <div class="todo-edit-container" v-else>
        <el-input
          v-model="editText"
          @blur="saveEdit"
          @keyup.enter="saveEdit"
          @keyup.esc="cancelEdit"
          ref="editInput"
          size="small"
          class="todo-edit-input"
        />
      </div>
    </div>

    <div class="todo-actions">
      <el-button
        type="text"
        size="small"
        @click="startEdit"
        :icon="Edit"
        class="action-btn edit-btn"
        v-if="!isEditing"
      />
      <el-button
        type="text"
        size="small"
        @click="deleteTodo"
        :icon="Delete"
        class="action-btn delete-btn"
        v-if="!isEditing"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { Edit, Delete } from '@element-plus/icons-vue';
import { useTodoStore } from '../stores/todo.js';

const props = defineProps({
  todo: {
    type: Object,
    required: true
  }
});

const todoStore = useTodoStore();
const isEditing = ref(false);
const editText = ref('');
const editInput = ref(null);

const toggleTodo = () => {
  todoStore.toggleTodo(props.todo.id);
};

const deleteTodo = () => {
  todoStore.deleteTodo(props.todo.id);
};

const startEdit = async () => {
  if (props.todo.completed) return; // 已完成的待办事项不允许编辑

  isEditing.value = true;
  editText.value = props.todo.text;

  await nextTick();
  if (editInput.value) {
    editInput.value.focus();
    editInput.value.select();
  }
};

const saveEdit = () => {
  if (editText.value.trim() && editText.value !== props.todo.text) {
    todoStore.editTodo(props.todo.id, editText.value);
  }
  isEditing.value = false;
};

const cancelEdit = () => {
  isEditing.value = false;
  editText.value = '';
};
</script>

<style scoped>
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 8px;
  background-color: #ffffff;
  transition: all 0.3s ease;
}

.todo-item:hover {
  border-color: #c0c4cc;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.todo-item.completed {
  background-color: #f5f7fa;
  border-color: #dcdfe6;
}

.todo-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.todo-checkbox {
  margin-right: 12px;
  flex-shrink: 0;
}

.todo-text-container {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.todo-text {
  word-break: break-word;
  line-height: 1.5;
  font-size: 14px;
  color: #303133;
}

.todo-text.completed-text {
  text-decoration: line-through;
  color: #909399;
}

.todo-edit-container {
  flex: 1;
  min-width: 0;
}

.todo-edit-input {
  width: 100%;
}

.todo-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
  flex-shrink: 0;
}

.action-btn {
  padding: 4px;
  min-width: auto;
  height: auto;
  opacity: 0.6;
  transition: opacity 0.3s ease;
}

.action-btn:hover {
  opacity: 1;
}

.edit-btn:hover {
  color: #409eff;
}

.delete-btn:hover {
  color: #f56c6c;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .todo-item {
    background-color: #333;
    border-color: #555;
  }

  .todo-item:hover {
    border-color: #666;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .todo-item.completed {
    background-color: #2a2a2a;
    border-color: #444;
  }

  .todo-text {
    color: #e0e0e0;
  }

  .todo-text.completed-text {
    color: #999;
  }

  .action-btn {
    color: #aaa;
  }

  .edit-btn:hover {
    color: #64b5f6;
  }

  .delete-btn:hover {
    color: #ef4444;
  }

  /* Element Plus 复选框组件深色模式适配 */
  :deep(.el-checkbox__inner) {
    background-color: #444;
    border-color: #666;
  }

  :deep(.el-checkbox__inner::after) {
    border-color: #e0e0e0;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background-color: #4073d2;
    border-color: #4073d2;
  }

  /* Element Plus 输入框组件深色模式适配 */
  :deep(.el-input__wrapper) {
    background-color: #444;
    border-color: #666;
  }

  :deep(.el-input__inner) {
    color: #e0e0e0;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .todo-item {
    padding: 10px 12px;
  }

  .todo-text {
    font-size: 13px;
  }

  .todo-actions {
    margin-left: 8px;
  }
}
</style>
