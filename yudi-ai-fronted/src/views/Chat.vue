<template>
  <div class="chat-container">
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

    <main class="main-content">
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

      <div class="message-list" ref="messageListRef">
        <div
          v-for="message in chatStore.messages"
          :key="`${message.id}-${message.content?.length || 0}`"
          class="message-item"
          :class="message.role"
          v-show="!(message.role === 'assistant' && !message.content && chatStore.isLoading)"
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
              :class="{
                'streaming-text-content': chatStore.isLoading && isLastMessage(message),
                'markdown-content': !(chatStore.isLoading && isLastMessage(message))
              }"
              :key="`content-${message.id}-${chatStore.isLoading && isLastMessage(message) ? 'streaming' : 'done'}`"
              v-html="getRenderedMarkdown(message, chatStore.isLoading && isLastMessage(message))"
              ref="markdownRef"
            ></div>
            <div v-else class="text-content">{{ message.content }}</div>
          </div>
        </div>
        
        <div 
          v-if="chatStore.isLoading && !hasAssistantMessageWithContent" 
          class="message-item assistant"
        >
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

      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入您的问题..."
            @keydown.enter.exact.prevent="handleSendMessage"
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
import { ref, nextTick, watch, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { getCurrentUser } from '@/api/user'
import { Plus, Delete, SwitchButton, Expand, Fold, ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const sidebarCollapsed = ref(false)
const inputText = ref('')
const messageListRef = ref(null)

// 检查是否有助手消息且有内容
const hasAssistantMessageWithContent = computed(() => {
  const messages = chatStore.messages
  if (messages.length === 0) return false
  const lastMsg = messages[messages.length - 1]
  return lastMsg && lastMsg.role === 'assistant' && lastMsg.content && lastMsg.content.trim().length > 0
})

// 判断是否是最后一条消息（用于流式传输判断）
// 企业级实现：确保准确判断最后一条消息
const isLastMessage = (message) => {
  if (!message) return false
  const messages = chatStore.messages
  if (messages.length === 0) return false
  const lastMsg = messages[messages.length - 1]
  return lastMsg && lastMsg.id === message.id && lastMsg.role === 'assistant'
}

onMounted(async () => {
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
  
  await chatStore.loadConversations()

  const urlConversationId = route.params.conversationId
  if (urlConversationId) {
    await chatStore.switchConversation(urlConversationId)
  } else {
    chatStore.currentConversationId = null
    chatStore.messages = []
  }
  
  handleResize()
  window.addEventListener('resize', handleResize)
})

const handleResize = () => {
  if (window.innerWidth < 768) {
    sidebarCollapsed.value = true
  }
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleNewConversation = async () => {
  chatStore.currentConversationId = null
  chatStore.messages = []
  inputText.value = ''
  if (route.path !== '/') {
    router.push({ name: 'Chat' })
  }
  scrollToBottom()
}

const handleSwitchConversation = (conversationId) => {
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
  if (route.params.conversationId !== conversationId) {
    router.push({ name: 'ChatWithId', params: { conversationId } })
  }
  if (window.innerWidth < 768) {
    sidebarCollapsed.value = true
  }
}

const handleDeleteConversation = async (conversationId) => {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatStore.deleteConversation(conversationId)
  } catch {
  }
}

const handleSendMessage = async () => {
  if (!inputText.value.trim() || chatStore.isLoading) return
  
  const query = inputText.value.trim()
  inputText.value = ''
  
  const result = await chatStore.sendMessage(query)
  if (result?.conversationId && route.params.conversationId !== result.conversationId) {
    router.replace({ name: 'ChatWithId', params: { conversationId: result.conversationId } })
  }
  await nextTick()
  scrollToBottom()
}

// 使用节流优化滚动性能
let scrollTimer = null
let lastScrollTime = 0
const SCROLL_THROTTLE = 100 // 100ms 节流

const scrollToBottom = () => {
  const now = Date.now()
  if (now - lastScrollTime < SCROLL_THROTTLE) {
    if (scrollTimer) clearTimeout(scrollTimer)
    scrollTimer = setTimeout(() => {
      nextTick(() => {
        if (messageListRef.value) {
          messageListRef.value.scrollTop = messageListRef.value.scrollHeight
        }
      })
      lastScrollTime = Date.now()
    }, SCROLL_THROTTLE - (now - lastScrollTime))
    return
  }
  
  lastScrollTime = now
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 使用 requestAnimationFrame 优化滚动，只在流式输出时使用
let rafScrollId = null
let pendingScroll = false
const scheduleScroll = () => {
  if (!pendingScroll) {
    pendingScroll = true
    rafScrollId = requestAnimationFrame(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
      pendingScroll = false
    })
  }
}

// 监听消息内容变化，使用节流滚动（仅在流式输出时）
// 优化：减少监听频率，避免频繁触发
let lastContentLength = 0
watch(() => {
  const messages = chatStore.messages
  if (messages.length === 0) return 0
  const lastMsg = messages[messages.length - 1]
  // 只监听最后一条助手消息的内容长度，避免频繁触发
  const currentLength = lastMsg && lastMsg.role === 'assistant' ? lastMsg.content?.length || 0 : 0
  // 只在内容长度变化超过一定阈值时才触发（减少频繁更新）
  if (Math.abs(currentLength - lastContentLength) > 10 || currentLength === 0) {
    lastContentLength = currentLength
    return currentLength
  }
  return lastContentLength
}, () => {
  if (chatStore.isLoading) {
    // 流式输出时使用 requestAnimationFrame 优化
    scheduleScroll()
  } else {
    // 非流式输出时正常滚动
    scrollToBottom()
  }
})

// 监听消息数量变化（新增消息时）
watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

// 企业级监听策略：监听最后一条助手消息的内容变化和加载状态
// 确保流式传输时实时更新，传输完成后正确渲染 Markdown
// 优化：减少监听频率，避免在流式输出时频繁触发
let lastWatchValue = null
watch(() => {
  const messages = chatStore.messages
  if (messages.length === 0) return null
  const lastMsg = messages[messages.length - 1]
  if (lastMsg && lastMsg.role === 'assistant') {
    // 流式输出时，只在加载状态变化时触发，避免内容长度变化时频繁触发
    if (chatStore.isLoading) {
      return `${lastMsg.id}-loading`
    } else {
      // 非流式输出时，监听内容变化
      const contentHash = lastMsg.content ? 
        lastMsg.content.substring(0, 100).replace(/\s/g, '').length : 0
      return `${lastMsg.id}-${lastMsg.content?.length || 0}-${contentHash}-done`
    }
  }
  return null
}, (newVal, oldVal) => {
  // 避免重复触发
  if (newVal === lastWatchValue) return
  lastWatchValue = newVal
  // 当流式传输完成时（从 loading 变为 done），清除缓存以确保重新渲染 Markdown
  const wasLoading = oldVal && oldVal.includes('loading')
  const isDone = newVal && newVal.includes('done')
  
  if (wasLoading && isDone) {
    // 流式传输刚完成，清除所有相关缓存，强制重新渲染为 Markdown
    const messages = chatStore.messages
    if (messages.length > 0) {
      const lastMsg = messages[messages.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        // 清除该消息的所有缓存项
        const keysToDelete = []
        for (const key of markdownCache.keys()) {
          if (key.startsWith(`${lastMsg.id}-`)) {
            keysToDelete.push(key)
          }
        }
        keysToDelete.forEach(key => markdownCache.delete(key))
        
        // 流式输出完成后，延迟执行代码高亮和样式修复，避免抖动
        nextTick(() => {
          setTimeout(() => {
            highlightCodeBlocks()
            applyMarkdownStyleFixes()
          }, 100)
        })
      }
    }
  } else {
    // 内容变化时，确保代码高亮（仅在非流式传输时）
    if (!chatStore.isLoading) {
      nextTick(() => {
        highlightCodeBlocks()
      })
    }
  }
}, { immediate: false })

// 自定义渲染器，优化特定格式的渲染
const renderer = new marked.Renderer()

// 自定义段落渲染，确保空行正确处理
renderer.paragraph = function(text) {
  // 如果段落为空，返回空字符串而不是空段落标签
  if (!text || text.trim() === '') {
    return ''
  }
  return '<p>' + text + '</p>\n'
}

// 自定义强调（斜体）渲染
renderer.em = function(text) {
  return '<em>' + text + '</em>'
}

// 自定义加粗渲染
renderer.strong = function(text) {
  return '<strong>' + text + '</strong>'
}

// 自定义列表项渲染，支持更好的格式
renderer.listitem = function(text, task, checked) {
  if (task) {
    return '<li class="task-list-item">' +
      '<input type="checkbox" disabled' + (checked ? ' checked' : '') + '> ' +
      text +
      '</li>\n'
  }
  return '<li>' + text + '</li>\n'
}

// 配置 marked 以支持代码高亮和更好的 markdown 渲染
marked.use({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.warn('代码高亮失败:', err)
      }
    }
    try {
      return hljs.highlightAuto(code).value
    } catch (err) {
      return code
    }
  },
  breaks: true,
  gfm: true,
  // 启用更宽松的解析，支持更多格式
  pedantic: false,
  // 支持更灵活的列表格式
  smartLists: true,
  // 支持智能标点符号
  smartypants: false,
  // 使用自定义渲染器
  renderer: renderer
})

