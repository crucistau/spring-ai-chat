<template>
  <div class="chat-input">
    <div :class="['input-wrapper', { focused: isFocused }]">
      <textarea
        ref="inputRef"
        v-model="text"
        @keydown.enter.exact.prevent="submit"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @input="autoResize"
        :placeholder="isLoggedIn ? '输入消息，按 Enter 发送...' : '登录后即可发送消息...'"
        rows="1"
        :disabled="disabled"
      />
      <button
        @click="submit"
        :disabled="disabled || !text.trim()"
        class="send-btn"
        :class="{ active: text.trim() && !disabled }"
        aria-label="发送"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
    <p class="hint">Enter 发送 · Shift+Enter 换行</p>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const emit = defineEmits(['send'])
const props = defineProps({
  disabled: Boolean,
  isLoggedIn: Boolean
})

const text = ref('')
const inputRef = ref(null)
const isFocused = ref(false)

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 140) + 'px'
}

function submit() {
  const trimmed = text.value.trim()
  if (!trimmed) return
  emit('send', trimmed)
  if (!props.isLoggedIn) return
  text.value = ''
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
  })
}
</script>

<style scoped>
.chat-input {
  padding: 12px 0 8px;
}

.input-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  background: var(--bg-chat);
  border: 1.5px solid var(--border);
  border-radius: 16px;
  padding: 10px 10px 10px 16px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 12px rgba(38, 52, 45, 0.04);
}

.input-wrapper.focused {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(42, 95, 78, 0.08), 0 4px 20px rgba(38, 52, 45, 0.06);
}

textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 0.9rem;
  line-height: 1.55;
  font-family: inherit;
  max-height: 140px;
  color: var(--text-primary);
  background: transparent;
  padding: 4px 0;
}

textarea::placeholder {
  color: #b0a99a;
}

.send-btn {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--border);
  color: #999;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.send-btn.active {
  background: var(--primary);
  color: white;
  box-shadow: 0 4px 14px rgba(42, 95, 78, 0.3);
}

.send-btn.active:hover {
  background: var(--primary-strong);
  transform: scale(1.06);
}

.send-btn.active:active {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint {
  text-align: center;
  font-size: 0.72rem;
  color: var(--text-secondary);
  margin-top: 8px;
  opacity: 0.7;
}
</style>
