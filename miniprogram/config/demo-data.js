const banners = [
  {
    id: 'ritual',
    theme: 'sage',
    eyebrow: 'MINDFUL RITUAL',
    title: '让身体慢下来',
    subtitle: '在草木香气里，找回松弛与平衡',
    action: 'services'
  },
  {
    id: 'shoulder',
    theme: 'amber',
    eyebrow: 'SEASONAL CARE',
    title: '肩颈舒缓计划',
    subtitle: '专注久坐疲劳，给紧绷一段休息时间',
    action: 'services'
  },
  {
    id: 'ai',
    theme: 'forest',
    eyebrow: 'AI CONCIERGE',
    title: '一句话完成预约',
    subtitle: '告诉智能顾问你的时间、预算和偏好',
    action: 'ai'
  }
]

const activities = [
  {
    id: 'new-guest',
    kicker: '新客体验',
    title: '首次到店礼遇',
    description: '活动能力尚未接入，当前仅作品牌展示',
    theme: 'cream'
  },
  {
    id: 'seasonal',
    kicker: '时令护理',
    title: '秋日舒缓专题',
    description: '具体价格以服务详情中的后端数据为准',
    theme: 'green'
  }
]

const bookingNotices = [
  '建议提前 10 分钟到店，以便完成护理前沟通。',
  '如需取消，请在预约开始前操作，最终以系统校验结果为准。',
  '特殊健康情况请在备注中说明，并于到店后主动告知技师。'
]

module.exports = {
  banners,
  activities,
  bookingNotices
}
