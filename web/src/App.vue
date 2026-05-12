<template>
  <div class="app-container">
    <ConversationSidebar
      :conversations="conversations"
      :active-id="activeConversationId"
      :is-logged-in="isLoggedIn"
      @select="selectConversation"
      @new-chat="newChat"
      @deleted="onDeleted"
      @renamed="loadConversations"
      @login-request="openLogin"
      @logout="onLogout"
    />

    <div class="main-area">
      <header class="app-header">
        <div>
          <p class="eyebrow">Workspace</p>
          <h1>智能对话助手</h1>
        </div>
        <span class="subtitle">{{ isLoggedIn ? '会话已同步' : '访客模式浏览，发送消息前登录' }}</span>
      </header>

      <ChatWindow
        ref="chatWindowRef"
        :conversation-id="activeConversationId"
        :is-logged-in="isLoggedIn"
        @conversation-created="onConversationCreated"
        @login-required="openLogin"
      />
    </div>

    <div v-if="showLogin" class="auth-overlay" @click.self="closeLogin">
      <LoginPage panel-mode @success="onLoginSuccess" @cancel="closeLogin" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import LoginPage from './components/LoginPage.vue'
import ChatWindow from './components/ChatWindow.vue'
import ConversationSidebar from './components/ConversationSidebar.vue'
import { listConversations } from './api/chat'

const isLoggedIn = ref(!!localStorage.getItem('token'))
const showLogin = ref(false)
const conversations = ref([])
const activeConversationId = ref(null)
const chatWindowRef = ref(null)

onMounted(() => {
  if (isLoggedIn.value) {
    loadConversations()
  }
})

function onLoginSuccess() {
  isLoggedIn.value = true
  showLogin.value = false
  loadConversations()
}

function onLogout() {
  isLoggedIn.value = false
  conversations.value = []
  activeConversationId.value = null
  chatWindowRef.value?.clearMessages()
}

async function loadConversations() {
  if (!isLoggedIn.value) return

  try {
    conversations.value = await listConversations()
  } catch (err) {
    console.error('Load conversations failed:', err)
  }
}

function selectConversation(id) {
  if (!isLoggedIn.value) {
    openLogin()
    return
  }
  activeConversationId.value = id
}

function newChat() {
  activeConversationId.value = null
  chatWindowRef.value?.clearMessages()
}

async function onConversationCreated() {
  await loadConversations()
  if (conversations.value.length > 0 && !activeConversationId.value) {
    activeConversationId.value = conversations.value[0].id
  }
}

async function onDeleted(id) {
  if (activeConversationId.value === id) {
    activeConversationId.value = null
    chatWindowRef.value?.clearMessages()
  }
  await loadConversations()
}

function openLogin() {
  showLogin.value = true
}

function closeLogin() {
  showLogin.value = false
}
</script>

<style scoped>
.app-container {
  display: flex;
  height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(22, 163, 74, 0.12), transparent 32rem),
    linear-gradient(135deg, #f6f3ec 0%, #eef5f1 48%, #f7f8f4 100%);
  color: var(--text-primary);
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 980px;
  margin: 0 auto;
  padding: 0 28px;
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 0 18px;
  border-bottom: 1px solid var(--border);
}

.eyebrow {
  margin-bottom: 4px;
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
}

.app-header h1 {
  font-size: 1.55rem;
  font-weight: 800;
  color: var(--text-primary);
}

.subtitle {
  font-size: 0.8rem;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(45, 70, 60, 0.1);
  border-radius: 999px;
  padding: 7px 12px;
  white-space: nowrap;
}

.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(16, 24, 20, 0.42);
  backdrop-filter: blur(10px);
}

@media (max-width: 760px) {
  .app-container {
    flex-direction: column;
  }

  .main-area {
    padding: 0 16px;
    width: 100%;
  }

  .app-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
