import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  login,
  sendLoginCode,
  register,
  sendRegisterCode,
  logout as apiLogout,
  getCurrentUser,
  updateUserProfile,
  sendChangeAccountCode,
  verifyChangeAccountCode,
  uploadAvatar as apiUploadAvatar
} from '@/api/user'
import { ElMessage } from 'element-plus'

const formatUserId = (value) => {
  if (value === null || value === undefined) return ''
  return String(value)
}

const normalizeUserInfo = (data) => {
  if (!data) return null
  return {
    id: formatUserId(data.id),
    userAccount: data.userAccount,
    userName: data.userName,
    userAvatar: data.userAvatar
  }
}

const getStoredUserInfo = () => {
  try {
    const cached = JSON.parse(localStorage.getItem('userInfo') || 'null')
    return normalizeUserInfo(cached)
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(getStoredUserInfo())

  const persistUserInfo = (data) => {
    const normalized = normalizeUserInfo(data)
    userInfo.value = normalized
    if (normalized) {
      localStorage.setItem('userInfo', JSON.stringify(normalized))
    } else {
      localStorage.removeItem('userInfo')
    }
  }

  const isLoggedIn = computed(() => !!token.value)

  const sendLoginVerificationCode = async (email) => {
    try {
      const response = await sendLoginCode({ email })
      if (response.code === 200) {
        ElMessage.success('验证码已发送到您的邮箱')
        return true
      } else {
        ElMessage.error(response.message || '发送验证码失败')
        return false
      }
    } catch (error) {
      ElMessage.error(error.message || '发送验证码失败')
      return false
    }
  }

  const sendRegisterVerificationCode = async (email) => {
    try {
      const response = await sendRegisterCode({ email })
      if (response.code === 200) {
        ElMessage.success('验证码已发送到您的邮箱')
        return true
      } else {
        ElMessage.error(response.message || '发送验证码失败')
        return false
      }
    } catch (error) {
      ElMessage.error(error.message || '发送验证码失败')
      return false
    }
  }

  const userLogin = async (loginData) => {
    try {
      const response = await login(loginData)
      if (response.code === 200 && response.data) {
        token.value = response.data.token
        persistUserInfo(response.data)
        localStorage.setItem('token', token.value)
        ElMessage.success('登录成功')
        return true
      } else {
        ElMessage.error(response.message || '登录失败')
        return false
      }
    } catch (error) {
      ElMessage.error(error.message || '登录失败')
      return false
    }
  }

  const userRegister = async (registerData) => {
    try {
      const response = await register(registerData)
      if (response.code === 200) {
        ElMessage.success('注册成功，请登录')
        return true
      } else {
        ElMessage.error(response.message || '注册失败')
        return false
      }
    } catch (error) {
      ElMessage.error(error.message || '注册失败')
      return false
    }
  }

  const userLogout = async () => {
    try {
      if (token.value) {
        await apiLogout(token.value)
      }
    } catch (_) {
    } finally {
      token.value = ''
      persistUserInfo(null)
      localStorage.removeItem('token')
    }
  }

  const restoreUserState = async () => {
    if (!token.value) {
      return false
    }

    try {
      const response = await getCurrentUser()
      if (response.code === 200 && response.data) {
        token.value = response.data.token || token.value
        persistUserInfo(response.data)
        localStorage.setItem('token', token.value)
        return true
      } else {
        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        return false
      }
    } catch (error) {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      return false
    }
  }

  const updateProfile = async (payload) => {
    try {
      const response = await updateUserProfile(payload)
      if (response.code === 200 && response.data) {
        persistUserInfo(response.data)
        if (response.data.token) {
          token.value = response.data.token
          localStorage.setItem('token', token.value)
        }
        ElMessage.success(response.message || '资料更新成功')
        return true
      } else {
        ElMessage.error(response.message || '资料更新失败')
        return false
      }
    } catch (error) {
      ElMessage.error(error.message || '资料更新失败')
      return false
    }
  }

  const uploadAvatarFile = async (file) => {
    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await apiUploadAvatar(formData)
      if (response.code === 200 && response.data) {
        ElMessage.success(response.message || '头像上传成功')
        return response.data
      } else {
        ElMessage.error(response.message || '头像上传失败')
        return null
      }
    } catch (error) {
      ElMessage.error(error.message || '头像上传失败')
      return null
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    sendLoginVerificationCode,
    sendRegisterVerificationCode,
    userLogin,
    userRegister,
    userLogout,
    restoreUserState,
    updateProfile,
    uploadAvatarFile,
    /**
     * 发送换绑邮箱验证码（发送到当前绑定邮箱）
     */
    sendChangeAccountVerificationCode: async () => {
      try {
        const response = await sendChangeAccountCode()
        if (response.code === 200) {
          ElMessage.success('验证码已发送到当前绑定邮箱')
          return true
        } else {
          ElMessage.error(response.message || '发送验证码失败')
          return false
        }
      } catch (error) {
        ElMessage.error(error.message || '发送验证码失败')
        return false
      }
    },
    /**
     * 校验换绑邮箱验证码
     */
    verifyChangeAccountVerificationCode: async (code) => {
      try {
        const response = await verifyChangeAccountCode({ code })
        if (response.code === 200) {
          ElMessage.success('验证码校验通过')
          return true
        } else {
          ElMessage.error(response.message || '验证码校验失败')
          return false
        }
      } catch (error) {
        ElMessage.error(error.message || '验证码校验失败')
        return false
      }
    }
  }
})

