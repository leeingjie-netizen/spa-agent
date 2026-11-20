Page({
  data: {
    result: null
  },

  onLoad() {
    const result = wx.getStorageSync('lastBookingResult')
    this.setData({ result: result && result.state === 'success' && result.appointment && result.appointment.id ? result : null })
  },

  goToOrders() {
    wx.switchTab({ url: '/pages/orders/index' })
  },

  goToHome() {
    wx.switchTab({ url: '/pages/home/index' })
  }
})
