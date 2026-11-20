const labels = {
 WAITING_PAYMENT: ['待支付','pending','unpaid'], WAITING_SERVICE: ['待服务','booked','upcoming'],
 IN_SERVICE: ['服务中','booked','inService'], COMPLETED: ['已完成','completed','completed'],
 CANCELLED: ['已取消','cancelled','cancelled'], CLOSED: ['已关闭','cancelled','cancelled'],
 REFUNDING: ['退款中','pending','refund'], REFUNDED: ['已退款','cancelled','refund']
}
const descriptions = {
 WAITING_PAYMENT: '请在订单有效期内完成支付，超时后预约时段将释放。',
 WAITING_SERVICE: '请按预约时间到店，需要调整可提前联系门店。',
 IN_SERVICE: '护理服务进行中，如需帮助请联系门店工作人员。',
 COMPLETED: '本次护理已完成，欢迎分享你的真实体验。',
 CANCELLED: '预约已取消，原时段已释放，可重新选择时间。',
 CLOSED: '未在有效期内支付，订单已关闭，原时段已释放。',
 REFUNDING: '退款正在处理，请以最新支付记录为准。',
 REFUNDED: '订单已退款，模拟支付的退款仅影响测试账本。'
}
function decorateOrder(item) {
 const meta = labels[item.status] || ['状态待确认','unknown','unknown']
 return { ...item, id: String(item.id), appointmentId: String(item.appointmentId),
  statusLabel: meta[0], statusTone: meta[1], statusGroup: meta[2],
  statusDescription: descriptions[item.status] || '请刷新查看最新订单状态。',
  paymentStatusLabel: ({UNPAID:'未支付',PAID:'已支付',REFUNDING:'退款中',REFUNDED:'已退款'})[item.paymentStatus] || '暂无支付记录',
  startTimeDisplay: String(item.startTime || '').slice(0,5), endTimeDisplay: String(item.endTime || '').slice(0,5),
  canPay: item.status === 'WAITING_PAYMENT', cancellable: ['WAITING_PAYMENT','WAITING_SERVICE'].includes(item.status),
  paymentLabel: ['MOCK', 'MOCK_WALLET'].includes(item.paymentChannel) ? '开发模拟 · 非真实收款' : (item.paymentStatus === 'UNPAID' ? '未支付' : (item.paymentStatus || '尚无支付记录'))
 }
}
module.exports = { decorateOrder }
