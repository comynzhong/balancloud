<template>
  <div class="sidebar-shell">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="variables.menuBackground"
        :text-color="variables.menuColor"
        :unique-opened="true"
        :active-text-color="theme"
        :collapse-transition="false"
        mode="vertical"
      >
        <sidebar-item
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
    <div class="sidebar-foot">
      <span class="foot-dot"></span>
      <span class="foot-txt">v{{ version }} · {{ envLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarItem from './SidebarItem'
import pkg from '../../../../package.json'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute();
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters =  computed(() => permissionStore.sidebarRouters);
const showLogo = computed(() => settingsStore.sidebarLogo);
const theme = computed(() => settingsStore.theme);
const isCollapse = computed(() => !appStore.sidebar.opened);
const version = pkg.version;
const envLabels = { development: '开发环境', staging: '预发环境', production: '生产环境' };
const envLabel = envLabels[import.meta.env.VITE_APP_ENV] || '开发环境';

const activeMenu = computed(() => {
  const { meta, path } = route;
  // if set path, the sidebar will highlight the path you set
  if (meta.activeMenu) {
    return meta.activeMenu;
  }
  return path;
})

</script>

<style lang="scss" scoped>
.sidebar-foot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 14px;
  border-top: 1px solid rgba(255, 255, 255, .07);
  font-size: 11px;
  letter-spacing: .02em;
  color: rgba(255, 255, 255, .35);
  white-space: nowrap;
  overflow: hidden;

  .foot-dot {
    flex: 0 0 auto;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #2BD69B;
    box-shadow: 0 0 0 3px rgba(43, 214, 155, .16);
  }

  .foot-txt {
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.hideSidebar .sidebar-foot {
  justify-content: center;
  padding: 12px 0 14px;

  .foot-txt {
    display: none;
  }
}
</style>