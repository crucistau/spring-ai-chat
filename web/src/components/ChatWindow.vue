<template>
  <div class="chat-window">
    <div class="messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-card">
          <p class="empty-title">开始一段新对话</p>
          <p>你可以先浏览界面；发送消息时会提示登录并保存会话。</p>
        </div>
      </div>
      <MessageBubble
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
      />
    </div>

    <p v-if="notice" class="notice">{{ notice }}</p>
    <ChatInput @send="handleSend" :disabled="loading" :is-logged-in="isLoggedIn" />
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'
import { appendMessageContent } from './chatMessages'
import { sendMessageStream, getMessages } from '../api/chat'

const props = defineProps({
  conversationId: { type: String, default: null },
  isLoggedIn: Boolean
})

const emit = defineEmits(['conversation-created', 'login-required'])
const messages = ref([])
const messagesContainer = ref(null)
const loading = ref(false)
const notice = ref('')

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

async function loadHistory(conversationId) {
  if (!conversationId) {
    messages.value = []
    return
  }
  try {
    const data = await getMessages(conversationId)
    messages.value = data.map(m => ({
      id: m.id,
      role: m.role,
      content: m.content
    }))
    scrollToBottom()
  } catch (err) {
    console.error('Load history failed:', err)
  }
}

watch(() => props.conversationId, (newId) => {
  loadHistory(newId)
}, { immediate: true })

async function handleSend(text) {
  notice.value = ''
  if (!props.isLoggedIn) {
    notice.value = '请先登录后再发送消息。'
    emit('login-required')
    return
  }

  const userMsg = {
    id: Date.now(),
    role: 'user',
    content: text
  }
  messages.value.push(userMsg)
  scrollToBottom()

  const assistantMessageId = Date.now() + 1
  messages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: ''
  })
  loading.value = true

  try {
    await sendMessageStream(text, props.conversationId, (chunk) => {
      messages.value = appendMessageContent(messages.value, assistantMessageId, chunk)
      scrollToBottom()
    })

    if (!props.conversationId) {
      emit('conversation-created')
    }
  } catch (err) {
    messages.value = appendMessageContent(
      messages.value,
      assistantMessageId,
      err.message || '请求失败，请检查后端服务是否启动。'
    )
    console.error('Chat error:', err)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function clearMessages() {
  messages.value = []
}

defineExpose({ clearMessages, loadHistory })
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 22px 0 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 0.95rem;
  text-align: center;
}

.empty-card {
  max-width: 420px;
  padding: 28px;
  border: 1px dashed rgba(42, 95, 78, 0.22);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.56);
}

.empty-title {
  color: var(--text-primary);
  font-size: 1.05rem;
  font-weight: 800;
  margin-bottom: 8px;
}

.notice {
  margin: 0 0 10px;
  color: #9a3412;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  padding: 9px 12px;
  font-size: 0.86rem;
}
</style>
