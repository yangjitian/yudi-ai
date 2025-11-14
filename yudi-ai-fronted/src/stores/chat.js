import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatStream, chat, getConversations, getConversationHistory, createConversationId, deleteConversationApi } from '@/api/chat'
import router from '@/router'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
  // 会话列表
  const conversations = ref([])
  
  // 当前会话ID
  const currentConversationId = ref(null)
  
  // 当前会话的消息列表
  const messages = ref([])
  
  // 是否正在加载
  const isLoading = ref(false)
  
  // 是否启用深度思考
  const useDeepThinking = ref(false)

  // 从后端加载会话列表
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

  // 创建新会话（后端立即生成ID并返回）
  const createConversation = async () => {
    try {
      const resp = await createConversationId()
      if (resp?.code === 200 && resp?.data) {
        const newId = resp.data
        // 插入到顶部（若已存在则先去重）
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
        // 同步URL到通用路由 /:conversationId，避免沿用旧ID
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

  // 切换会话并加载历史消息
  const switchConversation = async (conversationId) => {
    currentConversationId.value = conversationId
    messages.value = []
    
    // 加载历史消息
    try {
      const response = await getConversationHistory(conversationId)
      if (response.code === 200 && response.data) {
        const history = response.data
        history.forEach(memory => {
          // 添加用户消息
          if (memory.userInput) {
            addMessage('user', memory.userInput, conversationId, false)
          }
          // 添加AI回复
          if (memory.aiResponse) {
            addMessage('assistant', memory.aiResponse, conversationId, false)
          }
        })
      }
    } catch (error) {
      ElMessage.error('加载历史消息失败：' + (error.message || '未知错误'))
    }
  }

  // 删除会话（后端真删除 + 前端同步）
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
          await switchConversation(conversations.value[0].id)
        } else {
          currentConversationId.value = null
          messages.value = []
        }
      }
      ElMessage.success('删除成功')
    } catch (e) {
      ElMessage.error('删除失败：' + (e.message || '未知错误'))
      throw e
    }
  }

  // 生成会话标题（参考ChatGPT/DeepSeek的做法）
  const generateConversationTitle = (userInput) => {
    if (!userInput) return '新会话'
    const trimmed = userInput.trim()
    if (trimmed.length <= 8) return trimmed
    return trimmed.substring(0, 8) + '....'
  }

  // 添加消息
  const addMessage = (role, content, conversationId = null, updateTitle = true) => {
    const msg = {
      id: Date.now() + Math.random(),
      role, // 'user' or 'assistant'
      content,
      timestamp: Date.now()
    }
    messages.value.push(msg)
    
    // 如果是用户的第一条消息，更新会话标题（仅当是新会话时）
    if (role === 'user' && updateTitle) {
      const title = generateConversationTitle(content)
      if (conversationId) {
        const conversation = conversations.value.find(c => c.id === conversationId)
        if (conversation) {
          conversation.title = title
          conversation.updatedAt = Date.now()
        } else {
          // 如果列表中不存在该会话，补充一条
          conversations.value.unshift({
            id: conversationId,
            title: title,
            updatedAt: Date.now()
          })
        }
      } else {
        // 无会话ID时，更新占位会话标题
        const pendingIdx = conversations.value.findIndex(c => c.isPending)
        if (pendingIdx > -1) {
          conversations.value[pendingIdx].title = title
          conversations.value[pendingIdx].updatedAt = Date.now()
        }
      }
    }
  }

  // 更新最后一条消息（用于流式输出）
  const updateLastMessage = (content) => {
    if (messages.value.length > 0) {
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg.role === 'assistant') {
        lastMsg.content = content
      }
    }
  }

  // 前端不再生成会话ID，统一由后端生成

  // 发送消息（流式）
  const sendMessageStream = async (query, conversationId) => {
    isLoading.value = true
    addMessage('user', query, conversationId)
    
    // 添加一个空的助手消息，用于流式更新
    addMessage('assistant', '')
    
    try {
      const endpoint = useDeepThinking.value ? '/yd_manus/chat/stream' : '/cook/pg/chat/stream'
      const response = await chatStream(query, conversationId, endpoint)
      
      // 处理SSE流
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let fullContent = ''
      let receivedConversationId = conversationId
      let buffer = ''
      let currentEvent = null

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        
        // 保留最后一个不完整的行
        buffer = lines.pop() || ''

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i].trim()
          
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
            continue
          }
          
          if (line.startsWith('data:')) {
            const data = line.substring(5).trim()
            
            if (currentEvent === 'conversationId') {
              receivedConversationId = data
              // 后端首次返回真实ID时，绑定到占位会话并设置当前会话
              if (!conversationId) {
                const pendingIdx = conversations.value.findIndex(c => c.isPending)
                if (pendingIdx > -1) {
                  conversations.value[pendingIdx].id = receivedConversationId
                  conversations.value[pendingIdx].isPending = false
                } else {
                  // 若未找到占位会话，补充一条
                  conversations.value.unshift({
                    id: receivedConversationId,
                    title: '新会话',
                    updatedAt: Date.now()
                  })
                }
                currentConversationId.value = receivedConversationId
              }
              currentEvent = null
              continue
            }
            
            if (currentEvent === 'error') {
              throw new Error(data || '请求失败')
            }

            if (currentEvent === 'complete') {
              currentEvent = null
              continue
            }

            // 普通数据流（默认或 message 事件）
            if (!currentEvent || currentEvent === 'message') {
              if (data) {
              fullContent += data
              updateLastMessage(fullContent)
              }
              currentEvent = null
              continue
            }
            currentEvent = null
          }
          
          if (line === '') {
            currentEvent = null
          }
        }
      }

      // 确保会话ID已设置，并在后端返回真实ID时进行对齐
      if (receivedConversationId) {
        // 若后端返回的ID与传入不同（前端临时ID），则更新会话列表中的ID
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
      // 同步前端路由为后端实际使用的端点 + 会话ID，便于调试
      const endpointPath = useDeepThinking.value ? '/yd_manus/chat/stream' : '/cook/pg/chat/stream'
      if (receivedConversationId || conversationId) {
        const cid = receivedConversationId || conversationId
        if (router.currentRoute.value.path !== `${endpointPath}/${cid}`) {
          router.replace({ path: `${endpointPath}/${cid}` })
        }
      }

      isLoading.value = false
      return { success: true, conversationId: receivedConversationId || conversationId, endpointUsed: endpointPath }
    } catch (error) {
      isLoading.value = false
      updateLastMessage('抱歉，发生了错误：' + error.message)
      // 如果流式失败，尝试非流式接口
      return await sendMessageFallback(query, conversationId || currentConversationId.value)
    }
  }

  // 发送消息（非流式，作为兜底）
  const sendMessageFallback = async (query, conversationId) => {
    try {
      const endpoint = useDeepThinking.value ? '/yd_manus/chat' : '/cook/pg/chat'
      const response = await chat(query, conversationId, endpoint)
      
      if (response.code === 200 && response.data) {
        updateLastMessage(response.data.answer)
        const finalConversationId = response.data.conversationId || conversationId
        if (finalConversationId) {
          // 若后端返回的ID与传入不同（前端临时ID），则更新会话列表中的ID
          if (conversationId && finalConversationId !== conversationId) {
            const idx = conversations.value.findIndex(c => c.id === conversationId)
            if (idx > -1) {
              conversations.value[idx].id = finalConversationId
            }
          }
          // 无传入ID（新会话）：将占位会话与后端ID对齐
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
        // 同步前端路由为后端实际使用的端点 + 会话ID，便于调试
        const endpointPath = useDeepThinking.value ? '/yd_manus/chat' : '/cook/pg/chat'
        if (finalConversationId) {
          if (router.currentRoute.value.path !== `${endpointPath}/${finalConversationId}`) {
            router.replace({ path: `${endpointPath}/${finalConversationId}` })
          }
        }
        return { success: true, conversationId: finalConversationId, endpointUsed: endpointPath }
      } else {
        throw new Error(response.message || '请求失败')
      }
    } catch (error) {
      updateLastMessage('抱歉，发生了错误：' + error.message)
      return { success: false, error: error.message }
    }
  }

  // 发送消息（主入口）
  const sendMessage = async (query) => {
    let conversationId = currentConversationId.value
    // 如果没有当前会话：不提前创建后端会话ID，保持主页面状态
    // 可选：添加占位会话，等后端返回真实ID后再对齐
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

    // 优先尝试流式接口
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

