<template>
  <div class="login-shell">
    <header class="brand">
      <div class="brand-row">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="4" width="18" height="16" rx="4" />
            <path d="M7 9v3M11 9v3M15 9v3M19 9v3M7 16h10" />
          </svg>
        </span>
        <div class="brand-text">
          <div class="mark-cn">{{ runtime.brand.name }}</div>
          <div class="mark-en">
            BALANCLOUD
            <span v-if="runtime.multiTenantEnabled" class="mark-tag">多租户</span>
          </div>
        </div>
      </div>
      <p class="brand-sub">{{ runtime.brand.subtitle }}</p>
    </header>

    <div class="feature-field">
      <article class="fcard fcard-protocol">
        <div class="fc-head">
          <span class="fc-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l7 3v5.4c0 4.2-2.9 7.7-7 9.6-4.1-1.9-7-5.4-7-9.6V6l7-3z" />
              <path d="M9 11.6l2.1 2.1L15.2 9.6" />
            </svg>
          </span>
          <div class="fc-titles">
            <h3>双协议接入</h3>
            <p>云快充 MQTT + OCPP 1.6J</p>
          </div>
        </div>
        <svg class="fc-chart" viewBox="0 0 120 36" aria-hidden="true">
          <g fill="rgba(0, 168, 112, 0.3)">
            <rect x="2" y="22" width="9" height="14" rx="2" />
            <rect x="16" y="14" width="9" height="22" rx="2" />
            <rect x="30" y="26" width="9" height="10" rx="2" />
            <rect x="44" y="10" width="9" height="26" rx="2" />
            <rect x="58" y="18" width="9" height="18" rx="2" />
            <rect x="72" y="6" width="9" height="30" rx="2" />
            <rect x="86" y="24" width="9" height="12" rx="2" />
            <rect x="100" y="16" width="9" height="20" rx="2" />
          </g>
        </svg>
      </article>

      <article class="fcard fcard-alert">
        <div class="fc-head">
          <span class="fc-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 15.5V11a6 6 0 1 0-12 0v4.5L4.5 18h15L18 15.5z" />
              <path d="M10 20.2a2.2 2.2 0 0 0 4 0" />
            </svg>
          </span>
          <div class="fc-titles">
            <h3>实时告警</h3>
            <p>阈值判定 · 确认与恢复留痕</p>
          </div>
        </div>
        <svg class="fc-chart fc-radar" viewBox="0 0 120 70" aria-hidden="true">
          <ellipse cx="60" cy="52" rx="42" ry="9" fill="rgba(0, 168, 112, 0.07)" />
          <path d="M60 6l44 22-44 22-44-22z" fill="rgba(255, 255, 255, 0.52)" stroke="rgba(0, 168, 112, 0.3)" stroke-width="1.2" stroke-linejoin="round" />
          <path d="M60 22l28 14-28 14-28-14z" fill="rgba(0, 168, 112, 0.1)" stroke="rgba(0, 168, 112, 0.4)" stroke-width="1.2" stroke-linejoin="round" />
          <path d="M60 34l12 6-12 6-12-6z" fill="rgba(0, 168, 112, 0.16)" stroke="rgba(0, 168, 112, 0.5)" stroke-width="1.2" stroke-linejoin="round" />
          <circle cx="60" cy="49" r="2.2" fill="rgba(0, 168, 112, 0.6)" />
        </svg>
      </article>

      <article class="fcard fcard-tenant">
        <div class="fc-head">
          <span class="fc-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3.2l8.4 4.5L12 12.2 3.6 7.7 12 3.2z" />
              <path d="M3.6 12.2l8.4 4.5 8.4-4.5" />
              <path d="M3.6 16.6l8.4 4.5 8.4-4.5" />
            </svg>
          </span>
          <div class="fc-titles">
            <h3>多租户隔离</h3>
            <p>租户与场站两级数据权限</p>
          </div>
        </div>
        <svg class="fc-chart" viewBox="0 0 120 36" aria-hidden="true">
          <polyline
            points="0,28 14,20 28,24 42,12 56,17 70,7 84,13 98,5 112,9"
            fill="none"
            stroke="rgba(0, 168, 112, 0.38)"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </article>

      <article class="fcard fcard-chip chip-device">
        <div class="fc-head">
          <span class="fc-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 3v4M15 3v4" />
              <path d="M6.5 7h11v3.2a5.5 5.5 0 0 1-11 0V7z" />
              <path d="M12 15.7V21" />
            </svg>
          </span>
          <div class="fc-titles">
            <h3>仿真器</h3>
            <p>无真桩跑通全链路</p>
          </div>
        </div>
        <svg class="fc-spark" viewBox="0 0 48 18" aria-hidden="true">
          <polyline
            points="1,14 7,9 13,12 19,5 25,10 31,6 37,11 43,3 47,7"
            fill="none"
            stroke="rgba(0, 168, 112, 0.42)"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </article>

      <article class="fcard fcard-chip chip-billing">
        <div class="fc-head">
          <span class="fc-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="8" />
              <path d="M12 7.6V12l3 1.8" />
            </svg>
          </span>
          <div class="fc-titles">
            <h3>分时计费</h3>
            <p>峰平谷费率 · 时长 / 电量</p>
          </div>
        </div>
        <svg class="fc-spark" viewBox="0 0 48 18" aria-hidden="true">
          <polyline
            points="1,13 7,8 13,11 19,6 25,9 31,4 37,8 43,2 47,6"
            fill="none"
            stroke="rgba(0, 168, 112, 0.42)"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </article>
    </div>

    <p class="page-foot">AGPL-3.0 Open Source · 可自部署运营底座</p>

    <main class="entry">
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-card">
        <h3 class="title">欢迎登录</h3>
        <p class="subtitle">登录运营后台，查看实时充电与经营看板</p>

        <div v-if="runtime.maintenance.enabled" class="maintenance-tip" role="status">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3.6l8.4 14.8H3.6L12 3.6z" />
            <path d="M12 9.4v3.8M12 16.2v.2" />
          </svg>
          <div>
            <strong>维护模式</strong>
            <span>{{ runtime.maintenance.message || '平台正在维护，小程序暂停新充电业务。' }}</span>
          </div>
        </div>

        <el-form-item prop="tenantCode">
          <label class="field-label" for="login-tenant">租户编码<span class="field-optional">选填</span></label>
          <el-input
            id="login-tenant"
            v-model="loginForm.tenantCode"
            type="text"
            size="large"
            auto-complete="off"
            aria-label="租户编码"
            placeholder="自建运营商填写编码，默认运营商留空"
          >
            <template #prefix><svg-icon icon-class="guide" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="username">
          <label class="field-label" for="login-username">账号</label>
          <el-input
            id="login-username"
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            aria-label="账号"
            placeholder="管理员或运营账号"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <label class="field-label" for="login-password">密码</label>
          <el-input
            id="login-password"
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            aria-label="密码"
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>
        <div
          v-if="captchaEnabled"
          class="verify-row"
          :class="{ 'is-verified': !!loginForm.captchaToken }"
          role="button"
          tabindex="0"
          @click="openCaptcha(false)"
          @keydown.enter.prevent="openCaptcha(false)"
          @keydown.space.prevent="openCaptcha(false)"
        >
          <span class="verify-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l7 3v5.4c0 4.2-2.9 7.7-7 9.6-4.1-1.9-7-5.4-7-9.6V6l7-3z" />
              <path d="M9.4 12.2l1.9 1.9 3.5-3.6" />
            </svg>
          </span>
          <span class="verify-text">{{ loginForm.captchaToken ? "安全验证已通过" : "登录前需完成安全验证" }}</span>
          <span class="verify-action">{{ loginForm.captchaToken ? "重新验证" : "点击验证" }}</span>
        </div>

        <div class="form-row">
          <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
          <span class="form-hint">管理员 / 运营账号</span>
          <router-link v-if="register" class="link-type" :to="'/register'">立即注册</router-link>
        </div>

        <el-form-item class="submit-item">
          <el-button
            :loading="loading"
            size="large"
            type="primary"
            class="submit-btn"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
            <svg
              v-if="!loading"
              class="btn-arrow"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M4 12h15M13 6l6 6-6 6" />
            </svg>
          </el-button>
        </el-form-item>

        <div v-if="registration.enabled" class="register-entry">
          <span class="register-tip">还没有运营商账号？</span>
          <el-button link type="primary" class="register-link" @click="openRegistration">申请入驻</el-button>
        </div>

        <div class="card-meta">
          <span>忘记密码请联系管理员</span>
          <i aria-hidden="true">·</i>
          <span>登录凭证仅保存在本机会话</span>
        </div>
      </el-form>
    </main>

    <el-dialog
      v-model="registration.visible"
      class="register-dialog"
      title="运营商入驻申请"
      width="min(460px, 92vw)"
      align-center
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="registration.receipt = null"
    >
      <p class="register-desc">
        填写后由平台管理员审核，审核通过会创建租户与管理员账号，初始密码只展示一次。
      </p>

      <template v-if="registration.receipt">
        <div class="register-done" role="status">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M8.4 12.4l2.4 2.4 4.8-5.2" />
          </svg>
          <div>
            <strong>受理号 {{ registration.receipt.applicationNo }}</strong>
            <span>{{ registration.receipt.message }}</span>
          </div>
        </div>
      </template>
      <el-form
        v-else
        ref="registerRef"
        :model="registration.form"
        :rules="registrationRules"
        label-position="top"
      >
        <el-form-item label="运营商名称" prop="tenantName">
          <el-input
            v-model="registration.form.tenantName"
            maxlength="50"
            show-word-limit
            size="large"
            placeholder="例如：城南充电运营有限公司"
          />
        </el-form-item>
        <div class="register-pair">
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="registration.form.contactName" maxlength="30" size="large" placeholder="审核结果联系此人" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="registration.form.phone" maxlength="20" size="large" placeholder="6-20 位数字" />
          </el-form-item>
        </div>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registration.form.email" maxlength="100" size="large" placeholder="选填，用于接收开通通知" />
        </el-form-item>
        <el-form-item label="场站与规模说明" prop="remark">
          <el-input
            v-model="registration.form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填，例如场站数量、设备类型、期望上线时间"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button size="large" @click="registration.visible = false">
          {{ registration.receipt ? "关闭" : "取消" }}
        </el-button>
        <el-button
          v-if="!registration.receipt"
          size="large"
          type="primary"
          :loading="registration.submitting"
          @click="submitRegistration"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="captchaDialogVisible"
      class="captcha-dialog"
      title="安全验证"
      width="min(380px, 92vw)"
      align-center
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="pendingLogin = false"
    >
      <SliderCaptcha @verified="onCaptchaVerified" @enabled="onCaptchaEnabled" />
    </el-dialog>
  </div>
