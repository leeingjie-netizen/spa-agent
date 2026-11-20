const { request } = require('../utils/request')
module.exports = {
 summary: () => request({ url: '/api/loyalty/summary' }),
 coupons: () => request({ url: '/api/loyalty/coupons' }),
 claim: id => request({ url: `/api/loyalty/coupons/${id}/claim`, method: 'POST' }),
 topup: data => request({ url: '/api/loyalty/wallet/topup', method: 'POST', data }),
 applyBenefits: (id, data) => request({ url: `/api/orders/${id}/benefits`, method: 'POST', data })
}
