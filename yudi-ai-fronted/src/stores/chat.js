import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatStream, chat, getConversations, getConversationHistory, createConversationId, deleteConversationApi } from '@/api/chat'
import router from '@/router'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversationId = ref(null)
  const messages = ref([])
  const isLoading = ref(false)
  const useDeepThinking = ref(false)

  const loadConversations = async () => {
    try {
      const response = await getConversations()
      if (response.code === 200 && response.data) {
        conversations.value = response.data.map(conv => ({
          id: conv.conversationId,
          title: conv.title || '新会话',
          updatedAt: conv.updatedAt ? new Date(conv.updatedAt).getTime() : Date.now()
        }))
      }
    } catch (error) {
      ElMessage.error('加载会话列表失败：' + (error.message || '未知错误'))
    }
  }

  const createConversation = async () => {
    try {
      const resp = await createConversationId()
      if (resp?.code === 200 && resp?.data) {
        const newId = resp.data
        const existIdx = conversations.value.findIndex(c => c.id === newId)
        if (existIdx > -1) {
          conversations.value.splice(existIdx, 1)
        }
        conversations.value.unshift({
          id: newId,
          title: '新会话',
          updatedAt: Date.now()
        })
        currentConversationId.value = newId
        messages.value = []
        if (router.currentRoute.value.params.conversationId !== newId) {
          router.push({ name: 'ChatWithId', params: { conversationId: newId } })
        }
        return newId
      }
      throw new Error(resp?.message || '创建会话失败')
    } catch (e) {
      ElMessage.error('创建会话失败：' + (e.message || '未知错误'))
      return null
    }
  }

  const switchConversation = async (conversationId) => {
    currentConversationId.value = conversationId
    messages.value = []
    
    try {
      const response = await getConversationHistory(conversationId)
      if (response.code === 200 && response.data) {
        const history = response.data
        history.forEach(memory => {
          if (memory.userInput) {
            addMessage('user', memory.userInput, conversationId, false)
          }
          if (memory.aiResponse) {
            addMessage('assistant', memory.aiResponse, conversationId, false)
          }
        })
      }
    } catch (error) {
      ElMessage.error('加载历史消息失败：' + (error.message || '未知错误'))
    }
  }

  const deleteConversation = async (conversationId) => {
    try {
      const resp = await deleteConversationApi(conversationId)
      if (resp?.code !== 200) {
        throw new Error(resp?.message || '删除失败')
      }
      const index = conversations.value.findIndex(c => c.id === conversationId)
      if (index > -1) {
        conversations.value.splice(index, 1)
      }
      if (currentConversationId.value === conversationId) {
        if (conversations.value.length > 0) {
          const nextConversationId = conversations.value[0].id
          await switchConversation(nextConversationId)
          if (router.currentRoute.value.path !== `/c/${nextConversationId}`) {
            router.replace({ name: 'ChatWithId', params: { conversationId: nextConversationId } })
          }
        } else {
          currentConversationId.value = null
          messages.value = []
          if (router.currentRoute.value.path !== '/') {
            router.push({ name: 'Chat' })
          }
        }
      }
      ElMessage.success('删除成功')
    } catch (e) {
      ElMessage.error('删除失败：' + (e.message || '未知错误'))
      throw e
    }
  }

  const generateConversationTitle = (userInput) => {
    if (!userInput) return '新会话'
    const trimmed = userInput.trim()
    if (trimmed.length <= 8) return trimmed
    return trimmed.substring(0, 8) + '....'
  }

  const addMessage = (role, content, conversationId = null, updateTitle = true) => {
    const msg = {
      id: Date.now() + Math.random(),
      role, // 'user' or 'assistant'
      content,
      timestamp: Date.now()
    }
    messages.value.push(msg)
    
    if (role === 'user' && updateTitle) {
      const title = generateConversationTitle(content)
      if (conversationId) {
        const conversation = conversations.value.find(c => c.id === conversationId)
        if (conversation) {
          conversation.title = title
          conversation.updatedAt = Date.now()
        } else {
          conversations.value.unshift({
            id: conversationId,
            title: title,
            updatedAt: Date.now()
          })
        }
      } else {
        const pendingIdx = conversations.value.findIndex(c => c.isPending)
        if (pendingIdx > -1) {
          conversations.value[pendingIdx].title = title
          conversations.value[pendingIdx].updatedAt = Date.now()
        }
      }
    }
  }

  const updateLastMessage = (content) => {
    if (messages.value.length > 0) {
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        // 企业级响应式更新策略：
        // 1. 直接赋值触发 Vue 响应式系统
        // 2. 更新时间戳确保变化被检测
        // 3. 如果内容相同，仍然更新时间戳以确保 Vue 能检测到
        if (lastMsg.content !== content) {
          lastMsg.content = content
        }
        lastMsg.timestamp = Date.now()
      }
    }
  }

  const sendMessageStream = async (query, conversationId) => {
    isLoading.value = true
    addMessage('user', query, conversationId)
    
    let assistantMessageAdded = false
    
    try {
      const mode = useDeepThinking.value ? 'deep_thought' : 'normal';
      const response = await chatStream({ query, conversationId, mode });
      
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let fullContent = ''
      let receivedConversationId = conversationId
      let buffer = ''
      let currentEvent = null
      
      // 企业级更新策略：使用 requestAnimationFrame 优化性能，确保 Vue 能及时检测到变化
      let rafId = null
      let pendingUpdate = false
      const scheduleUpdate = () => {
        if (!pendingUpdate) {
          pendingUpdate = true
          rafId = requestAnimationFrame(() => {
            if (assistantMessageAdded && fullContent !== undefined) {
              updateLastMessage(fullContent)
            }
            pendingUpdate = false
          })
        }
      }

      // SSE 数据缓冲区，用于处理多行 data
      let dataBuffer = []
      
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i]
          
          // 处理 event 行（去除前后空白）
          if (line.trim().startsWith('event:')) {
            currentEvent = line.substring(line.indexOf(':') + 1).trim()
            continue
          }
          
          // 处理 data 行（保留原始内容，不 trim，因为可能包含重要空白）
          if (line.startsWith('data:')) {
            // SSE 规范：data: 后面的内容保留原始格式
            // 如果 data: 后面有空格，第一个空格后的内容才是数据
            const dataContent = line.substring(5) // 保留 'data:' 后的所有内容，包括前导空格
            
            // 对于非 message 事件，可以 trim
            if (currentEvent === 'conversationId') {
              const data = dataContent.trim()
              receivedConversationId = data
              if (!conversationId) {
                const pendingIdx = conversations.value.findIndex(c => c.isPending)
                if (pendingIdx > -1) {
                  conversations.value[pendingIdx].id = receivedConversationId
                  conversations.value[pendingIdx].isPending = false
                } else {
                  conversations.value.unshift({
                    id: receivedConversationId,
                    title: '新会话',
                    updatedAt: Date.now()
                  })
                }
                currentConversationId.value = receivedConversationId
              }
              currentEvent = null
              dataBuffer = []
              continue
            }
            
            if (currentEvent === 'error') {
              throw new Error(dataContent.trim() || '请求失败')
            }

            if (currentEvent === 'complete') {
              currentEvent = null
              dataBuffer = []
              continue
            }

            // message 事件：累积多行 data
            if (!currentEvent || currentEvent === 'message') {
              // 累积 data 行（SSE 支持多行 data）
              dataBuffer.push(dataContent)
              continue
            }
            
            currentEvent = null
            dataBuffer = []
          }
          
          // 空行表示一个完整的 SSE 消息结束
          if (line.trim() === '') {
            // 处理累积的 data
            if (dataBuffer.length > 0 && (!currentEvent || currentEvent === 'message')) {
              // 合并多行 data，保留换行符
              // SSE 规范：多行 data 之间用 \n 连接
              // 注意：data: 后的第一个空格是分隔符，应该移除
              let data = dataBuffer.join('\n')
              
              // 处理 SSE 规范：data: 后的第一个空格是分隔符
              // 但实际数据可能没有前导空格，所以只移除第一个空格（如果存在）
              if (data.length > 0 && data[0] === ' ') {
                data = data.substring(1)
              }
              
              if (data.length > 0) {
                if (!assistantMessageAdded) {
                  addMessage('assistant', '')
                  assistantMessageAdded = true
                }
                fullContent += data
                // 企业级更新：使用 requestAnimationFrame 优化性能
                // 确保内容实时更新，同时避免过度渲染
                scheduleUpdate()
              }
              dataBuffer = []
            }
            currentEvent = null
          }
        }
      }
      
      // 处理最后剩余的 data（流结束时可能没有空行）
      if (dataBuffer.length > 0 && (!currentEvent || currentEvent === 'message')) {
        let data = dataBuffer.join('\n')
        // 移除前导空格（SSE 规范：data: 后的第一个空格是分隔符）
        if (data.length > 0 && data[0] === ' ') {
          data = data.substring(1)
        }
        if (data.length > 0) {
          if (!assistantMessageAdded) {
            addMessage('assistant', '')
            assistantMessageAdded = true
          }
          fullContent += data
          scheduleUpdate()
        }
      }
      
      // 企业级清理：确保最后一次更新完成
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      if (pendingUpdate) {
        pendingUpdate = false
      }
      // 确保最终内容被更新
      if (assistantMessageAdded && fullContent !== undefined) {
        updateLastMessage(fullContent)
      }
      
      if (!assistantMessageAdded && fullContent) {
        addMessage('assistant', fullContent)
      }

      if (receivedConversationId) {
        if (conversationId && receivedConversationId !== conversationId) {
          const idx = conversations.value.findIndex(c => c.id === conversationId)
          if (idx > -1) {
            conversations.value[idx].id = receivedConversationId
          }
        }
        if (!conversationId || receivedConversationId !== conversationId) {
          currentConversationId.value = receivedConversationId
        }
      }
      if (receivedConversationId || conversationId) {
        const cid = receivedConversationId || conversationId
        if (router.currentRoute.value.path !== `/c/${cid}`) {
          router.replace({ name: 'ChatWithId', params: { conversationId: cid } })
        }
      }

      isLoading.value = false
      return { success: true, conversationId: receivedConversationId || conversationId }
    } catch (error) {
      isLoading.value = false
      if (!assistantMessageAdded) {
        addMessage('assistant', '抱歉，发生了错误：' + error.message)
      } else {
        updateLastMessage('抱歉，发生了错误：' + error.message)
      }
      return await sendMessageFallback(query, conversationId || currentConversationId.value)
    }
  }

  const sendMessageFallback = async (query, conversationId) => {
    try {
      const mode = useDeepThinking.value ? 'deep_thought' : 'normal';
      const response = await chat({ query, conversationId, mode });
      
      if (response.code === 200 && response.data) {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          updateLastMessage(response.data.answer)
        } else {
          addMessage('assistant', response.data.answer)
        }
        const finalConversationId = response.data.conversationId || conversationId
        if (finalConversationId) {
          if (conversationId && finalConversationId !== conversationId) {
            const idx = conversations.value.findIndex(c => c.id === conversationId)
            if (idx > -1) {
              conversations.value[idx].id = finalConversationId
            }
          }
          if (!conversationId) {
            const pendingIdx = conversations.value.findIndex(c => c.isPending)
            if (pendingIdx > -1) {
              conversations.value[pendingIdx].id = finalConversationId
              conversations.value[pendingIdx].isPending = false
            } else {
              conversations.value.unshift({
                id: finalConversationId,
                title: '新会话',
                updatedAt: Date.now()
              })
            }
          }
          if (!conversationId || finalConversationId !== conversationId) {
            currentConversationId.value = finalConversationId
          }
        }
        if (finalConversationId) {
          if (router.currentRoute.value.path !== `/c/${finalConversationId}`) {
            router.replace({ name: 'ChatWithId', params: { conversationId: finalConversationId } })
          }
        }
        return { success: true, conversationId: finalConversationId }
      } else {
        throw new Error(response.message || '请求失败')
      }
    } catch (error) {
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        updateLastMessage('抱歉，发生了错误：' + error.message)
      } else {
        addMessage('assistant', '抱歉，发生了错误：' + error.message)
      }
      return { success: false, error: error.message }
    }
  }

  const sendMessage = async (query) => {
    let conversationId = currentConversationId.value
    if (!conversationId) {
      const hasPending = conversations.value.some(c => c.isPending)
      if (!hasPending) {
        conversations.value.unshift({
          id: `pending_${Date.now()}`,
          title: '新会话',
          updatedAt: Date.now(),
          isPending: true
        })
      }
    }

    return await sendMessageStream(query, conversationId || null)
  }

  return {
    conversations,
    currentConversationId,
    messages,
    isLoading,
    useDeepThinking,
    loadConversations,
    createConversation,
    switchConversation,
    deleteConversation,
    addMessage,
    sendMessage
  }
})

