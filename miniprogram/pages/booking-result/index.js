Page({
  data: {
    result: null
  },

  onLoad() {
    this.setData({ result: wx.getStorageSync('lastBookingResult') || null })
  },

  goToOrders() {
    wx.switchTab({ url: '/pages/orders/index' })
  },

  goToHome() {
    wx.switchTab({ url: '/pages/home/index' })
  }
})
