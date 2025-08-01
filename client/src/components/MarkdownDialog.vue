<template>
  <el-dialog
    v-model="dialogVisible"
    @close="handleClose"
    :title="title"
    :width="width"
    :modal="true"
    :close-on-click-modal="true"
    :center="true"
    :top="'10vh'"
  >
    <div class="markdown-content" v-html="renderedMarkdown"></div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { marked } from 'marked';
import hljs from 'highlight.js/lib/core';
import markdown from 'highlight.js/lib/languages/markdown';
import 'highlight.js/styles/github.css';
import mermaid from 'mermaid';

hljs.registerLanguage('markdown', markdown);

// 配置mermaid
console.log('初始化mermaid配置');
// 优化mermaid配置以提高兼容性
mermaid.initialize({
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'loose',
  flowchart: {
    useMaxWidth: true,
    htmlLabels: true
  },
  pie: {
    useMaxWidth: true
  },
  // 启用日志以帮助调试
  logLevel: 1
});

// 监听mermaid错误
mermaid.parseError = function(err, hash) {
  console.error('Mermaid解析错误:', err);
  console.error('错误详情:', JSON.stringify(err));
};

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    default: 'Markdown Viewer'
  },
  content: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '80%'
  }
});

const emit = defineEmits(['update:visible']);

// 内部状态管理与双向同步
const dialogVisible = ref(props.visible);

// 同步父组件状态到内部
watch(() => props.visible, (newVal) => {
  dialogVisible.value = newVal;
});

// 同步内部状态到父组件
watch(dialogVisible, (newVal) => {
  emit('update:visible', newVal);
});

const handleClose = () => {
  dialogVisible.value = false;
};

const { t } = useI18n();

// 配置 marked
// 创建自定义marked渲染器
const renderer = new marked.Renderer();
const originalCodeRenderer = renderer.code;

// 重写代码块渲染方法以支持mermaid
renderer.code = function(code, lang, escaped) {
  // 确保正确识别mermaid语言（不区分大小写）
  if (lang && lang.toLowerCase() === 'mermaid') {
    const id = 'mermaid-' + Math.random().toString(36).substring(2, 9);
    console.log(`生成mermaid元素: ${id}`);
    return `<div id="${id}" class="mermaid">${code}</div>`;
  }
  // 对于其他语言，使用原始的代码渲染器
  return originalCodeRenderer.call(this, code, lang, escaped);
};

// 配置marked选项
marked.setOptions({
  highlight: function (code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  },
  breaks: true,
  gfm: true,
  renderer: renderer
});

// 配置完成后初始化渲染
onMounted(() => {
  // 初始渲染mermaid图表
  renderMermaid();
});

const renderedMarkdown = computed(() => {
  const html = marked.parse(props.content);
  console.log('Marked解析后的HTML:', html);
  // 解析完成后渲染mermaid图表
  nextTick(() => {
    console.log('DOM更新后执行renderMermaid');
    renderMermaid();
  });
  return html;
});

// 渲染Mermaid图表
// 添加重试计数器
let renderAttempts = 0;
const MAX_RENDER_ATTEMPTS = 10;

const renderMermaid = () => {
  // 只有当弹窗可见时才执行渲染
  if (!dialogVisible.value) {
    console.log('弹窗不可见，跳过渲染');
    renderAttempts = 0; // 重置重试计数
    return;
  }

  console.log('开始渲染mermaid图表');
  renderAttempts++;
  
  // 直接检查组件内的markdown-content元素
  const contentEl = document.querySelector('.markdown-content');
  console.log('内容元素是否存在:', !!contentEl);
  if (contentEl) {
    console.log('内容元素HTML:', contentEl.innerHTML);
  }

  // 使用更具体的选择器查找mermaid元素
  const mermaidElements = document.querySelectorAll('.markdown-content .mermaid');
  console.log('找到的mermaid元素数量:', mermaidElements.length);

  if (mermaidElements.length === 0) {
    // 检查是否有其他可能的mermaid元素
    const allMermaidElements = document.querySelectorAll('.mermaid');
    console.log('页面上所有mermaid元素数量:', allMermaidElements.length);

    // 如果没有找到mermaid元素，可能是marked还没完成解析，稍后重试
    if (renderAttempts < MAX_RENDER_ATTEMPTS) {
      console.log(`未找到mermaid元素，100ms后重试 (${renderAttempts}/${MAX_RENDER_ATTEMPTS})`);
      setTimeout(renderMermaid, 100);
    } else {
      console.log(`达到最大重试次数(${MAX_RENDER_ATTEMPTS})，停止渲染尝试`);
      renderAttempts = 0; // 重置重试计数
    }
    return;
  } else {
    renderAttempts = 0; // 找到元素，重置重试计数
  }

  mermaidElements.forEach(el => {
    const code = el.textContent;
    const id = el.id;
    console.log(`渲染图表 ${id}:`, code);
    // 验证mermaid代码格式
    try {
      const parseResult = mermaid.parse(code);
      console.log(`图表 ${id} 解析结果:`, parseResult);
    } catch (e) {
      console.error(`图表 ${id} 解析错误:`, e);
    }
    // 使用异步渲染并增加超时处理
    mermaid.render(id, code, (svgCode, bindFunctions) => {
      if (svgCode) {
        console.log(`图表 ${id} 渲染成功，SVG代码长度: ${svgCode.length}`);
        el.innerHTML = svgCode;
        if (bindFunctions) {
          console.log(`图表 ${id} 绑定交互函数`);
          bindFunctions(el);
        }
      } else {
        console.error(`图表 ${id} 渲染失败: 未生成SVG代码`);
      }
    });
  });
};

// 监听内容变化重新渲染图表
watch(() => props.content, () => {
  renderMermaid();
});

</script>

<style scoped>
.markdown-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 16px;
  line-height: 1.6;
}

/* mermaid图表样式 */
.markdown-content .mermaid {
  text-align: center;
  margin: 16px 0;
}

/* 确保图表响应式 */
.markdown-content .mermaid svg {
  max-width: 100%;
  height: auto;
}

.markdown-content h1,
.markdown-content h2,
.markdown-content h3,
.markdown-content h4,
.markdown-content h5,
.markdown-content h6 {
  margin-top: 1.5em;
  margin-bottom: 0.5em;
}

.markdown-content p {
  margin-bottom: 1em;
}

.markdown-content pre {
  background-color: #f5f5f5;
  padding: 1em;
  border-radius: 4px;
  overflow-x: auto;
}

.markdown-content code {
  font-family: system-ui, sans-serif;
}

.markdown-content ul,
.markdown-content ol {
  margin-left: 2em;
  margin-bottom: 1em;
}
</style>
