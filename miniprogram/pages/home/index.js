const { getServices } = require('../../api/services')
const { getStaffList } = require('../../api/staff')
const { banners, activities } = require('../../config/demo-data')

Page({
  data: {
    banners,
    activities,
    services: [],
    staff: [],
    loading: true,
    errorMessage: ''
  },

  onLoad() {
    this.loadHomeData()
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 0 })
  },

  onPullDownRefresh() {
    this.loadHomeData().finally(() => wx.stopPullDownRefresh())
  },

  async loadHomeData() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [services, staff] = await Promise.all([getServices(), getStaffList()])
      this.setData({
        services: (services || []).slice(0, 3),
        staff: (staff || []).slice(0, 3)
      })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this.setData({ loading: false })
    }
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
