const { request } = require('../../utils/request')
const { summary } = require('../../api/loyalty')
const { requireLogin } = require('../../utils/session')
Page({
 data: { loading: true, errorMessage: '', entries: [], total: 0 },
 onShow() { this.load() },
 async load() {
  if (!requireLogin()) { this.setData({ loading: false }); return }
  this.setData({ loading: true, errorMessage: '' })
  try { const [entries, account] = await Promise.all([request({ url: '/api/operations/points' }), summary()]); this.setData({ entries: entries || [], total: account.points }) }
  catch (error) { this.setData({ errorMessage: error.message }) }
  finally { this.setData({ loading: false }) }
 }
})
