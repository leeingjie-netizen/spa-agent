const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const root = path.resolve(__dirname, '../miniprogram')
function page(file, modules = {}, wxOverrides = {}) {
 let definition
 const storage = new Map()
 const wx = { getStorageSync: k => storage.get(k), setStorageSync: (k,v) => storage.set(k,v), removeStorageSync: k => storage.delete(k), showModal() {}, showToast() {}, redirectTo() {}, navigateTo() {}, ...wxOverrides }
 vm.runInNewContext(fs.readFileSync(path.join(root, file), 'utf8'), { Page: d => definition=d, require: n => modules[n] || (n.endsWith('config/env') ? { getRuntimeConfig: () => ({ environment: 'development' }) } : n.endsWith('utils/date') ? require(path.join(root,'utils/date')) : n.endsWith('utils/order') ? require(path.join(root,'utils/order')) : n.endsWith('utils/markdown') ? require(path.join(root,'utils/markdown')) : {}), wx, getApp: () => ({ globalData: { currentUser: null } }), getCurrentPages: () => [], setTimeout, clearTimeout, console }, { filename: file })
 const result = { ...definition, data: JSON.parse(JSON.stringify(definition.data)), setData(data) { Object.assign(this.data,data) } }
 return { instance: result, wx, storage }
}
const event = (key, value) => ({ currentTarget: { dataset: { [key]: value } } })
test('assistant Markdown preserves prices and newlines while refusing model HTML', () => {
 const {renderMarkdown}=require(path.join(root,'utils/markdown'))
 const nodes=renderMarkdown('**肩颈护理**\n- 价格 ¥99，30分钟\n\n<script>alert(1)</script>')
 assert.equal(nodes[0].children[0].name,'strong')
 assert.equal(nodes[0].children[0].children[0].text,'肩颈护理')
 assert.equal(nodes[1].children[0].text,'• 价格 ¥99，30分钟')
 assert.equal(nodes[3].children[0].text,'<script>alert(1)</script>')
 assert.equal(JSON.stringify(nodes).includes('"name":"script"'),false)
})
test('member benefits load only balances and coupons, never consumer financial ledgers', async () => {
 let ledgerCalls=0
 const {instance:p}=page('pages/benefits/index.js',{'../../utils/session':{requireLogin:()=>true},'../../api/loyalty':{summary:async()=>({walletBalance:'200.00',points:120,memberLevel:'STANDARD'}),coupons:async()=>({owned:[],templates:[]}),ledger:()=>{ledgerCalls++;throw new Error('must not be requested')}}})
 await p.load();assert.equal(ledgerCalls,0);assert.equal(p.data.summary.walletBalance,'200.00');assert.equal(p.data.errorMessage,'')
})
test('profile refuses an old accounts balance response after switching identity', async () => {
 let current={token:'account-a'};const reply=deferred()
 const {instance:p}=page('pages/profile/index.js',{'../../utils/session':{getSession:()=>current},'../../api/loyalty':{summary:()=>reply.promise,coupons:async()=>({owned:[]})}})
 const active=p.loadAssets();current={token:'account-b'};reply.resolve({walletBalance:'999.00',points:999});await active
 assert.equal(p.data.assetItems[0].value,'我的余额')
})
test('assistant rejects whitespace, prevents concurrent sends and renders its response', async () => {
 const result=deferred();let calls=0
 const {instance:p}=page('pages/ai-chat/index.js',{'../../utils/session':{requireLogin:()=>true},'../../utils/id':{createClientId:()=> 'unique'},'../../api/ai':{sendChatMessage:()=>{calls++;return result.promise}}})
 p.handleInput({detail:{value:'   '}});p.sendMessage();assert.equal(calls,0);assert.equal(p.data.hasInput,false)
 p.handleInput({detail:{value:'查询服务'}});p.sendMessage();p.sendMessage();assert.equal(calls,1);assert.equal(p.data.sending,true)
 result.resolve('**护理**\n¥99 · 30分钟');await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.data.sending,false);assert.equal(p.data.messages.at(-1).nodes[0].children[0].name,'strong');clearTimeout(p._scrollTimer)
})
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => {resolve=a;reject=b}); return { promise,resolve,reject } }
test('all scripts parse, JSON is valid, registered pages have complete files and bound handlers', () => {
 function walk(dir) { return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e => e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]) }
 for(const file of walk(root)) {
  if(file.endsWith('.js')) new vm.Script(fs.readFileSync(file,'utf8'),{filename:file})
  if(file.endsWith('.json')) JSON.parse(fs.readFileSync(file,'utf8'))
  assert(!/\?{4,}/.test(fs.readFileSync(file,'utf8')),`corrupt text: ${file}`)
 }
 const app=JSON.parse(fs.readFileSync(path.join(root,'app.json'),'utf8'))
 for(const name of app.pages) {
  for(const ext of ['js','json','wxss','wxml']) assert(fs.existsSync(path.join(root,`${name}.${ext}`)))
  const {instance}=page(`${name}.js`)
  const template=fs.readFileSync(path.join(root,`${name}.wxml`),'utf8')
  for(const [,handler] of template.matchAll(/(?:bind|catch)(?::)?[a-z]+="([a-zA-Z][a-zA-Z0-9_]*)"/g)) assert.equal(typeof instance[handler],'function',`${name}: missing ${handler}`)
 }
})
test('booking ignores stale slots after rapid date changes and stale errors do not erase latest loading', async () => {
 const first=deferred(), second=deferred();let calls=0
 const {instance:p}=page('pages/booking/index.js',{'../../api/staff':{getAvailableSlots:()=>++calls===1?first.promise:second.promise}})
 Object.assign(p.data,{selectedStaffId:'1',serviceId:'2',selectedDate:'2099-01-01'})
 const old=p.loadSlots();p.data.selectedDate='2099-01-02';const latest=p.loadSlots()
 second.resolve(['14:00:00']);await latest
 first.resolve(['09:00:00']);await old
 assert.deepEqual(Array.from(p.data.slots),['14:00']);assert.equal(p.data.slotsLoading,false)
 p.selectTime(event('time','09:00'));assert.equal(p.data.selectedTime,'')
})
test('booking retry reuses requestId, blocks double submit, and never stores a false success', async () => {
 const pending=deferred();const sent=[];let count=0
 const {instance:p,storage}=page('pages/booking/index.js',{
  '../../api/appointments':{createAppointment: body => {sent.push(body);return ++count===1?pending.promise:Promise.reject(new Error('offline'))}},
  '../../utils/session':{requireLogin:()=>true,getSession:()=>({user:{id:'account-a'}})},
  '../../utils/id':{createClientId:()=>`request-${count}`}
 })
 Object.assign(p.data,{serviceId:'2',selectedStaffId:'1',selectedDate:'2099-01-01',selectedTime:'14:00',service:{name:'care'},remark:''})
 const first=p.createBooking({name:'staff'});await p.createBooking({name:'staff'});assert.equal(sent.length,1)
 pending.reject(new Error('timeout'));await first;await p.createBooking({name:'staff'})
 assert.equal(sent[0].requestId,sent[1].requestId);assert.equal(storage.has('lastBookingResult'),false);assert.equal(p.data.submitting,false)
 assert.equal(sent[0].userId,undefined)
})
test('result page refuses stale or unconfirmed stored objects', () => {
 const {instance:p,storage}=page('pages/booking-result/index.js')
 storage.set('lastBookingResult',{appointment:{id:'old'}});p.onLoad();assert.equal(p.data.result,null)
 storage.set('lastBookingResult',{state:'success',appointment:{id:'new'}});p.onLoad();assert.equal(p.data.result.appointment.id,'new')
})
test('detail fetches server state by id instead of trusting cached list objects', async () => {
 let called
 const {instance:p,storage}=page('pages/order-detail/index.js',{'../../utils/session':{requireLogin:()=>true},'../../api/orders':{getOrder: async id => {called=id;return {id,appointmentId:'apt',status:'CANCELLED',serviceName:'fresh'}}}})
 storage.set('selectedOrder',{status:1,serviceName:'stale'});p.onLoad({id:'42'});await p.loadOrder()
 assert.equal(called,'42');assert.equal(p.data.order.serviceName,'fresh');assert.equal(p.data.order.status,'CANCELLED')
})
test('request sends token and expires session on 401 without treating it as success', async () => {
 let cleared=false,header,redirect=false
 const module={exports:{}}
 vm.runInNewContext(fs.readFileSync(path.join(root,'utils/request.js'),'utf8'),{module,require:n=>n.includes('env')?{getRuntimeConfig:()=>({baseUrl:'http://local'})}:{getSession:()=>({token:'secret'}),clearSession:()=>cleared=true},getCurrentPages:()=>[{route:'pages/orders/index'}],wx:{request: o=>{header=o.header;o.success({statusCode:401,data:{message:'expired'}})},navigateTo:()=>redirect=true}})
 await assert.rejects(module.exports.request({url:'/api/orders'}),/登录/)
 assert.equal(header.Authorization,'Bearer secret');assert(cleared);assert(redirect)
})