// 缓存 markdown 渲染结果，避免重复渲染
const markdownCache = new Map()
const MAX_CACHE_SIZE = 50

// 标记是否正在流式渲染中，用于优化性能
const streamingRenderStates = new Map()

// 防抖函数 - 减少频繁更新导致的页面闪烁
function debounce(func, wait) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

// 防抖处理的代码高亮和样式修复
const debouncedHighlightAndFix = debounce((messageId) => {
  nextTick(() => {
    try {
      // 清除已高亮的代码块类
      const codeBlocks = messageListRef.value?.querySelectorAll('pre code.hljs')
      if (codeBlocks) {
        codeBlocks.forEach(block => {
          block.classList.remove('hljs')
        })
      }
      
      // 重新高亮
      highlightCodeBlocks()
      
      // 应用样式修复
      applyMarkdownStyleFixes()
    } catch (error) {
      console.warn('防抖处理高亮失败:', error)
    }
  })
}, 50) // 50ms的延迟，平衡响应速度和性能

// 转义 HTML 特殊字符，但保留换行符
const escapeHtml = (text) => {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// 将纯文本转换为 HTML，保留换行和基本格式
// 企业级实现：处理各种边界情况
const textToHtml = (text) => {
  if (!text) return ''
  if (typeof text !== 'string') {
    console.warn('textToHtml: 输入不是字符串类型', typeof text)
    text = String(text)
  }
  // 转义 HTML 特殊字符
  const escaped = escapeHtml(text)
  // 将换行符转换为 <br>，保留多个连续换行
  // 同时处理 Windows (\r\n) 和 Unix (\n) 换行符
  return escaped.replace(/\r\n/g, '<br>').replace(/\n/g, '<br>').replace(/\r/g, '<br>')
}

// 确保流式传输中的Markdown内容安全且可渲染
// 处理不完整的Markdown语法，避免渲染错误
const ensureSafeMarkdown = (text) => {
  if (!text) return ''
  
  // 创建安全副本进行处理
  let safeText = text
  
  // 处理常见的不完整Markdown语法
  
  // 1. 确保未闭合的代码块被正确闭合
  const codeBlockRegex = /```[\s\S]*?```/g
  const codeBlocks = text.match(codeBlockRegex) || []
  const codeBlockCount = codeBlocks.length
  const openingBackticks = (text.match(/```/g) || []).length
  
  // 如果有奇数个```，添加一个闭合的```
  if (openingBackticks % 2 !== 0) {
    safeText = safeText + '\n```'
  }
  
  // 2. 处理不完整的列表项，确保每个项目都有结束
  // 找到最后一个列表项的位置
  const lastListItemIndex = Math.max(
    safeText.lastIndexOf('\n- '),
    safeText.lastIndexOf('\n1. '),
    safeText.lastIndexOf('\n* ')
  )
  
  // 如果最后一项后面没有其他内容或只有空格，确保它有一个结束标记
  if (lastListItemIndex !== -1) {
    const textAfterLastListItem = safeText.substring(lastListItemIndex + 1).trim()
    if (textAfterLastListItem && !textAfterLastListItem.startsWith('-') && 
        !textAfterLastListItem.startsWith('1.') && !textAfterLastListItem.startsWith('*')) {
      // 在最后一个列表项后添加一个空行，帮助渲染器理解列表结束
      // 不需要额外处理，marked应该能正确处理这种情况
    }
  }
  
  // 3. 确保不完整的链接被安全处理
  // 这是一个简单的检查，实际处理可能需要更复杂的解析
  const linkCount = (safeText.match(/\[/g) || []).length
  const linkCloseCount = (safeText.match(/\]/g) || []).length
  
  // 如果有未闭合的链接括号，添加闭合括号
  if (linkCount > linkCloseCount) {
    // 这是一个简化处理，在实际应用中可能需要更精确的位置处理
    safeText = safeText + ']()'
  }
  
  return safeText
}

// 优化的Markdown渲染函数 - 完全重写
const getRenderedMarkdown = (message, isStreaming) => {
  // 边界情况处理
  if (!message) {
    console.warn('getRenderedMarkdown: message 为空')
    return ''
  }
  
  // 直接访问 message.content，确保 Vue 能追踪到变化
  let content = message.content
  if (content === null || content === undefined) {
    content = ''
  }
  if (typeof content !== 'string') {
    console.warn('getRenderedMarkdown: content 不是字符串类型', typeof content)
    content = String(content)
  }
  
  // 空内容处理
  if (!content || content.trim().length === 0) {
    return ''
  }
  
  // 流式传输时的优化渲染逻辑
  if (isStreaming) {
    // 记录流式渲染状态
    streamingRenderStates.set(message.id, true)
    
    try {
      // 预处理内容，优化格式识别
      let processedContent = preprocessContent(content)
      
      // 使用自定义的流式渲染配置
      const rendered = marked.parse(processedContent, {
        breaks: true,
        gfm: true,
        pedantic: false,
        silent: true, // 忽略不完整语法的警告
        smartLists: true,
        
        // 使用全局配置的渲染器
        renderer: renderer,
        
        // 特殊配置，优化不完整内容的渲染
        mangle: false,
        headerIds: false
      })
      
      // 流式输出时禁用DOM操作，避免抖动
      // debouncedHighlightAndFix 只在流式输出完成后调用
      
      return rendered
    } catch (e) {
      console.warn('流式Markdown渲染失败，降级为增强版纯文本:', e.message)
      // 降级为增强版纯文本显示
      return enhancedTextToHtml(content)
    }
  }
  
  // 非流式传输时的完整渲染
  const contentHash = generateContentHash(content)
  const cacheKey = `${message.id}-${content.length}-${contentHash}`
  
  // 缓存管理
  if (markdownCache.has(cacheKey)) {
    const cached = markdownCache.get(cacheKey)
    if (cached && cached.originalContent === content) {
      // 直接使用缓存，不触发DOM操作（样式已通过CSS类处理）
      return cached.rendered
    }
    markdownCache.delete(cacheKey)
  }
  
  try {
    // 流式转完整时的特殊处理
    if (streamingRenderStates.has(message.id)) {
      // 完全清除相关状态和缓存
      clearMessageCacheAndState(message.id)
    }
    
    // 对完整内容进行预处理，确保一致性
    const processedContent = preprocessContent(content)
    
    // 使用最佳配置渲染完整内容
    const rendered = marked.parse(processedContent, {
      breaks: true,
      gfm: true,
      pedantic: false,
      smartLists: true,
      
      // 使用全局配置的渲染器
      renderer: renderer,
      headerIds: false,
      sanitize: false
    })
    
    // 验证渲染结果
    if (!rendered || typeof rendered !== 'string') {
      console.warn('Markdown 渲染结果无效，降级为增强版纯文本')
      return enhancedTextToHtml(content)
    }
    
    // 缓存管理 - LRU策略
    if (markdownCache.size >= MAX_CACHE_SIZE) {
      const firstKey = markdownCache.keys().next().value
      markdownCache.delete(firstKey)
    }
    
    // 存储带原始内容的缓存
    markdownCache.set(cacheKey, {
      rendered,
      originalContent: content
    })
    
    // 非流式传输完成后，延迟执行代码高亮（样式已通过CSS类处理）
    nextTick(() => {
      setTimeout(() => {
        highlightCodeBlocks()
      }, 50)
    })
    
    return rendered
  } catch (e) {
    console.error('Markdown 渲染错误:', e, '内容长度:', content.length)
    // 错误降级处理
    return enhancedTextToHtml(content)
  }
}

// 预处理内容，优化Markdown解析
function preprocessContent(content) {
  // 防止不完整的Markdown语法
  let processed = content
  
  // 先处理代码块，避免修改代码中的内容
  const codeBlockRegex = /```[\s\S]*?```/g
  const codeBlocks = []
  let codeBlockIndex = 0
  processed = processed.replace(codeBlockRegex, (match) => {
    const placeholder = `__CODE_BLOCK_${codeBlockIndex}__`
    codeBlocks[codeBlockIndex] = match
    codeBlockIndex++
    return placeholder
  })
  
  // 处理行内代码块
  const inlineCodeRegex = /`[^`\n]+`/g
  const inlineCodes = []
  let inlineCodeIndex = 0
  processed = processed.replace(inlineCodeRegex, (match) => {
    const placeholder = `__INLINE_CODE_${inlineCodeIndex}__`
    inlineCodes[inlineCodeIndex] = match
    inlineCodeIndex++
    return placeholder
  })
  
  // 修复不完整的标题语法（如###标题后无内容）
  processed = processed.replace(/^(#{1,6})\s*$/gm, '$1  ')
  
  // 修复标题格式：确保 # 后有一个空格
  processed = processed.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  
  // 修复列表项后的空格缺失
  processed = processed.replace(/^(\s*[-*+]|\d+\.)\s*$/gm, '$1 ')
  
  // 修复列表项格式：确保列表标记后有空格
  processed = processed.replace(/^(\s*)([-*+])([^\s-*+])/gm, '$1$2 $3')
  processed = processed.replace(/^(\s*)(\d+\.)([^\s\d])/gm, '$1$2 $3')
  
  // 处理任务列表格式：[ ] 和 [x]
  processed = processed.replace(/^(\s*[-*+])\s*\[\s*\]\s*/gm, '$1 [ ] ')
  processed = processed.replace(/^(\s*[-*+])\s*\[[xX]\]\s*/gm, '$1 [x] ')
  
  // 先处理带空格的粗体格式（星号）：将 ** 文本 ** 转换为 **文本**
  // 必须先处理粗体，避免被斜体处理误匹配
  processed = processed.replace(/\*\*\s+([^*\n]+?)\s+\*\*/g, '**$1**')
  
  // 处理带空格的粗体格式（下划线）：将 __ 文本 __ 转换为 __文本__
  processed = processed.replace(/__\s+([^_\n]+?)\s+__/g, '__$1__')
  
  // 再处理带空格的星号格式：将 * 文本 * 转换为 *文本*（斜体）
  // 此时已经处理过粗体，所以单个星号就是斜体
  // 但要避免匹配列表标记（行首的 *）
  // 使用更简单的方法：先处理非行首的斜体
  processed = processed.replace(/([^\n\*])\*\s+([^*\n]+?)\s+\*/g, '$1*$2*')
  // 处理行首的斜体（但不是列表标记）
  processed = processed.replace(/^(\s+)\*\s+([^*\n]+?)\s+\*/gm, '$1*$2*')
  
  // 处理带空格的下划线斜体格式：将 _ 文本 _ 转换为 _文本_
  // 此时已经处理过粗体（__文本__），所以单个下划线就是斜体
  processed = processed.replace(/([^\n_])_\s+([^_\n]+?)\s+_/g, '$1_$2_')
  // 处理行首的下划线斜体
  processed = processed.replace(/^(\s+)_\s+([^_\n]+?)\s+_/gm, '$1_$2_')
  
  // 处理删除线格式：将 ~~ 文本 ~~ 转换为 ~~文本~~
  processed = processed.replace(/~~\s+([^~\n]+?)\s+~~/g, '~~$1~~')
  
  // 处理链接格式：修复 [ 文本 ]( 链接 ) 中的空格
  processed = processed.replace(/\[\s+([^\]]+?)\s+\]\(\s*([^)]+?)\s*\)/g, '[$1]($2)')
  
  // 处理图片格式：修复 ![ 文本 ]( 链接 ) 中的空格
  processed = processed.replace(/!\[\s+([^\]]+?)\s+\]\(\s*([^)]+?)\s*\)/g, '![$1]($2)')
  
  // 处理引用块格式：确保 > 后有空格
  processed = processed.replace(/^(\s*)>\s*$/gm, '$1> ')
  processed = processed.replace(/^(\s*)>([^\s>])/gm, '$1> $2')
  
  // 处理水平分割线：统一格式
  processed = processed.replace(/^(\s*)([-*_])\s*\2\s*\2\s*$/gm, '$1---')
  
  // 处理表格格式（常见的流式输出问题）
  processed = fixTableFormat(processed)
  
  // 处理脚注格式：修复 [^ 文本 ] 中的空格
  processed = processed.replace(/\[\^\s+([^\]]+?)\s+\]/g, '[^$1]')
  
  // 处理行内数学公式（如果支持）：修复 $ 公式 $ 中的空格
  processed = processed.replace(/\$\s+([^$\n]+?)\s+\$/g, '$$1$')
  
  // 处理块级数学公式（如果支持）：修复 $$ 公式 $$ 中的空格
  processed = processed.replace(/\$\$\s+([^$]+?)\s+\$\$/g, (match, formula) => {
    return '$$' + formula + '$$'
  })
  
  // 优化emoji和特殊字符的处理（但不要破坏已修复的格式）
  processed = optimizeEmojiAndSpecialChars(processed)
  
  // 修复不完整的代码块标记
  processed = fixIncompleteCodeBlocks(processed)
  
  // 修复不完整的链接和图片
  processed = fixIncompleteLinks(processed)
  
  // 恢复代码块
  inlineCodes.forEach((code, index) => {
    processed = processed.replace(`__INLINE_CODE_${index}__`, code)
  })
  codeBlocks.forEach((code, index) => {
    processed = processed.replace(`__CODE_BLOCK_${index}__`, code)
  })
  
  return processed
}

// 修复表格格式问题
function fixTableFormat(content) {
  const lines = content.split('\n')
  let inTable = false
  let tableLines = []
  let resultLines = []
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    
    // 检查表格分隔行 (|---| 格式)
    const isSeparator = /^\s*\|(\s*[-:]+[-:\s]*\|)+\s*$/.test(line)
    const isTableRow = /^\s*\|/.test(line)
    
    if (isSeparator) {
      // 如果之前有表格行但没有分隔行，先添加一个分隔行
      if (inTable && tableLines.length > 0 && !tableLines.some(l => /^\s*\|(\s*[-:]+[-:\s]*\|)+\s*$/.test(l))) {
        // 根据第一行的列数生成分隔行
        const firstRow = tableLines[0]
        const columnCount = (firstRow.match(/\|/g) || []).length - 1
        if (columnCount > 0) {
          const separator = '|' + ' --- |'.repeat(columnCount)
          tableLines.splice(1, 0, separator)
        }
      }
      inTable = true
      tableLines.push(line)
    } else if (isTableRow) {
      // 表格内容行
      if (!inTable) {
        // 如果这是第一行表格，标记为表格开始
        inTable = true
      }
      tableLines.push(line)
    } else {
      // 表格外的行
      if (inTable) {
        // 表格结束，处理收集的表格行
        if (tableLines.length >= 2) {
          // 确保有分隔行
          const hasSeparator = tableLines.some(l => /^\s*\|(\s*[-:]+[-:\s]*\|)+\s*$/.test(l))
          if (!hasSeparator && tableLines.length > 0) {
            // 根据第一行的列数生成分隔行
            const firstRow = tableLines[0]
            const columnCount = (firstRow.match(/\|/g) || []).length - 1
            if (columnCount > 0) {
              const separator = '|' + ' --- |'.repeat(columnCount)
              tableLines.splice(1, 0, separator)
            }
          }
          resultLines = resultLines.concat(tableLines)
        } else {
          // 不完整表格，作为普通文本处理
          resultLines = resultLines.concat(tableLines)
        }
        tableLines = []
        inTable = false
      }
      resultLines.push(line)
    }
  }
  
  // 处理文件末尾的表格
  if (inTable) {
    if (tableLines.length >= 2) {
      // 确保有分隔行
      const hasSeparator = tableLines.some(l => /^\s*\|(\s*[-:]+[-:\s]*\|)+\s*$/.test(l))
      if (!hasSeparator && tableLines.length > 0) {
        const firstRow = tableLines[0]
        const columnCount = (firstRow.match(/\|/g) || []).length - 1
        if (columnCount > 0) {
          const separator = '|' + ' --- |'.repeat(columnCount)
          tableLines.splice(1, 0, separator)
        }
      }
      resultLines = resultLines.concat(tableLines)
    } else {
      resultLines = resultLines.concat(tableLines)
    }
  }
  
  return resultLines.join('\n')
}

// 优化emoji和特殊字符处理
function optimizeEmojiAndSpecialChars(content) {
  // 确保emoji和文本之间有空格
  let optimized = content
  
  // 在emoji和非空格字符之间添加空格
  optimized = optimized.replace(/([\u{1F300}-\u{1F6FF}\u{1F900}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}])([^\s])/gu, '$1 $2')
  optimized = optimized.replace(/([^\s])([\u{1F300}-\u{1F6FF}\u{1F900}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}])/gu, '$1 $2')
  
  // 确保标题标记（#）后有空格，但不要破坏已修复的星号格式
  // 只处理 # 开头的标题，不处理 * 和 +（列表标记）
  optimized = optimized.replace(/(^|\n)(#{1,6})([^\s#])/gm, '$1$2 $3')
  
  return optimized
}

// 修复不完整的代码块标记
function fixIncompleteCodeBlocks(content) {
  let fixed = content
  
  // 统计代码块标记的数量
  const codeBlockMatches = fixed.match(/```/g)
  if (!codeBlockMatches) return fixed
  
  const codeBlockCount = codeBlockMatches.length
  
  // 如果有奇数个 ```，说明有未闭合的代码块
  if (codeBlockCount % 2 !== 0) {
    // 检查最后一个代码块是否在行首
    const lastBacktickIndex = fixed.lastIndexOf('```')
    const textAfterLastBacktick = fixed.substring(lastBacktickIndex + 3)
    
    // 如果最后一个 ``` 后面还有内容，添加闭合标记
    if (textAfterLastBacktick.trim().length > 0) {
      // 检查是否已经有换行，如果没有则添加
      if (!textAfterLastBacktick.startsWith('\n')) {
        fixed = fixed + '\n```'
      } else {
        fixed = fixed + '```'
      }
    }
  }
  
  // 修复不完整的行内代码标记（单个 ` 没有闭合）
  // 但要注意不要破坏正常的代码块
  const lines = fixed.split('\n')
  const fixedLines = lines.map(line => {
    // 跳过代码块内的行
    if (line.includes('```')) return line
    
    // 统计行内代码标记
    const inlineCodeMatches = line.match(/`/g)
    if (!inlineCodeMatches) return line
    
    const inlineCodeCount = inlineCodeMatches.length
    
    // 如果有奇数个 `，尝试修复（但要小心，可能是故意的）
    // 这里只处理明显不完整的情况
    if (inlineCodeCount === 1 && line.trim().endsWith('`')) {
      // 如果行尾只有一个 `，可能是未闭合的，但为了安全，不自动修复
      return line
    }
    
    return line
  })
  
  return fixedLines.join('\n')
}

// 修复不完整的链接和图片
function fixIncompleteLinks(content) {
  let fixed = content
  
  // 修复不完整的链接：[文本]( 或 [文本](链接 或 [文本
  // 使用更智能的匹配，避免误修复
  fixed = fixed.replace(/\[([^\]]+)\]\(\s*$/gm, '[$1]()')
  fixed = fixed.replace(/\[([^\]]+)\]\(([^)]+)\s*$/gm, '[$1]($2)')
  
  // 修复不完整的图片：![文本]( 或 ![文本](链接 或 ![文本
  fixed = fixed.replace(/!\[([^\]]+)\]\(\s*$/gm, '![$1]()')
  fixed = fixed.replace(/!\[([^\]]+)\]\(([^)]+)\s*$/gm, '![$1]($2)')
  
  // 修复只有 [ 没有 ] 的情况（但要小心，可能是故意的）
  // 这里只处理明显不完整的情况：行尾的 [ 后面没有 ]
  const lines = fixed.split('\n')
  const fixedLines = lines.map(line => {
    // 检查是否有未闭合的 [
    const openBrackets = (line.match(/\[/g) || []).length
    const closeBrackets = (line.match(/\]/g) || []).length
    
    // 如果行尾有未闭合的 [，且后面没有 ]，可能是未完成的链接
    if (openBrackets > closeBrackets && line.trim().endsWith('[')) {
      // 为了安全，不自动修复，保持原样
      return line
    }
    
    return line
  })
  
  return fixedLines.join('\n')
}

