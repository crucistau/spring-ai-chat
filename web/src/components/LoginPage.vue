<template>
  <div :class="['auth-page', { 'panel-mode': panelMode }]">
    <div class="auth-card">
      <button v-if="panelMode" class="close-btn" type="button" @click="$emit('cancel')" aria-label="关闭">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M1 1l12 12M13 1L1 13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
      </button>
      <div class="auth-header">
        <div class="auth-logo">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="8" fill="var(--primary)"/>
            <path d="M8 14h12M14 8v12" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
        </div>
        <p class="auth-kicker">账号访问</p>
        <h2 class="auth-title">{{ isLogin ? '登录后开始对话' : '创建账号' }}</h2>
        <p class="auth-subtitle">浏览页面无需登录，发送消息时需要账号来保存会话。</p>
      </div>

      <form @submit.prevent="handleSubmit">
        <div class="field-group">
          <label class="field-label">用户名</label>
          <div class="input-wrap" :class="{ focused: focusUsername, 'has-value': username }">
            <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.8"/>
              <path d="M4 20c0-3.3 3.6-6 8-6s8 2.7 8 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
            <input
              v-model="username"
              type="text"
              placeholder="请输入用户名"
              required
              :minlength="2"
              :maxlength="20"
              autocomplete="username"
              @focus="focusUsername = true"
              @blur="focusUsername = false"
            />
          </div>
        </div>

        <div class="field-group">
          <label class="field-label">密码</label>
          <div class="input-wrap" :class="{ focused: focusPassword, 'has-value': password }">
            <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none">
              <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8"/>
              <path d="M8 11V8a4 4 0 118 0v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              required
              :minlength="6"
              autocomplete="current-password"
              @focus="focusPassword = true"
              @blur="focusPassword = false"
            />
            <button
              type="button"
              class="toggle-pwd"
              @click="showPassword = !showPassword"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              tabindex="-1"
            >
              <svg v-if="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" stroke="currentColor" stroke-width="1.8"/>
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8"/>
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19M1 1l22 22" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </div>

        <p v-if="error" class="error-msg">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="#ef4444" stroke-width="2"/>
            <path d="M12 8v4M12 16h.01" stroke="#ef4444" stroke-width="2" stroke-linecap="round"/>
          </svg>
          {{ error }}
        </p>

        <button type="submit" class="submit-btn" :disabled="submitting">
          <span v-if="submitting" class="spinner"></span>
          {{ submitting ? '处理中...' : (isLogin ? '登录' : '注册') }}
        </button>
      </form>

      <div class="divider">
        <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
      </div>

      <button class="switch-btn" @click="toggleMode">
        {{ isLogin ? '立即注册' : '返回登录' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login, register } from '../api/auth'

const emit = defineEmits(['success', 'cancel'])
defineProps({
  panelMode: Boolean
})

const isLogin = ref(true)
const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)
const showPassword = ref(false)
const focusUsername = ref(false)
const focusPassword = ref(false)

function toggleMode() {
  isLogin.value = !isLogin.value
  error.value = ''
}

async function handleSubmit() {
  error.value = ''
  submitting.value = true
  try {
    const data = isLogin.value
      ? await login(username.value, password.value)
      : await register(username.value, password.value)

    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    emit('success', data)
  } catch (err) {
    error.value = err.message || (isLogin.value ? '登录失败' : '注册失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background:
    radial-gradient(circle at 20% 20%, rgba(226, 180, 90, 0.18), transparent 26rem),
    var(--bg-main);
}

.auth-page.panel-mode {
  height: auto;
  background: transparent;
}

.auth-card {
  background: var(--bg-chat);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 36px 32px 32px;
  width: min(420px, calc(100vw - 32px));
  box-shadow: var(--shadow-lg);
  position: relative;
  animation: cardIn 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.97);
  }
}

.close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: #fff;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-main);
  color: var(--text-primary);
  transform: rotate(90deg);
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;
}

.auth-logo {
  display: inline-flex;
  margin-bottom: 16px;
}

.auth-kicker {
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 6px;
}

.auth-title {
  color: var(--text-primary);
  font-size: 1.4rem;
  font-weight: 800;
  margin-bottom: 6px;
}

.auth-subtitle {
  color: var(--text-secondary);
  font-size: 0.85rem;
  line-height: 1.6;
}

.field-group {
  margin-bottom: 18px;
}

.field-label {
  display: block;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  letter-spacing: 0.01em;
}

.input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fbfaf6;
  border: 1.5px solid var(--border);
  border-radius: 12px;
  padding: 0 14px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.input-wrap.focused {
  border-color: var(--primary);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(42, 95, 78, 0.1);
}

.input-icon {
  flex-shrink: 0;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.input-wrap.focused .input-icon {
  color: var(--primary);
}

.input-wrap input {
  flex: 1;
  width: 100%;
  padding: 12px 0;
  border: none;
  outline: none;
  font-size: 0.9rem;
  color: var(--text-primary);
  background: transparent;
  font-family: inherit;
}

.input-wrap input::placeholder {
  color: #b0a99a;
}

.toggle-pwd {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}

.toggle-pwd:hover {
  color: var(--primary);
  background: rgba(42, 95, 78, 0.06);
}

.error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #dc2626;
  font-size: 0.84rem;
  margin-bottom: 14px;
  padding: 10px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  animation: shake 0.35s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-4px); }
  40%, 80% { transform: translateX(4px); }
}

.submit-btn {
  width: 100%;
  padding: 13px;
  background: var(--primary);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 4px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--primary-strong);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(42, 95, 78, 0.25);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: none;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.divider {
  text-align: center;
  margin: 20px 0 14px;
  position: relative;
}

.divider::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  background: var(--border);
}

.divider span {
  position: relative;
  background: var(--bg-chat);
  padding: 0 14px;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.switch-btn {
  width: 100%;
  padding: 11px;
  background: transparent;
  color: var(--primary);
  border: 1.5px solid var(--primary);
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.switch-btn:hover {
  background: rgba(42, 95, 78, 0.06);
}
</style>
