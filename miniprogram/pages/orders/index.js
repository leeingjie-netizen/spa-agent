const { getAppointmentSummaries, cancelAppointment } = require('../../api/appointments')

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unpaid', label: '待支付' },
  { key: 'upcoming', label: '待服务' },
  { key: 'inService', label: '服务中' },
  { key: 'completed', label: '已完成' }
]

function decorateAppointment(item) {
  const statusMap = {
    0: { label: '待确认', tone: 'pending', group: 'upcoming' },
    1: { label: '已预约', tone: 'booked', group: 'upcoming' },
    2: { label: '已完成', tone: 'completed', group: 'completed' },
    3: { label: '已取消', tone: 'cancelled', group: 'cancelled' }
  }
  const meta = statusMap[item.status] || { label: '状态未知', tone: 'unknown', group: 'unknown' }
  return {
    ...item,
    appointmentId: String(item.appointmentId),
    serviceId: String(item.serviceId),
    staffId: String(item.staffId),
    startTimeDisplay: String(item.startTime || '').slice(0, 5),
    endTimeDisplay: String(item.endTime || '').slice(0, 5),
    statusLabel: meta.label,
    statusTone: meta.tone,
    statusGroup: meta.group
  }
}

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

  onLoad() {
    this.loadAppointments()
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar()
    if (tabBar) tabBar.setData({ selected: 2 })
    if (this.data.appointments.length) this.loadAppointments()
  },

  onPullDownRefresh() {
    this.loadAppointments().finally(() => wx.stopPullDownRefresh())
  },

  async loadAppointments() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const userId = getApp().globalData.currentUser.id
      const appointments = (await getAppointmentSummaries(userId) || []).map(decorateAppointment)
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
    const appointmentId = String(event.currentTarget.dataset.id)
    const order = this.data.appointments.find((item) => item.appointmentId === appointmentId)
    wx.setStorageSync('selectedOrder', order)
    wx.navigateTo({ url: '/pages/order-detail/index' })
  },

  rebook(event) {
    wx.navigateTo({ url: `/pages/booking/index?serviceId=${event.currentTarget.dataset.serviceId}` })
  },

  requestPayment() {
    wx.showModal({
      title: '支付功能待接入',
      content: '当前后端尚未接入微信支付。正式接入后将由后端创建支付单并校验支付结果。',
      showCancel: false,
      confirmColor: '#315C4C'
    })
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
    this.setData({ cancellingId: appointmentId })
    try {
      await cancelAppointment(appointmentId, getApp().globalData.currentUser.id)
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
