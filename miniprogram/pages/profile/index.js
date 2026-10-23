const features = require('../../config/features')
const { getRuntimeConfig } = require('../../config/env')

Page({
  data: {
    user: null,
    runtime: getRuntimeConfig(),
    assetItems: [
      { key: 'balance', label: '余额', value: '未开通' },
      { key: 'coupons', label: '优惠券', value: '未开通' },
      { key: 'points', label: '积分', value: '未开通' }
    ],
    serviceItems: [
      { key: 'appointments', title: '我的预约', description: '查看预约与取消状态', enabled: true },
      { key: 'recharge', title: '储值与充值', description: '账户能力尚未接入', enabled: false },
      { key: 'pointsMall', title: '积分商城', description: '积分能力尚未接入', enabled: false },
      { key: 'reviews', title: '我的评价', description: '评价能力尚未接入', enabled: false },
      { key: 'favorites', title: '我的收藏', description: '收藏能力尚未接入', enabled: false },
      { key: 'customerService', title: '联系客服', description: '正式客服尚未配置', enabled: features.customerServiceEnabled },
      { key: 'faq', title: '常见问题', description: '预约、到店与取消说明', enabled: true }
    ]
  },

  onLoad() {
    this.setData({ user: getApp().globalData.currentUser })
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 3 })
  },

  goToOrders() {
    wx.switchTab({ url: '/pages/orders/index' })
  },

  handleAsset(event) {
    wx.showModal({
      title: `${event.currentTarget.dataset.label}功能未开通`,
      content: '当前后端尚未实现该账户资产能力，因此不展示虚构余额或权益。',
      showCancel: false,
      confirmColor: '#315C4C'
    })
  },

  handleService(event) {
    const { key, enabled } = event.currentTarget.dataset
    if (key === 'appointments') {
      this.goToOrders()
      return
    }
    if (key === 'faq') {
      wx.showModal({
        title: '预约须知',
        content: '预约时段以系统实时查询为准；取消操作必须通过后端规则校验；支付功能目前尚未接入。',
        showCancel: false,
        confirmColor: '#315C4C'
      })
      return
    }
    if (!enabled) {
      wx.showToast({ title: '该功能尚未开通', icon: 'none' })
    }
  },

  showEnvironment() {
    wx.showModal({
      title: '当前运行环境',
      content: `${this.data.runtime.name}\n${this.data.runtime.baseUrl}\n当前 userId=${this.data.user.id}，仅用于开发演示。`,
      showCancel: false
    })
  }
})
