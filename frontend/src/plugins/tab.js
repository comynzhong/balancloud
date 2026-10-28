import router from '@/router'

export default {
  // 刷新当前页：经 /redirect 中转，强制重新挂载页面组件
  refreshPage(obj) {
    const { path, query, matched } = router.currentRoute.value;
    let view = obj;
    if (view === undefined) {
      matched.forEach((m) => {
        if (m.components && m.components.default && m.components.default.name) {
          if (!['Layout', 'ParentView'].includes(m.components.default.name)) {
            view = { name: m.components.default.name, path: path, query: query };
          }
        }
      });
    }
    return router.replace({
      path: '/redirect' + (view ? view.path : path),
      query: view ? view.query : query
    });
  },
  // 关闭当前页并打开新页
  closeOpenPage(obj) {
    if (obj !== undefined) {
      return router.push(obj);
    }
  },
  // 关闭当前页：返回上一页
  closePage(obj) {
    if (obj !== undefined) {
      return router.push(obj);
    }
    return router.back();
  }
}