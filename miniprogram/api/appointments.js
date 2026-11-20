const { request } = require('../utils/request')

function createAppointment(data) {
  return request({ url: '/api/appointments', method: 'POST', data })
}

function getAppointmentSummaries() {
  return request({ url: '/api/appointments/me' })
}

function cancelAppointment(appointmentId) {
  return request({
    url: `/api/appointments/${appointmentId}/cancel`,
    method: 'POST'
  })
}

function getAppointment(id) { return request({ url: `/api/appointments/${id}` }) }
module.exports = { createAppointment, getAppointmentSummaries, cancelAppointment, getAppointment }
