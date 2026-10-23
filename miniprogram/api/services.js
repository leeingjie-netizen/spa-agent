const { request } = require('../utils/request')

function getServices() {
  return request({ url: '/api/services' })
}

function getServiceDetail(serviceId) {
  return request({ url: `/api/services/${serviceId}` })
}

module.exports = { getServices, getServiceDetail }
