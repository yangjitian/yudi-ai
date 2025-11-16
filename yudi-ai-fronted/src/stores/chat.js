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
      if (lastMsg.role === 'assistant') {
        lastMsg.content = content
      }
    }
  }

  const sendMessageStream = async (query, conversationId) => {
    isLoading.value = true
    addMessage('user', query, conversationId)
    addMessage('assistant', '')
    
    try {
      const mode = useDeepThinking.value ? 'deep_thought' : 'normal';
      const response = await chatStream({ query, conversationId, mode });
      
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
              continue
            }
            
            if (currentEvent === 'error') {
              throw new Error(data || '请求失败')
            }

            if (currentEvent === 'complete') {
            currentEvent = null
            continue
            }

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
      updateLastMessage('抱歉，发生了错误：' + error.message)
      return await sendMessageFallback(query, conversationId || currentConversationId.value)
    }
  }

  const sendMessageFallback = async (query, conversationId) => {
    try {
      const mode = useDeepThinking.value ? 'deep_thought' : 'normal';
      const response = await chat({ query, conversationId, mode });
      
      if (response.code === 200 && response.data) {
        updateLastMessage(response.data.answer)
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
      updateLastMessage('抱歉，发生了错误：' + error.message)
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

