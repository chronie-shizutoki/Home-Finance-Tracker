<template>
  <glass-dialog
    :visible="dialogVisible"
    @update:visible="handleClose"
    :title="title"
    :width="width"
  >
    <div class="markdown-content" v-html="renderedMarkdown"></div>
  </glass-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { marked } from 'marked';
import hljs from 'highlight.js/lib/core';
import markdown from 'highlight.js/lib/languages/markdown';
import 'highlight.js/styles/github.css';
import GlassDialog from './GlassDialog.vue';

hljs.registerLanguage('markdown', markdown);

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
  },
});

const emit = defineEmits(['update:visible']);

// Internal state management and bidirectional synchronization
const dialogVisible = ref(props.visible);

// Sync parent component state to internal
watch(() => props.visible, (newVal) => {
  dialogVisible.value = newVal;
});

// Sync internal state to parent component
watch(dialogVisible, (newVal) => {
  emit('update:visible', newVal);
});

const handleClose = (newVal) => {
  dialogVisible.value = newVal;
};

useI18n();

// Configure marked.js
// Create custom marked renderer
const renderer = new marked.Renderer();
const originalCodeRenderer = renderer.code;

// Use original code renderer
renderer.code = originalCodeRenderer;

// Configure marked options
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

const renderedMarkdown = computed(() => {
  return marked.parse(props.content);
});

</script>

<style scoped src="../styles/components/MarkdownDialog.css"></style>
