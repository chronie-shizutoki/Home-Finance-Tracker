<!-- ExpensePagination.vue -->
<template>
    <div class="pagination">
      <button @click.prevent.stop="$emit('page-change', 1)" :disabled="currentPage === 1" title="首页">
        &lt;&lt;
      </button>
      <button @click.prevent.stop="$emit('page-change', currentPage - 1)" :disabled="currentPage === 1" title="上一页">
        &lt;
      </button>

      <template v-for="page in visiblePages" :key="page">
        <button
          @click.prevent.stop="$emit('page-change', page)"
          :class="{ active: currentPage === page }"
          :title="`第 ${page} 页`"
          :disabled="page === '...'"
        >
          {{ page }}
        </button>
      </template>

      <button @click.prevent.stop="$emit('page-change', currentPage + 1)" :disabled="currentPage === totalPages" title="下一页">
        &gt;
      </button>
      <button @click.prevent.stop="$emit('page-change', totalPages)" :disabled="currentPage === totalPages" title="末页">
       &gt;&gt;
      </button>
    </div>
  </template>

<script>
export default {
  props: {
    currentPage: Number,
    totalPages: Number,
    visiblePages: Array
  }
};
</script>

  <style scoped>
  .pagination {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin-top: 20px;
  }

  .pagination button {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 6px;
    background-color: transparent;
    color: #495057;
    cursor: pointer;
    transition: all 0.2s ease;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

  .pagination button:hover:not(:disabled) {
    background-color: #4361ee;
    color: white;
  }

  .pagination button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .pagination button.active {
    background-color: #4361ee;
    color: white;
    font-weight: 600;
  }

  .pagination svg {
    width: 20px;
    height: 20px;
    fill: currentColor;
  }

  /* 深色模式适配 */
  @media (prefers-color-scheme: dark) {
    .pagination button {
      background-color: rgba(30, 30, 30, 0.5);
      color: #e5e7eb;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(75, 85, 99, 0.3);
    }

    .pagination button:hover:not(:disabled) {
      background-color: #4361ee;
      color: white;
    }

    .pagination button:disabled {
      background-color: rgba(30, 30, 30, 0.3);
      color: #6b7280;
    }

    .pagination button.active {
      background-color: #4361ee;
      color: white;
    }
  }
  </style>
