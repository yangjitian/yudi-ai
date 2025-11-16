import request from './request'

export const sendLoginCode = (data) => {
  return request({
    url: '/email/send-login-code',
    method: 'post',
    data: { email: data.email || data.userAccount }
  })
}

export const sendRegisterCode = (data) => {
  return request({
    url: '/email/send-register-code',
    method: 'post',
    data: { email: data.email || data.userAccount }
  })
}

export const login = (data) => {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

export const register = (data) => {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export const logout = (token) => {
  return request({
    url: '/user/logout',
    method: 'post',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
}

export const getCurrentUser = () => {
  return request({
    url: '/user/current',
    method: 'get'
  })
}