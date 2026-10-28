<template>
  <div class="app-container module-page">
    <div class="module-head">
      <div>
        <p class="module-kicker">BALANCLOUD</p>
        <h2>{{ title }}</h2>
        <p class="module-desc">{{ description }}</p>
      </div>
      <el-tag type="warning" effect="plain">{{ milestone }} 接入</el-tag>
    </div>

    <el-card shadow="never" class="module-card">
      <el-empty :description="`${title}将在 ${milestone} 阶段完成`">
        <template #image>
          <div class="empty-icon">
            <el-icon :size="42"><Lightning /></el-icon>
          </div>
        </template>
        <ul class="feature-list">
          <li v-for="feature in features" :key="feature">{{ feature }}</li>
        </ul>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  milestone: {
    type: String,
    default: 'M1'
  },
  features: {
    type: Array,
    default: () => []
  }
})
</script>

<style lang="scss" scoped>
.module-page {
  .module-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
  }

  .module-kicker {
    margin: 0 0 6px;
    color: #0ea5e9;
    font-size: 11px;
    letter-spacing: 2px;
  }

  h2 {
    margin: 0;
    color: #0f172a;
    font-size: 24px;
    letter-spacing: 1px;
  }

  .module-desc {
    margin: 8px 0 0;
    color: #64748b;
    line-height: 1.6;
  }

  .module-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 96px;
    height: 96px;
    margin: 0 auto;
    border-radius: 50%;
    color: #0ea5e9;
    background: linear-gradient(135deg, #e0f2fe, #ecfdf5);
  }

  .feature-list {
    margin: 0;
    padding: 0;
    list-style: none;
    color: #475569;
    font-size: 13px;
    line-height: 2;

    li::before {
      content: '·';
      margin-right: 8px;
      color: #0ea5e9;
      font-weight: 700;
    }
  }
}
</style>
