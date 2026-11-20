const { request } = require('../utils/request')
const { saveSession, clearSession } = require('../utils/session')
async function loginWechat() {
  const { code } = await new Promise((resolve, reject) => wx.login({ success: resolve, fail: reject }))
  if (!code) throw new Error('微信未返回登录凭证，请重试')
  const result = await request({ url: '/api/auth/wechat-login', method: 'POST', data: { code } })
  saveSession(result)
  return result
}
async function logout() {
  try { await request({ url: '/api/auth/logout', method: 'POST' }) } finally { clearSession() }
}
const getMe = () => request({ url: '/api/auth/me' })
module.exports = { loginWechat, logout, getMe }
