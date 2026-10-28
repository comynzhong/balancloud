<template>
  <div class="sidebar-brand" :class="{ 'is-collapse': collapse }">
    <router-link class="brand-link" to="/">
      <span class="brand-mark">
        <img :src="logo" alt="" />
      </span>
      <span class="brand-txt">
        <span class="brand-name sidebar-title">{{ title }}</span>
        <span class="brand-sub">BALANCLOUD</span>
      </span>
    </router-link>
  </div>
</template>

<script setup>
import useRuntimeStore from '@/store/modules/runtime'
import logo from '@/assets/logo/logo.png'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const runtimeStore = useRuntimeStore();
const title = computed(() => runtimeStore.brand.name || import.meta.env.VITE_APP_TITLE);
</script>

<style lang="scss" scoped>
.sidebar-brand {
  flex: 0 0 auto;
  padding: 16px 16px 14px;

  .brand-link {
    display: flex;
    align-items: center;
    gap: 10px;
    overflow: hidden;
  }

  .brand-mark {
    flex: 0 0 auto;
    width: 34px;
    height: 34px;
    filter: drop-shadow(0 3px 8px rgba(0, 0, 0, .45));

    img {
      display: block;
      width: 100%;
      height: 100%;
    }
  }

  .brand-txt {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .brand-name {
    overflow: hidden;
    color: #fff;
    font-size: 14.5px;
    font-weight: 600;
    letter-spacing: .04em;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .brand-sub {
    margin-top: 1px;
    color: rgba(255, 255, 255, .4);
    font-size: 9.5px;
    font-weight: 500;
    letter-spacing: .18em;
    white-space: nowrap;
  }

  &.is-collapse {
    display: flex;
    justify-content: center;
    padding: 16px 0 12px;

    .brand-txt {
      display: none;
    }
  }
}
</style>