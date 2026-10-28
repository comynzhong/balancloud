<template>
  <div class="app-container profile-page" v-loading="loading">
    <div class="page-head">
      <div>
        <p class="page-kicker">ACCOUNT · PROFILE</p>
        <h2>个人中心</h2>
        <p class="page-desc">
          当前登录账号自己的资料与密码。角色、场站范围与账号状态由管理员在「系统管理 → 用户管理」维护，本页改不了。
        </p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" :xs="24">
        <el-card class="identity-card" shadow="never">
          <div class="identity-top">
            <el-avatar :size="72" :src="user.avatar || undefined" class="identity-avatar">{{ avatarInitial }}</el-avatar>
            <div class="identity-name">{{ user.nickName || user.userName || '-' }}</div>
            <div class="identity-account">{{ user.userName || '-' }}</div>
          </div>
          <dl class="identity-list">
            <div class="identity-item">
              <dt>所属运营商</dt>
              <dd>{{ tenantName || '-' }}</dd>
            </div>
            <div class="identity-item">
              <dt>角色</dt>
              <dd class="identity-tags">
                <el-tag v-for="name in roleNames" :key="name" size="small" effect="plain">{{ name }}</el-tag>
                <span v-if="!roleNames.length" class="muted">未分配角色</span>
              </dd>
            </div>
            <div class="identity-item">
              <dt>账号状态</dt>
              <dd>
                <el-tag size="small" effect="plain" :type="user.status === '0' ? 'success' : 'danger'">
                  {{ userStatusLabel(user.status) }}
                </el-tag>
              </dd>
            </div>
            <div class="identity-item">
              <dt>手机号码</dt>
              <dd>{{ user.phoneNumber || '-' }}</dd>
            </div>
            <div class="identity-item">
              <dt>邮箱</dt>
              <dd class="identity-wrap">{{ user.email || '-' }}</dd>
            </div>
            <div class="identity-item">
              <dt>创建时间</dt>
              <dd>{{ formatTime(user.createTime) }}</dd>
            </div>
            <div class="identity-item">
              <dt>最近登录</dt>
              <dd>
                {{ formatTime(user.loginDate) }}
                <span v-if="user.loginIp" class="muted">· {{ user.loginIp }}</span>
              </dd>
            </div>
            <div class="identity-item">
              <dt>密码更新</dt>
              <dd>{{ formatTime(user.pwdUpdateDate) }}</dd>
            </div>
          </dl>
        </el-card>
      </el-col>

      <el-col :span="16" :xs="24">
        <el-card shadow="never">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本资料" name="userinfo">
              <userInfo :user="user" @saved="onProfileSaved" />
            </el-tab-pane>
            <el-tab-pane label="修改密码" name="resetPwd">
              <resetPwd />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Profile">
import userInfo from './userInfo'
import resetPwd from './resetPwd'
import { getUserProfile } from '@/api/system/user'
import { formatTime, userStatusLabel } from '@/utils/balan'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()

const loading = ref(true)
const activeTab = ref('userinfo')
const user = ref({})
const tenantName = ref('')

const roleNames = computed(() => user.value.roleNames || [])
const avatarInitial = computed(() => ((user.value.nickName || user.value.userName || 'U').trim().slice(0, 1)).toUpperCase())

function load() {
  loading.value = true
  return getUserProfile()
    .then(res => {
      user.value = res.data?.user || {}
      tenantName.value = res.data?.tenantName || ''
    })
    .finally(() => {
      loading.value = false
    })
}

/** 昵称同时显示在顶栏，改完同步 store，避免要刷新才更新。 */
function onProfileSaved(form) {
  user.value = { ...user.value, ...form }
  if (form.nickName) {
    userStore.nickName = form.nickName
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.profile-page {
  .identity-card {
    border-radius: 14px;
  }
}

.identity-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 0 18px;
  border-bottom: 1px solid var(--bc-border);
}

.identity-avatar {
  background: var(--bc-brand-deep);
  color: #fff;
  font-size: 30px;
  font-weight: 600;
}

.identity-name {
  color: var(--bc-text);
  font-size: 17px;
  font-weight: 600;
}

.identity-account {
  color: var(--bc-text-muted);
  font-size: 12.5px;
  letter-spacing: 0.02em;
}

.identity-list {
  margin: 14px 0 0;
}

.identity-item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--bc-border);

  &:last-child {
    border-bottom: none;
  }

  dt {
    flex: none;
    color: var(--bc-text-muted);
    font-size: 12.5px;
  }

  dd {
    margin: 0;
    color: var(--bc-text);
    font-size: 13px;
    text-align: right;
  }
}

.identity-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}

.identity-wrap {
  word-break: break-all;
}

.muted {
  color: var(--bc-text-muted);
  font-size: 12px;
}
</style>