</template>

<script setup>
import SliderCaptcha from "@/components/SliderCaptcha/index.vue";
import Cookies from "js-cookie";
import { encrypt, decrypt } from "@/utils/jsencrypt";
import useUserStore from '@/store/modules/user'
import useRuntimeStore from '@/store/modules/runtime'
import { getCodeImg } from "@/api/login";
import { getRegistrationStatus, applyTenantRegistration } from "@/api/balan/runtime";

const userStore = useUserStore()
const runtime = useRuntimeStore()
const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance();

const loginForm = ref({
  tenantCode: "",
  username: "",
  password: "",
  rememberMe: false,
  captchaToken: ""
});

const loginRules = {
  tenantCode: [
    { pattern: /^[A-Za-z0-9_-]{0,50}$/, trigger: "blur", message: "租户编码只含字母、数字、下划线或短横线" }
  ],
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }]
};

const loading = ref(false);
// 验证码开关（由后端配置决定，弹窗组件加载时也会回传）
const captchaEnabled = ref(true);
const captchaDialogVisible = ref(false);
// 弹窗是否由「登录」按钮触发：验证通过后自动继续登录
const pendingLogin = ref(false);
// 注册开关
const register = ref(false);
const redirect = ref(undefined);

// 运营商自助入驻：入口是否开放只问后端，社区版与关掉多租户时 enabled 为 false
const registration = reactive({
  enabled: false,
  visible: false,
  submitting: false,
  receipt: null,
  form: { tenantName: "", contactName: "", phone: "", email: "", remark: "" }
});

