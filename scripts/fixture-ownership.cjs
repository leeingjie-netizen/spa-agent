const assert=require('node:assert/strict')
function validateFixtureOrder(manifest,order){
 const f=manifest.fixtures
 assert((f.orderIds||[]).map(String).includes(String(order.id)),'Order is outside the fixture manifest')
 assert((f.users||[]).map(String).includes(String(order.userId)),'Manifest order owner mismatch')
 assert((f.appointmentIds||[]).map(String).includes(String(order.appointmentId)),'Manifest appointment mismatch')
 assert.equal(String(order.staffId),String(f.staffId),'Manifest staff mismatch')
 assert.equal(String(order.serviceId),String(f.serviceId),'Manifest service mismatch')
}
module.exports={validateFixtureOrder}