// 生成内容哈希，优化缓存键
function generateContentHash(content) {
  if (!content || content.length === 0) return 0
  
  // 使用简单但有效的哈希算法
  let hash = 0
  for (let i = 0; i < Math.min(200, content.length); i++) {
    const char = content.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // 转换为32位整数
  }
  return Math.abs(hash)
}

// 清除消息的缓存和状态
function clearMessageCacheAndState(messageId) {
  // 清除流式状态
  streamingRenderStates.delete(messageId)
  
  // 清除相关的所有缓存
  for (const key of markdownCache.keys()) {
    if (key.startsWith(messageId + '-')) {
      markdownCache.delete(key)
    }
  }
}

// 增强版文本转HTML函数
function enhancedTextToHtml(text) {
  if (!text) return ''
  
  try {
    // 先转义HTML特殊字符
    let html = escapeHtml(text)
    
    // 处理换行符
    html = html.replace(/\r\n/g, '<br>').replace(/\n/g, '<br>').replace(/\r/g, '<br>')
    
    // 保留基本格式标记
    // 处理粗体标记
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    html = html.replace(/__(.+?)__/g, '<strong>$1</strong>')
    
    // 处理斜体标记
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
    html = html.replace(/_(.+?)_/g, '<em>$1</em>')
    
    // 处理行内代码
    html = html.replace(/`(.+?)`/g, '<code style="background-color: #f1f2f4; padding: 0.2em 0.4em; border-radius: 3px;">$1</code>')
    
    return html
  } catch (e) {
    console.warn('增强版文本转HTML失败:', e)
    return escapeHtml(text).replace(/\n/g, '<br>')
  }
}