// 入驻表单挂的是「边改边纠」口径：只挂 blur 的话，用户改对了红字还留着，看起来像没生效
const registrationRules = {
  tenantName: [
    { required: true, trigger: ["blur", "change"], message: "请输入运营商名称" },
    { max: 50, trigger: ["blur", "change"], message: "运营商名称不超过 50 个字符" }
  ],
  contactName: [
    { required: true, trigger: ["blur", "change"], message: "请输入联系人" },
    { max: 30, trigger: ["blur", "change"], message: "联系人不超过 30 个字符" }
  ],
  phone: [
    { required: true, trigger: ["blur", "change"], message: "请输入联系电话" },
    { pattern: /^\d{6,20}$/, trigger: ["blur", "change"], message: "联系电话只允许 6-20 位数字" }
  ],
  email: [
    { pattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, trigger: ["blur", "change"], message: "邮箱格式不正确" }
  ],
  remark: [{ max: 500, trigger: ["blur", "change"], message: "说明不超过 500 个字符" }]
};

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect;
}, { immediate: true });

onMounted(() => {
  getCodeImg().then((res) => {
    captchaEnabled.value = res.captchaEnabled !== false;
  }).catch(() => {});
  getRegistrationStatus().then((res) => {
    registration.enabled = !!(res.data && res.data.enabled);
  }).catch(() => {});
});

