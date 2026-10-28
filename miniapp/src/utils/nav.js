// 自定义导航（navigationStyle: custom）的页统一按微信胶囊按钮实测位置排布。
// 微信端 CSS 里的 --status-bar-height 由框架注入成固定 25px，刘海屏实际 44~48px，
// 按它留白会把返回行压进状态栏；H5 没有胶囊，返回 null 让页面保持 CSS 默认。
export function capsuleNav() {
  // #ifdef MP-WEIXIN
  const rect = uni.getMenuButtonBoundingClientRect()
  const sys = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
  if (!rect || !rect.height) return null
  return {
    statusBar: { height: `${rect.top}px` },
    navBar: {
      height: `${rect.height}px`,
      paddingRight: `${Math.max(0, sys.windowWidth - rect.left + 8)}px`
    }
  }
  // #endif
  // #ifndef MP-WEIXIN
  return null
  // #endif
}
