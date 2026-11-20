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
      const previous = this.data.selected
      wx.switchTab({
        url: path,
        success: () => this.setData({ selected: Number(index) }),
        fail: () => {
          this.setData({ selected: previous })
          wx.showToast({ title: '页面暂时无法打开，请重试', icon: 'none' })
        }
      })
    }
  }
})
