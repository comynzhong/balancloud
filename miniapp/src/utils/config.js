// #ifdef H5
// H5 开发期走同源代理（vite.config.js 的 /dev-api），远端浏览器才不会再打自己的 localhost
export const BASE_URL = import.meta.env.VITE_API_BASE || (import.meta.env.DEV ? '/dev-api' : 'http://localhost:8080')
// #endif
// #ifndef H5
export const BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
// #endif
export const DEFAULT_MAX_DURATION = 480
export const DEFAULT_MAX_AMOUNT = 50
export const SUPPORT_PHONE = '400-000-0000'
