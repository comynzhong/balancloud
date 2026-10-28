<template>
  <el-drawer v-model="showSettings" :withHeader="false" direction="rtl" size="300px">
    <div class="setting-drawer-title">
      <h3 class="drawer-title">界面配置</h3>
    </div>
    <div class="drawer-item">
      <span>主题颜色</span>
      <span class="comp-style">
        <el-color-picker v-model="theme" :predefine="predefineColors" @change="themeChange"/>
      </span>
    </div>
    <el-divider />

    <h3 class="drawer-title">系统布局配置</h3>

    <div class="drawer-item">
      <span>开启 TopNav</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.topNav" @change="topNavChange" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>固定 Header</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.fixedHeader" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>显示 Logo</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.sidebarLogo" class="drawer-switch" />
      </span>
    </div>

    <div class="drawer-item">
      <span>动态标题</span>
      <span class="comp-style">
        <el-switch v-model="settingsStore.dynamicTitle" class="drawer-switch" />
      </span>
    </div>

    <el-divider />

    <el-button type="primary" plain icon="DocumentAdd" @click="saveSetting">保存配置</el-button>
    <el-button plain icon="Refresh" @click="resetSetting">重置配置</el-button>
  </el-drawer>

</template>

<script setup>
import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { useDynamicTitle } from '@/utils/dynamicTitle'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'
import { handleThemeStyle } from '@/utils/theme'

const { proxy } = getCurrentInstance();
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()
const showSettings = ref(false);
const theme = ref(settingsStore.theme);
const storeSettings = computed(() => settingsStore);
const predefineColors = ref(["#00A870", "#12C58A", "#3A71A8", "#FEC171", "#30B08F", "#4AB7BD", "#E65D6E", "#C03639"]);

/** 是否需要topnav */
function topNavChange(val) {
  if (!val) {
    appStore.toggleSideBarHide(false);
    permissionStore.setSidebarRouters(permissionStore.defaultRoutes);
  }
}

function themeChange(val) {
  settingsStore.theme = val;
  handleThemeStyle(val);
}
function saveSetting() {
  proxy.$modal.loading("正在保存到本地，请稍候...");
  let layoutSetting = {
    "topNav": storeSettings.value.topNav,
    "fixedHeader": storeSettings.value.fixedHeader,
    "sidebarLogo": storeSettings.value.sidebarLogo,
    "dynamicTitle": storeSettings.value.dynamicTitle,
    "theme": storeSettings.value.theme
  };
  localStorage.setItem("layout-setting", JSON.stringify(layoutSetting));
  setTimeout(proxy.$modal.closeLoading(), 1000)
}
function resetSetting() {
  proxy.$modal.loading("正在清除设置缓存并刷新，请稍候...");
  localStorage.removeItem("layout-setting")
  setTimeout("window.location.reload()", 1000)
}
function openSetting() {
  showSettings.value = true;
}

defineExpose({
  openSetting,
})
</script>

<style lang='scss' scoped>
.setting-drawer-title {
  margin-bottom: 12px;
  color: rgba(0, 0, 0, 0.85);
  line-height: 22px;
  font-weight: bold;
  .drawer-title {
    font-size: 14px;
  }
}

.drawer-item {
  color: rgba(0, 0, 0, 0.65);
  padding: 12px 0;
  font-size: 14px;

  .comp-style {
    float: right;
    margin: -3px 8px 0px 0px;
  }
}
</style>