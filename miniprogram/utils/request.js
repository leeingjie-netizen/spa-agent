const { getRuntimeConfig } = require('../config/env')

function request(options) {
  const runtime = getRuntimeConfig()

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${runtime.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'content-type': 'application/json',
        ...(options.header || {})
      },
      timeout: options.timeout || 15000,
      success(response) {
        const payload = response.data || {}

        if (response.statusCode >= 200 && response.statusCode < 300 && payload.code === 200) {
          resolve(payload.data)
          return
        }

        const error = new Error(payload.message || `请求失败（HTTP ${response.statusCode}）`)
        error.statusCode = response.statusCode
        error.payload = payload
        reject(error)
      },
      fail(error) {
        const networkError = new Error(error.errMsg || '网络连接失败，请稍后重试')
        networkError.cause = error
        reject(networkError)
      }
    })
  })
}

module.exports = {
  request
}
