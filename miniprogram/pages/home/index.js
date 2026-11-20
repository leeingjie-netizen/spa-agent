const { getServices } = require('../../api/services')
const { request } = require('../../utils/request')
const { getStaffList } = require('../../api/staff')
const banners = [{ id: 'brand', theme: 'sage', eyebrow: 'A MOMENT FOR YOURSELF', title: '留一刻，给自己', subtitle: '从一次护理开始，照顾好自己', action: 'services' }]

Page({
  data: {
    banners,
    activities: [],
    contentError: '',
    greeting: '欢迎来到栖心，给自己一段松弛时光',
    services: [],
    staff: [],
    loading: true,
    errorMessage: ''
  },

  onLoad() {
    this.loadHomeData()
    this.loadContent()
  },

  onShow() {
    const user = getApp().globalData.currentUser
    this.setData({ greeting: user ? `${user.nickname || '你好'}，今天也照顾好自己` : '欢迎来到栖心，给自己一段松弛时光' })
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 0 })
    if (this._homeLoaded) { this.loadHomeData(); this.loadContent() }
  },

  onPullDownRefresh() {
    Promise.all([this.loadHomeData(), this.loadContent()]).finally(() => wx.stopPullDownRefresh())
  },

  async loadHomeData() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [services, staff] = await Promise.all([getServices(), getStaffList()])
      this.setData({
        services: (services || []).slice(0, 3),
        staff: (staff || []).filter(item => Number(item.status) === 1).slice(0, 3)
      })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this._homeLoaded = true
      this.setData({ loading: false })
    }
  },

  async loadContent() {
    try { const activities = await request({ url: '/api/operations/content' }); this.setData({ activities: (activities || []).map(item => ({...item, imageFailed: false})), contentError: '' }) }
    catch (error) { this.setData({ contentError: error.message || '门店动态加载失败' }) }
  },

  handleActivityImageError(event) {
    const id = String(event.currentTarget.dataset.id)
    this.setData({ activities: this.data.activities.map(item => String(item.id) === id ? {...item, imageFailed: true} : item) })
  },

  handleBannerTap(event) {
    const { action } = event.currentTarget.dataset
    if (action === 'ai') {
      wx.navigateTo({ url: '/pages/ai-chat/index' })
      return
    }
    wx.switchTab({ url: '/pages/services/index' })
  },

  goToServices() {
    wx.switchTab({ url: '/pages/services/index' })
  },

  goToOrders() {
    wx.switchTab({ url: '/pages/orders/index' })
  },

  goToAi() {
    wx.navigateTo({ url: '/pages/ai-chat/index' })
  },

  goToServiceDetail(event) {
    wx.navigateTo({ url: `/pages/service-detail/index?id=${event.detail.serviceId}` })
  },

  goToBooking(event) {
    wx.navigateTo({ url: `/pages/booking/index?serviceId=${event.detail.serviceId}` })
  }
})
