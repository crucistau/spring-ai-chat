<template>
  <div :class="['message-bubble', message.role]">
    <div class="avatar">
      {{ message.role === 'user' ? '我' : '助' }}
    </div>
    <div class="content">
      <div v-if="message.role === 'assistant'" v-html="renderedContent"></div>
      <div v-else>{{ message.content }}</div>
      <span v-if="message.createdAt" class="timestamp">{{ formatTime(message.createdAt) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const props = defineProps({
  message: { type: Object, required: true }
})

const md = new MarkdownIt({
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch (_) {
        // Ignore highlighting errors and fall back to escaped text.
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

const renderedContent = computed(() => {
  if (!props.message.content) return '<span class="typing-indicator">...</span>'
  return md.render(props.message.content)
})

function formatTime(dateStr) {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay < 7) return `${diffDay}天前`
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message-bubble.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-bubble.assistant {
  align-self: flex-start;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
}

.user .avatar {
  background: var(--bg-user);
  color: var(--text-user);
}

.assistant .avatar {
  background: var(--bg-assistant);
  color: var(--text-assistant);
  border: 1px solid rgba(45, 70, 60, 0.08);
}

.content {
  padding: 12px 15px;
  border-radius: 16px;
  font-size: 0.9rem;
  line-height: 1.6;
  word-break: break-word;
}

.user .content {
  background: var(--bg-user);
  color: var(--text-user);
  border-bottom-right-radius: 6px;
  box-shadow: 0 12px 28px rgba(42, 95, 78, 0.14);
}

.assistant .content {
  background: var(--bg-assistant);
  color: var(--text-assistant);
  border-bottom-left-radius: 6px;
  border: 1px solid rgba(45, 70, 60, 0.08);
}

.content :deep(pre) {
  background: #1e293b;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.content :deep(code) {
  font-size: 0.85rem;
}

.content :deep(p) {
  margin: 4px 0;
}

.timestamp {
  display: block;
  font-size: 0.7rem;
  color: var(--text-secondary, #999);
  margin-top: 4px;
  text-align: right;
}

.user .timestamp {
  text-align: left;
}

.typing-indicator {
  color: var(--text-secondary);
}
</style>
