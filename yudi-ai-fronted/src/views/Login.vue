<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>雨落有味</h1>
        <p>AI烹饪助手</p>
      </div>
      
      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-width="0">
            <el-form-item prop="userAccount">
              <el-input
                v-model="loginForm.userAccount"
                placeholder="请输入邮箱"
                size="large"
                prefix-icon="Message"
              />
            </el-form-item>
            <el-form-item prop="verificationCode">
              <div class="code-input-group">
                <el-input
                  v-model="loginForm.verificationCode"
                  placeholder="请输入验证码"
                  size="large"
                  prefix-icon="Lock"
                  maxlength="6"
                />
                <el-button
                  :disabled="loginCodeCountdown > 0"
                  :loading="loginCodeSending"
                  @click="handleSendLoginCode"
                  size="large"
                >
                  {{ loginCodeCountdown > 0 ? `${loginCodeCountdown}秒后重试` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loginLoading"
                @click="handleLogin"
                style="width: 100%"
              >
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0">
            <el-form-item prop="userAccount">
              <el-input
                v-model="registerForm.userAccount"
                placeholder="请输入邮箱"
                size="large"
                prefix-icon="Message"
              />
            </el-form-item>
            <el-form-item prop="verificationCode">
              <div class="code-input-group">
                <el-input
                  v-model="registerForm.verificationCode"
                  placeholder="请输入验证码"
                  size="large"
                  prefix-icon="Lock"
                  maxlength="6"
                />
                <el-button
                  :disabled="registerCodeCountdown > 0"
                  :loading="registerCodeSending"
                  @click="handleSendRegisterCode"
                  size="large"
                >
                  {{ registerCodeCountdown > 0 ? `${registerCodeCountdown}秒后重试` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="registerLoading"
                @click="handleRegister"
                style="width: 100%"
              >
                注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loginLoading = ref(false)
const registerLoading = ref(false)
const loginCodeCountdown = ref(0)
const registerCodeCountdown = ref(0)
const loginCodeSending = ref(false)
const registerCodeSending = ref(false)

const loginForm = reactive({
  userAccount: '',
  verificationCode: ''
})

const registerForm = reactive({
  userAccount: '',
  verificationCode: ''
})

const loginRules = {
  userAccount: [
    { required: true, message: '请输入邮箱', trigger: ['blur', 'change'] },
    { validator: (rule, value, callback) => {
      const v = String(value || '').trim()
      if (!v) return callback(new Error('请输入邮箱'))
      if (/\s/.test(v)) return callback(new Error('邮箱不能包含空格'))
      if (v.length > 254) return callback(new Error('邮箱长度不能超过254字符'))
      const parts = v.split('@')
      if (parts.length !== 2) return callback(new Error('请输入正确的邮箱格式'))
      if (parts[0].length === 0 || parts[0].length > 64) return callback(new Error('邮箱本地部分不能超过64字符'))
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
      if (!emailRegex.test(v)) return callback(new Error('请输入正确的邮箱格式'))
      callback()
    }, trigger: ['blur', 'change'] }
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: ['blur', 'change'] },
    { validator: (rule, value, callback) => {
      const v = String(value || '').trim()
      if (!v) return callback(new Error('请输入验证码'))
      if (!/^\d{6}$/.test(v)) return callback(new Error('验证码为6位数字'))
      callback()
    }, trigger: ['blur', 'change'] }
  ]
}

const registerRules = {
  userAccount: [
    { required: true, message: '请输入邮箱', trigger: ['blur', 'change'] },
    { validator: (rule, value, callback) => {
      const v = String(value || '').trim()
      if (!v) return callback(new Error('请输入邮箱'))
      if (/\s/.test(v)) return callback(new Error('邮箱不能包含空格'))
      if (v.length > 254) return callback(new Error('邮箱长度不能超过254字符'))
      const parts = v.split('@')
      if (parts.length !== 2) return callback(new Error('请输入正确的邮箱格式'))
      if (parts[0].length === 0 || parts[0].length > 64) return callback(new Error('邮箱本地部分不能超过64字符'))
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
      if (!emailRegex.test(v)) return callback(new Error('请输入正确的邮箱格式'))
      callback()
    }, trigger: ['blur', 'change'] }
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: ['blur', 'change'] },
    { validator: (rule, value, callback) => {
      const v = String(value || '').trim()
      if (!v) return callback(new Error('请输入验证码'))
      if (!/^\d{6}$/.test(v)) return callback(new Error('验证码为6位数字'))
      callback()
    }, trigger: ['blur', 'change'] }
  ]
}

// 发送登录验证码
const handleSendLoginCode = async () => {
  const valid = await new Promise(resolve => {
    if (!loginFormRef.value) return resolve(false)
    loginFormRef.value.validateField('userAccount', (errorMessage) => {
      resolve(!errorMessage)
    })
  })
  if (!valid) return
  
  loginCodeSending.value = true
  try {
    const success = await userStore.sendLoginVerificationCode(loginForm.userAccount)
    if (success) {
      loginCodeCountdown.value = 60
      const timer = setInterval(() => {
        loginCodeCountdown.value--
        if (loginCodeCountdown.value <= 0) {
          clearInterval(timer)
        }
      }, 1000)
    }
  } finally {
    loginCodeSending.value = false
  }
}

// 发送注册验证码
const handleSendRegisterCode = async () => {
  const valid = await new Promise(resolve => {
    if (!registerFormRef.value) return resolve(false)
    registerFormRef.value.validateField('userAccount', (errorMessage) => {
      resolve(!errorMessage)
    })
  })
  if (!valid) return
  
  registerCodeSending.value = true
  try {
    const success = await userStore.sendRegisterVerificationCode(registerForm.userAccount)
    if (success) {
      registerCodeCountdown.value = 60
      const timer = setInterval(() => {
        registerCodeCountdown.value--
        if (registerCodeCountdown.value <= 0) {
          clearInterval(timer)
        }
      }, 1000)
    }
  } finally {
    registerCodeSending.value = false
  }
}

// 登录
const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loginLoading.value = true
      const success = await userStore.userLogin(loginForm)
      loginLoading.value = false
      if (success) {
        router.push('/')
      }
    }
  })
}

// 注册
const handleRegister = async () => {
  if (!registerFormRef.value) return
  
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      registerLoading.value = true
      const success = await userStore.userRegister(registerForm)
      registerLoading.value = false
      if (success) {
        activeTab.value = 'login'
        loginForm.userAccount = registerForm.userAccount
        registerForm.userAccount = ''
        registerForm.verificationCode = ''
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 32px;
    color: #333;
    margin: 0 0 8px 0;
  }
  
  p {
    color: #666;
    margin: 0;
  }
}

.login-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }
}

.code-input-group {
  display: flex;
  gap: 12px;
  
  .el-input {
    flex: 1;
  }
}
</style>


