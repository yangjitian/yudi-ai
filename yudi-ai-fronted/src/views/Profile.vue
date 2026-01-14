<template>
  <div class="profile-page">
    <div class="profile-card glass-card">
      <div class="profile-header">
        <h2>个人资料</h2>
        <p>管理您的账号信息和联系方式</p>
      </div>

      <el-divider />

      <div class="profile-section">
        <div class="avatar-block">
          <el-avatar :size="80" :src="profileForm.userAvatar">
            {{ profileForm.userName?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="avatar-actions">
            <el-upload
              class="avatar-upload"
              accept="image/*"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
              :disabled="uploadingAvatar"
            >
              <el-button type="primary" :loading="uploadingAvatar" class="gradient-btn">
                上传头像
              </el-button>
            </el-upload>
            <span class="avatar-tip">支持 JPG / PNG / GIF / BMP / WEBP，大小不超过 4MB</span>
          </div>
        </div>

        <el-form :model="profileForm" label-width="90px" class="profile-form" :disabled="basicSaving">
          <el-form-item label="用户ID">
            <el-input v-model="profileForm.id" disabled class="glass-input" />
          </el-form-item>
          <el-form-item label="用户昵称">
            <el-input v-model="profileForm.userName" placeholder="请输入昵称" class="glass-input" />
          </el-form-item>
          <el-form-item label="登录账号">
            <div class="account-row">
              <el-input v-model="profileForm.userAccount" disabled class="glass-input" />
              <el-button type="primary" link @click="handleChangeAccountClick">
                更换邮箱
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <div class="actions-row">
          <el-button @click="goBack">返回聊天</el-button>
          <el-button type="primary" :loading="basicSaving" @click="handleSaveBasicProfile" class="gradient-btn">
            保存资料
          </el-button>
        </div>
      </div>
    </div>

    <!-- 换绑邮箱 - 验证码输入弹窗 -->
    <el-dialog
      v-model="verifyDialogVisible"
      title="邮箱换绑验证"
      width="420px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">
        我们已经向您当前绑定的邮箱
        <span class="highlight">{{ profileForm.userAccount }}</span>
        发送了一条验证码，请输入验证码完成安全验证。
      </p>
      <el-form :model="verifyForm" label-width="80px">
        <el-form-item label="验证码">
          <el-input
            v-model="verifyForm.code"
            placeholder="请输入6位验证码"
            maxlength="6"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="verifyDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="verifyLoading" @click="handleVerifyCode">
            确 认
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 换绑邮箱 - 新邮箱输入弹窗 -->
    <el-dialog
      v-model="newEmailDialogVisible"
      title="输入新邮箱"
      width="420px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">
        验证通过，请输入要绑定的新邮箱地址。
      </p>
      <el-form :model="newEmailForm" label-width="80px">
        <el-form-item label="新邮箱">
          <el-input
            v-model="newEmailForm.email"
            placeholder="请输入新的邮箱账号"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="newEmailDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="newEmailLoading" @click="handleSubmitNewEmail">
            确认换绑
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const profileForm = ref({
  id: '',
  userName: '',
  userAccount: '',
  userAvatar: ''
})

const basicSaving = ref(false)
const uploadingAvatar = ref(false)

const verifyDialogVisible = ref(false)
const newEmailDialogVisible = ref(false)
const verifyLoading = ref(false)
const newEmailLoading = ref(false)

const verifyForm = ref({
  code: ''
})

const newEmailForm = ref({
  email: ''
})

const initProfile = () => {
  const info = userStore.userInfo
  if (!info) {
    return
  }
  profileForm.value = {
    id: info.id,
    userName: info.userName,
    userAccount: info.userAccount,
    userAvatar: info.userAvatar
  }
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    const restored = await userStore.restoreUserState()
    if (!restored) {
      router.replace('/login')
      return
    }
  }
  if (!userStore.userInfo) {
    try {
      // 如果有 token 但本地 userInfo 丢失，尝试从后端恢复
      const ok = await userStore.restoreUserState()
      if (!ok) {
        router.replace('/login')
        return
      }
    } catch {
      router.replace('/login')
      return
    }
  }
  initProfile()
})

