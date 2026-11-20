const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/session')
Page({
 data: { id: '', rating: 5, comment: '', sending: false, errorMessage: '', done: false, stars: [1,2,3,4,5] },
 onLoad(options) { this.setData({ id: String(options.id || '') }) },
 selectRating(e) { this.setData({ rating: Number(e.currentTarget.dataset.rating) }) },
 input(e) { this.setData({ comment: e.detail.value }) },
 async submit() {
  if (this.data.sending || this.data.done || !requireLogin()) return
  if (!this.data.id) { this.setData({ errorMessage: '缺少订单编号，请从已完成订单重新进入' }); return }
  this.setData({ sending: true, errorMessage: '' })
  try { await request({ url: `/api/orders/${this.data.id}/review`, method: 'POST', data: { rating: this.data.rating, comment: this.data.comment.trim() } }); this.setData({ done: true }) }
  catch (error) { this.setData({ errorMessage: error.message }) }
  finally { this.setData({ sending: false }) }
 },
 back() { wx.navigateBack() }
})
