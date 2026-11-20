// Real local HTTP acceptance. Execute only after the fixture SQL review plan and database backup.
const fs = require('node:fs')
const path = require('node:path')
const { randomUUID } = require('node:crypto')
const assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..')
const base = process.env.SPA_ACCEPTANCE_URL || 'http://127.0.0.1:18080'
if (base !== 'http://127.0.0.1:18080') throw new Error('Only the verified local acceptance server on 127.0.0.1:18080 is allowed')
if (!fs.existsSync(path.join(root,'db/verification/http_acceptance_test.sql'))) throw new Error('Fixture SQL review plan must exist before execution')
const env = Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(l => /^[A-Z_]+=/.test(l)).map(l => { const at=l.indexOf('=');return [l.slice(0,at),l.slice(at+1).replace(/^"|"$/g,'')] }))
const tag = `acceptance_${randomUUID().replaceAll('-','').slice(0,16)}`
const report = { tag, base, startedAt: new Date().toISOString(), assertions: [], fixtures: {}, retained: true, notes: ['Only uniquely tagged fixture records are created. No automatic cleanup. Review db/verification/http_acceptance_test.sql and this manifest before cleanup.','No real AI, WeChat login or monetary payment is called.'] }
const output = path.join(root,'target',`http-${tag}.json`)
fs.mkdirSync(path.dirname(output),{recursive:true})
function save() { fs.writeFileSync(output,JSON.stringify(report,null,2)) }
function check(name, condition) { assert(condition,name);report.assertions.push({name,passed:true});save() }
async function raw(url, token, method='GET', data) {
 const response = await fetch(base+url,{ method, headers:{'content-type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(20000) })
 let payload;try { payload=await response.json() } catch { throw new Error(`${method} ${url}: invalid JSON (${response.status})`) }
 return { http:response.status, payload, good:response.ok&&payload.code===200 }
}
async function ok(url, token, method='GET', data) {const r=await raw(url,token,method,data);if(!r.good) throw new Error(`${method} ${url}: HTTP ${r.http}, code ${r.payload.code}: ${r.payload.message||r.payload.msg||'request rejected'}`);return r.payload.data}
async function denied(name,url,token,method='GET',data,status) {const r=await raw(url,token,method,data);check(name,!r.good&&(status===undefined||r.http===status));return r}
const time = value => String(value).slice(0,5)
async function main() {
 const adminPassword=process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD || env.SPA_AUTH_ADMIN_PASSWORD
 if (!env.SPA_AUTH_ADMIN_USERNAME || !adminPassword) throw new Error('Local admin username or SPA_ACCEPTANCE_ADMIN_PASSWORD is missing')
 const admin=await ok('/api/auth/admin-login',null,'POST',{username:env.SPA_AUTH_ADMIN_USERNAME,password:adminPassword});const A=admin.token
 check('administrator login returns ADMIN identity',admin.user.role==='ADMIN')
 await denied('unauthenticated private orders are rejected','/api/orders',null,'GET',undefined,401)
 const a=await ok('/api/auth/dev-login',null,'POST',{identity:`${tag}_a`}),b=await ok('/api/auth/dev-login',null,'POST',{identity:`${tag}_b`})
 const U=a.token,V=b.token;report.fixtures.users=[a.user.id,b.user.id]
 check('isolated development logins create distinct USER accounts',a.user.role==='USER'&&b.user.role==='USER'&&a.user.id!==b.user.id&&a.mode==='DEVELOPMENT_SIMULATION')
 await denied('USER cannot access administrator catalog','/api/admin/catalog/staff',U,'GET',undefined,403)
 const staff=await ok('/api/admin/catalog/staff',A,'POST',{name:tag,introduction:'Dedicated local acceptance fixture; not a real therapist',avatarUrl:'',status:1})
 const service=await ok('/api/admin/catalog/services',A,'POST',{name:tag,description:'Dedicated local acceptance fixture; not a real service',durationMinutes:45,price:120,coverUrl:'',status:1})
 report.fixtures.staffId=staff.id;report.fixtures.serviceId=service.id;save()
 const linked=await ok(`/api/admin/catalog/staff/${staff.id}/services`,A,'PUT',{serviceIds:[service.id]})
 check('fixture staff is linked to fixture service',linked.some(s=>s.id===service.id))
 const staffPassword=randomUUID()+'Aa1!'
 const employee=await ok('/api/admin/accounts/staff',A,'POST',{username:tag,password:staffPassword,staffId:staff.id});report.fixtures.staffUserId=employee.id
 const employeeLogin=await ok('/api/auth/staff-login',null,'POST',{username:tag,password:staffPassword});const S=employeeLogin.token
 check('staff login is bound to the dedicated staff ID',employeeLogin.user.role==='STAFF'&&employeeLogin.user.staffId===staff.id)
 // Select two wholly empty future dates; do not overwrite any existing global business-day records.
 const today = new Date(new Date().toLocaleString('en-US',{timeZone:'Asia/Shanghai'}));let date,closureDate
 for(let offset=150;offset<260;offset+=2){const d=new Date(today);d.setDate(d.getDate()+offset);const first=d.toISOString().slice(0,10);const calendar=await ok(`/api/admin/scheduling/calendar?from=${first}&days=2`,A)
  if(!calendar.schedules.length&&!calendar.businessDays.length&&!calendar.leaves.length&&!calendar.exceptions.length){date=first;d.setDate(d.getDate()+1);closureDate=d.toISOString().slice(0,10);break}}
 if(!date) throw new Error('No empty future test date was found; stopped without overwriting existing schedules')
 report.fixtures.dates=[date,closureDate];save()
 const weekday=new Date(date+'T12:00:00Z').getUTCDay()||7
 await ok(`/api/staff/${staff.id}/weekly-rules/${weekday}`,A,'PUT',{isWorking:1,startTime:'09:00:00',endTime:'18:00:00',breakStartTime:'12:00:00',breakEndTime:'13:00:00'})
 const generation=await ok('/api/admin/scheduling/generate',A,'POST',{staffId:staff.id,from:date,days:1})
 const repeat=await ok('/api/admin/scheduling/generate',A,'POST',{staffId:staff.id,from:date,days:1})
 check('real schedule generation creates exactly one fixture day',Number(generation.generated)===1)
 check('repeated generation preserves the existing record',Number(repeat.generated)===0)
 await ok(`/api/admin/scheduling/schedule/${staff.id}/${date}`,A,'PUT',{status:1,startTime:'09:00:00',endTime:'18:00:00',breakStartTime:'12:00:00',breakEndTime:'13:00:00',confirmManual:true,reason:tag})
 const calendar=await ok(`/api/admin/scheduling/calendar?from=${date}&days=1&staffId=${staff.id}`,A)
 check('calendar marks manual adjustment without duplicate schedule',calendar.schedules.length===1&&calendar.schedules[0].source==='MANUAL')
 const slots=async()=> (await ok(`/api/staff/${staff.id}/availableSlots?serviceId=${service.id}&date=${date}`)).map(time)
 check('45 minute slots crossing lunch are excluded',!(await slots()).includes('11:30')&&!(await slots()).includes('12:00'))
 const leave=await ok('/api/staff/leaves',S,'POST',{date,allDay:false,startTime:'09:00:00',endTime:'10:00:00',reason:tag});report.fixtures.leaveIds=[leave.id]
 check('pending leave does not block availability',(await slots()).includes('09:00'))
 await denied('staff cannot approve leave',`/api/admin/scheduling/leaves/${leave.id}/review`,S,'POST',{approved:true,reason:tag},403)
 const approved=await ok(`/api/admin/scheduling/leaves/${leave.id}/review`,A,'POST',{approved:true,reason:tag})
 check('approval records reviewer and timestamp',approved.status==='APPROVED'&&approved.reviewedBy&&approved.reviewedAt)
 check('approved partial leave blocks appointment starts',!(await slots()).includes('09:00'))
 const block=await ok('/api/admin/scheduling/exceptions',A,'POST',{staffId:staff.id,date,startTime:'14:00:00',endTime:'15:00:00',reason:tag});report.fixtures.exceptionId=block.id
 check('temporary exception is reflected by public availability',!(await slots()).includes('14:00'))
 const body={serviceId:service.id,staffId:staff.id,appointmentDate:date,startTime:'10:00:00',remark:tag,requestId:`${tag}_booking`}
 await denied('direct booking cannot bypass lunch','/api/appointments',U,'POST',{...body,startTime:'12:00:00',requestId:`tag_lunch_${tag}`})
 await denied('direct booking cannot bypass approved leave','/api/appointments',U,'POST',{...body,startTime:'09:00:00',requestId:`tag_leave_${tag}`})
 const appointment=await ok('/api/appointments',U,'POST',body);report.fixtures.appointmentIds=[appointment.id]
 const duplicate=await ok('/api/appointments',U,'POST',body)
 check('identical appointment request replays the same ID',appointment.id===duplicate.id)
 await denied('request ID cannot reveal another users appointment','/api/appointments',V,'POST',body)
 let orders=await ok('/api/orders',U);const order=orders.find(o=>o.appointmentId===appointment.id)
 check('appointment creates one actual WAITING_PAYMENT order',order&&order.status==='WAITING_PAYMENT'&&orders.filter(o=>o.appointmentId===appointment.id).length===1)
 report.fixtures.orderIds=[order.id];save()
 await denied('user cannot read another users order',`/api/orders/${order.id}`,V,'GET',undefined,403)
 await denied('user cannot pay another users order',`/api/orders/${order.id}/pay`,V,'POST',{channel:'MOCK',requestId:`${tag}_steal`},403)
 await denied('closure with existing appointment returns conflict','/api/admin/scheduling/business-days/'+date,A,'PUT',{closed:true,reason:tag},409)
 await ok('/api/admin/scheduling/business-days/'+date,A,'PUT',{closed:false,openTime:'09:00:00',closeTime:'18:00:00',reason:tag})
 await denied('narrow special opening cannot displace existing appointment','/api/admin/scheduling/business-days/'+date,A,'PUT',{closed:false,openTime:'11:00:00',closeTime:'18:00:00',reason:tag},409)
 const afterConflict=await ok(`/api/admin/scheduling/business-days?from=${date}&days=1`,A)
 check('failed business-day change rolls back to original fixture opening',afterConflict.length===1&&time(afterConflict[0].openTime)==='09:00')
 const conflictingLeave=await ok('/api/staff/leaves',S,'POST',{date,allDay:true,reason:tag});report.fixtures.leaveIds.push(conflictingLeave.id)
 await denied('leave approval reports existing booking conflict',`/api/admin/scheduling/leaves/${conflictingLeave.id}/review`,A,'POST',{approved:true,reason:tag},409)
 const pending=(await ok('/api/staff/leaves',S)).find(l=>l.id===conflictingLeave.id)
 check('failed approval leaves request PENDING',pending.status==='PENDING')
 await ok(`/api/staff/leaves/${conflictingLeave.id}/withdraw`,S,'POST')
 await ok(`/api/admin/scheduling/schedule/${staff.id}/${closureDate}`,A,'PUT',{status:1,startTime:'09:00:00',endTime:'18:00:00',confirmManual:true,reason:tag})
 const beforeClosure=await ok(`/api/staff/${staff.id}/availableSlots?serviceId=${service.id}&date=${closureDate}`)
 check('dedicated closure date has genuine availability before closure',beforeClosure.length>0)
 await ok('/api/admin/scheduling/business-days/'+closureDate,A,'PUT',{closed:true,reason:tag})
 const closedDay=await ok(`/api/staff/${staff.id}/availableSlots?serviceId=${service.id}&date=${closureDate}`)
 check('dedicated closed date returns no availability',closedDay.length===0)
 await denied('direct create cannot bypass closure','/api/appointments',U,'POST',{...body,appointmentDate:closureDate,startTime:'10:00:00',requestId:`${tag}_closed`})
 const payment={channel:'MOCK',requestId:`${tag}_payment`}
 const paid=await ok(`/api/orders/${order.id}/pay`,U,'POST',payment)
 check('mock payment has explicit MOCK channel and PAID state',paid.status==='WAITING_SERVICE'&&paid.paymentChannel==='MOCK'&&paid.paymentStatus==='PAID')
 const paidAgain=await ok(`/api/orders/${order.id}/pay`,U,'POST',payment)
 check('replayed payment is idempotent',paidAgain.id===order.id&&paidAgain.status==='WAITING_SERVICE')
 await ok(`/api/admin/orders/${order.id}/transition`,A,'POST',{status:'IN_SERVICE'})
 const completed=await ok(`/api/admin/orders/${order.id}/transition`,A,'POST',{status:'COMPLETED'})
 check('administrator drives actual order lifecycle to COMPLETED',completed.status==='COMPLETED')
 await ok(`/api/orders/${order.id}/review`,U,'POST',{rating:5,comment:tag})
 const account=await ok('/api/loyalty/summary',U)
 check('completed fixture service awards real ledger points',Number(account.points)===120)
 const points=await ok('/api/operations/points',U)
 check('points endpoint contains the completed order',points.some(p=>p.orderId===order.id&&Number(p.points)===120))
 // Concurrency: distinct users race for one fixture staff/time; exactly one must succeed.
 const contenders=await Promise.all([raw('/api/appointments',U,'POST',{...body,startTime:'16:00:00',requestId:`${tag}_race_a`}),raw('/api/appointments',V,'POST',{...body,startTime:'16:00:00',requestId:`${tag}_race_b`})])
 check('real Redisson+MySQL concurrent same-slot booking yields exactly one winner',contenders.filter(r=>r.good).length===1)
 const winner=contenders.findIndex(r=>r.good),winnerToken=winner===0?U:V,winnerApt=contenders[winner].payload.data;report.fixtures.appointmentIds.push(winnerApt.id)
 const winnerOrder=(await ok('/api/orders',winnerToken)).find(o=>o.appointmentId===winnerApt.id);report.fixtures.orderIds.push(winnerOrder.id)
 await ok(`/api/orders/${winnerOrder.id}/cancel`,winnerToken,'POST')
 check('cancelled fixture appointment releases its occupied slots',(await slots()).includes('16:00'))
 // Real simulated wallet ledger, exact request replay and refund verification.
 const topup={amount:200,requestId:`${tag}_topup`}
 await ok('/api/loyalty/wallet/topup',U,'POST',topup);const balance=await ok('/api/loyalty/wallet/topup',U,'POST',topup)
 check('test wallet topup request is idempotent and explicitly simulated',Number(balance.walletBalance)===200&&balance.walletMode==='DEVELOPMENT_SIMULATION')
 const later=await ok('/api/appointments',U,'POST',{...body,startTime:'15:00:00',requestId:`${tag}_wallet_booking`});report.fixtures.appointmentIds.push(later.id)
 const walletOrder=(await ok('/api/orders',U)).find(o=>o.appointmentId===later.id);report.fixtures.orderIds.push(walletOrder.id)
 await ok('/api/admin/coupons',A,'POST',{title:tag,discountAmount:10,minAmount:100,expiresAt:closureDate+'T23:59:00',remaining:2})
 const template=(await ok('/api/admin/coupons',A)).find(t=>t.title===tag);report.fixtures.couponTemplateId=template.id
 const claimed=await ok(`/api/loyalty/coupons/${template.id}/claim`,U,'POST');const coupon=claimed.owned.find(c=>c.title===tag);report.fixtures.userCouponId=coupon.id
 check('coupon claim produces a real available user coupon',coupon.status==='AVAILABLE')
 await denied('duplicate coupon claim cannot mint another coupon',`/api/loyalty/coupons/${template.id}/claim`,U,'POST')
 const discounted=await ok(`/api/orders/${walletOrder.id}/benefits`,U,'POST',{couponId:coupon.id,points:100})
 check('coupon plus points redemption computes server-side amount',Number(discounted.amount)===109)
 check('reserved points are deducted from real available ledger',Number((await ok('/api/loyalty/summary',U)).points)===20)
 await ok(`/api/orders/${walletOrder.id}/pay`,U,'POST',{channel:'MOCK_WALLET',requestId:`${tag}_wallet_pay`})
 const spent=await ok('/api/loyalty/summary',U)
 check('wallet payment changes actual test ledger balance',Number(spent.walletBalance)===91)
 const refunded=await ok(`/api/orders/${walletOrder.id}/cancel`,U,'POST')
 const restored=await ok('/api/loyalty/summary',U)
 check('mock wallet cancellation records refund and restores test balance',refunded.status==='REFUNDED'&&Number(restored.walletBalance)===200)
 check('cancel restores reserved loyalty points',Number(restored.points)===120)
 check('cancel releases coupon back to availability',(await ok('/api/loyalty/coupons',U)).owned.find(c=>c.id===coupon.id).status==='AVAILABLE')
 await denied('consumer cannot read wallet financial ledger','/api/loyalty/wallet/ledger',U,'GET',undefined,403)
 await denied('staff cannot read administrator financial ledger','/api/admin/finance/ledger',S,'GET',undefined,403)
 const ledger=await ok('/api/admin/finance/ledger?mode=SIMULATED&source=WALLET&size=100',A)
 check('administrator sees topup payment and refund audit entries for fixture user',ledger.items.filter(e=>String(e.userId)===String(a.user.id)).length===3)
 await ok('/api/auth/logout',V,'POST')
 await denied('logout actually revokes bearer token','/api/orders',V,'GET',undefined,401)
 await ok('/api/auth/logout',U,'POST');await ok('/api/auth/logout',S,'POST');await ok('/api/auth/logout',A,'POST')
 report.completedAt=new Date().toISOString();report.passed=true;save()
 console.log(`PASS: ${report.assertions.length} real HTTP assertions. Manifest: ${output}`)
}
main().catch(error=>{report.failedAt=new Date().toISOString();report.passed=false;report.error=error.message;save();console.error(`FAIL: ${error.message}. Fixture manifest: ${output}`);process.exitCode=1})
