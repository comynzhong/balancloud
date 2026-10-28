<template>
  <div class="header-search">
    <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
      <circle cx="11" cy="11" r="6.4" />
      <path d="m20 20-3.6-3.6" />
    </svg>
    <el-select
      ref="headerSearchSelectRef"
      v-model="search"
      :remote-method="querySearch"
      filterable
      default-first-option
      remote
      placeholder="搜索菜单、订单、场站…"
      class="header-search-select"
      @change="change"
    >
      <el-option v-for="option in options" :key="option.item.path" :value="option.item" :label="option.item.title.join(' > ')" />
    </el-select>
    <span class="search-kbd">Ctrl K</span>
  </div>
</template>

<script setup>
import Fuse from 'fuse.js'
import { getNormalPath } from '@/utils/ruoyi'
import { isHttp } from '@/utils/validate'
import usePermissionStore from '@/store/modules/permission'

const search = ref('');
const options = ref([]);
const searchPool = ref([]);
const fuse = ref(undefined);
const headerSearchSelectRef = ref(null);
const router = useRouter();
const routes = computed(() => usePermissionStore().routes);

function change(val) {
  const path = val.path;
  const query = val.query;
  if (isHttp(path)) {
    // http(s):// 路径新窗口打开
    const pindex = path.indexOf("http");
    window.open(path.substr(pindex, path.length), "_blank");
  } else {
    if (query) {
      router.push({ path: path, query: JSON.parse(query) });
    } else {
      router.push(path)
    }
  }

  search.value = ''
  options.value = []
}
function initFuse(list) {
  fuse.value = new Fuse(list, {
    shouldSort: true,
    threshold: 0.4,
    location: 0,
    distance: 100,
    minMatchCharLength: 1,
    keys: [{
      name: 'title',
      weight: 0.7
    }, {
      name: 'path',
      weight: 0.3
    }]
  })
}
// Filter out the routes that can be displayed in the sidebar
// And generate the internationalized title
function generateRoutes(routes, basePath = '', prefixTitle = []) {
  let res = []

  for (const r of routes) {
    // skip hidden router
    if (r.hidden) { continue }
    const p = r.path.length > 0 && r.path[0] === '/' ? r.path : '/' + r.path;
    const data = {
      path: !isHttp(r.path) ? getNormalPath(basePath + p) : r.path,
      title: [...prefixTitle]
    }

    if (r.meta && r.meta.title) {
      data.title = [...data.title, r.meta.title]

      if (r.redirect !== 'noRedirect') {
        // only push the routes with title
        // special case: need to exclude parent router without redirect
        res.push(data)
      }
    }
    if (r.query) {
      data.query = r.query
    }

    // recursive child routes
    if (r.children) {
      const tempRoutes = generateRoutes(r.children, data.path, data.title)
      if (tempRoutes.length >= 1) {
        res = [...res, ...tempRoutes]
      }
    }
  }
  return res
}
function querySearch(query) {
  if (query !== '') {
    options.value = fuse.value.search(query)
  } else {
    options.value = []
  }
}

function focusSearch() {
  headerSearchSelectRef.value && headerSearchSelectRef.value.focus()
}

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    focusSearch()
  }
}

onMounted(() => {
  searchPool.value = generateRoutes(routes.value);
  window.addEventListener('keydown', handleShortcut)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleShortcut)
})

watchEffect(() => {
  searchPool.value = generateRoutes(routes.value)
})

watch(searchPool, (list) => {
  initFuse(list)
})
</script>

<style lang='scss' scoped>
.header-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 250px;
  height: 34px;
  padding: 0 8px 0 11px;
  border-radius: 10px;
  background: #F2F5F4;
  color: #98A2B3;
  transition: box-shadow .15s ease, background .15s ease;

  &:hover,
  &:focus-within {
    background: #EDF2F0;
    box-shadow: inset 0 0 0 1px #BCE7D6;
  }

  .search-icon {
    flex: 0 0 auto;
    width: 15px;
    height: 15px;
  }

  .header-search-select {
    flex: 1;
    min-width: 0;
    font-size: 12.5px;

    :deep(.el-input__wrapper) {
      padding: 0;
      background: transparent;
      box-shadow: none !important;
    }

    :deep(.el-input__inner) {
      height: 32px;
      color: #101828;
      font-size: 12.5px;

      &::placeholder {
        color: #98A2B3;
      }
    }
  }

  .search-kbd {
    flex: 0 0 auto;
    padding: 2px 6px;
    border: 1px solid #E7ECEA;
    border-radius: 6px;
    background: #fff;
    color: #98A2B3;
    font-size: 10.5px;
  }
}
</style>