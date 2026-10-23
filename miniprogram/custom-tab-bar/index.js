Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', icon: 'home' },
      { pagePath: '/pages/services/index', text: '服务', icon: 'services' },
      { pagePath: '/pages/orders/index', text: '订单', icon: 'orders' },
      { pagePath: '/pages/profile/index', text: '我的', icon: 'profile' }
    ]
  },
  methods: {
    switchTab(event) {
      const { path, index } = event.currentTarget.dataset
      this.setData({ selected: Number(index) })
      wx.switchTab({ url: path })
    }
  }
})