function openRegistration() {
  registration.receipt = null;
  registration.visible = true;
}

function submitRegistration() {
  proxy.$refs.registerRef.validate(valid => {
    if (!valid) return;
    registration.submitting = true;
    applyTenantRegistration({ ...registration.form }).then((res) => {
      registration.receipt = res.data || {};
      registration.form = { tenantName: "", contactName: "", phone: "", email: "", remark: "" };
      proxy.$modal.msgSuccess("申请已提交，等待平台审核");
    }).catch(() => {
      // 失败原因由请求拦截器提示，弹窗保留让用户改完重提
    }).finally(() => {
      registration.submitting = false;
    });
  });
}

function openCaptcha(forLogin = false) {
  if (!captchaEnabled.value || loading.value) return;
  pendingLogin.value = forLogin;
  captchaDialogVisible.value = true;
}

function onCaptchaEnabled(value) {
  captchaEnabled.value = value;
  if (value) return;
  captchaDialogVisible.value = false;
  if (pendingLogin.value) {
    pendingLogin.value = false;
    doLogin();
  }
}

function onCaptchaVerified(token) {
  loginForm.value.captchaToken = token;
  const continueLogin = pendingLogin.value;
  // 让「验证通过」状态短暂可见后再收起弹窗
  setTimeout(() => {
    if (!captchaDialogVisible.value) return;
    captchaDialogVisible.value = false;
    pendingLogin.value = false;
    if (continueLogin) doLogin();
  }, 450);
}

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (!valid) return;
    if (captchaEnabled.value && !loginForm.value.captchaToken) {
      openCaptcha(true);
      return;
    }
    doLogin();
  });
}

function doLogin() {
  loading.value = true;
  // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
  if (loginForm.value.rememberMe) {
    Cookies.set("username", loginForm.value.username, { expires: 30 });
    Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 });
    Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 });
  } else {
    // 否则移除
    Cookies.remove("username");
    Cookies.remove("password");
    Cookies.remove("rememberMe");
  }
  // 调用action的登录方法
  userStore.login(loginForm.value).then(() => {
    const query = route.query;
    const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
      if (cur !== "redirect") {
        acc[cur] = query[cur];
      }
      return acc;
    }, {});
    router.push({ path: redirect.value || "/", query: otherQueryParams });
  }).catch(() => {
    loading.value = false;
    // 通行令牌已被消费，重新验证后才能再次登录
    loginForm.value.captchaToken = "";
  });
}

function getCookie() {
  const username = Cookies.get("username");
  const password = Cookies.get("password");
  const rememberMe = Cookies.get("rememberMe");
  loginForm.value = {
    tenantCode: "",
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe),
    captchaToken: ""
  };
}

getCookie();
</script>

<style lang='scss' scoped>
.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 452px;
  grid-template-rows: auto 1fr auto;
  gap: 0 48px;
  height: 100%;
  min-height: 100vh;
  padding: 44px 56px 38px;
  // 场景图直接铺满，不加任何压暗蒙版
  background: url("../assets/images/login-charging-scene.jpg") center center / cover no-repeat, #edf7f2;
  color: #0b2b21;
}

