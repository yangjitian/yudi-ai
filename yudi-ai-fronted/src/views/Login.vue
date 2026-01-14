<template>
  <div class="login-container">
    <div class="login-box glass-card">
      <div class="login-header">
        <div class="logo-icon">🍽️</div>
        <h1>雨落有味</h1>
        <p>您的私人AI烹饪助手</p>
      </div>

      
      <el-tabs v-model="activeTab" class="login-tabs" stretch>
        <el-tab-pane label="Let's Cook!" name="login">
          <div class="tab-content-wrapper">
             <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-width="0" size="large">
              <el-form-item prop="userAccount">
                <el-input
                  v-model="loginForm.userAccount"
                  placeholder="请输入邮箱"
                  prefix-icon="Message"
                  class="custom-input"
                />
              </el-form-item>
              <el-form-item prop="verificationCode">
                <div class="code-input-group">
                  <el-input
                    v-model="loginForm.verificationCode"
                    placeholder="验证码"
                    prefix-icon="Key"
                    maxlength="6"
                    class="custom-input"
                  />
                  <el-button
                    type="primary"
                    plain
                    :disabled="loginCodeCountdown > 0"
                    :loading="loginCodeSending"
                    @click="handleSendLoginCode"
                    class="code-btn"
                  >
                    {{ loginCodeCountdown > 0 ? `${loginCodeCountdown}s` : '获取' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  :loading="loginLoading"
                  @click="handleLogin"
                  class="submit-btn"
                >
                  立即登录
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="Join Us" name="register">
           <div class="tab-content-wrapper">
            <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0" size="large">
              <el-form-item prop="userAccount">
                <el-input
                  v-model="registerForm.userAccount"
                  placeholder="请输入邮箱"
                  prefix-icon="Message"
                  class="custom-input"
                />
              </el-form-item>
              <el-form-item prop="verificationCode">
                 <div class="code-input-group">
                  <el-input
                    v-model="registerForm.verificationCode"
                    placeholder="验证码"
                    prefix-icon="Key"
                    maxlength="6"
                    class="custom-input"
                  />
                  <el-button
                    type="primary"
                    plain
                    :disabled="registerCodeCountdown > 0"
                    :loading="registerCodeSending"
                    @click="handleSendRegisterCode"
                    class="code-btn"
                  >
                     {{ registerCodeCountdown > 0 ? `${registerCodeCountdown}s` : '获取' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  :loading="registerLoading"
                  @click="handleRegister"
                  class="submit-btn register-btn"
                >
                  注册账号
                </el-button>
              </el-form-item>
            </el-form>
          </div>
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
import { Message, Key } from '@element-plus/icons-vue'

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

const commonRules = {
  userAccount: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '需6位数字', trigger: 'blur' }
  ]
}

const loginRules = { ...commonRules }
const registerRules = { ...commonRules }

// 发送登录验证码
const handleSendLoginCode = async () => {
  if (!loginForm.userAccount) return ElMessage.warning('请先输入邮箱')
  
  loginCodeSending.value = true
  try {
    const success = await userStore.sendLoginVerificationCode(loginForm.userAccount)
    if (success) {
      startCountdown(loginCodeCountdown)
      ElMessage.success('验证码已发送')
    }
  } finally {
    loginCodeSending.value = false
  }
}

// 发送注册验证码
const handleSendRegisterCode = async () => {
  if (!registerForm.userAccount) return ElMessage.warning('请先输入邮箱')
  
  registerCodeSending.value = true
  try {
    const success = await userStore.sendRegisterVerificationCode(registerForm.userAccount)
    if (success) {
      startCountdown(registerCodeCountdown)
      ElMessage.success('验证码已发送')
    }
  } finally {
    registerCodeSending.value = false
  }
}

const startCountdown = (countdownRef) => {
  countdownRef.value = 60
  const timer = setInterval(() => {
    countdownRef.value--
    if (countdownRef.value <= 0) clearInterval(timer)
  }, 1000)
}

// 登录
const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loginLoading.value = true
      try {
        const success = await userStore.userLogin(loginForm)
        if (success) router.push('/')
      } finally {
        loginLoading.value = false
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
      try {
        const success = await userStore.userRegister(registerForm)
        if (success) {
          activeTab.value = 'login'
          loginForm.userAccount = registerForm.userAccount
          resetRegisterForm()
          ElMessage.success('注册成功，请登录')
        }
      } finally {
        registerLoading.value = false
      }
    }
  })
}

const resetRegisterForm = () => {
  registerForm.userAccount = ''
  registerForm.verificationCode = ''
}
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  // Background handled globally in App.vue
}

// .login-box styles... inherited largely from glass-card but can add specifics
.login-box {
  width: 90%;
  max-width: 420px;
  padding: 40px 30px;
  // glass-card class handles the visual style
}


.login-header {
  text-align: center;
  margin-bottom: 30px;
  
  .logo-icon {
    font-size: 48px;
    margin-bottom: 10px;
    animation: bounce 2s infinite;
  }
  
  h1 {
    font-size: 28px;
    font-weight: 700;
    color: #1e293b;
    margin: 0;
    letter-spacing: 1px;
    background: linear-gradient(to right, #6366f1, #ec4899);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  
  p {
    color: #64748b;
    margin-top: 8px;
    font-size: 14px;
  }
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

// Form Styling
.tab-content-wrapper {
  padding-top: 10px;
}

:deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: rgba(0,0,0,0.05);
}

:deep(.el-tabs__item) {
  font-size: 16px;
  color: #64748b;
  &.is-active {
    color: #6366f1;
    font-weight: 600;
  }
}

:deep(.el-tabs__active-bar) {
  background-color: #6366f1;
  height: 3px;
  border-radius: 3px;
}

:deep(.el-input__wrapper) {
  background-color: rgba(255,255,255,0.6);
  box-shadow: none;
  border: 1px solid rgba(0,0,0,0.05);
  transition: all 0.3s ease;
  border-radius: 12px;
  padding: 8px 11px;
  
  &:hover, &.is-focus {
    box-shadow: 0 0 0 1px #6366f1 inset;
    background-color: rgba(255,255,255,0.9);
  }
}

.code-input-group {
  display: flex;
  gap: 12px;
  
  .el-input {
    flex: 1;
  }
  
  .code-btn {
    border-radius: 12px;
    padding: 12px 20px;
    font-weight: 600;
    min-width: 100px;
  }
}

.submit-btn {
  width: 100%;
  border-radius: 12px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  box-shadow: 0 4px 14px 0 rgba(99, 102, 241, 0.39);
  background-image: linear-gradient(to right, #6366f1, #8b5cf6);
  border: none;
  transition: all 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px 0 rgba(99, 102, 241, 0.23);
  }
  
  &:active {
     transform: translateY(1px);
  }
}

.register-btn {
  background-image: linear-gradient(to right, #ec4899, #8b5cf6);
  box-shadow: 0 4px 14px 0 rgba(236, 72, 153, 0.39);
  
  &:hover {
      box-shadow: 0 6px 20px 0 rgba(236, 72, 153, 0.23);
  }
}
</style>
