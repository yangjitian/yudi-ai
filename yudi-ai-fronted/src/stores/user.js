import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, sendLoginCode, register, sendRegisterCode, logout as apiLogout, getCurrentUser } from '@/api/user'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  // 发送登录验证码
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

  // 发送注册验证码
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

  // 登录
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

  // 注册
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

  // 登出：先调用后端销毁Token，再本地清理
  const userLogout = async () => {
    try {
      if (token.value) {
        await apiLogout(token.value)
      }
    } catch (_) {
      // 忽略登出接口异常，继续本地清理
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }

  // 恢复登录状态：应用启动时调用，验证并恢复用户登录状态
  const restoreUserState = async () => {
    // 如果本地没有token，直接返回
    if (!token.value) {
      return false
    }

    try {
      const response = await getCurrentUser()
      if (response.code === 200 && response.data) {
        // Token有效，恢复用户信息
        token.value = response.data.token || token.value
        userInfo.value = {
          id: response.data.id,
          userAccount: response.data.userAccount,
          userName: response.data.userName,
          userAvatar: response.data.userAvatar
        }
        // 更新localStorage
        localStorage.setItem('token', token.value)
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
        return true
      } else {
        // 响应异常，清除本地数据
        token.value = ''
        userInfo.value = null
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        return false
      }
    } catch (error) {
      // 请求失败（通常是401未授权），清除本地数据
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

