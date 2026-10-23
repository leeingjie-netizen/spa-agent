const { getServiceDetail } = require('../../api/services')
const { getStaffByService, getAvailableSlots } = require('../../api/staff')
const { createAppointment } = require('../../api/appointments')
const { buildDateOptions, trimTime } = require('../../utils/date')
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
    submitting: false,
    errorMessage: ''
  },

  onLoad(options) {
    const serviceId = String(options.serviceId || '')
    const dateOptions = buildDateOptions(10)
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

  async loadBookingBase() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [service, staffList] = await Promise.all([
        getServiceDetail(this.data.serviceId),
        getStaffByService(this.data.serviceId)
      ])
      this.setData({
        service,
        staffList: (staffList || []).map((item) => ({
          ...item,
          id: String(item.id)
        }))
      })
    } catch (error) {
      this.setData({ errorMessage: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  selectStaff(event) {
    const selectedStaffId = String(event.currentTarget.dataset.id)
    this.setData({ selectedStaffId, selectedTime: '', slots: [] })
    this.loadSlots()
  },

  selectDate(event) {
    this.setData({
      selectedDate: event.currentTarget.dataset.date,
      selectedTime: '',
      slots: []
    })
    if (this.data.selectedStaffId) this.loadSlots()
  },

  selectTime(event) {
    this.setData({ selectedTime: event.currentTarget.dataset.time })
  },

  handleRemarkInput(event) {
    this.setData({ remark: event.detail.value })
  },

  async loadSlots() {
    const { selectedStaffId, serviceId, selectedDate } = this.data
    if (!selectedStaffId || !selectedDate) return

    this.setData({ slotsLoading: true, selectedTime: '', slots: [] })
    try {
      const slots = await getAvailableSlots(selectedStaffId, serviceId, selectedDate)
      this.setData({ slots: (slots || []).map(trimTime) })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    } finally {
      this.setData({ slotsLoading: false })
    }
  },

  submitBooking() {
    const { service, selectedStaffId, selectedDate, selectedTime, staffList } = this.data
    if (!selectedStaffId || !selectedDate || !selectedTime || this.data.submitting) return

    const staff = staffList.find((item) => String(item.id) === selectedStaffId)
    wx.showModal({
      title: '确认预约信息',
      content: `${service.name}\n${staff.name} · ${selectedDate} ${selectedTime}\n预约金额 ¥${service.price}`,
      confirmText: '确认预约',
      confirmColor: '#315C4C',
      success: (result) => {
        if (result.confirm) this.createBooking(staff)
      }
    })
  },

  async createBooking(staff) {
    this.setData({ submitting: true })
    try {
      const appointment = await createAppointment({
        userId: getApp().globalData.currentUser.id,
        serviceId: this.data.serviceId,
        staffId: this.data.selectedStaffId,
        appointmentDate: this.data.selectedDate,
        startTime: this.data.selectedTime,
        remark: this.data.remark.trim(),
        requestId: createClientId('mini')
      })

      wx.setStorageSync('lastBookingResult', {
        appointment,
        serviceName: this.data.service.name,
        staffName: staff.name
      })
      wx.redirectTo({ url: '/pages/booking-result/index' })
    } catch (error) {
      wx.showModal({ title: '预约未完成', content: error.message, showCancel: false })
      if (/时间段|冲突|预约/.test(error.message)) this.loadSlots()
    } finally {
      this.setData({ submitting: false })
    }
  }
})