.brand {
  grid-column: 1;
  grid-row: 1;

  .brand-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .brand-mark {
    display: grid;
    place-items: center;
    flex: none;
    width: 44px;
    height: 44px;
    border-radius: 13px;
    background: linear-gradient(145deg, #12c07c, #06825f);
    color: #f2fffa;
    box-shadow: 0 10px 22px rgba(4, 96, 70, 0.28);

    svg {
      width: 22px;
      height: 22px;
    }
  }

  .mark-cn {
    font-size: 21px;
    font-weight: 700;
    letter-spacing: 0.14em;
    line-height: 1.3;
    color: #08251c;
  }

  .mark-en {
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.32em;
    color: #3f8d72;
  }

  .mark-tag {
    display: inline-block;
    margin-left: 6px;
    padding: 1px 6px;
    border-radius: 999px;
    background: rgba(0, 168, 112, 0.12);
    letter-spacing: 0.08em;
    font-size: 9px;
    color: #067a56;
    vertical-align: 1px;
  }

  .brand-sub {
    margin: 14px 0 0 56px;
    font-size: 13px;
    letter-spacing: 0.08em;
    color: #33584b;
  }
}

.feature-field {
  position: relative;
  grid-column: 1;
  grid-row: 2;
  align-self: end;
  display: grid;
  grid-template-columns: repeat(20, minmax(0, 1fr));
  grid-template-rows: repeat(11, minmax(0, 1fr));
  gap: clamp(8px, 1.2vh, 12px) 14px;
  width: 100%;
  height: 64vh;
  max-height: 660px;
  // 整组右移，并等比缩小，锚定左下角，底部位置不变
  transform: translateX(clamp(56px, 5vw, 88px)) scale(0.88);
  transform-origin: left bottom;
  // 整体落在场景中下部的空地/场站区域，不贴页面最底部
  margin-bottom: 10vh;
}

.fcard {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 10px;
  padding: clamp(13px, 1.9vh, 20px) clamp(15px, 1.9vh, 20px);
  border-radius: clamp(16px, 1.9vh, 20px);
  background:
    radial-gradient(120% 130% at 100% 100%, rgba(0, 200, 140, 0.12) 0%, rgba(0, 200, 140, 0) 62%),
    linear-gradient(148deg, rgba(255, 255, 255, 0.64) 0%, rgba(255, 255, 255, 0.34) 100%);
  border: 1px solid rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 38px rgba(9, 66, 47, 0.13), inset 0 1px 0 rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px) saturate(1.12);
  -webkit-backdrop-filter: blur(16px) saturate(1.12);
  overflow: hidden;
  animation: rise 0.62s cubic-bezier(0.22, 0.61, 0.36, 1) both;

  .fc-head {
    display: flex;
    align-items: center;
    gap: clamp(9px, 1.3vh, 14px);
    min-width: 0;
  }

  .fc-icon {
    position: relative;
    display: grid;
    place-items: center;
    flex: none;
    width: clamp(46px, 7.6vh, 78px);
    height: clamp(46px, 7.6vh, 78px);
    background: linear-gradient(160deg, rgba(0, 148, 102, 0.82), rgba(0, 108, 76, 0.5));
    clip-path: polygon(50% 0%, 93% 25%, 93% 75%, 50% 100%, 7% 75%, 7% 25%);
    color: #00996a;

    &::before {
      content: "";
      position: absolute;
      inset: clamp(2px, 0.32vh, 4px);
      background: linear-gradient(160deg, rgba(255, 255, 255, 0.96), rgba(232, 255, 247, 0.8));
      clip-path: inherit;
    }

    svg {
      position: relative;
      z-index: 1;
      width: 45%;
      height: 45%;
    }
  }

  .fc-titles {
    min-width: 0;

    h3 {
      margin: 0 0 5px;
      font-size: clamp(18px, 2.9vh, 27px);
      font-weight: 600;
      letter-spacing: 0.02em;
      line-height: 1.2;
      color: #0b2b21;
    }

    p {
      margin: 0;
      font-size: clamp(11px, 1.3vh, 13px);
      line-height: 1.5;
      color: #4c6d61;
    }
  }

  .fc-chart {
    position: absolute;
    right: clamp(16px, 2.2vh, 26px);
    bottom: clamp(14px, 2.2vh, 24px);
    width: clamp(120px, 21vh, 210px);
    height: auto;
    opacity: 0.92;
    pointer-events: none;
  }
}

