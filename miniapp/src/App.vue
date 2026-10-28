<script>
import { loadRuntimeConfig } from '@/utils/runtime'
import { applyTenantFromUrl } from '@/utils/auth'

export default {
  onLaunch() {
    // 登录由页面按需触发，避免启动阶段阻塞渲染
    applyTenantFromUrl()
    loadRuntimeConfig()
  }
}
</script>

<style lang="scss">
page {
  --brand: #00a870;
  --brand-600: #00895c;
  --brand-700: #006b49;
  --brand-soft: #e6f7f1;
  --brand-line: #c6ebdd;
  --ink: #101828;
  --ink-2: #475467;
  --ink-3: #98a2b3;
  --line: #edf0ee;
  --line-soft: #f5f7f6;
  --bg: #f5f7f6;
  --amber: #f79009;
  --amber-soft: #fff6e8;
  --red: #f04438;
  --red-soft: #fef0ef;
  --shadow: 0 2rpx 4rpx rgba(16, 24, 40, 0.04), 0 16rpx 48rpx rgba(16, 24, 40, 0.05);

  background: var(--bg);
  color: var(--ink);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

view,
text,
button,
input,
textarea {
  box-sizing: border-box;
}

.bc-page {
  min-height: 100vh;
  padding: 24rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

.card {
  background: #ffffff;
  border-radius: 36rpx;
  padding: 28rpx 30rpx;
  border: 2rpx solid #f1f4f2;
  box-shadow: var(--shadow);
}

.card + .card {
  margin-top: 20rpx;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 4rpx 0;
}

.page-title {
  font-size: 44rpx;
  font-weight: 700;
  letter-spacing: -0.4rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink);
  margin: 40rpx 4rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title .more {
  font-size: 24rpx;
  font-weight: 400;
  color: var(--ink-3);
}

.muted {
  color: var(--ink-3);
}

.small {
  font-size: 24rpx;
}

.tiny {
  font-size: 20rpx;
}

.brand {
  color: var(--brand-600);
}

.row {
  display: flex;
  align-items: center;
}

.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.flex-1 {
  flex: 1;
  min-width: 0;
}

.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.num {
  font-family: 'DIN Alternate', 'Helvetica Neue', sans-serif;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.5rpx;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 32rpx;
}

.chip-green {
  background: var(--brand-soft);
  color: var(--brand-600);
}

.chip-amber {
  background: var(--amber-soft);
  color: #b54708;
}

.chip-red {
  background: var(--red-soft);
  color: #b42318;
}

.chip-gray {
  background: #f2f4f7;
  color: #667085;
}

.btn-primary {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  background: linear-gradient(120deg, #00b87c, #009a66);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
  box-shadow: 0 16rpx 32rpx -18rpx rgba(0, 168, 112, 0.9);
}

.btn-primary::after {
  border: none;
}

.btn-primary[disabled] {
  background: #e7ebe9;
  color: var(--ink-3);
  box-shadow: none;
}

.btn-ghost {
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 2rpx solid var(--brand-line);
  color: var(--brand-600);
  font-size: 28rpx;
  text-align: center;
}

.btn-ghost::after {
  border: none;
}

.empty {
  padding: 110rpx 0;
  text-align: center;
  color: var(--ink-3);
  font-size: 26rpx;
}

.empty-mark {
  width: 110rpx;
  height: 110rpx;
  margin: 0 auto 24rpx;
  border-radius: 50%;
  background: var(--line-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c6cdd5;
  font-size: 44rpx;
  font-weight: 700;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--brand);
  margin-right: 10rpx;
}

.seg {
  display: flex;
  padding: 6rpx;
  border-radius: 999rpx;
  background: #ebefec;
}

.seg-item {
  flex: 1;
  padding: 14rpx 0;
  border-radius: 999rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-2);
}

.seg-item.on {
  background: #ffffff;
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 2rpx 6rpx rgba(16, 24, 40, 0.08);
}

.bar {
  height: 10rpx;
  border-radius: 999rpx;
  background: #eff2f0;
  overflow: hidden;
}

.bar-inner {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #34d399, #00a870);
  transition: width 0.4s ease;
}

.bar-inner.amber {
  background: linear-gradient(90deg, #fdb022, #f79009);
}

.link {
  color: var(--brand-600);
}

.hint-box {
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--brand-soft);
  color: var(--brand-700);
  font-size: 22rpx;
  line-height: 1.6;
}
</style>
