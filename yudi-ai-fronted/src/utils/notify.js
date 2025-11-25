import { ElMessage } from 'element-plus'

const recent = new Map()
const windowMs = 3000

const codeMessage = (code) => {
  if (code === 401) return '登录已过期，请重新登录'
  if (code === 403) return '权限不足，无权执行该操作'
  if (code === 404) return '资源不存在或已删除'
  if (code === 429) return '请求过于频繁，请稍后再试'
  if (code >= 500 && code <= 599) return '服务器繁忙，请稍后再试'
  return null
}

const normalizeError = (err, fallback) => {
  if (!err) return { key: fallback || 'unknown', message: fallback || '发生未知错误' }
  if (typeof err === 'string') return { key: err, message: err }
  const isAxios = !!(err.response || err.config)
  if (!isAxios) {
    const msg = err.message || fallback || '发生未知错误'
    return { key: msg, message: msg }
  }
  const status = err.response?.status
  const respMsg = err.response?.data?.message
  const mapped = typeof status === 'number' ? codeMessage(status) : null
  const message = mapped || respMsg || fallback || err.message || '请求失败'
  const key = mapped ? String(status) : (respMsg || fallback || message)
  return { key, message }
}

const dedupeShow = (key, showFn) => {
  const now = Date.now()
  const ts = recent.get(key)
  if (ts && now - ts < windowMs) return
  recent.set(key, now)
  setTimeout(() => {
    if (recent.get(key) === now) recent.delete(key)
  }, windowMs)
  showFn()
}

export const notifyError = (err, fallback) => {
  const { key, message } = normalizeError(err, fallback)
  dedupeShow(key, () => {
    ElMessage({ type: 'error', message, showClose: true, grouping: true })
  })
}

export const notifySuccess = (message) => {
  if (!message) return
  ElMessage({ type: 'success', message, showClose: false })
}

export const notifyWarning = (message) => {
  if (!message) return
  ElMessage({ type: 'warning', message, showClose: true, grouping: true })
}

export const notifyInfo = (message) => {
  if (!message) return
  ElMessage({ type: 'info', message })
}
