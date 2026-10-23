const { request } = require('../utils/request')

function createAppointment(data) {
  return request({ url: '/api/appointments', method: 'POST', data })
}

function getAppointmentSummaries(userId) {
  return request({ url: `/api/appointments/user/${userId}/summaries` })
}

function cancelAppointment(appointmentId, userId) {
  return request({
    url: `/api/appointments/${appointmentId}/cancel`,
    method: 'POST',
    data: { userId },
    header: { 'content-type': 'application/x-www-form-urlencoded' }
  })
}

module.exports = { createAppointment, getAppointmentSummaries, cancelAppointment }