// 三块大卡缩到 2/3，左上角锚定；内部字号/图标单独放大补偿，避免缩完太小
.fcard-protocol,
.fcard-alert,
.fcard-tenant {
  scale: 0.667;
  transform-origin: left top;

  .fc-icon {
    width: clamp(55px, 9.1vh, 94px);
    height: clamp(55px, 9.1vh, 94px);
  }

  .fc-titles h3 {
    font-size: clamp(26px, 4.2vh, 36px);
  }

  .fc-titles p {
    font-size: clamp(15px, 2.3vh, 20px);
  }

  .fc-chart {
    width: clamp(140px, 25vh, 250px);
  }
}

.fcard-protocol {
  grid-area: 1 / 1 / 5 / 12;
}

.fcard-alert {
  grid-area: 5 / 9 / 9 / 21;
  // 与协议卡右下角轻微叠压，形成参考图里的错位层次
  margin-top: calc(-1 * clamp(14px, 1.8vh, 18px));
  animation-delay: 0.08s;

  .fc-chart {
    width: clamp(130px, 23vh, 230px);
  }
}

.fcard-tenant {
  grid-area: 9 / 1 / 12 / 12;
  animation-delay: 0.14s;
}

.fcard-chip {
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: clamp(10px, 1.4vh, 14px) clamp(11px, 1.5vh, 16px);

  .fc-head {
    align-items: center;
    gap: 8px;
  }

  .fc-icon {
    width: clamp(24px, 3vh, 32px);
    height: clamp(24px, 3vh, 32px);

    svg {
      width: 50%;
      height: 50%;
    }
  }

  .fc-titles h3 {
    margin: 0;
    font-size: clamp(12px, 1.4vh, 14px);
    white-space: nowrap;
  }

  .fc-titles p {
    display: none;
  }
}

.chip-device {
  grid-area: 6 / 4 / 8 / 8;
  animation-delay: 0.2s;
}

.chip-billing {
  grid-area: 10 / 14 / 12 / 19;
  // 比租户卡底边略高，避免两块卡在同一条水平线上
  margin-bottom: 2.5vh;
  animation-delay: 0.26s;
}

.fc-spark {
  position: absolute;
  right: 10px;
  bottom: 9px;
  width: 44px;
  height: 16px;
  opacity: 0.9;
  pointer-events: none;
}

.page-foot {
  grid-column: 1;
  grid-row: 3;
  margin: 0;
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: #4f7268;
}

.entry {
  grid-column: 2;
  grid-row: 1 / span 3;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
}

.login-card {
  width: 100%;
  max-width: 452px;
  padding: 38px 38px 30px;
  border-radius: 26px;
  background: linear-gradient(156deg, rgba(255, 255, 255, 0.74) 0%, rgba(255, 255, 255, 0.52) 46%, rgba(238, 253, 246, 0.46) 100%);
  border: 1px solid rgba(255, 255, 255, 0.9);
  // 外圈浅色光晕模拟参考图的玻璃描边
  box-shadow:
    0 30px 70px rgba(7, 58, 42, 0.18),
    0 0 0 1px rgba(255, 255, 255, 0.5),
    0 0 0 10px rgba(255, 255, 255, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px) saturate(1.15);
  -webkit-backdrop-filter: blur(20px) saturate(1.15);
  animation: rise 0.66s cubic-bezier(0.22, 0.61, 0.36, 1) both;

  :deep(.el-form-item) {
    margin-bottom: 15px;
  }

  :deep(.el-form-item__content) {
    display: block;
    line-height: normal;
  }

  :deep(.el-form-item__error) {
    padding-top: 3px;
  }

  :deep(.el-input__wrapper) {
    height: 48px;
    padding: 0 13px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.88);
    box-shadow: 0 0 0 1px rgba(16, 84, 64, 0.12) inset;
    transition: background 0.18s ease, box-shadow 0.18s ease;
  }

  :deep(.el-input__wrapper.is-focus) {
    background: #fff;
    box-shadow: 0 0 0 1px rgba(0, 168, 112, 0.66) inset, 0 0 0 3px rgba(0, 168, 112, 0.14);
  }

  :deep(.el-input__inner) {
    font-size: 13.5px;
    color: #0f2b22;
  }

  :deep(.el-input__inner::placeholder) {
    color: #93a8a0;
  }

  .input-icon {
    height: auto;
    width: 15px;
    margin-left: 0;
    color: #7d968c;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    font-size: 12px;
    font-weight: 500;
    letter-spacing: 0.06em;
    color: #3d5c52;
  }

  .field-optional {
    margin-left: 6px;
    font-weight: 400;
    letter-spacing: 0;
    color: #93a49c;
  }
}

