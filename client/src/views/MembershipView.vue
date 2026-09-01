<template>
  <!-- Fixed Header sits outside the form container so the form never
       overlaps the title bar even with absolute-positioned toolbars. -->
  <Header :title="$t('membership.title')" :show-back="true" back-route="/" />

  <div class="membership-container">
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- User login/register form -->
    <div class="login-form" v-if="!isLoggedIn">
      <GlassForm @submit.prevent="handleLogin">
        <GlassFormItem :label="$t('membership.username')" prop="username" error="">
          <GlassInput 
            v-model="loginForm.username" 
            :placeholder="$t('membership.usernamePlaceholder')" 
            showWordLimit
            :maxlength="20"
          ></GlassInput>
        </GlassFormItem>
        <GlassFormItem>
          <GlassButton type="primary" @click="handleLogin" :disabled="isLoggingIn">
            {{ $t('membership.loginOrRegister') }}
          </GlassButton>
        </GlassFormItem>
      </GlassForm>
    </div>
    
    <!-- User info and membership status card -->
    <div class="user-info-card" v-if="isLoggedIn && userInfo">
      <div class="user-info-content">
        <AvatarUpload
          v-model="avatarUrl"
          @avatar-uploaded="handleAvatarUploaded"
          :max-size="10"
          :size="100"
          :username="userInfo?.username"
        />
        <div class="user-details">
          <p>{{ userInfo.username }}</p>
        </div>
      </div>
      <GlassButton 
        type="warning" 
        @click="logout"
        style="margin-left: 10px"
      >
        {{ $t('membership.logout') }}
      </GlassButton>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Header from '@/components/Header.vue';
import GlassButton from '@/components/GlassButton.vue';
import GlassForm from '@/components/GlassForm.vue';
import GlassFormItem from '@/components/GlassFormItem.vue';
import GlassInput from '@/components/GlassInput.vue';
import MessageTip from '@/components/MessageTip.vue';
import AvatarUpload from '@/components/AvatarUpload.vue';
import '../styles/ui/membership.css';

export default {
  name: 'MembershipView',
  components: {
    GlassButton,
    GlassForm,
    GlassFormItem,
    GlassInput,
    MessageTip,
    AvatarUpload
  },
  setup() {
    const router = useRouter()
    const { t, locale } = useI18n()
    const isLoggingIn = ref(false)
    const userInfo = ref(null)
    const successMessage = ref('')
    const errorMessage = ref('')
    const avatarUrl = ref('')
    const isLoggedIn = ref(false)
    const loginFormRef = ref(null)
    const loginForm = ref({
      username: ''
    })
    const formLabelWidth = '100px'
    
    // Go to home
    const goToHome = () => {
      router.push('/')
    }
    
    // Login form validation rules
    const loginRules = {
      username: [
        { required: true, message: t('membership.error.requiredUsername'), trigger: 'blur' },
        { pattern: /^[A-Za-z]+$/, message: t('membership.error.usernameFormat'), trigger: 'blur' },
        { min: 1, max: 20, message: t('membership.error.usernameLength'), trigger: 'blur' }
      ]
    }
    
    // Get username from local storage
    const getUsername = () => {
      return localStorage.getItem('username') || ''
    }

    // Get avatar from local storage
    const getAvatar = () => {
      return localStorage.getItem('avatar-' + getUsername()) || ''
    }

    // Save avatar to local storage
    const saveAvatar = (avatarDataUrl) => {
      localStorage.setItem('avatar-' + getUsername(), avatarDataUrl)
      avatarUrl.value = avatarDataUrl
    }

    // Handle avatar uploaded
    const handleAvatarUploaded = (avatarDataUrl) => {
      saveAvatar(avatarDataUrl)
      successMessage.value = t('membership.success.avatarUploaded')
    }
    
    // Check login status
    const checkLoginStatus = () => {
      const username = getUsername()
      isLoggedIn.value = !!username
      loginForm.value.username = username
      if (isLoggedIn.value) {
        userInfo.value = { username }
        avatarUrl.value = getAvatar()
      } else {
        userInfo.value = null
        avatarUrl.value = ''
      }
    }
    
    // Login or register
    const handleLogin = async () => {
      if (!loginForm.value.username.trim()) {
        errorMessage.value = t('membership.error.requiredUsername')
        return
      }
      
      // Validate username format, only allow uppercase and lowercase letters
      const usernamePattern = /^[A-Za-z]+$/
      if (!usernamePattern.test(loginForm.value.username)) {
        errorMessage.value = t('membership.error.usernameFormat')
        return
      }
      
      try {
        isLoggingIn.value = true
        
        // Save username to local storage
        localStorage.setItem('username', loginForm.value.username)
        isLoggedIn.value = true
        
        // Set user info
        userInfo.value = { username: loginForm.value.username }
        avatarUrl.value = getAvatar()
        
        successMessage.value = t('membership.success.login')
      } catch (error) {
        console.error('Login failed:', error)
        errorMessage.value = t('membership.error.loginFailed')
      } finally {
        isLoggingIn.value = false
      }
    }
    
    // Logout
    const logout = () => {
      localStorage.removeItem('username')
      isLoggedIn.value = false
      userInfo.value = null
      successMessage.value = t('membership.success.logout')
    }
    
    
    // Fetch data on page load
    onMounted(() => {
      // Check login status on page load
      checkLoginStatus()
    })
    
    return {
      isLoggingIn,
      userInfo,
      isLoggedIn,
      loginForm,
      loginFormRef,
      loginRules,
      formLabelWidth,
      handleLogin,
      logout,
      successMessage,
      errorMessage,
      goToHome,
      avatarUrl,
      handleAvatarUploaded
    }
  }
}
</script>