test('mock payment requires confirmation, prevents concurrent requests and uses authoritative refreshed state', async () => {
 const payment=deferred();let modal,paid=0,toasts=0
 const {instance:p}=page('pages/order-detail/index.js',{
  '../../utils/session':{requireLogin:()=>true},
  '../../utils/id':{createClientId:()=> 'stable-payment'},
  '../../api/orders':{payOrder:()=>{paid++;return payment.promise},getOrder:async()=>({id:'42',appointmentId:'a',status:'WAITING_SERVICE',paymentChannel:'MOCK',paymentStatus:'PAID'})}
 },{showModal:options=>modal=options,showToast:()=>toasts++})
 Object.assign(p.data,{id:'42',order:{canPay:true}})
 p.pay();assert.equal(paid,0)
 const active=modal.success({confirm:true});p.pay();assert.equal(paid,1)
 payment.resolve({});await active
 assert.equal(p.data.order.status,'WAITING_SERVICE');assert.equal(p.data.order.paymentLabel,'开发模拟 · 非真实收款');assert.equal(toasts,1)
 p.pay();assert.equal(paid,1)
})
test('review records success only after the backend confirms and blocks duplicate submissions', async () => {
 const response=deferred();let calls=0
 const {instance:p}=page('pages/review/index.js',{'../../utils/session':{requireLogin:()=>true},'../../utils/request':{request:()=>{calls++;return response.promise}}})
 p.onLoad({id:'42'});const active=p.submit();await p.submit();assert.equal(calls,1);assert.equal(p.data.done,false)
 response.reject(new Error('not eligible'));await active
 assert.equal(p.data.done,false);assert.equal(p.data.errorMessage,'not eligible');assert.equal(p.data.sending,false)
})

