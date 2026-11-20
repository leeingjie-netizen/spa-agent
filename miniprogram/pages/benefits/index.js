const api = require('../../api/loyalty')
const { requireLogin } = require('../../utils/session')
const { createClientId } = require('../../utils/id')
const levels = { STANDARD: '标准会员', SILVER: '银卡会员', GOLD: '金卡会员' }
const couponStatus = { AVAILABLE: '可使用', RESERVED: '订单已锁定', USED: '已使用', EXPIRED: '已过期' }
Page({
 data: { tab: 'member', tabs: [{key:'member',label:'会员'},{key:'coupons',label:'优惠券'},{key:'wallet',label:'测试余额'}], loading: true, errorMessage: '', summary: null, templates: [], owned: [], claiming: '', toppingUp: false, showTopup: false, amount: '', levelLabel: '' },
 onLoad(options) { this.setData({ tab: ['member','coupons','wallet'].includes(options.tab) ? options.tab : 'member' }) },
 onShow() { this.load() },
 switchTab(e) { this.setData({ tab: e.currentTarget.dataset.tab }) },
 inputAmount(e) { this.setData({ amount: e.detail.value }) },
 toggleTopup() { this.setData({showTopup: !this.data.showTopup}) },
 async load() {
  if (!requireLogin()) { this.setData({ loading: false }); return }
  this.setData({ loading: true, errorMessage: '' })
  try {
   const [summary, coupons] = await Promise.all([api.summary(), api.coupons()])
   this.setData({ summary, levelLabel: levels[summary.memberLevel] || summary.memberLevel, templates: coupons.templates || [], owned: (coupons.owned || []).map(c => ({...c,statusLabel:couponStatus[c.status] || c.status})) })
  } catch (error) { this.setData({ errorMessage: error.message }) }
  finally { this.setData({ loading: false }) }
 },
 async claim(e) {
  if (this.data.claiming) return
  this.setData({ claiming: String(e.currentTarget.dataset.id), errorMessage: '' })
  try { await api.claim(this.data.claiming); await this.load(); wx.showToast({ title: '已领取优惠券' }) }
  catch (error) { this.setData({ errorMessage: error.message }) }
  finally { this.setData({ claiming: '' }) }
 },
 topup() {
  if (this.data.toppingUp || !this.data.summary || !this.data.summary.mockWalletEnabled) return
  if (!/^\d{1,5}(\.\d{1,2})?$/.test(this.data.amount) || Number(this.data.amount) <= 0 || Number(this.data.amount) > 10000) { this.setData({ errorMessage: '模拟金额需大于0且不超过10000，最多两位小数' }); return }
  const amount = this.data.amount
  wx.showModal({ title: '增加开发测试余额', content: `将增加 ¥${amount} 测试余额，不发生真实充值，也不能兑换现金。是否继续？`, confirmText: '确认模拟', confirmColor: '#315C4C', success: async result => {
   if (!result.confirm || this.data.toppingUp) return
   this.setData({ toppingUp: true, errorMessage: '' })
   if (!this._topup || this._topup.amount !== amount) this._topup = { amount, requestId: createClientId('test-wallet') }
   try { await api.topup(this._topup); this._topup = null; this.setData({ amount: '' }); await this.load(); wx.showToast({ title: '测试余额已更新', icon: 'none' }) }
   catch (error) { this.setData({ errorMessage: `${error.message}。请刷新核对余额；相同金额重试不会重复增加。` }) }
   finally { this.setData({ toppingUp: false }) }
  } })
 }
})