.title {
  margin: 0 0 6px;
  font-size: 29px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #08251c;
}

.subtitle {
  margin: 0 0 26px;
  font-size: 12.5px;
  color: #4c6d61;
}

.maintenance-tip {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin: -12px 0 18px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 247, 232, 0.86);
  box-shadow: 0 0 0 1px rgba(196, 132, 18, 0.24) inset;
  color: #8a5a06;

  svg {
    flex: none;
    width: 17px;
    height: 17px;
    margin-top: 1px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 12px;
    letter-spacing: 0.06em;
  }

  span {
    margin-top: 2px;
    font-size: 11.5px;
    line-height: 1.5;
    color: #8a641f;
  }
}

.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0 18px;

  :deep(.el-checkbox__label) {
    font-size: 12px;
    color: #446457;
  }
}

.verify-row {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 48px;
  margin-bottom: 14px;
  padding: 0 13px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 0 0 1px rgba(16, 84, 64, 0.12) inset;
  color: #3d5c52;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.92);
    box-shadow: 0 0 0 1px rgba(0, 168, 112, 0.42) inset;
  }

  &:focus-visible {
    outline: none;
    background: #fff;
    box-shadow: 0 0 0 1px rgba(0, 168, 112, 0.66) inset, 0 0 0 3px rgba(0, 168, 112, 0.14);
  }

  .verify-icon {
    display: grid;
    place-items: center;
    flex: none;
    width: 24px;
    height: 24px;
    border-radius: 7px;
    background: rgba(18, 192, 124, 0.14);
    color: #0a9a6b;

    svg {
      width: 15px;
      height: 15px;
    }
  }

  .verify-text {
    flex: 1;
    min-width: 0;
  }

  .verify-action {
    flex: none;
    font-size: 12px;
    color: #0a9a6b;
  }

  &.is-verified {
    background: rgba(18, 192, 124, 0.07);
    box-shadow: 0 0 0 1px rgba(0, 168, 112, 0.34) inset;

    .verify-text {
      color: #0a7350;
    }

    .verify-action {
      color: #3f8d72;
    }
  }
}

// 弹窗挂载在 body 下，需要全局选择器；逐条展开，避免嵌套被 scoped 编译器折叠
:global(.captcha-dialog.el-dialog) {
  margin: auto !important;
  padding: 22px 24px 20px;
  border-radius: 20px;
  background: linear-gradient(156deg, rgba(255, 255, 255, 0.97) 0%, rgba(238, 253, 246, 0.95) 100%);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow:
    0 30px 70px rgba(7, 58, 42, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.96);
}

:global(.captcha-dialog .el-dialog__header) {
  padding: 0 0 4px;
  margin-right: 0;
}

:global(.captcha-dialog .el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #08251c;
}

:global(.captcha-dialog .el-dialog__headerbtn) {
  top: 4px;
  right: 4px;
}

:global(.captcha-dialog .el-dialog__body) {
  padding: 14px 0 0;
}

:global(.captcha-dialog .slider-captcha) {
  margin: 0;
}

// 入驻申请弹窗同样挂在 body 下，沿用登录卡的玻璃质感
:global(.register-dialog.el-dialog) {
  margin: auto !important;
  padding: 22px 26px 18px;
  border-radius: 20px;
  background: linear-gradient(156deg, rgba(255, 255, 255, 0.97) 0%, rgba(238, 253, 246, 0.95) 100%);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow:
    0 30px 70px rgba(7, 58, 42, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.96);
}

:global(.register-dialog .el-dialog__header) {
  padding: 0 0 4px;
  margin-right: 0;
}

