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

export const sendChangeAccountCode = () => {
  return request({
    url: '/email/send-change-account-code',
    method: 'post'
  })
}

export const verifyChangeAccountCode = (data) => {
  return request({
    url: '/email/verify-change-account-code',
    method: 'post',
    data
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

export const updateUserProfile = (data) => {
  return request({
    url: '/user/update',
    method: 'post',
    data
  })
}

export const uploadAvatar = (formData) => {
  return request({
    url: '/user/avatar/upload',
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data: formData
  })
}