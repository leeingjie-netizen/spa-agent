const { getServiceDetail } = require('../../api/services')
const { getStaffByService } = require('../../api/staff')
const { bookingNotices } = require('../../config/demo-data')

Page({
  data: {
    serviceId: '',
    service: null,
    staffList: [],
    imageFailed: false,
    bookingNotices,
    loading: true,
    errorMessage: ''
  },

  onLoad(options) {
    const serviceId = String(options.id || '')

    if (!serviceId) {
      this.setData({ loading: false, errorMessage: '服务参数不正确' })
      return
    }

    this.setData({ serviceId })
    this.loadService()
  },

  onShow() { if (this._detailLoaded) this.loadService() },

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
      const [service, staffList] = await Promise.all([getServiceDetail(this.data.serviceId), getStaffByService(this.data.serviceId)])
      if (!service) throw new Error('该护理暂不可用')
      this.setData({ service, staffList: (staffList || []).filter(item => Number(item.status) === 1) })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this._detailLoaded = true
      this.setData({ loading: false })
    }
  },

  imageError() { this.setData({ imageFailed: true }) },

  onShareAppMessage() {
    const service = this.data.service
    return {
      title: service ? `${service.name}｜栖心 SPA` : '栖心 SPA',
      path: `/pages/service-detail/index?id=${this.data.serviceId}`
    }
  }
})