// 优化的代码高亮函数
const highlightCodeBlocks = () => {
  if (!messageListRef.value) {
    return
  }
  
  try {
    // 获取所有代码块
    const codeBlocks = messageListRef.value.querySelectorAll('pre code')
    if (!codeBlocks || codeBlocks.length === 0) {
      return
    }
    
    // 批量处理，减少DOM操作次数
    requestAnimationFrame(() => {
      // 预先准备所有需要高亮的块
      const blocksToHighlight = []
      codeBlocks.forEach(block => {
        // 移除之前的高亮类和样式
        block.className = ''
        block.removeAttribute('style')
        blocksToHighlight.push(block)
      })
      
      // 一次性高亮所有块
      blocksToHighlight.forEach(block => {
        try {
          // 尝试自动检测语言并高亮
          hljs.highlightElement(block)
          
          // 确保正确应用样式
          block.style.fontFamily = 'Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace'
          block.style.fontSize = '0.9em'
        } catch (e) {
          // 高亮失败时应用基础样式
          block.style.backgroundColor = '#f6f8fa'
          block.style.padding = '1em'
          block.style.borderRadius = '3px'
          block.style.fontFamily = 'monospace'
          console.warn('单个代码块高亮失败，应用基础样式:', e)
        }
      })
    })
  } catch (e) {
    console.error('代码高亮处理失败:', e)
  }
}

