const { getRuntimeConfig } = require('./config/env')

App({
  globalData: {
    runtime: getRuntimeConfig(),
    currentUser: {
      id: '1',
      nickname: '体验用户',
      avatarUrl: ''
    }
  }
})
