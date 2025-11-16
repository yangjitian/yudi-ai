<template>
  <div class="chat-container">
    <!-- 左侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo-container">
          <img src="/logo.png" alt="雨落有味" class="logo-img" />
          <h2 class="logo">雨落有味</h2>
        </div>
        <el-button
          v-if="!sidebarCollapsed"
          type="primary"
          :icon="Plus"
          @click="handleNewConversation"
          class="new-chat-btn"
        >
          新建会话
        </el-button>
        <el-button
          v-else
          :icon="Plus"
          circle
          @click="handleNewConversation"
          class="new-chat-btn-icon"
        />
      </div>
      
      <div class="sidebar-content" v-if="!sidebarCollapsed">
        <div class="conversation-list">
          <div
            v-for="conversation in chatStore.conversations"
            :key="conversation.id"
            class="conversation-item"
            :class="{ active: conversation.id === chatStore.currentConversationId }"
            @click="handleSwitchConversation(conversation.id)"
          >
            <div class="conversation-info">
              <span class="conversation-title">{{ conversation.title }}</span>
              <span class="conversation-time">{{ formatTime(conversation.updatedAt) }}</span>
            </div>
            <el-button
              :icon="Delete"
              text
              circle
              size="small"
              @click.stop="handleDeleteConversation(conversation.id)"
              class="delete-btn"
            />
          </div>
        </div>
      </div>
      
      <div class="sidebar-footer">
        <div class="user-info" v-if="!sidebarCollapsed && userStore.userInfo">
          <el-avatar :size="32" :src="userStore.userInfo.userAvatar">
            {{ userStore.userInfo.userName?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="user-details">
            <span class="user-name">{{ userStore.userInfo.userName }}</span>
            <el-button
              :icon="SwitchButton"
              text
              circle
              size="small"
              @click="handleLogout"
              class="logout-btn"
              title="退出登录"
            />
          </div>
        </div>
        <el-button
          v-else
          :icon="sidebarCollapsed ? Expand : Fold"
          circle
          @click="toggleSidebar"
          class="toggle-sidebar-btn"
        />
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 移动端侧边栏切换按钮 -->
      <div class="mobile-header">
        <el-button
          :icon="sidebarCollapsed ? Expand : Fold"
          circle
          @click="toggleSidebar"
          class="mobile-toggle-btn"
        />
        <div class="mobile-logo-container">
          <img src="/logo.png" alt="雨落有味" class="mobile-logo-img" />
          <h2 class="mobile-title">雨落有味</h2>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageListRef">
        <div
          v-for="message in chatStore.messages"
          :key="message.id"
          class="message-item"
          :class="message.role"
        >
          <div class="message-avatar">
            <el-avatar v-if="message.role === 'user'" :size="32">
              {{ userStore.userInfo?.userName?.charAt(0) || 'U' }}
            </el-avatar>
            <el-avatar v-else :size="32" :icon="ChatDotRound" />
          </div>
          <div class="message-content">
            <div
              v-if="message.role === 'assistant'"
              class="markdown-content"
              v-html="renderMarkdown(message.content)"
            ></div>
            <div v-else class="text-content">{{ message.content }}</div>
          </div>
        </div>
        
        <div v-if="chatStore.isLoading" class="message-item assistant">
          <div class="message-avatar">
            <el-avatar :size="32" :icon="ChatDotRound" />
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入您的问题..."
            @keydown.enter.exact.prevent="handleSendMessage"
            @keydown.enter.shift.exact="handleShiftEnter"
            :disabled="chatStore.isLoading"
            class="message-input"
          />
          <div class="input-actions">
            <el-switch
              v-model="chatStore.useDeepThinking"
              active-text="深度思考"
              inactive-text="普通模式"
              size="small"
            />
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="chatStore.isLoading"
              @click="handleSendMessage"
              :disabled="!inputText.trim()"
              class="send-btn"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { getCurrentUser } from '@/api/user'
import { Plus, Delete, SwitchButton, Expand, Fold, ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const sidebarCollapsed = ref(false)
const inputText = ref('')
const messageListRef = ref(null)

// 初始化
onMounted(async () => {
  // 加载用户信息
  if (!userStore.userInfo) {
    try {
      const response = await getCurrentUser()
      if (response.code === 200 && response.data) {
        userStore.userInfo = {
          id: response.data.id,
          userAccount: response.data.userAccount,
          userName: response.data.userName,
          userAvatar: response.data.userAvatar
        }
        localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      }
    } catch (error) {
      ElMessage.error('加载用户信息失败')
    }
  }
  
  // 加载会话列表
  await chatStore.loadConversations()

  // 如果URL携带会话ID，则直接切换到该会话；否则停留在主页面（无会话ID状态）
  const urlConversationId = route.params.conversationId
  if (urlConversationId) {
    await chatStore.switchConversation(urlConversationId)
  } else {
    // 保持在首页，无会话ID，不自动创建或跳转
    chatStore.currentConversationId = null
    chatStore.messages = []
  }
  
  // 响应式处理
  handleResize()
  window.addEventListener('resize', handleResize)
})

// 响应式处理
const handleResize = () => {
  if (window.innerWidth < 768) {
    sidebarCollapsed.value = true
  }
}

// 切换侧边栏
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 新建会话
const handleNewConversation = async () => {
  // 重置为"主页面"状态（无会话ID）
  chatStore.currentConversationId = null
  chatStore.messages = []
  inputText.value = ''
  // 跳转到首页 localhost:7000
  if (route.path !== '/') {
    router.push({ name: 'Chat' })
  }
  scrollToBottom()
}

// 切换会话
const handleSwitchConversation = (conversationId) => {
  // 若是占位会话（pending_ 前缀），仅重置输入与消息，不请求历史、不改路由
  if (typeof conversationId === 'string' && conversationId.startsWith('pending_')) {
    chatStore.currentConversationId = null
    chatStore.messages = []
    inputText.value = ''
    scrollToBottom()
    if (window.innerWidth < 768) {
      sidebarCollapsed.value = true
    }
    return
  }
  chatStore.switchConversation(conversationId)
  inputText.value = ''
  scrollToBottom()
  // 同步URL到 /c/:conversationId
  if (route.params.conversationId !== conversationId) {
    router.push({ name: 'ChatWithId', params: { conversationId } })
  }
  if (window.innerWidth < 768) {
    sidebarCollapsed.value = true
  }
}

// 删除会话
const handleDeleteConversation = async (conversationId) => {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatStore.deleteConversation(conversationId)
  } catch {
    // 用户取消
  }
}

// 发送消息
const handleSendMessage = async () => {
  if (!inputText.value.trim() || chatStore.isLoading) return
  
  const query = inputText.value.trim()
  inputText.value = ''
  
  const result = await chatStore.sendMessage(query)
  // 如果是新会话，首次消息发送后拿到ID，更新URL为 /c/{conversationId}
  if (result?.conversationId && route.params.conversationId !== result.conversationId) {
    router.replace({ name: 'ChatWithId', params: { conversationId: result.conversationId } })
  }
  await nextTick()
  scrollToBottom()
}

// Shift+Enter 换行
const handleShiftEnter = () => {
  // 默认行为，允许换行
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 监听消息变化，自动滚动
watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

// Markdown渲染
const renderMarkdown = (content) => {
  if (!content) return ''
  try {
    return marked(content, { breaks: true })
  } catch (e) {
    return content
  }
}

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
  // 支持时间戳（数字）和日期字符串
  const date = typeof timestamp === 'number' ? new Date(timestamp) : new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }
}


// 登出
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.userLogout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style lang="scss" scoped>
.chat-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 280px;
  background: #f7f7f8;
  border-right: 1px solid #e5e5e6;
  display: flex;
  flex-direction: column;
  transition: transform 0.3s;
  
  &.collapsed {
    transform: translateX(-100%);
    position: absolute;
    z-index: 100;
    height: 100%;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e5e5e6;
  
  .logo-container {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
    
    .logo-img {
      width: 32px;
      height: 32px;
      object-fit: contain;
    }
    
    .logo {
      font-size: 20px;
      margin: 0;
      color: #333;
    }
  }
  
  .new-chat-btn {
    width: 100%;
  }
  
  .new-chat-btn-icon {
    width: 100%;
  }
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-list {
  .conversation-item {
    display: flex;
    align-items: center;
    padding: 12px;
    border-radius: 8px;
    cursor: pointer;
    margin-bottom: 4px;
    transition: background 0.2s;
    
    &:hover {
      background: #e9e9eb;
      
      .delete-btn {
        opacity: 1;
      }
    }
    
    &.active {
      background: #e9e9eb;
    }
    
    .conversation-info {
      flex: 1;
      min-width: 0;
      
      .conversation-title {
        display: block;
        font-size: 14px;
        color: #333;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      .conversation-time {
        display: block;
        font-size: 12px;
        color: #999;
        margin-top: 4px;
      }
    }
    
    .delete-btn {
      opacity: 0;
      transition: opacity 0.2s;
    }
  }
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #e5e5e6;
  
  .user-info {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .user-details {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }
    
    .user-name {
      flex: 1;
      font-size: 14px;
      color: #333;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .logout-btn {
      flex-shrink: 0;
    }
  }
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  position: relative;
}

.mobile-header {
  display: none;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e5e6;
  align-items: center;
  gap: 12px;
  
  .mobile-logo-container {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .mobile-logo-img {
      width: 24px;
      height: 24px;
      object-fit: contain;
    }
    
    .mobile-title {
      font-size: 18px;
      margin: 0;
    }
  }
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.message-item {
  display: flex;
  gap: 12px;
  
  &.user {
    flex-direction: row-reverse;
    
    .message-content {
      background: #007aff;
      color: white;
      border-radius: 18px 18px 4px 18px;
    }
  }
  
  &.assistant {
    .message-content {
      background: #f1f1f3;
      color: #333;
      border-radius: 18px 18px 18px 4px;
    }
  }
  
  .message-avatar {
    flex-shrink: 0;
  }
  
  .message-content {
    max-width: 70%;
    padding: 12px 16px;
    word-wrap: break-word;
    
    .text-content {
      white-space: pre-wrap;
    }
    
    .markdown-content {
      :deep(p) {
        margin: 0 0 8px 0;
        
        &:last-child {
          margin-bottom: 0;
        }
      }
      
      :deep(code) {
        background: rgba(0, 0, 0, 0.1);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Courier New', monospace;
      }
      
      :deep(pre) {
        background: rgba(0, 0, 0, 0.1);
        padding: 12px;
        border-radius: 8px;
        overflow-x: auto;
        
        code {
          background: none;
          padding: 0;
        }
      }
    }
  }
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
  
  span {
    width: 8px;
    height: 8px;
    background: #999;
    border-radius: 50%;
    animation: typing 1.4s infinite;
    
    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

.input-area {
  padding: 16px;
  border-top: 1px solid #e5e5e6;
  background: #fff;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  
  .message-input {
    width: 100%;
  }
  
  .input-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .send-btn {
      flex-shrink: 0;
    }
  }
}

@media (max-width: 768px) {
  .mobile-header {
    display: flex;
  }
  
  .sidebar {
    &.collapsed {
      transform: translateX(-100%);
    }
  }
}
</style>