test('test wallet requires explicit confirmation and reuses its request id after an uncertain response', async () => {
 let modal,calls=[]
 const {instance:p}=page('pages/benefits/index.js',{
  '../../utils/session':{requireLogin:()=>true},'../../utils/id':{createClientId:()=> 'wallet-stable'},
  '../../api/loyalty':{topup:async body=>{calls.push(body);throw new Error('timeout')}}
 },{showModal:options=>modal=options})
 Object.assign(p.data,{summary:{mockWalletEnabled:true,walletBalance:0},amount:'50'})
 p.topup();assert.equal(calls.length,0);await modal.success({confirm:true})
 p.topup();await modal.success({confirm:true})
 assert.equal(calls.length,2);assert.equal(calls[0].requestId,calls[1].requestId)
 assert.equal(p.data.summary.walletBalance,0);assert.match(p.data.errorMessage,/timeout/)
 p.data.summary.mockWalletEnabled=false;p.topup();assert.equal(calls.length,2)
})
test('coupon claims are deduplicated and failures never add a fabricated coupon', async () => {
 const response=deferred();let calls=0
 const {instance:p}=page('pages/benefits/index.js',{'../../api/loyalty':{claim:()=>{calls++;return response.promise}}})
 const active=p.claim(event('id','coupon'));await p.claim(event('id','coupon'));assert.equal(calls,1)
 response.reject(new Error('sold out'));await active
 assert.equal(p.data.owned.length,0);assert.equal(p.data.errorMessage,'sold out');assert.equal(p.data.claiming,'')
})

