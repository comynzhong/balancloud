<template>
  <div class="navbar">
    <div class="navbar-left">
      <hamburger id="hamburger-container" class="hamburger-container navbar-icon" @toggleClick="toggleSideBar" />
      <breadcrumb v-if="!settingsStore.topNav" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-if="settingsStore.topNav" id="topmenu-container" class="topmenu-container" />
    </div>

    <div class="navbar-right">
      <template v-if="appStore.device !== 'mobile'">
        <header-search id="header-search" class="navbar-search" />

        <button v-if="showAlertBell" type="button" class="navbar-icon alert-bell" title="未闭环告警" @click="goAlerts">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18.2 8.6a6.2 6.2 0 1 0-12.4 0c0 6.4-2.4 8.1-2.4 8.1h17.2s-2.4-1.7-2.4-8.1" />
            <path d="M13.7 20.4a2 2 0 0 1-3.4 0" />
          </svg>
          <span v-if="openAlerts > 0" class="alert-count">{{ openAlerts > 99 ? '99+' : openAlerts }}</span>
        </button>

        <button v-if="showMessageBell" type="button" class="navbar-icon message-bell" title="消息中心" @click="goMessages">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="5" width="18" height="14" rx="2" />
            <path d="m3.5 7 8.5 6 8.5-6" />
          </svg>
          <span v-if="messageUnread > 0" class="message-count">{{ messageUnread > 99 ? '99+' : messageUnread }}</span>
        </button>

        <screenfull id="screenfull" class="navbar-icon" />
        <span class="navbar-divider"></span>
      </template>

      <el-dropdown class="navbar-user" trigger="click" @command="handleCommand">
        <div class="user-card">
          <el-avatar :size="30" :src="userStore.avatar || undefined" class="user-avatar">{{ avatarInitial }}</el-avatar>
          <span class="user-meta">
            <span class="user-name">{{ displayName }}</span>
            <span class="user-role">{{ roleLabel }}</span>
          </span>
          <svg class="user-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="m6 9 6 6 6-6" />
          </svg>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item command="setLayout" v-if="settingsStore.showSettings">
              <span>布局设置</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from '@/components/TopNav'
import Hamburger from '@/components/Hamburger'
import Screenfull from '@/components/Screenfull'
import HeaderSearch from '@/components/HeaderSearch'
import { getAlertStats } from '@/api/balan/dashboard'
import { subscribeEventStream } from '@/utils/eventStream'
import { checkPermi } from '@/utils/permission'
import useAppStore from '@/store/modules/app'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import useMessageStore from '@/store/modules/message'

const appStore = useAppStore()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const messageStore = useMessageStore()
const router = useRouter()

const openAlerts = ref(0);
const showAlertBell = computed(() => checkPermi(['alert:list']));
const displayName = computed(() => userStore.nickName || userStore.name);
const avatarInitial = computed(() => (displayName.value || 'U').trim().slice(0, 1).toUpperCase());
const roleLabels = { admin: '超级管理员', operator: '场站运营', maintainer: '运维人员', viewer: '访客' };
const roleLabel = computed(() => {
  const first = (userStore.roles || [])[0];
  return roleLabels[first] || first || '—';
});

let alertTimer = null;
function loadAlerts() {
  if (!showAlertBell.value) {
    return;
  }
  getAlertStats().then(res => {
    const stats = res.data || {};
    openAlerts.value = (stats.openCount || 0) + (stats.acknowledgedCount || 0);
  }).catch(() => { });
}

function goAlerts() {
  router.push('/alert/record');
}

const messageUnread = computed(() => messageStore.unread);
const showMessageBell = computed(() => checkPermi(['system:message:list']));
let messageTimer = null;
let unsubscribeMessages = null;

function loadMessageUnread() {
  if (!showMessageBell.value) {
    return;
  }
  messageStore.refresh();
}

function goMessages() {
  router.push('/system/message');
}

function toggleSideBar() {
  appStore.toggleSideBar()
}

onMounted(() => {
  loadAlerts();
  alertTimer = setInterval(loadAlerts, 60000);
  loadMessageUnread();
  messageTimer = setInterval(loadMessageUnread, 60000);
  if (showMessageBell.value) {
    unsubscribeMessages = subscribeEventStream({ message: loadMessageUnread });
  }
})

onBeforeUnmount(() => {
  clearInterval(alertTimer);
  clearInterval(messageTimer);
  unsubscribeMessages?.();
})

function handleCommand(command) {
  switch (command) {
    case "profile":
      router.push('/user/profile');
      break;
    case "setLayout":
      setLayout();
      break;
    case "logout":
      logout();
      break;
    default:
      break;
  }
}

function logout() {
  ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logOut().then(() => {
      location.href = '/index';
    })
  }).catch(() => { });
}

const emits = defineEmits(['setLayout'])
function setLayout() {
  emits('setLayout');
}
</script>

<style lang='scss' scoped>
.navbar {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 58px;
  padding: 0 18px 0 10px;
  background: #fff;
  border-bottom: 1px solid #E7ECEA;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.navbar-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #475467;
  cursor: pointer;
  transition: background .15s ease, color .15s ease;
  -webkit-tap-highlight-color: transparent;

  &:hover {
    background: #F2F5F4;
    color: #101828;
  }

  svg {
    width: 18px;
    height: 18px;
  }

  .svg-icon {
    width: 18px;
    height: 18px;
  }
}

.alert-bell,
.message-bell {
  position: relative;

  .alert-count,
  .message-count {
    position: absolute;
    top: 1px;
    right: 0;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border: 2px solid #fff;
    border-radius: 9px;
    background: #F04438;
    color: #fff;
    font-size: 10px;
    font-weight: 600;
    line-height: 12px;
    text-align: center;
  }
}

.navbar-divider {
  flex: 0 0 auto;
  width: 1px;
  height: 20px;
  margin: 0 4px;
  background: #E7ECEA;
}

.navbar-user {
  .user-card {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 4px 8px 4px 4px;
    border-radius: 12px;
    cursor: pointer;
    outline: none;
    transition: background .15s ease;

    &:hover {
      background: #F2F5F4;
    }
  }

  .user-avatar {
    flex: 0 0 auto;
    border-radius: 10px;
    background: var(--bc-brand-deep);
    color: #fff;
    font-size: 13px;
    font-weight: 600;

    :deep(img) {
      object-fit: cover;
    }
  }

  .user-meta {
    display: flex;
    flex-direction: column;
    line-height: 1.25;
    max-width: 132px;
  }

  .user-name {
    overflow: hidden;
    color: #101828;
    font-size: 12.5px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .user-role {
    overflow: hidden;
    color: #98A2B3;
    font-size: 10.5px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .user-chevron {
    flex: 0 0 auto;
    width: 13px;
    height: 13px;
    color: #98A2B3;
  }
}
</style>