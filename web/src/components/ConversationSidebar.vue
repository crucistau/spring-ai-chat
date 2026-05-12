<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div class="brand-mark">智</div>
      <div>
        <p class="brand-title">对话工作台</p>
        <p class="brand-subtitle">记录、整理、继续思考</p>
      </div>
      <button class="new-chat-btn" @click="$emit('new-chat')" title="新对话">+</button>
    </div>

    <div v-if="!isLoggedIn" class="guest-panel">
      <p class="guest-title">访客模式</p>
      <p>你可以先查看页面。发送消息或查看历史时再登录。</p>
      <button class="login-btn" @click="$emit('login-request')">登录账号</button>
    </div>

    <div v-else class="conversation-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        :class="['conv-item', { active: conv.id === activeId }]"
        @click="$emit('select', conv.id)"
      >
        <div class="conv-info">
          <div v-if="editingId === conv.id" class="rename-row">
            <input
              ref="renameInput"
              v-model="renameValue"
              @keyup.enter="confirmRename(conv.id)"
              @keyup.escape="cancelRename"
              @blur="confirmRename(conv.id)"
            />
          </div>
          <div v-else class="conv-title" :title="conv.title">{{ conv.title || '新对话' }}</div>
        </div>
        <div class="conv-actions" @click.stop>
          <button class="action-btn" title="重命名" @click="startRename(conv)">✎</button>
          <button class="action-btn delete-btn" title="删除" @click="handleDelete(conv.id)">×</button>
        </div>
      </div>
      <div v-if="conversations.length === 0" class="empty-list">暂无历史会话</div>
    </div>

    <div class="sidebar-footer">
      <div v-if="isLoggedIn" class="user-info">
        <span class="user-avatar">{{ username.charAt(0).toUpperCase() }}</span>
        <span class="user-name">{{ username }}</span>
      </div>
      <div v-else class="user-info muted">
        <span class="user-avatar">访</span>
        <span class="user-name">未登录</span>
      </div>
      <button v-if="isLoggedIn" class="logout-btn" @click="handleLogout" title="退出登录">退出</button>
      <button v-else class="logout-btn" @click="$emit('login-request')" title="登录">登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { deleteConversation, renameConversation } from '../api/chat'

defineProps({
  conversations: { type: Array, default: () => [] },
  activeId: { type: String, default: null },
  isLoggedIn: Boolean
})

const emit = defineEmits(['select', 'new-chat', 'deleted', 'renamed', 'logout', 'login-request'])

const username = computed(() => localStorage.getItem('username') || '用户')
const editingId = ref(null)
const renameValue = ref('')
const renameInput = ref(null)

function startRename(conv) {
  editingId.value = conv.id
  renameValue.value = conv.title || ''
  nextTick(() => {
    if (renameInput.value) renameInput.value[0]?.focus()
  })
}

async function confirmRename(id) {
  const title = renameValue.value.trim()
  if (title && editingId.value) {
    try {
      await renameConversation(id, title)
      emit('renamed')
    } catch (e) {
      console.error('Rename failed:', e)
    }
  }
  editingId.value = null
}

function cancelRename() {
  editingId.value = null
}

async function handleDelete(id) {
  if (!confirm('确定要删除这个对话吗？')) return
  try {
    await deleteConversation(id)
    emit('deleted', id)
  } catch (e) {
    console.error('Delete failed:', e)
  }
}

function handleLogout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  emit('logout')
}
</script>

<style scoped>
.sidebar {
  width: 286px;
  min-width: 286px;
  background: rgba(255, 255, 255, 0.78);
  border-right: 1px solid rgba(45, 70, 60, 0.12);
  display: flex;
  flex-direction: column;
  height: 100vh;
  backdrop-filter: blur(18px);
}

.sidebar-header {
  padding: 18px;
  border-bottom: 1px solid var(--border);
  display: grid;
  grid-template-columns: 42px 1fr 38px;
  gap: 10px;
  align-items: center;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(145deg, var(--primary), #1b4336);
  font-weight: 800;
  box-shadow: 0 12px 30px rgba(42, 95, 78, 0.22);
}

.brand-title {
  color: var(--text-primary);
  font-size: 0.96rem;
  font-weight: 800;
}

.brand-subtitle {
  color: var(--text-secondary);
  font-size: 0.74rem;
  margin-top: 3px;
}

.new-chat-btn {
  width: 38px;
  height: 38px;
  background: var(--primary);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 1.3rem;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, background 0.2s;
}

.new-chat-btn:hover {
  background: var(--primary-strong);
  transform: translateY(-1px);
}

.guest-panel {
  margin: 14px;
  padding: 16px;
  border: 1px solid rgba(42, 95, 78, 0.12);
  border-radius: 16px;
  background: #fbfaf6;
  color: var(--text-secondary);
  font-size: 0.84rem;
  line-height: 1.6;
}

.guest-title {
  color: var(--text-primary);
  font-weight: 800;
  margin-bottom: 4px;
}

.login-btn {
  width: 100%;
  margin-top: 14px;
  padding: 10px;
  border: 1px solid rgba(42, 95, 78, 0.2);
  border-radius: 10px;
  background: #fff;
  color: var(--primary);
  cursor: pointer;
  font-weight: 700;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
  margin-bottom: 3px;
}

.conv-item:hover {
  background: rgba(42, 95, 78, 0.07);
}

.conv-item.active {
  background: #e8f1eb;
  box-shadow: inset 3px 0 0 var(--primary);
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 0.86rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
}

.rename-row input {
  width: 100%;
  border: 1px solid var(--primary);
  border-radius: 8px;
  padding: 3px 7px;
  font-size: 0.85rem;
  outline: none;
  font-family: inherit;
}

.conv-actions {
  display: none;
  gap: 4px;
  margin-left: 4px;
  flex-shrink: 0;
}

.conv-item:hover .conv-actions {
  display: flex;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.8rem;
  color: var(--text-secondary);
  padding: 2px 5px;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.action-btn:hover {
  background: rgba(42, 95, 78, 0.1);
}

.delete-btn:hover {
  color: #ef4444;
  background: #fef2f2;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: var(--primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 600;
  flex-shrink: 0;
}

.muted .user-avatar {
  background: #d8ded6;
  color: var(--text-secondary);
}

.user-name {
  font-size: 0.85rem;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 6px 12px;
  font-size: 0.8rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.logout-btn:hover {
  color: #ef4444;
  border-color: #ef4444;
}

.empty-list {
  color: var(--text-secondary);
  font-size: 0.84rem;
  padding: 16px 12px;
}

@media (max-width: 760px) {
  .sidebar {
    width: 100%;
    min-width: 0;
    height: auto;
    max-height: 42vh;
  }
}
</style>
