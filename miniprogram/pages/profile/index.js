const { getMe, logout } = require('../../api/auth')
const { requireLogin, getSession } = require('../../utils/session')
const features = require('../../config/features')
const { getRuntimeConfig } = require('../../config/env')
const loyalty = require('../../api/loyalty')

Page({
  data: {
    user: null,
    modeLabel: '',
    runtime: getRuntimeConfig(),
    assetItems: [
      { key: 'balance', label: '测试余额', value: '我的余额' },
      { key: 'coupons', label: '我的优惠券', value: '查看优惠券' },
      { key: 'points', label: '我的积分', value: '查看积分' }
    ],
    serviceItems: [
      { key: 'member', title: '会员等级与权益', description: '查看账户等级和实际门店权益', enabled: true },
      { key: 'reviews', title: '服务评价', description: '从已完成订单分享护理体验', enabled: true },
      { key: 'customerService', title: '联系客服', description: '正式客服尚未配置', enabled: features.customerServiceEnabled },
      { key: 'faq', title: '常见问题', description: '预约、到店与取消说明', enabled: true }
    ]
  },

  onLoad() {
    const session = getSession()
    this.setData({ user: getApp().globalData.currentUser, modeLabel: session && session.mode === 'DEVELOPMENT_SIMULATION' ? '开发测试身份' : '你的专属护理空间' })
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 3 })
    const session = getSession()
    this.setData({ user: getApp().globalData.currentUser, modeLabel: session && session.mode === 'DEVELOPMENT_SIMULATION' ? '开发测试身份' : '你的专属护理空间' })
    if (this.data.user) {
      getMe().then((user) => { getApp().globalData.currentUser = user; this.setData({ user }) }).catch(() => this.setData({ user: getApp().globalData.currentUser }))
      this.loadAssets()
    } else this.resetAssets()
  },

  login() { requireLogin() },
  resetAssets() { this.setData({assetItems:[{key:'balance',label:'测试余额',value:'我的余额'},{key:'coupons',label:'我的优惠券',value:'查看优惠券'},{key:'points',label:'我的积分',value:'查看积分'}]}) },
  async loadAssets() {
    const session = getSession(), version = this._assetVersion = (this._assetVersion || 0) + 1
    try {
      const [summary, coupons] = await Promise.all([loyalty.summary(),loyalty.coupons()])
      if (!getSession() || !session || getSession().token !== session.token || this._assetVersion !== version) return
      this.setData({assetItems:[
        {key:'balance',label:'测试余额',value:`¥${Number(summary.walletBalance).toFixed(2)}`},
        {key:'coupons',label:'我的优惠券',value:`${(coupons.owned || []).filter(c=>c.status==='AVAILABLE').length} 张`},
        {key:'points',label:'我的积分',value:String(summary.points)}
      ]})
    } catch (_) { if(getSession() && session && getSession().token === session.token && this._assetVersion === version) this.setData({assetItems:[{key:'balance',label:'测试余额',value:'点击重试'},{key:'coupons',label:'我的优惠券',value:'查看优惠券'},{key:'points',label:'我的积分',value:'查看积分'}]}) }
  },

  async logout() {
    wx.showModal({ title: '退出登录？', content: '退出后仍可浏览服务，预约与订单需要重新登录。', success: async (result) => {
      if (!result.confirm) return
      try { await logout() } catch (_) { /* local session is always cleared */ }
      this.setData({ user: null })
      this._assetVersion = (this._assetVersion || 0) + 1
      this.resetAssets()
    } })
  },

  goToOrders() {
    if (!requireLogin()) return
    wx.switchTab({ url: '/pages/orders/index' })
  },

  goMembership() { if (requireLogin()) wx.navigateTo({ url: '/pages/benefits/index?tab=member' }) },

  handleAsset(event) {
    const key = event.currentTarget.dataset.key
    if (key === 'balance' || key === 'coupons') { if (requireLogin()) wx.navigateTo({ url: `/pages/benefits/index?tab=${key === 'coupons' ? 'coupons' : 'wallet'}` }); return }
    if (key === 'points') { if (requireLogin()) wx.navigateTo({ url: '/pages/points/index' }); return }
    wx.showModal({
      title: `${event.currentTarget.dataset.label}功能未开通`,
      content: '当前后端尚未实现该账户资产能力，因此不展示虚构余额或权益。',
      showCancel: false,
      confirmColor: '#315C4C'
    })
  },

  handleService(event) {
    const { key, enabled } = event.currentTarget.dataset
    if (key === 'member') { this.goMembership(); return }
    if (key === 'points') { if (requireLogin()) wx.navigateTo({ url: '/pages/points/index' }); return }
    if (key === 'reviews') { this.goToOrders(); return }
    if (key === 'recharge') { if (requireLogin()) wx.navigateTo({ url: '/pages/benefits/index?tab=wallet' }); return }
    if (key === 'appointments') {
      this.goToOrders()
      return
    }
    if (key === 'faq') {
      wx.showModal({
        title: '预约须知',
        content: '预约时段以系统实时查询为准；午休、已批准请假和停业时间不可预约。取消结果以服务端规则为准。',
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
    wx.showModal({ title: '关于栖心', content: '每一次护理，都从认真安排你的时间开始。账户信息与预约状态以门店系统为准。', showCancel: false, confirmColor: '#315C4C' })
  }
})
