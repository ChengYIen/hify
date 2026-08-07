<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()

// =========================================================================
// Mode: login | register
// =========================================================================

const mode = ref<'login' | 'register'>('login')
const isLogin = computed(() => mode.value === 'login')

// =========================================================================
// Form
// =========================================================================

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  displayName: '',
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名 3–64 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== formData.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const rules = computed(() => (isLogin.value ? loginRules : registerRules))

// =========================================================================
// Toggle mode — reset form
// =========================================================================

function switchMode(target: 'login' | 'register') {
  mode.value = target
  formData.username = ''
  formData.password = ''
  formData.confirmPassword = ''
  formData.displayName = ''
  formRef.value?.resetFields()
}

// =========================================================================
// Submit
// =========================================================================

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const url = isLogin.value ? '/api/v1/auth/login' : '/api/v1/auth/register'
    const body: Record<string, string> = {
      username: formData.username,
      password: formData.password,
    }
    if (!isLogin.value) {
      body.displayName = formData.displayName || formData.username
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const result = await response.json()
    if (result.code !== 200) {
      ElMessage.error(result.message || (isLogin.value ? '登录失败' : '注册失败'))
      return
    }

    const { token, userId, username, displayName } = result.data
    localStorage.setItem('token', token)

    try {
      const { useUserStore } = await import('@/stores/user')
      useUserStore().setLogin({ id: userId, username, displayName, token })
    } catch {
      // store not critical
    }

    ElMessage.success(isLogin.value
      ? `欢迎回来，${displayName || username}`
      : `注册成功，欢迎 ${displayName || username}`)

    const redirect = (route.query.redirect as string) || '/provider'
    router.replace(redirect)
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : '网络异常，请稍后重试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- Logo -->
      <div class="login-header">
        <div class="login-logo">
          <span class="logo-h">H</span>
        </div>
        <h1 class="login-title">Hify</h1>
        <p class="login-subtitle">AI Agent 对话平台</p>
      </div>

      <!-- Form -->
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
        class="login-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <!-- 注册模式：显示名 -->
        <el-form-item v-if="!isLogin" label="显示名" prop="displayName">
          <el-input
            v-model="formData.displayName"
            placeholder="选填，默认同用户名"
            :prefix-icon="User"
            size="large"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <!-- 注册模式：确认密码 -->
        <el-form-item v-if="!isLogin" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="formData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="handleSubmit"
        >
          {{ loading ? (isLogin ? '登录中...' : '注册中...') : (isLogin ? '登 录' : '注 册') }}
        </el-button>
      </el-form>

      <!-- Footer -->
      <p class="login-footer">
        <template v-if="isLogin">
          没有账号？<a href="#" @click.prevent="switchMode('register')">立即注册</a>
        </template>
        <template v-else>
          已有账号？<a href="#" @click.prevent="switchMode('login')">返回登录</a>
        </template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f1ff 0%, #f8f9fb 50%, #f0fdf9 100%);
}

.login-card {
  width: 400px;
  padding: 40px 36px 32px;
  background: var(--hify-bg-container);
  border-radius: var(--hify-radius-xl);
  box-shadow: var(--hify-shadow-xl);
  border: 1px solid var(--hify-border-light);
}

/* ---- Header ---- */
.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--hify-primary-500), var(--hify-primary-400));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 20px rgba(99, 89, 255, 0.35);
}

.logo-h {
  font-size: 26px;
  font-weight: 800;
  color: #fff;
  line-height: 1;
}

.login-title {
  font-size: var(--hify-font-size-2xl);
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--hify-text-primary);
  margin: 0 0 4px;
}

.login-subtitle {
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-tertiary);
  margin: 0;
}

/* ---- Form ---- */
.login-form {
  margin-bottom: 0;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  font-weight: 600;
  letter-spacing: 2px;
}

/* ---- Footer ---- */
.login-footer {
  text-align: center;
  margin: 20px 0 0;
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-tertiary);
}
</style>
