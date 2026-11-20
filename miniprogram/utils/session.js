const KEY = 'spaSession'
function getSession() { return wx.getStorageSync(KEY) || null }
function saveSession(session) {
  const previous = getSession()
  if (previous && String(previous.user.id) !== String(session.user.id)) clearSession()
  wx.setStorageSync(KEY, session)
  const app = getApp()
  app.globalData.currentUser = session.user
}
function clearSession() {
  wx.removeStorageSync(KEY)
  wx.removeStorageSync('lastBookingResult')
  wx.removeStorageSync('aiConversationId')
  wx.removeStorageSync('pendingBooking')
  wx.removeStorageSync('selectedOrder')
  const app = getApp()
  if (app) app.globalData.currentUser = null
}
function requireLogin() {
  if (getSession()) return true
  wx.navigateTo({ url: '/pages/login/index' })
  return false
}
module.exports = { getSession, saveSession, clearSession, requireLogin }
