const { getRuntimeConfig } = require('./config/env')

App({
  globalData: {
    runtime: getRuntimeConfig(),
    currentUser: null
  },
  onLaunch() {
    const session = wx.getStorageSync('spaSession')
    if (session && session.user) this.globalData.currentUser = session.user
  }
})
