import request from './request'
import { useUserStore } from '@/stores/user'

/**
 * 统一流式聊天接口 (SSE)
 * @param {object} params
 * @param {string} params.query - 用户查询
 * @param {string} params.mode - 聊天模式 ('normal' 或 'deep_thought')
 * @param {string|null} params.conversationId - 会话ID
 * @returns {Promise<Response>} Fetch API的Response对象
 */
export const chatStream = ({ query, mode, conversationId }) => {
  const userStore = useUserStore()
  const endpoint = conversationId ? `/api/c/chat/stream/${encodeURIComponent(conversationId)}` : '/api/c/chat/stream'

  return fetch(endpoint, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${userStore.token}`,
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json',
      'Cache-Control': 'no-cache'
    },
    body: JSON.stringify({ query, mode })
  })
}

/**
 * 统一非流式聊天接口
 * @param {object} params
 * @param {string} params.query - 用户查询
 * @param {string} params.mode - 聊天模式 ('normal' 或 'deep_thought')
 * @param {string|null} params.conversationId - 会话ID
 * @returns {Promise}
 */
export const chat = ({ query, mode, conversationId }) => {
  const endpoint = conversationId ? `/c/chat/${encodeURIComponent(conversationId)}` : '/c/chat'
  return request({
    url: endpoint,
    method: 'post',
    data: { query, mode }
  })
}

export const deleteConversationApi = (conversationId) => {
  return request({
    url: '/history/conversation/delete',
    method: 'post',
    params: { conversationId }
  })
}

export const getConversations = () => {
  return request({
    url: '/history/conversations',
    method: 'get'
  })
}

export const getConversationHistory = (conversationId) => {
  return request({
    url: '/history/conversation/get',
    method: 'get',
    params: { conversationId }
  })
}

export const createConversationId = () => {
  return request({
    url: '/history/conversation/new',
    method: 'post'
  })
}