// 全面的Markdown样式修复函数
const applyMarkdownStyleFixes = () => {
  if (!messageListRef.value) {
    return
  }
  
  try {
    // 应用整体样式到所有markdown-content元素
    const markdownContents = messageListRef.value.querySelectorAll('.markdown-content')
    markdownContents.forEach(container => {
      // 设置基础字体和行高
      container.style.fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
      container.style.lineHeight = '1.6'
      container.style.color = '#333'
      container.style.wordBreak = 'break-word'
    })
    
    // 修复标题样式（h1-h6）
    const headers = messageListRef.value.querySelectorAll('h1, h2, h3, h4, h5, h6')
    headers.forEach(header => {
      header.style.marginTop = '1.5em'
      header.style.marginBottom = '0.5em'
      header.style.fontWeight = '600'
      header.style.lineHeight = '1.25'
      
      // 根据标题级别设置大小
      const level = parseInt(header.tagName.substring(1))
      header.style.fontSize = `${Math.max(1, 1.8 - level * 0.2)}em`
    })
    
    // 修复段落样式
    const paragraphs = messageListRef.value.querySelectorAll('p')
    paragraphs.forEach(p => {
      p.style.marginTop = '1em'
      p.style.marginBottom = '1em'
      p.style.textAlign = 'left'
    })
    
    // 修复列表样式
    const lists = messageListRef.value.querySelectorAll('ul, ol')
    lists.forEach(list => {
      list.style.marginTop = '1em'
      list.style.marginBottom = '1em'
      list.style.paddingLeft = '2em'
    })
    
    const listItems = messageListRef.value.querySelectorAll('ul li, ol li')
    listItems.forEach(item => {
      item.style.marginBottom = '0.5em'
      item.style.lineHeight = '1.5'
    })
    
    // 修复代码块样式
    const preBlocks = messageListRef.value.querySelectorAll('pre')
    preBlocks.forEach(block => {
      block.style.backgroundColor = '#f6f8fa'
      block.style.borderRadius = '6px'
      block.style.padding = '16px'
      block.style.overflow = 'auto'
      block.style.marginTop = '1em'
      block.style.marginBottom = '1em'
      block.style.fontSize = '0.9em'
      block.style.lineHeight = '1.45'
      block.style.border = '1px solid #e1e4e8'
    })
    
    // 修复内联代码样式
    const inlineCodes = messageListRef.value.querySelectorAll('p code, li code, td code')
    inlineCodes.forEach(code => {
      code.style.backgroundColor = '#f1f2f4'
      code.style.padding = '0.2em 0.4em'
      code.style.borderRadius = '3px'
      code.style.fontFamily = 'Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace'
      code.style.fontSize = '0.9em'
      code.style.lineHeight = '1.5'
    })
    
    // 修复表格样式
    const tables = messageListRef.value.querySelectorAll('table')
    tables.forEach(table => {
      table.style.borderCollapse = 'collapse'
      table.style.width = '100%'
      table.style.marginTop = '1em'
      table.style.marginBottom = '1em'
      table.style.overflowX = 'auto'
      table.style.display = 'block'
    })
    
    const tableCells = messageListRef.value.querySelectorAll('th, td')
    tableCells.forEach(cell => {
      cell.style.border = '1px solid #e1e4e8'
      cell.style.padding = '8px 12px'
      cell.style.textAlign = 'left'
    })
    
    const tableHeaders = messageListRef.value.querySelectorAll('th')
    tableHeaders.forEach(header => {
      header.style.backgroundColor = '#f6f8fa'
      header.style.fontWeight = '600'
    })
    
    // 修复水平线样式
    const hrElements = messageListRef.value.querySelectorAll('hr')
    hrElements.forEach(hr => {
      hr.style.border = '0'
      hr.style.borderTop = '1px solid #e1e4e8'
      hr.style.margin = '2em 0'
    })
    
    // 修复链接样式
    const links = messageListRef.value.querySelectorAll('a')
    links.forEach(link => {
      link.style.color = '#0366d6'
      link.style.textDecoration = 'none'
      
      // 添加悬停效果
      link.setAttribute('onmouseover', 'this.style.textDecoration="underline"')
      link.setAttribute('onmouseout', 'this.style.textDecoration="none"')
    })
    
    // 修复引用块样式
    const blockquotes = messageListRef.value.querySelectorAll('blockquote')
    blockquotes.forEach(quote => {
      quote.style.borderLeft = '4px solid #dfe2e5'
      quote.style.paddingLeft = '1em'
      quote.style.color = '#6a737d'
      quote.style.margin = '1em 0'
    })
    
    // 修复加粗样式
    const boldElements = messageListRef.value.querySelectorAll('strong, b')
    boldElements.forEach(bold => {
      bold.style.fontWeight = '600'
    })
    
    // 修复斜体样式
    const italicElements = messageListRef.value.querySelectorAll('em, i')
    italicElements.forEach(italic => {
      italic.style.fontStyle = 'italic'
    })
  } catch (e) {
    console.warn('应用Markdown样式修复失败:', e)
  }
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
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
    
    // 流式传输时的纯文本样式（保留换行，类似 ChatGPT）
    .streaming-text-content {
      white-space: pre-wrap;
      line-height: 1.6;
      word-wrap: break-word;
    }
    
    .markdown-content {
      line-height: 1.6;
      word-wrap: break-word;
      
      :deep(p) {
        margin: 0 0 12px 0;
        line-height: 1.6;
        
        &:last-child {
          margin-bottom: 0;
        }
      }
      
      :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
        margin: 16px 0 8px 0;
        font-weight: 600;
        line-height: 1.4;
        
        &:first-child {
          margin-top: 0;
        }
      }
      
      :deep(h1) {
        font-size: 1.5em;
        border-bottom: 1px solid rgba(0, 0, 0, 0.1);
        padding-bottom: 8px;
      }
      
      :deep(h2) {
        font-size: 1.3em;
      }
      
      :deep(h3) {
        font-size: 1.1em;
      }
      
      :deep(ul), :deep(ol) {
        margin: 8px 0;
        padding-left: 24px;
      }
      
      :deep(li) {
        margin: 4px 0;
        line-height: 1.6;
      }
      
      :deep(blockquote) {
        margin: 8px 0;
        padding: 8px 16px;
        border-left: 4px solid rgba(0, 0, 0, 0.2);
        background: rgba(0, 0, 0, 0.05);
        border-radius: 4px;
      }
      
      :deep(a) {
        color: #007aff;
        text-decoration: none;
        
        &:hover {
          text-decoration: underline;
        }
      }
      
      :deep(strong) {
        font-weight: 600;
      }
      
      :deep(em) {
        font-style: italic;
      }
      
      :deep(code) {
        background: rgba(0, 0, 0, 0.1);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Courier New', 'Consolas', 'Monaco', monospace;
        font-size: 0.9em;
      }
      
      :deep(pre) {
        background: #1e1e1e;
        padding: 16px;
        border-radius: 8px;
        overflow-x: auto;
        margin: 12px 0;
        position: relative;
        
        code {
          background: none;
          padding: 0;
          color: #d4d4d4;
          font-size: 0.9em;
          line-height: 1.5;
        }
      }
      
      :deep(table) {
        border-collapse: collapse;
        width: 100%;
        margin: 12px 0;
        
        th, td {
          border: 1px solid rgba(0, 0, 0, 0.1);
          padding: 8px 12px;
          text-align: left;
        }
        
        th {
          background: rgba(0, 0, 0, 0.05);
          font-weight: 600;
        }
      }
      
      :deep(hr) {
        border: none;
        border-top: 1px solid rgba(0, 0, 0, 0.1);
        margin: 16px 0;
      }
      
      :deep(img) {
        max-width: 100%;
        height: auto;
        border-radius: 4px;
        margin: 8px 0;
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

