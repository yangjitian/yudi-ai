import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, sendLoginCode, register, sendRegisterCode, logout as apiLogout, getCurrentUser } from '@/api/user'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

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
        userInfo.value = {
          id: response.data.id,
          userAccount: response.data.userAccount,
          userName: response.data.userName,
          userAvatar: response.data.userAvatar
        }
        localStorage.setItem('token', token.value)
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
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
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
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
        userInfo.value = {
          id: response.data.id,
          userAccount: response.data.userAccount,
          userName: response.data.userName,
          userAvatar: response.data.userAvatar
        }
        localStorage.setItem('token', token.value)
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
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

  return {
    token,
    userInfo,
    isLoggedIn,
    sendLoginVerificationCode,
    sendRegisterVerificationCode,
    userLogin,
    userRegister,
    userLogout,
    restoreUserState
  }
})

