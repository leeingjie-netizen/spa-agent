const { getRuntimeConfig } = require('../config/env')
const { getSession, clearSession } = require('./session')
let redirectingToLogin = false

function request(options) {
  const runtime = getRuntimeConfig()

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${runtime.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'content-type': 'application/json',
        ...(getSession() ? { Authorization: `Bearer ${getSession().token}` } : {}),
        ...(options.header || {})
      },
      timeout: options.timeout || 15000,
      success(response) {
        const payload = response.data || {}
        if (response.statusCode === 401 && /^\/api\/auth\/(?:admin|staff|wechat|dev)-login$/.test(options.url)) {
          const error = new Error(payload.message || '登录认证失败，请重试')
          error.statusCode = 401
          reject(error)
          return
        }
        if (response.statusCode === 401) {
          clearSession()
          const pages = getCurrentPages()
          if (!redirectingToLogin && (!pages.length || pages[pages.length - 1].route !== 'pages/login/index')) {
            redirectingToLogin = true
            wx.navigateTo({ url: '/pages/login/index', complete: () => { redirectingToLogin = false } })
          }
          const error = new Error('登录已过期，请重新登录')
          error.statusCode = 401
          reject(error)
          return
        }

        if (response.statusCode >= 200 && response.statusCode < 300 && payload.code === 200) {
          resolve(payload.data)
          return
        }

        const error = new Error(payload.message || payload.msg || `请求失败（HTTP ${response.statusCode}）`)
        error.statusCode = response.statusCode
        error.payload = payload
        reject(error)
      },
      fail(error) {
        const networkError = new Error(/timeout/i.test(error.errMsg || '') ? '请求超时，请检查网络后重试；提交结果可到订单页核对' : '暂时无法连接服务，请检查网络后重试')
        networkError.cause = error
        reject(networkError)
      }
    })
  })
}

module.exports = {
  request
}
