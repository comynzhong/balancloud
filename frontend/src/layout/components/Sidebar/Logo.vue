<template>
  <div class="sidebar-brand" :class="{ 'is-collapse': collapse }">
    <router-link class="brand-link" to="/">
      <span class="brand-mark">
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M13.2 2.6 4.8 13.4h5.3l-1.5 8 8.6-11H11.7l1.5-7.8Z" fill="#fff" />
        </svg>
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
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: linear-gradient(140deg, #12C58A, #00875A);
    box-shadow: 0 3px 8px -3px rgba(0, 0, 0, .45);

    svg {
      width: 19px;
      height: 19px;
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