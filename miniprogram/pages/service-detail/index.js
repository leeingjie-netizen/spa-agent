const { getServiceDetail } = require('../../api/services')
const { bookingNotices } = require('../../config/demo-data')

Page({
  data: {
    serviceId: '',
    service: null,
    bookingNotices,
    loading: true,
    errorMessage: ''
  },

  onLoad(options) {
    const serviceId = String(options.id || '')

    if (!serviceId) {
      this.setData({ errorMessage: '服务参数不正确' })
      return
    }

    this.setData({ serviceId })
    this.loadService()
  },

  goToBooking() {
    wx.navigateTo({ url: `/pages/booking/index?serviceId=${this.data.serviceId}` })
  },

  goToAi() {
    const service = this.data.service
    wx.navigateTo({
      url: `/pages/ai-chat/index?serviceId=${this.data.serviceId}&serviceName=${encodeURIComponent(service.name)}`
    })
  },

  async loadService() {
    this.setData({
      loading: true,
      errorMessage: ''
    })
    try {
      const service = await getServiceDetail(this.data.serviceId)
      this.setData({ service })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  onShareAppMessage() {
    const service = this.data.service
    return {
      title: service ? `${service.name}｜栖心 SPA` : '栖心 SPA',
      path: `/pages/service-detail/index?id=${this.data.serviceId}`
    }
  }
})
