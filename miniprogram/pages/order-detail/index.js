const { cancelAppointment } = require('../../api/appointments')

Page({
  data: {
    order: null,
    cancelling: false
  },

  onLoad() {
    this.setData({ order: wx.getStorageSync('selectedOrder') || null })
  },

  rebook() {
    wx.redirectTo({ url: `/pages/booking/index?serviceId=${this.data.order.serviceId}` })
  },

  showPaymentNotice() {
    wx.showModal({ title: '支付功能待接入', content: '页面结构已预留。正式支付必须由 Spring Boot 创建支付单、验签并处理回调。', showCancel: false })
  },

  confirmCancel() {
    wx.showModal({
      title: '确认取消预约？',
      content: '后端会再次校验取消规则，并在成功后释放预约时段。',
      confirmText: '取消预约',
      confirmColor: '#9B5E4A',
      success: (result) => {
        if (result.confirm) this.cancelOrder()
      }
    })
  },

  async cancelOrder() {
    this.setData({ cancelling: true })
    try {
      await cancelAppointment(this.data.order.appointmentId, getApp().globalData.currentUser.id)
      wx.showToast({ title: '预约已取消' })
      setTimeout(() => wx.switchTab({ url: '/pages/orders/index' }), 700)
    } catch (error) {
      wx.showModal({ title: '取消失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ cancelling: false })
    }
  }
})
