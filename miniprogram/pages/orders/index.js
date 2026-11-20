const { requireLogin } = require('../../utils/session')
const { listOrders, cancelOrder } = require('../../api/orders')
const { decorateOrder } = require('../../utils/order')

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unpaid', label: '待支付' },
  { key: 'upcoming', label: '待服务' },
  { key: 'inService', label: '服务中' },
  { key: 'completed', label: '已完成' },
  { key: 'cancelled', label: '已取消' }
]

Page({
  data: {
    tabs,
    activeTab: 'all',
    appointments: [],
    filteredAppointments: [],
    loading: true,
    cancellingId: '',
    errorMessage: ''
  },

  onLoad() {},

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 2 })
    this.loadAppointments()
  },

  onPullDownRefresh() {
    this.loadAppointments().finally(() => wx.stopPullDownRefresh())
  },

  async loadAppointments() {
    if (!requireLogin()) { this.setData({ loading: false, appointments: [], filteredAppointments: [] }); return }
    this.setData({ loading: true, errorMessage: '' })
    try {
      const appointments = (await listOrders() || []).map(decorateOrder)
      this.setData({ appointments })
      this.applyFilter()
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  selectTab(event) {
    this.setData({ activeTab: event.currentTarget.dataset.key })
    this.applyFilter()
  },

  applyFilter() {
    const { activeTab, appointments } = this.data
    let filteredAppointments = appointments
    if (activeTab !== 'all') {
      filteredAppointments = appointments.filter((item) => item.statusGroup === activeTab)
    }
    this.setData({ filteredAppointments })
  },

  viewDetail(event) {
    wx.navigateTo({ url: `/pages/order-detail/index?id=${event.currentTarget.dataset.id}` })
  },

  rebook(event) {
    wx.navigateTo({ url: `/pages/booking/index?serviceId=${event.currentTarget.dataset.serviceId}` })
  },

  confirmCancel(event) {
    const appointmentId = String(event.currentTarget.dataset.id)
    if (this.data.cancellingId) return
    wx.showModal({
      title: '取消预约',
      content: '取消后对应时段将被释放。最终是否允许取消由后端再次校验。',
      confirmText: '确认取消',
      confirmColor: '#9B5E4A',
      success: (result) => {
        if (result.confirm) this.cancelOrder(appointmentId)
      }
    })
  },

  async cancelOrder(appointmentId) {
    if (this.data.cancellingId) return
    this.setData({ cancellingId: appointmentId })
    try {
      await cancelOrder(appointmentId)
      wx.showToast({ title: '预约已取消', icon: 'success' })
      await this.loadAppointments()
    } catch (error) {
      wx.showModal({ title: '取消失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ cancellingId: '' })
    }
  },

  goToServices() {
    wx.switchTab({ url: '/pages/services/index' })
  }
})
