const { request } = require('../utils/request')

function getStaffList() {
  return request({ url: '/api/staff' })
}

function getStaffByService(serviceId) {
  return request({ url: `/api/staff/${serviceId}/staff` })
}

function getAvailableSlots(staffId, serviceId, date) {
  return request({
    url: `/api/staff/${staffId}/availableSlots`,
    data: { serviceId, date }
  })
}

module.exports = { getStaffList, getStaffByService, getAvailableSlots }
