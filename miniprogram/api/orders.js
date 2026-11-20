const { request } = require('../utils/request')
module.exports = {
 listOrders: () => request({ url: '/api/orders' }),
 getOrder: id => request({ url: `/api/orders/${id}` }),
 cancelOrder: id => request({ url: `/api/orders/${id}/cancel`, method: 'POST' }),
 payOrder: (id, requestId, channel = 'MOCK') => request({ url: `/api/orders/${id}/pay`, method: 'POST', data: { channel, requestId } })
}
