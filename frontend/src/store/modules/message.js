import { defineStore } from 'pinia'
import { getUnreadMessageCount } from '@/api/balan/message'

const useMessageStore = defineStore('message', {
  state: () => ({
    unread: 0
  }),
  actions: {
    async refresh() {
      try {
        const res = await getUnreadMessageCount()
        this.unread = Number(res.data?.count || 0)
      } catch {
        // 顶栏角标失败不影响主流程，等待下一次刷新
      }
    }
  }
})

export default useMessageStore
