<template>
  <section class="chat-page">
    <header class="page-heading">
      <h1>{{ tr("投研顾问") }}</h1>
      <el-button
        v-if="isMobile"
        :icon="Fold"
        :aria-expanded="sidebarVisible"
        aria-controls="advisor-sessions"
        @click="sidebarVisible = !sidebarVisible"
      >{{ tr("对话列表") }}</el-button>
    </header>
    <div class="chat-view">
      <!-- Sidebar -->
      <transition name="sidebar-slide">
        <div id="advisor-sessions" v-show="sidebarVisible" class="chat-sidebar" :class="{ 'is-mobile': isMobile }">
          <div class="sidebar-header">
            <el-button type="primary" class="new-chat-btn" @click="createSession">
              <el-icon><Plus /></el-icon>
              <span>{{ tr("新建对话") }}</span>
            </el-button>
          </div>
          <div class="session-list">
            <div
              v-for="s in sessions"
              :key="s.id"
              class="session-item"
              :class="{ active: s.id === activeSessionId }"
              @click="selectSession(s)"
            >
              <div class="session-info">
                <div class="session-title">{{ tr(s.title || '新对话') }}</div>
                <div class="session-time">{{ formatSessionTime(s.updatedAt) }}</div>
              </div>
              <el-icon
                class="session-delete"
                @click.stop="deleteSession(s)"
              ><Delete /></el-icon>
            </div>
            <div v-if="sessions.length === 0" class="session-empty">{{ tr("暂无对话记录") }}</div>
          </div>
        </div>
      </transition>

      <!-- Mobile overlay -->
      <div
        v-if="isMobile && sidebarVisible"
        class="sidebar-overlay"
        @click="sidebarVisible = false"
      />

      <!-- Main chat area -->
      <div class="chat-main">
        <!-- Messages -->
        <div class="messages-area" ref="messagesRef">
          <div v-if="messages.length === 0 && !loading" class="empty-state">
            <div class="empty-icon">
              <el-icon :size="27"><ChatDotRound /></el-icon>
            </div>
            <p class="empty-text">{{ tr("开始和投研顾问聊聊吧") }}</p>
            <p class="empty-hint">{{ tr("可以问它关于持仓、行情、历史决策经验等问题") }}</p>
          </div>
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-bubble">
              <div
                v-if="msg.role === 'assistant'"
                class="message-content markdown-body"
                v-html="renderMarkdown(msg.content)"
              />
              <div v-else class="message-content">{{ msg.content }}</div>
              <div class="message-time">{{ formatMessageTime(msg.createdAt) }}</div>
            </div>
          </div>
          <!-- Thinking indicator -->
          <div v-if="thinking" class="message-row assistant">
            <div class="message-bubble thinking-bubble">
              <div class="thinking-indicator">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="thinking-text">{{ tr("思考中...") }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Input area -->
        <div class="input-area">
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              :placeholder="tr(&quot;输入消息，Enter 发送，Shift+Enter 换行&quot;)"
              :disabled="sending"
              resize="none"
              @keydown="onInputKeydown"
            />
            <el-button
              type="primary"
              class="send-btn"
              :disabled="!inputText.trim() || sending"
              :loading="sending"
              @click="sendMessage"
            >
              {{ sending ? tr("发送中") : tr("发送") }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { language, t as tr } from '../preferences.js'
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { Plus, Delete, ChatDotRound, Fold } from '@element-plus/icons-vue'
import { safeMarkdown } from '../markdown.js'
import { ElMessage, ElMessageBox } from 'element-plus'

// ===== State =====
const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const thinking = ref(false)
const loading = ref(false)
const messagesRef = ref(null)
const sidebarVisible = ref(true)
const isMobile = ref(false)

// ===== Markdown =====
function renderMarkdown(text) {
  if (!text) return ''
  return safeMarkdown(text)
}

// ===== Responsive =====
function checkMobile() {
  const mobile = window.innerWidth <= 820
  if (mobile === isMobile.value) return
  isMobile.value = mobile
  sidebarVisible.value = !mobile
}

// ===== Formatting =====
function formatSessionTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return d.toLocaleTimeString(language.value, { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString(language.value, { month: '2-digit', day: '2-digit' })
}

function formatMessageTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleTimeString(language.value, { hour: '2-digit', minute: '2-digit' })
}

// ===== Auto-scroll =====
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(messages, () => scrollToBottom(), { deep: true })
watch(thinking, () => scrollToBottom())

// ===== API: Sessions =====
async function loadSessions() {
  try {
    const res = await fetch('/api/chat/sessions')
    if (res.ok) {
      const data = await res.json()
      sessions.value = data.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
    }
  } catch (e) {
    console.warn('Failed to load sessions:', e)
  }
}

async function createSession() {
  try {
    const res = await fetch('/api/chat/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: tr("新对话") })
    })
    if (res.ok) {
      const session = await res.json()
      sessions.value.unshift(session)
      await selectSession(session)
      if (isMobile.value) sidebarVisible.value = false
    }
  } catch (e) {
    ElMessage.error(tr("创建对话失败"))
  }
}

async function selectSession(session) {
  activeSessionId.value = session.id
  messages.value = []
  loading.value = true
  if (isMobile.value) sidebarVisible.value = false
  try {
    const res = await fetch(`/api/chat/sessions/${session.id}/messages`)
    if (res.ok) {
      messages.value = await res.json()
      scrollToBottom()
    }
  } catch (e) {
    console.warn('Failed to load messages:', e)
  } finally {
    loading.value = false
  }
}

async function deleteSession(session) {
  try {
    await ElMessageBox.confirm(tr("确定删除对话「{0}」？", { 0: session.title || '新对话' }), tr("确认删除"), {
      type: 'warning'
    })
    const res = await fetch(`/api/chat/sessions/${session.id}`, { method: 'DELETE' })
    if (res.ok) {
      sessions.value = sessions.value.filter(s => s.id !== session.id)
      if (activeSessionId.value === session.id) {
        activeSessionId.value = null
        messages.value = []
      }
      ElMessage.success(tr("已删除"))
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(tr("删除失败"))
    }
  }
}

// ===== API: Send Message (SSE) =====
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  // Ensure active session
  if (!activeSessionId.value) {
    await createSession()
    if (!activeSessionId.value) return
  }

  // Add user message to UI immediately
  messages.value.push({
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  })
  inputText.value = ''
  sending.value = true
  thinking.value = true
  scrollToBottom()

  // Prepare assistant message for streaming
  let assistantContent = ''
  const assistantMsg = {
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString()
  }

  try {
    const response = await fetch(`/api/chat/sessions/${activeSessionId.value}/messages/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: text })
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEventName = ''
    let messageAdded = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const rawData = line.slice(5).trim()
          if (!rawData) continue

          if (currentEventName === 'start') {
            thinking.value = true
          } else if (currentEventName === 'message') {
            // Stream text chunks
            thinking.value = false
            assistantContent += rawData
            if (!messageAdded) {
              messages.value.push(assistantMsg)
              messageAdded = true
            }
            assistantMsg.content = assistantContent
            scrollToBottom()
          } else if (currentEventName === 'done') {
            thinking.value = false
            // Refresh session list to update title/time
            loadSessions()
          } else if (currentEventName === 'error') {
            thinking.value = false
            try {
              const errData = JSON.parse(rawData)
              ElMessage.error(errData.message || tr("回复出错"))
            } catch {
              ElMessage.error(tr("回复出错"))
            }
          } else {
            // Try JSON parse for structured data
            try {
              const parsed = JSON.parse(rawData)
              if (parsed.type === 'start') {
                thinking.value = true
              } else if (parsed.type === 'message' || parsed.content) {
                thinking.value = false
                const chunk = parsed.content || parsed.data || ''
                assistantContent += chunk
                if (!messageAdded) {
                  messages.value.push(assistantMsg)
                  messageAdded = true
                }
                assistantMsg.content = assistantContent
                scrollToBottom()
              } else if (parsed.type === 'done') {
                thinking.value = false
                loadSessions()
              } else if (parsed.type === 'error') {
                thinking.value = false
                ElMessage.error(parsed.message || tr("回复出错"))
              }
            } catch {
              // Plain text data chunk
              thinking.value = false
              assistantContent += rawData
              if (!messageAdded) {
                messages.value.push(assistantMsg)
                messageAdded = true
              }
              assistantMsg.content = assistantContent
              scrollToBottom()
            }
          }
          currentEventName = ''
        }
      }
    }
  } catch (e) {
    console.error('Send message failed:', e)
    ElMessage.error(tr("发送失败: ") + e.message)
    // Remove empty assistant message if nothing was received
    if (assistantMsg.content === '') {
      const idx = messages.value.indexOf(assistantMsg)
      if (idx >= 0) messages.value.splice(idx, 1)
    }
  } finally {
    sending.value = false
    thinking.value = false
  }
}

// ===== Input handling =====
function onInputKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// ===== Lifecycle =====
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  loadSessions()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.chat-page { display: grid; gap: 18px; min-width: 0; }
.chat-view {
  display: flex;
  height: max(450px, calc(100dvh - var(--workspace-bar-height) - 145px));
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

/* ===== Sidebar ===== */
.chat-sidebar {
  width: 232px;
  min-width: 232px;
  background: var(--surface-2);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 14px;
  border-bottom: 1px solid var(--border);
}

.new-chat-btn {
  width: 100%;
  font-weight: 500;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  border: 1px solid transparent;
  margin-bottom: 4px;
}

.session-item:hover {
  background: var(--panel);
}

.session-item.active {
  background: var(--accent-soft);
  border-color: var(--accent-border);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 11px;
  color: var(--muted);
  margin-top: 2px;
}

.session-delete {
  color: var(--muted);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s, color 0.2s;
  cursor: pointer;
  flex-shrink: 0;
}

.session-item:hover .session-delete, .session-item:focus-within .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: var(--negative);
}

.session-empty {
  text-align: center;
  color: var(--muted);
  font-size: 13px;
  padding: 32px 16px;
}

/* ===== Main Chat Area ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--surface);
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ===== Empty State ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 55px;
  height: 55px;
  border-radius: 14px;
  background: var(--accent-soft);
  color: var(--accent);
  margin-bottom: 4px;
}

.empty-text {
  font-size: 17px;
  font-weight: 600;
  color: var(--text);
}

.empty-hint {
  font-size: 13px;
  color: var(--muted);
}

/* ===== Message Bubbles ===== */
.message-row {
  display: flex;
  max-width: 100%;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
}

.message-row.user .message-bubble {
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  border-bottom-right-radius: 4px;
}

.message-row.assistant .message-bubble {
  background: var(--surface);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.message-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
  word-break: break-word;
}

.message-time {
  font-size: 11px;
  color: var(--muted);
  margin-top: 6px;
  text-align: right;
}

/* ===== Markdown in assistant messages ===== */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px;
  color: var(--text);
  font-weight: 600;
}

.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.markdown-body :deep(li) {
  margin: 3px 0;
}

.markdown-body :deep(code) {
  background: var(--surface-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--accent);
}

.markdown-body :deep(pre) {
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px 16px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-2);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  font-size: 13px;
}

.markdown-body :deep(th) {
  background: var(--surface-2);
  font-weight: 600;
  color: var(--text-3);
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--text-3);
  background: var(--accent-soft);
  border-radius: 0 6px 6px 0;
}

.markdown-body :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(strong) {
  color: var(--text);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 12px 0;
}

/* ===== Thinking Indicator ===== */
.thinking-bubble {
  background: var(--surface);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: dot-bounce 1.4s ease-in-out infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dot-bounce {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1.1);
  }
}

.thinking-text {
  font-size: 13px;
  color: var(--text-3);
  margin-left: 6px;
}

/* ===== Input Area ===== */
.input-area {
  padding: 16px 24px;
  border-top: 1px solid var(--border);
  background: var(--surface-2);
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea) {
  flex: 1;
}

.send-btn {
  min-width: 80px;
  height: 40px;
  font-weight: 500;
  flex-shrink: 0;
}

.sidebar-overlay {
  position: absolute;
  inset: 0;
  background: #17181555;
  z-index: 1;
}

/* ===== Mobile sidebar ===== */
.chat-sidebar.is-mobile {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 2;
  box-shadow: var(--shadow);
}

.sidebar-slide-enter-active,
.sidebar-slide-leave-active {
  transition: transform .24s var(--ease-out);
}

.sidebar-slide-enter-from,
.sidebar-slide-leave-to {
  transform: translateX(-100%);
}

/* ===== Scrollbar ===== */
.messages-area::-webkit-scrollbar,
.session-list::-webkit-scrollbar {
  width: 5px;
}

.messages-area::-webkit-scrollbar-thumb,
.session-list::-webkit-scrollbar-thumb {
  background: var(--border-strong);
  border-radius: 3px;
}

.messages-area::-webkit-scrollbar-thumb:hover,
.session-list::-webkit-scrollbar-thumb:hover {
  background: var(--muted);
}

.messages-area::-webkit-scrollbar-track,
.session-list::-webkit-scrollbar-track {
  background: transparent;
}

/* ===== Responsive ===== */
@media (max-width: 740px) {
  .chat-view {
    height: max(390px, calc(100dvh - var(--workspace-bar-height) - 153px));
  }

  .messages-area {
    padding: 16px 12px;
  }

  .input-area {
    padding: 12px;
  }

  .message-bubble {
    max-width: 88%;
  }

  .send-btn {
    min-width: 64px;
  }
  .session-delete {
    opacity: 1;
  }
}
</style>