test('AI service context never changes the exact confirmation message and failures remain retryable', async () => {
 const sent=[]
 const {instance:p}=page('pages/ai-chat/index.js',{
  '../../api/ai':{sendChatMessage:async body=>{sent.push(body);throw new Error('智能顾问尚未配置')}},
  '../../utils/id':{createClientId:()=> 'ai-test'}
 })
 Object.assign(p.data,{contextPrompt:'当前页面服务：护理',conversationId:'private-session',sending:true})
 await p.requestAnswer('确认预约')
 assert.equal(sent[0].message,'确认预约');assert.equal(p.data.sending,false)
 assert.equal(p.data.retryContent,'确认预约');assert.equal(p.data.messages[0].role,'error')
 await p.requestAnswer('有哪些技师？')
 assert.match(sent[1].message,/当前页面服务/);clearTimeout(p._scrollTimer)
})

test('network failures show readable recovery advice instead of raw platform error codes', async () => {
 const module={exports:{}}
 vm.runInNewContext(fs.readFileSync(path.join(root,'utils/request.js'),'utf8'),{module,
  require:n=>n.includes('env')?{getRuntimeConfig:()=>({baseUrl:'http://local'})}:{getSession:()=>null},
  wx:{request:o=>o.fail({errMsg:'request:fail net::ERR_CONNECTION_REFUSED'})}
 })
 await assert.rejects(module.exports.request({url:'/api/services'}),/暂时无法连接服务/)
})

test('tab selection changes only after navigation success and failure gives feedback', () => {
 let component,options,toasts=0
 vm.runInNewContext(fs.readFileSync(path.join(root,'custom-tab-bar/index.js'),'utf8'),{
  Component:d=>component=d,wx:{switchTab:o=>options=o,showToast:()=>toasts++}
 })
 const instance={data:{selected:0},setData:d=>Object.assign(instance.data,d)}
 component.methods.switchTab.call(instance,{currentTarget:{dataset:{path:'/pages/orders/index',index:2}}})
 assert.equal(instance.data.selected,0);options.fail();assert.equal(instance.data.selected,0);assert.equal(toasts,1)
 options.success();assert.equal(instance.data.selected,2)
})


test('BUG-003 home retains configured OSS image and shows image failure for retry',async()=>{
 const url='https://demo.oss-cn-hangzhou.aliyuncs.com/image.png?Expires=123&Signature=a%2Fb%3D';
 const {instance:p}=page('pages/home/index.js',{'../../utils/request':{request:async()=>[{id:'9',title:'真实后台配置',imageUrl:url}]}});
 await p.loadContent();assert.equal(p.data.activities[0].imageUrl,url);assert.equal(p.data.activities[0].imageFailed,false);
 p.handleActivityImageError(event('id','9'));assert.equal(p.data.activities[0].imageFailed,true);await p.loadContent();assert.equal(p.data.activities[0].imageFailed,false);
 const html=fs.readFileSync(path.join(root,'pages/home/index.wxml'),'utf8');assert.match(html,/src="{{item.imageUrl}}"/);assert.match(html,/binderror="handleActivityImageError"/);
});
test('BUG-007 booking excludes disabled staff and refuses a forged selection',async()=>{
 const {instance:p}=page('pages/booking/index.js',{'../../api/services':{getServiceDetail:async()=>({id:'1'})},'../../api/staff':{getStaffByService:async()=>[{id:'1',status:0},{id:'2',status:1}]}});
 p.data.selectedStaffId='1';p.data.selectedTime='10:00';p.data.slots=['10:00'];await p.loadBookingBase();assert.equal(p.data.staffList.length,1);assert.equal(p.data.staffList[0].id,'2');assert.equal(p.data.selectedStaffId,'');assert.equal(p.data.selectedTime,'');p.selectStaff(event('id','1'));assert.equal(p.data.selectedStaffId,'');
});
