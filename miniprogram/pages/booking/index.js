const { getServiceDetail } = require('../../api/services')
const { getStaffByService, getAvailableSlots } = require('../../api/staff')
const { createAppointment } = require('../../api/appointments')
const { buildDateOptions, trimTime } = require('../../utils/date')
const { requireLogin, getSession } = require('../../utils/session')
const { createClientId } = require('../../utils/id')

Page({
  data: {
    serviceId: '',
    service: null,
    staffList: [],
    dateOptions: [],
    selectedStaffId: '',
    selectedDate: '',
    selectedTime: '',
    slots: [],
    remark: '',
    loading: true,
    slotsLoading: false,
    slotsError: '',
    submitting: false,
    errorMessage: ''
  },

  onLoad(options) {
    const serviceId = String(options.serviceId || '')
    const dateOptions = buildDateOptions(30)
    this.setData({
      serviceId,
      dateOptions,
      selectedDate: dateOptions[0].value
    })
    if (!serviceId) {
      this.setData({ loading: false, errorMessage: '缺少服务参数' })
      return
    }
    this.loadBookingBase()
  },

  onShow() { if (this._baseLoaded && !this.data.submitting) this.loadBookingBase() },

  async loadBookingBase() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [service, staffList] = await Promise.all([
        getServiceDetail(this.data.serviceId),
        getStaffByService(this.data.serviceId)
      ])
      const available = (staffList || []).filter(item => Number(item.status) === 1)
      if (this.data.selectedStaffId && !available.some(item => String(item.id) === this.data.selectedStaffId)) {
        this._slotsSequence = (this._slotsSequence || 0) + 1
        this.setData({ selectedStaffId: '', selectedTime: '', slots: [], slotsLoading: false })
      }
      this.setData({
        service,
        staffList: (staffList || []).filter(item => Number(item.status) === 1).map((item) => ({
          ...item,
          id: String(item.id)
        }))
      })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this._baseLoaded = true
      this.setData({ loading: false })
    }
  },

  onUnload() { this._slotsSequence = (this._slotsSequence || 0) + 1 },

  selectStaff(event) {
    if (this.data.submitting) return
    const selectedStaffId = String(event.currentTarget.dataset.id)
    if (!this.data.staffList.some(item => item.id === selectedStaffId && Number(item.status) === 1)) return
    this.setData({ selectedStaffId, selectedTime: '', slots: [] })
    this.loadSlots()
  },

  selectDate(event) {
    if (this.data.submitting) return
    this.setData({
      selectedDate: event.currentTarget.dataset.date,
      selectedTime: '',
      slots: []
    })
    if (this.data.selectedStaffId) this.loadSlots()
  },

  selectTime(event) {
    if (this.data.submitting || this.data.slotsLoading) return
    const time = event.currentTarget.dataset.time
    if (this.data.slots.includes(time)) this.setData({ selectedTime: time })
  },

  handleRemarkInput(event) {
    this.setData({ remark: event.detail.value })
  },

  async loadSlots() {
    const { selectedStaffId, serviceId, selectedDate } = this.data
    if (!selectedStaffId || !selectedDate) return

    const sequence = this._slotsSequence = (this._slotsSequence || 0) + 1
    this.setData({ slotsLoading: true, slotsError: '', selectedTime: '', slots: [] })
    try {
      const slots = await getAvailableSlots(selectedStaffId, serviceId, selectedDate)
      if (sequence === this._slotsSequence) this.setData({ slots: (slots || []).map(trimTime) })
    } catch (error) {
      if (sequence === this._slotsSequence) this.setData({ slotsError: error.message })
    } finally {
      if (sequence === this._slotsSequence) this.setData({ slotsLoading: false })
    }
  },

  submitBooking() {
    if (!requireLogin()) return
    if (this._confirming) return
    const { service, selectedStaffId, selectedDate, selectedTime, staffList } = this.data
    if (!selectedStaffId || !selectedDate || !selectedTime || this.data.submitting) return

    const staff = staffList.find((item) => String(item.id) === selectedStaffId)
    if (!service || !staff || this.data.slotsLoading) return
    this._confirming = true
    wx.showModal({
      title: '确认预约信息',
      content: `${service.name}\n${staff.name} · ${selectedDate} ${selectedTime}\n预约金额 ¥${service.price}`,
      confirmText: '确认预约',
      confirmColor: '#315C4C',
      success: (result) => {
        if (result.confirm) this.createBooking(staff)
      },
      complete: () => { this._confirming = false }
    })
  },

  async createBooking(staff) {
    if (this.data.submitting || !requireLogin()) return
    const payload = {
      serviceId: this.data.serviceId,
      staffId: this.data.selectedStaffId,
      appointmentDate: this.data.selectedDate,
      startTime: this.data.selectedTime,
      remark: this.data.remark.trim()
    }
    const fingerprint = JSON.stringify({ ...payload, userId: getSession().user.id })
    const pending = wx.getStorageSync('pendingBooking')
    const requestId = pending && pending.fingerprint === fingerprint ? pending.requestId : createClientId('mini')
    wx.setStorageSync('pendingBooking', { fingerprint, requestId })
    wx.removeStorageSync('lastBookingResult')
    this.setData({ submitting: true })
    try {
      const appointment = await createAppointment({ ...payload, requestId })
      if (!appointment || !appointment.id) throw new Error('服务端未返回预约确认，请查看订单后再重试')
      wx.removeStorageSync('pendingBooking')
      wx.setStorageSync('lastBookingResult', {
        state: 'success',
        appointment,
        serviceName: this.data.service.name,
        staffName: staff.name
      })
      wx.redirectTo({ url: '/pages/booking-result/index' })
    } catch (error) {
      const uncertain = !error.statusCode || error.statusCode >= 500
      wx.showModal({ title: uncertain ? '暂未确认预约结果' : '预约未完成', content: uncertain ? `${error.message}。请求可能已到达，请先查看订单；同一信息重试会沿用原请求标识。` : error.message, confirmText: '查看订单', cancelText: '留在此页', success: (result) => { if (result.confirm) wx.switchTab({ url: '/pages/orders/index' }) } })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
