const loyalty = require('../../api/loyalty')
const { getOrder, cancelOrder, payOrder } = require('../../api/orders')
const { decorateOrder } = require('../../utils/order')
const { createClientId } = require('../../utils/id')
const { getRuntimeConfig } = require('../../config/env')
const { requireLogin } = require('../../utils/session')

Page({
 data: { id: '', order: null, loading: true, errorMessage: '', cancelling: false, paying: false, mockPayment: getRuntimeConfig().environment === 'development', coupons: [], couponLabels: ['不使用优惠券'], couponIndex: 0, points: '', applying: false, benefitNote: '', paymentChoices: ['开发模拟支付', '开发测试余额'], paymentIndex: 0 },
 onLoad(options) { this.setData({ id: String(options.id || '') }) },
 onShow() { this.loadOrder() },
 async loadOrder() {
  if (!requireLogin()) { this.setData({ loading: false }); return }
  if (!this.data.id) { this.setData({ loading: false, errorMessage: '缺少预约编号，请从列表重新进入' }); return }
  this.setData({ loading: true, errorMessage: '' })
  try {
   const item = await getOrder(this.data.id)
   if (!item) throw new Error('预约不存在')
   this.setData({ order: decorateOrder(item) })
   if (item.status === 'WAITING_PAYMENT') {
    try { const result = await loyalty.coupons(); const coupons = (result.owned || []).filter(c => c.status === 'AVAILABLE'); this.setData({ coupons, couponLabels: ['不使用优惠券', ...coupons.map(c => `${c.title} · 满${c.minAmount}减${c.discountAmount}`)], couponIndex: 0 }) } catch (_) { /* order remains usable when optional coupon query fails */ }
   }
  } catch (error) { this.setData({ errorMessage: error.message, order: null }) }
  finally { this.setData({ loading: false }) }
 },
 selectCoupon(e) { this.setData({ couponIndex: Number(e.detail.value) }) },
 inputPoints(e) { this.setData({ points: e.detail.value }) },
 selectPayment(e) { this.setData({ paymentIndex: Number(e.detail.value) }) },
 async applyBenefits() {
  if (this.data.applying || this.data.paying || !this.data.order.canPay) return
  if (this.data.points && !/^\d+$/.test(this.data.points)) { this.setData({ benefitNote: '积分请输入非负整数' }); return }
  this.setData({ applying: true, benefitNote: '' })
  const coupon = this.data.coupons[this.data.couponIndex - 1]
  try { await loyalty.applyBenefits(this.data.id, { couponId: coupon ? coupon.id : null, points: Number(this.data.points || 0) }); this._payRequestId = null; await this.loadOrder(); this.setData({ benefitNote: '优惠已计入当前应付金额。再次确认将按新选择更新优惠。', points: '' }) }
  catch (error) { this.setData({ benefitNote: error.message }) }
  finally { this.setData({ applying: false }) }
 },
 review() { wx.navigateTo({ url: `/pages/review/index?id=${this.data.id}` }) },
 pay() {
  if (this.data.paying || this.data.applying || !this.data.mockPayment || !this.data.order.canPay) return
  wx.showModal({ title: '开发模拟支付', content: '此操作只验证测试订单状态，不会扣款，也不代表门店真实收款。是否继续？', confirmText: '确认模拟', confirmColor: '#315C4C', success: async result => {
   if (!result.confirm || this.data.paying) return
   this.setData({ paying: true })
   const channel = this.data.paymentIndex === 1 ? 'MOCK_WALLET' : 'MOCK'
   if (this._payChannel !== channel) this._payRequestId = null
   this._payChannel = channel
   this._payRequestId = this._payRequestId || createClientId('mock-pay')
   try { await payOrder(this.data.id, this._payRequestId, channel); await this.loadOrder(); wx.showToast({ title: '模拟支付已确认', icon: 'none' }) }
   catch (error) { wx.showModal({ title: '模拟支付未确认', content: `${error.message}。请刷新订单确认结果后再试。`, showCancel: false }) }
   finally { this.setData({ paying: false }) }
  } })
 },
 rebook() { wx.redirectTo({ url: `/pages/booking/index?serviceId=${this.data.order.serviceId}` }) },
 confirmCancel() {
  if (this.data.cancelling) return
  wx.showModal({ title: '取消这次预约？', content: '取消后将释放护理时段，最终结果以服务端校验为准。', confirmText: '确认取消', confirmColor: '#9B5E4A', success: async (result) => {
   if (!result.confirm || this.data.cancelling) return
   this.setData({ cancelling: true })
   try { await cancelOrder(this.data.order.id); wx.showToast({ title: '预约已取消' }); await this.loadOrder() }
   catch (error) { wx.showModal({ title: '取消未完成', content: error.message, showCancel: false }) }
   finally { this.setData({ cancelling: false }) }
  } })
 }
})
