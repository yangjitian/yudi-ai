import request from './request'

// 发送登录验证码
export const sendLoginCode = (data) => {
  return request({
    url: '/email/send-login-code',
    method: 'post',
    data: { email: data.email || data.userAccount }
  })
}

// 发送注册验证码
export const sendRegisterCode = (data) => {
  return request({
    url: '/email/send-register-code',
    method: 'post',
    data: { email: data.email || data.userAccount }
  })
}

// 登录
export const login = (data) => {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

// 注册
export const register = (data) => {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

// 登出
export const logout = (token) => {
  return request({
    url: '/user/logout',
    method: 'post',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}

// 获取当前用户信息
export const getCurrentUser = () => {
  return request({
    url: '/user/current',
    method: 'get'
  })
}

