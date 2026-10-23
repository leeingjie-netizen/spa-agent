const { getServices } = require('../../api/services')

const categories = [
  { key: 'all', label: '全部' },
  { key: 'shoulder', label: '肩颈舒缓' },
  { key: 'body', label: '全身护理' },
  { key: 'foot', label: '足部护理' }
]

Page({
  data: {
    categories,
    activeCategory: 'all',
    keyword: '',
    services: [],
    filteredServices: [],
    loading: true,
    errorMessage: ''
  },

  onLoad() {
    this.loadServices()
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 1 })
  },

  onPullDownRefresh() {
    this.loadServices().finally(() => wx.stopPullDownRefresh())
  },

  async loadServices() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const services = await getServices()
      this.setData({ services: services || [] })
      this.applyFilters()
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  handleKeywordInput(event) {
    this.setData({ keyword: event.detail.value })
    this.applyFilters()
  },

  clearKeyword() {
    this.setData({ keyword: '' })
    this.applyFilters()
  },

  selectCategory(event) {
    this.setData({ activeCategory: event.currentTarget.dataset.key })
    this.applyFilters()
  },

  applyFilters() {
    const { services, keyword, activeCategory } = this.data
    const normalizedKeyword = keyword.trim().toLowerCase()

    const filteredServices = services.filter((service) => {
      const searchable = `${service.name || ''} ${service.description || ''}`.toLowerCase()
      const keywordMatched = !normalizedKeyword || searchable.includes(normalizedKeyword)
      let categoryMatched = true

      if (activeCategory === 'shoulder') categoryMatched = /肩|颈/.test(searchable)
      if (activeCategory === 'body') categoryMatched = /精油|泰式|全身|spa/i.test(searchable)
      if (activeCategory === 'foot') categoryMatched = /足/.test(searchable)

      return keywordMatched && categoryMatched
    })

    this.setData({ filteredServices })
  },

  goToDetail(event) {
    wx.navigateTo({ url: `/pages/service-detail/index?id=${event.detail.serviceId}` })
  },

  goToBooking(event) {
    wx.navigateTo({ url: `/pages/booking/index?serviceId=${event.detail.serviceId}` })
  }
})