const goBack = () => {
  router.push('/')
}

const handleSaveBasicProfile = async () => {
  basicSaving.value = true
  try {
    const success = await userStore.updateProfile({
      userName: profileForm.value.userName,
      userAvatar: profileForm.value.userAvatar
    })
    if (success) {
      initProfile()
    }
  } finally {
    basicSaving.value = false
  }
}

const handleChangeAccountClick = () => {
  ElMessageBox.confirm(
    '确定要更换当前绑定的邮箱吗？后续登录需要使用新邮箱账号。',
    '确认换绑',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    const ok = await userStore.sendChangeAccountVerificationCode()
    if (ok) {
      verifyForm.value.code = ''
      verifyDialogVisible.value = true
    }
  }).catch(() => {})
}

const handleVerifyCode = async () => {
  if (!verifyForm.value.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  verifyLoading.value = true
  try {
    const ok = await userStore.verifyChangeAccountVerificationCode(verifyForm.value.code)
    if (ok) {
      verifyDialogVisible.value = false
      newEmailForm.value.email = ''
      newEmailDialogVisible.value = true
    }
  } finally {
    verifyLoading.value = false
  }
}

const handleSubmitNewEmail = async () => {
  if (!newEmailForm.value.email) {
    ElMessage.warning('请输入新邮箱')
    return
  }
  newEmailLoading.value = true
  try {
    const success = await userStore.updateProfile({
      userAccount: newEmailForm.value.email
    })
    if (success) {
      newEmailDialogVisible.value = false
      initProfile()
    }
  } finally {
    newEmailLoading.value = false
  }
}

const beforeAvatarUpload = (file) => {
  const isImage = file.type?.startsWith('image/')
  const isLt4M = file.size / 1024 / 1024 < 4

  if (!isImage) {
    ElMessage.error('请上传图片类型的文件')
  }
  if (!isLt4M) {
    ElMessage.error('头像大小不能超过 4MB')
  }

  return isImage && isLt4M
}

const handleAvatarUpload = async ({ file, onSuccess, onError }) => {
  uploadingAvatar.value = true
  try {
    const avatarUrl = await userStore.uploadAvatarFile(file)
    if (!avatarUrl) {
      onError?.(new Error('上传失败'))
      return
    }
    profileForm.value.userAvatar = avatarUrl
    await userStore.updateProfile({
      userAvatar: avatarUrl
    })
    initProfile()
    onSuccess?.(avatarUrl)
  } catch (error) {
    onError?.(error)
  } finally {
    uploadingAvatar.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  // Background handled globally in App.vue
  padding: 24px;
}

.profile-card {
  width: 100%;
  max-width: 720px;
  // Use glass-card visualization via class attribute in template
  
  padding: 40px;
}

.profile-header {
  text-align: center;
  margin-bottom: 30px;

  h2 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
    color: #1e293b;
    background: linear-gradient(to right, #6366f1, #ec4899);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  p {
    margin-top: 8px;
    margin-bottom: 0;
    color: #64748b;
    font-size: 14px;
  }
}

.profile-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.avatar-block {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 10px;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.avatar-upload {
  width: fit-content;
}

.avatar-tip {
  font-size: 12px;
  color: #94a3b8;
}

// Override removed in favor of global .glass-input class

.profile-form {
  margin-top: 10px;
}

.account-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.actions-row {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 20px;
  
  .el-button {
    min-width: 120px;
    height: 40px;
    border-radius: 10px;
  }
}

.dialog-tip {
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 20px;

  .highlight {
    color: #6366f1;
    font-weight: 600;
  }
}

.dialog-footer {
  display: inline-flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

@media (max-width: 768px) {
  .profile-card {
    padding: 24px 20px;
  }

  .avatar-block {
    flex-direction: column;
    align-items: center;
    width: 100%;
  }
}
</style>


