const { request } = require('../utils/request')

function sendChatMessage(data) {
  return request({ url: '/api/ai/chat', method: 'POST', data, timeout: 60000 })
}

module.exports = { sendChatMessage }
