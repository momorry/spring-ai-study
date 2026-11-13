<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <el-icon :size="36" color="#FAFAF5"><Monitor /></el-icon>
        <h2 class="login-title">青梧小筑</h2>
        <p class="login-subtitle">{{ isRegister ? '注册账号以继续' : '请登录以继续' }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        size="large"
        @keyup.enter="handleSubmit"
      >
        <el-form-item v-if="isRegister" prop="nickName">
          <el-input
            v-model="form.nickName"
            placeholder="请输入昵称（选填）"
            :prefix-icon="Avatar"
            maxlength="32"
            clearable
          />
        </el-form-item>

        <el-form-item prop="loginAcct">
          <el-input
            v-model="form.loginAcct"
            :placeholder="isRegister ? '请输入手机号或邮箱' : '请输入登录号'"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item v-if="isRegister" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ isRegister ? '注 册' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-switch">
        <el-link type="primary" :underline="false" @click="toggleMode">
          {{ isRegister ? '已有账号？去登录' : '还没有账号？去注册' }}
        </el-link>
      </div>

      <div v-if="!isRegister" class="login-tip">
        演示账号：<b>admin</b> / <b>123456</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const isRegister = ref(false)

const MOBILE_RE = /^1[3-9]\d{9}$/
const EMAIL_RE = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/

const form = reactive({
  nickName: '',
  loginAcct: '',
  password: '',
  confirmPassword: ''
})

// 注册时登录号仅允许手机号 / 邮箱，与后端校验规则保持一致
const validateAcct = (rule, value, callback) => {
  if (!value) return callback(new Error('请输入手机号或邮箱'))
  if (MOBILE_RE.test(value) || EMAIL_RE.test(value)) return callback()
  callback(new Error('登录号仅支持手机号或邮箱'))
}

const validateConfirm = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
  callback()
}

const rules = computed(() => {
  if (isRegister.value) {
    return {
      loginAcct: [{ required: true, validator: validateAcct, trigger: 'blur' }],
      password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
      ],
      confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
    }
  }
  return {
    loginAcct: [{ required: true, message: '请输入登录号', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
  }
})

// 登录/注册共用一个表单，切换时清空校验状态，避免残留提示
const toggleMode = () => {
  isRegister.value = !isRegister.value
  formRef.value?.clearValidate()
}

const handleLogin = async () => {
  await userStore.login({
    loginAcct: form.loginAcct,
    password: form.password
  })
  ElMessage.success('登录成功')
}

const handleRegister = async () => {
  await userStore.register({
    loginAcct: form.loginAcct,
    password: form.password,
    nickName: form.nickName || undefined
  })
  ElMessage.success('注册成功')
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      if (isRegister.value) {
        await handleRegister()
      } else {
        await handleLogin()
      }
      // 注册接口同样签发 token，登录后统一按 redirect 回目标页；排除跳回登录页本身的情况
      const redirect = route.query.redirect
      router.replace(redirect && redirect !== route.path ? redirect : '/')
    } catch (e) {
      ElMessage.error(e.message || (isRegister.value ? '注册失败' : '登录失败'))
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2C1810 0%, #3E2723 30%, #FAFAF5 70%, #F48C06 100%);
}

.login-box {
  width: 400px;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-title {
  margin: 12px 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.login-subtitle {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.login-form {
  margin-top: 8px;
}

.login-button {
  width: 100%;
}

.login-switch {
  margin-top: 4px;
  text-align: center;
  font-size: 13px;
}

.login-tip {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>
