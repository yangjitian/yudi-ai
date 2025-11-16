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
  const endpoint = conversationId ? `/api/c/chat/${encodeURIComponent(conversationId)}` : '/api/c/chat'
  return request({
    url: endpoint,
    method: 'post',
    data: { query, mode }
  })
}

// 删除会话（连同所有轮次）
export const deleteConversationApi = (conversationId) => {
  return request({
    url: '/api/conversation/delete', // 路径已更新
    method: 'post',
    params: { conversationId }
  })
}

// 获取会话列表
export const getConversations = () => {
  return request({
    url: '/api/conversations', // 路径已更新
    method: 'get'
  })
}

// 获取会话历史消息
export const getConversationHistory = (conversationId) => {
  return request({
    url: '/api/conversation/history', // 路径已更新
    method: 'get',
    params: { conversationId }
  })
}

// 创建新会话ID（后端立即生成并返回）
export const createConversationId = () => {
  return request({
    url: '/api/conversation/new', // 路径已更新
    method: 'post'
  })
}

