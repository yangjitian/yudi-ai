import request from './request'
import { useUserStore } from '@/stores/user'

// 流式聊天（SSE）
export const chatStream = (query, conversationId, endpoint = '/cook/pg/chat/stream') => {
  const userStore = useUserStore()
  const params = new URLSearchParams({ query })
  const endpointWithId = conversationId ? `${endpoint}/${encodeURIComponent(conversationId)}` : endpoint

  return fetch(`/api${endpointWithId}?${params.toString()}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${userStore.token}`,
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache'
    }
  })
}

// 删除会话（连同所有轮次）
export const deleteConversationApi = (conversationId) => {
  return request({
    url: '/cook/conversation/delete',
    method: 'post',
    params: { conversationId }
  })
}
// 非流式聊天
export const chat = (query, conversationId, endpoint = '/cook/pg/chat') => {
  const params = { query }
  const endpointWithId = conversationId ? `${endpoint}/${encodeURIComponent(conversationId)}` : endpoint
  return request({
    url: endpointWithId,
    method: 'get',
    params
  })
}

// 深度思考流式聊天
export const ydStreamChat = (query, conversationId) => {
  return chatStream(query, conversationId, '/yd_manus/chat/stream')
}

// 深度思考非流式聊天
export const ydChat = (query, conversationId) => {
  return chat(query, conversationId, '/yd_manus/chat')
}

// 获取会话列表
export const getConversations = () => {
  return request({
    url: '/cook/conversations',
    method: 'get'
  })
}

// 获取会话历史消息
export const getConversationHistory = (conversationId) => {
  return request({
    url: '/cook/conversation/history',
    method: 'get',
    params: { conversationId }
  })
}

// 创建新会话ID（后端立即生成并返回）
export const createConversationId = () => {
  return request({
    url: '/cook/conversation/new',
    method: 'post'
  })
}