:global(.register-dialog .el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #08251c;
}

:global(.register-dialog .el-dialog__headerbtn) {
  top: 4px;
  right: 4px;
}

:global(.register-dialog .el-dialog__body) {
  padding: 10px 0 0;
}

:global(.register-dialog .el-dialog__footer) {
  padding: 12px 0 0;
}

:global(.register-dialog .el-form-item) {
  margin-bottom: 14px;
}

:global(.register-dialog .el-form-item__label) {
  padding-bottom: 2px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.05em;
  color: #3d5c52;
}

.register-desc {
  margin: 0 0 14px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #5c7a6e;
}

.register-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.register-done {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 14px 15px;
  border-radius: 12px;
  background: rgba(18, 192, 124, 0.08);
  box-shadow: 0 0 0 1px rgba(0, 168, 112, 0.26) inset;
  color: #0a7350;

  svg {
    flex: none;
    width: 19px;
    height: 19px;
    margin-top: 1px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 13px;
    letter-spacing: 0.04em;
  }

  span {
    margin-top: 4px;
    font-size: 11.5px;
    line-height: 1.6;
    color: #3f7360;
  }
}

.form-hint {
  margin-left: auto;
  font-size: 11.5px;
  color: #7d968c;
}

.register-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 14px;

  .register-tip {
    font-size: 11.5px;
    color: #6b8a80;
  }

  .register-link {
    font-size: 12px;
    letter-spacing: 0.04em;
  }
}

.submit-item {
  margin-bottom: 0 !important;
}

.submit-btn {
  width: 100%;
  height: 50px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3em;
  color: #fff;
  background: linear-gradient(100deg, #12c07c 0%, #0a9a6b 44%, #0a5343 100%);
  box-shadow: 0 14px 30px rgba(6, 110, 80, 0.3);
  transition: transform 0.16s ease, box-shadow 0.16s ease, filter 0.16s ease;

  &:hover,
  &:focus-visible {
    filter: brightness(1.05);
    box-shadow: 0 18px 34px rgba(6, 110, 80, 0.36);
    transform: translateY(-1px);
  }

  .btn-arrow {
    width: 15px;
    height: 15px;
    margin-left: 9px;
    transition: transform 0.18s ease;
  }

  &:hover .btn-arrow {
    transform: translateX(3px);
  }
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  font-size: 11.5px;
  color: #6b8a80;

  i {
    font-style: normal;
    opacity: 0.55;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 窄一些时小卡放不下右侧 sparkline，隐藏避免压字
@media (max-width: 1450px) {
  .fc-spark {
    display: none;
  }
}

// 中等宽度：收起装饰浮层，避免卡片挤到登录卡
@media (max-width: 1180px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto 1fr auto;
    gap: 0;
    padding: 40px 36px 32px;
  }

  .feature-field {
    display: none;
  }

  .brand {
    grid-row: 1;
  }

  .entry {
    grid-column: 1;
    grid-row: 2;
  }

  .page-foot {
    grid-column: 1;
    grid-row: 3;
    text-align: center;
  }
}

@media (max-width: 900px) {
  .login-shell {
    padding: 26px 20px 24px;
    background-position: 32% center;
  }

  .brand .brand-sub {
    margin-left: 0;
    margin-top: 10px;
  }

  .login-card {
    padding: 30px 24px 24px;
    border-radius: 22px;
  }

  .title {
    font-size: 26px;
  }
}

// 短屏压缩：1366×768 / 1024×768 下完整展示登录卡，不出现滚动条
@media (max-height: 820px) {
  .login-shell {
    padding: 24px 36px 20px;
  }

  .login-card {
    padding: 28px 32px 24px;
  }

  .title {
    font-size: 26px;
  }

  .subtitle {
    margin-bottom: 18px;
  }

  .login-card :deep(.el-form-item) {
    margin-bottom: 11px;
  }

  .login-card :deep(.el-input__wrapper) {
    height: 44px;
  }

  .verify-row {
    height: 44px;
    margin-bottom: 12px;
  }

  .submit-btn {
    height: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fcard,
  .login-card {
    animation: none;
  }

  .submit-btn,
  .submit-btn .btn-arrow,
  .login-card :deep(.el-input__wrapper) {
    transition: none;
  }
}
</style>
