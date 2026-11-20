const { loginWechat } = require('../../api/auth')
const { request } = require('../../utils/request')
const { saveSession } = require('../../utils/session')
const { getRuntimeConfig } = require('../../config/env')
Page({
 data: { loading: false, errorMessage: '', devLoginEnabled: getRuntimeConfig().devLoginEnabled, identity: '' },
 handleIdentity(e) { this.setData({ identity: e.detail.value }) },
 async devLogin() {
  if (this.data.loading || !this.data.devLoginEnabled) return
  if (!/^[a-zA-Z0-9_-]{3,40}$/.test(this.data.identity)) { this.setData({ errorMessage: '测试身份请输入3至40位字母、数字或下划线' }); return }
  this.setData({ loading: true, errorMessage: '' })
  try {
   const session = await request({ url: '/api/auth/dev-login', method: 'POST', data: { identity: this.data.identity } })
   saveSession(session)
   wx.switchTab({ url: '/pages/profile/index' })
  } catch (error) { this.setData({ errorMessage: error.message }) }
  finally { this.setData({ loading: false }) }
 },
 async login() {
  if (this.data.loading) return
  this.setData({ loading: true, errorMessage: '' })
  try {
   await loginWechat()
   const pages = getCurrentPages()
   if (pages.length > 1) wx.navigateBack()
   else wx.switchTab({ url: '/pages/profile/index' })
  } catch (error) { this.setData({ errorMessage: error.message || '微信登录暂不可用，请稍后重试' }) }
  finally { this.setData({ loading: false }) }
 },
 browse() { wx.switchTab({ url: '/pages/home/index' }) }
})
