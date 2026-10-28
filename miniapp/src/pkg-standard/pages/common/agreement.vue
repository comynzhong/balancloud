<template>
  <view class="bc-page">
    <view class="card doc">
      <view class="doc-title">{{ isPrivacy ? '隐私政策' : '用户协议' }}</view>
      <view class="doc-update">更新日期：2026 年 10 月</view>

      <view v-for="(section, index) in sections" :key="index" class="section">
        <view class="section-head">{{ section.title }}</view>
        <view v-for="(paragraph, pIndex) in section.paragraphs" :key="pIndex" class="paragraph">{{ paragraph }}</view>
      </view>

      <view class="doc-foot muted small">
        本页面为衡电云控开源项目自带的示例文本，正式运营前请由运营方按当地法规补充并替换。
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const type = ref('user')
const isPrivacy = computed(() => type.value === 'privacy')

const userSections = [
  {
    title: '一、服务说明',
    paragraphs: [
      '衡电云控为充电场站运营方提供充电设备接入与运营管理能力。您通过小程序使用扫码充电、订单查询与模拟支付等功能。',
      '本示例文本仅用于演示产品流程，不构成正式法律文件。'
    ]
  },
  {
    title: '二、充电规则',
    paragraphs: [
      '启动充电前请确认设备、端口状态与计费标准。充电费用按平台公示的费率实时计算，并以订单结算结果为准。',
      '同一用户同一时间最多保留一笔进行中的充电订单。达到您设置的时长或金额上限时，平台将自动停止充电。'
    ]
  },
  {
    title: '三、支付说明',
    paragraphs: [
      '当前版本仅提供模拟支付，用于演示订单结算流程，不会产生真实扣款，也不涉及退款。',
      '后续接入真实支付渠道时，将另行更新本协议并明确支付与退款规则。'
    ]
  },
  {
    title: '四、用户责任',
    paragraphs: [
      '请勿在设备故障、线缆破损或环境异常时充电；发现异常请立即停止并联系场站负责人。',
      '因不当使用造成的设备损坏或人身财产损失，由责任方依法承担。'
    ]
  }
]

const privacySections = [
  {
    title: '一、我们收集的信息',
    paragraphs: [
      '登录信息：小程序登录标识或开发登录手机号，用于识别您的账号并关联充电订单。',
      '使用信息：场站与设备编号、端口号、充电起止时间、电量与费用，用于计费、订单查询与客服处理。'
    ]
  },
  {
    title: '二、信息的使用',
    paragraphs: [
      '上述信息仅用于提供充电服务、生成订单、处理客诉与保障设备安全，不用于与充电服务无关的营销推送。',
      '本开源版本不接入第三方统计分析或广告 SDK。'
    ]
  },
  {
    title: '三、信息的存储与保护',
    paragraphs: [
      '账号与订单数据存储在运营方自行部署的服务器中。运营方应采取访问控制、传输加密与备份等措施保护数据安全。',
      '您可联系场站负责人或平台客服查询、更正与删除与您相关的信息。'
    ]
  },
  {
    title: '四、位置权限',
    paragraphs: [
      '仅在您主动查看附近场站时请求定位，用于计算场站距离；拒绝授权不影响扫码充电与订单功能。'
    ]
  }
]

const sections = computed(() => (isPrivacy.value ? privacySections : userSections))

onLoad((options) => {
  if (options.type === 'privacy') {
    type.value = 'privacy'
  }
  uni.setNavigationBarTitle({ title: isPrivacy.value ? '隐私政策' : '用户协议' })
})
</script>

<style lang="scss" scoped>
.doc {
  padding: 36rpx 32rpx;
}

.doc-title {
  font-size: 38rpx;
  font-weight: 700;
}

.doc-update {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #98a2b3;
}

.section {
  margin-top: 36rpx;
}

.section-head {
  font-size: 28rpx;
  font-weight: 600;
  color: #101828;
  margin-bottom: 12rpx;
}

.paragraph {
  font-size: 25rpx;
  line-height: 1.85;
  color: #475467;
  margin-top: 10rpx;
}

.doc-foot {
  margin-top: 40rpx;
  padding-top: 24rpx;
  border-top: 2rpx dashed #edf0ee;
  line-height: 1.7;
  font-size: 21rpx;
}
</style>
