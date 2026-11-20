const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/admin.js','utf8');
function harness(initialHash='',options={}){
 const elements=new Map(),fields=new Map(),calls=[],storage=options.storage||new Map();if(options.token)storage.set('qixin-admin-token',options.token);
 function element(key){if(elements.has(key))return elements.get(key);let html='';const item={hidden:false,disabled:false,style:{},value:'',textContent:'',checked:false,classList:{toggle(){}},handlers:{},addEventListener(type,handler){this.handlers[type]=handler},showModal(){this.open=true},close(){this.open=false},querySelector(selector){const match=selector.match(/name="([^" ]+)"/);return match?fields.get(match[1]):element(selector)}};
 Object.defineProperty(item,'innerHTML',{get(){return html},set(value){html=value;if(key!=='#dialog-content')return;fields.clear();for(const match of value.matchAll(/<(input|textarea|select)\b([^>]*)(?:>([\s\S]*?)<\/\1>)?/g)){const name=match[2].match(/name="([^"]+)"/);if(!name)continue;const type=match[1];let val=match[2].match(/value="([^"]*)"/)?.[1]||'';if(type==='textarea')val=match[3]||'';if(type==='select'){const options=[...(match[3]||'').matchAll(/<option\s+value="([^"]*)"([^>]*)>/g)];val=(options.find(o=>o[2].includes('selected'))||options[0])?.[1]||'';}fields.set(name[1],{value:val,disabled:false,required:match[2].includes('required'),checked:match[2].includes('checked')});}}});elements.set(key,item);return item;}
 const listeners={};
 const context=vm.createContext({location:{hash:initialHash},addEventListener:(type,handler)=>listeners[type]=handler,document:{querySelector:element,querySelectorAll:()=>[]},sessionStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},Date,Intl,console,URL,URLSearchParams,AbortController,setTimeout,clearTimeout,FormData:class{constructor(form){this.data=form.data||{}}get(key){return this.data[key]}[Symbol.iterator](){return Object.entries(this.data)[Symbol.iterator]()}},fetch:async(path,options)=>{calls.push({path,options});const data=await h.respond(path,options);return {ok:(h.httpStatus||200)<400,status:h.httpStatus||200,json:async()=>({code:h.httpStatus||200,message:h.message||'',data})};}});
 const h={context,calls,fields,element,listeners,storage,respond:path=>path==='/api/auth/me'?(options.user||{role:'ADMIN',nickname:'管理员'}):[],run:code=>vm.runInContext(code,context),click:dataset=>element('#content').onclick({target:{closest:()=>({dataset})}})};
 context.window=context;vm.runInContext(fs.readFileSync(__dirname+'/order-records.js','utf8'),context);vm.runInContext(source,context);h.run("state.staff=[{id:'1',name:'甲'},{id:'2',name:'乙'},{id:'3',name:'丙'}];state.staffId='1';state.from='2030-01-01';state.role='ADMIN';state.user={role:'ADMIN'}");return h;
}
test('calendar distinguishes closure, pending leave and exceptions without inferring missing shifts',async()=>{
 const h=harness();h.respond=()=>({schedules:[],leaves:[{staffId:'1',workDate:'2030-01-01',status:'PENDING'}],businessDays:[{workDate:'2030-01-01',closed:true}],exceptions:[{staffId:'1',workDate:'2030-01-01',startTime:'12:00',endTime:'13:00'}]});const html=await h.run('calendar()');assert.match(html,/门店停业/);assert.match(html,/待审批/);assert.match(html,/class="shift pending-leave"/);assert.match(html,/class="shift exception"/);assert.match(html,/12:00—13:00/);assert.equal((html.match(/data-action="day"/g)||[]).length,30);assert.match(html,/2030-01-01/);assert.match(html,/尚未配置排班/);
});
test('weekly rule dialog prefills existing break times and resting status',async()=>{
 const h=harness();h.run("state.weeklyStaffId='1';state.weeklyRules=[{dayOfWeek:2,isWorking:0,startTime:'10:00:00',endTime:'17:00:00',breakStartTime:'12:00:00',breakEndTime:'13:00:00'}]");await h.click({action:'rule',id:'1',day:'2'});assert.equal(h.fields.get('isWorking').value,'0');assert.equal(h.fields.get('startTime').value,'10:00');assert.equal(h.fields.get('breakStartTime').value,'12:00');assert.equal(h.fields.get('startTime').disabled,true);assert.equal(h.fields.get('startTime').step,'1800');
});
test('day editor prefills actual schedule and reloads after technician changes',async()=>{
 const h=harness();h.run("state.calendar={schedules:[{staffId:'1',workDate:'2030-01-01',status:1,startTime:'11:00',endTime:'19:00'}]}");await h.click({action:'day',date:'2030-01-01'});assert.equal(h.fields.get('startTime').value,'11:00');h.respond=()=>({schedules:[{staffId:'2',workDate:'2030-01-01',status:0}]});h.fields.get('staffId').value='2';await h.fields.get('staffId').onchange();assert.equal(h.fields.get('status').value,'0');assert.equal(h.fields.get('startTime').disabled,true);assert.equal(h.fields.get('confirmManual').checked,false);assert.match(h.calls.at(-1).path,/staffId=2/);
});
test('late technician response cannot overwrite the current selection',async()=>{
 const h=harness();h.run('state.calendar={schedules:[]}');await h.click({action:'day',date:'2030-01-01'});let release;h.respond=path=>path.endsWith('staffId=2')?new Promise(resolve=>release=resolve):{schedules:[{staffId:'3',workDate:'2030-01-01',status:1,startTime:'15:00',endTime:'19:00'}]};h.fields.get('staffId').value='2';const older=h.fields.get('staffId').onchange();h.fields.get('staffId').value='3';await h.fields.get('staffId').onchange();release({schedules:[{staffId:'2',workDate:'2030-01-01',status:1,startTime:'08:00'}]});await older;assert.equal(h.fields.get('startTime').value,'15:00');assert.equal(h.element('#save-dialog').disabled,false);
});
test('content editing preserves current description, active state, image and sorting',async()=>{
 const h=harness();h.run("state.contents=[{id:'9',title:'舒缓时刻',description:'真实活动说明',active:false,imageUrl:'https://example.com/a.jpg',sortOrder:5}]");await h.click({action:'content',id:'9'});assert.equal(h.fields.get('description').value,'真实活动说明');assert.equal(h.fields.get('active').value,'false');assert.equal(h.fields.get('sortOrder').value,'5');h.respond=()=>({});await h.run("saveAction({title:'舒缓时刻',description:'真实活动说明',active:'false',sortOrder:'5',imageUrl:''})");const call=h.calls.at(-1);assert.equal(call.path,'/api/admin/content/9');assert.equal(call.options.method,'PUT');assert.deepEqual(JSON.parse(call.options.body),{title:'舒缓时刻',description:'真实活动说明',active:false,sortOrder:5,imageUrl:null});
});
test('staff account UI binds technician and sends matching account contract',async()=>{
 const h=harness();await h.click({action:'staff-account',id:'2'});assert.equal(h.fields.get('staffId').value,'2');h.respond=()=>({});await h.run("saveAction({staffId:'2',username:'therapist',password:'test-only-long-password'})");assert.equal(h.calls.at(-1).path,'/api/admin/accounts/staff');assert.equal(JSON.parse(h.calls.at(-1).options.body).staffId,'2');
});
test('order states are Chinese and cancellation is confirmed before transition',async()=>{
 const h=harness();h.respond=()=>[{id:'5',serviceName:'肩颈护理',status:'WAITING_SERVICE',paymentStatus:'PAID',paymentChannel:'MOCK'}];const html=await h.run('orders()');assert.match(html,/待服务/);assert.match(html,/已支付/);assert.match(html,/取消并退款/);const prior=h.calls.length;await h.click({action:'order-cancel',id:'5'});assert.equal(h.calls.length,prior);assert.match(h.element('#dialog-content').innerHTML,/不代表真实资金退款/);h.respond=()=>({});await h.run('saveAction({})');assert.equal(h.calls.at(-1).path,'/api/admin/orders/5/transition');assert.deepEqual(JSON.parse(h.calls.at(-1).options.body),{status:'CANCELLED'});
});
test('business editor preserves existing closure and explanation',async()=>{
 const h=harness();h.run("state.businessDays=[{workDate:'2030-01-01',closed:true,reason:'节假日休息'}]");await h.click({action:'business-day',date:'2030-01-01'});assert.equal(h.fields.get('closed').value,'true');assert.equal(h.fields.get('openTime').disabled,true);assert.equal(h.fields.get('reason').value,'节假日休息');
});
test('leave application defaults to all day with partial time fields disabled',async()=>{
 const h=harness();h.run("state.role='STAFF'");await h.click({action:'leave-submit'});assert.equal(h.fields.get('startTime').disabled,true);h.fields.get('allDay').value='false';h.fields.get('allDay').onchange();assert.equal(h.fields.get('startTime').disabled,false);assert.equal(h.fields.get('startTime').required,true);
});
test('duplicate click listeners and duplicate session restoration are removed',()=>{
 assert.equal((source.match(/if\(state.token\)api/g)||[]).length,1);assert.equal((source.match(/#content'\)\.addEventListener/g)||[]).length,0);const h=harness();assert.equal(typeof h.element('#content').onclick,'function');
});

test('hash deep links survive initialization and navigation updates the URL',async()=>{
 const h=harness('#calendar');assert.equal(h.run('state.view'),'calendar');h.respond=()=>[];await h.run("navigate('catalog')");assert.equal(h.context.location.hash,'catalog');assert.equal(h.run('state.view'),'catalog');
 h.run("state.token='test-token'");h.context.location.hash='#leaves';await h.listeners.hashchange();assert.equal(h.run('state.view'),'leaves');
});
test('staff routing does not expose administrator views',async()=>{
 const h=harness();h.run("state.role='STAFF';state.view='leaves';state.token='test-token'");h.context.location.hash='#catalog';await h.listeners.hashchange();assert.equal(h.run('state.view'),'leaves');
});
test('coupon configuration uses real amount and activity contracts',async()=>{
 const h=harness();h.respond=path=>path==='/api/admin/coupons'?[{id:'21',title:'舒缓礼遇',discountAmount:'20.00',minAmount:'100.00',expiresAt:'2030-02-01T23:59:00',remaining:10,active:true}]:[];const html=await h.run('content()');assert.match(html,/舒缓礼遇/);assert.match(html,/停止发放/);const previous=h.calls.length;await h.click({action:'coupon-active',id:'21'});assert.equal(h.calls.length,previous);h.respond=()=>({});await h.run('saveAction({})');assert.equal(h.calls.at(-1).path,'/api/admin/coupons/21/active');assert.deepEqual(JSON.parse(h.calls.at(-1).options.body),{active:false});
 await h.click({action:'coupon-create'});await h.run("saveAction({title:'新客礼遇',remaining:'20',discountAmount:'10.00',minAmount:'100.00',expiresAt:'2030-02-01T23:59'})");const created=JSON.parse(h.calls.at(-1).options.body);assert.equal(h.calls.at(-1).path,'/api/admin/coupons');assert.equal(created.remaining,20);assert.equal(created.expiresAt,'2030-02-01T23:59:00');assert.equal(created.discountAmount,'10.00');
});


test('BUG-002 administrators approve only and cannot open employee application',async()=>{
 const h=harness();h.respond=()=>[];assert.doesNotMatch(await h.run('leaves()'),/data-action="leave-submit"/);
 await h.click({action:'leave-submit'});assert.equal(h.element('#editor').open,undefined);assert.match(h.element('#notice').textContent,/仅员工/);
 h.run("state.role='STAFF'");assert.match(await h.run('leaves()'),/data-action="leave-submit"/);
});
test('BUG-004 login rejection differs from expired session, forbidden and network errors',async()=>{
 const h=harness();h.run("state.token='existing'");h.httpStatus=401;
 await assert.rejects(h.run("api('/api/auth/admin-login')"),/账号或密码错误/);assert.equal(h.run('state.token'),'existing');
 await assert.rejects(h.run("api('/api/admin/catalog/staff')"),/登录已失效/);assert.equal(h.run('state.token'),'');
 h.httpStatus=403;await assert.rejects(h.run("api('/api/admin/catalog/staff')"),/权限不足/);
 h.respond=()=>{throw new Error('failed to fetch')};await assert.rejects(h.run("api('/api/auth/staff-login')"),/网络异常/);
});
test('BUG-005 success toast lasts 1800ms and replaces the previous timer',()=>{
 const h=harness(),timers=[];h.context.setTimeout=(fn,ms)=>{timers.push({fn,ms});return timers.length};let cleared=[];h.context.clearTimeout=id=>cleared.push(id);
 h.run("notice('已保存')");h.run("notice('已停用')");assert.equal(timers.at(-1).ms,1800);assert(cleared.includes(1));assert.equal(h.element('#notice').textContent,'已停用');assert.equal(h.element('#notice').hidden,false);
 timers.at(-1).fn();assert.equal(h.element('#notice').hidden,true);
});
test('BUG-006 whitespace and illegal price never send and failed save restores button',async()=>{
 const h=harness();await h.click({action:'service'});let prior=h.calls.length;
 const form={data:{name:'   ',price:'1',durationMinutes:'30',status:'1'}};
 await h.element('#editor-form').onsubmit({preventDefault(){},target:form});assert.equal(h.calls.length,prior);assert.match(h.element('#dialog-error').textContent,/空白/);assert.equal(h.element('#save-dialog').disabled,false);
 form.data.name='valid';form.data.price='-1';await h.element('#editor-form').onsubmit({preventDefault(){},target:form});assert.equal(h.calls.length,prior);assert.match(h.element('#dialog-error').textContent,/价格/);
 h.run("saveAction=async()=>{throw new Error('服务拒绝保存')}");form.data.price='1.00';await h.element('#editor-form').onsubmit({preventDefault(){},target:form});assert.match(h.element('#dialog-error').textContent,/服务拒绝/);assert.equal(h.element('#save-dialog').disabled,false);assert.equal(h.element('#editor').open,true);
});
test('BUG-008 independent filters reset page and preserve appointment or order semantics',async()=>{
 const h=harness();h.run("state.view='orders';window.orderRecordState.page=3");h.respond=()=>({items:[],total:0});
 await h.run('orders()');h.run('window.bindOrderRecords()');h.element('#record-status').value='COMPLETED';h.element('#record-payment').value='PAID';
 await h.element('#record-status').onchange();assert.equal(h.run('window.orderRecordState.page'),1);assert.equal(h.run('window.orderRecordState.status'),'COMPLETED');assert.match(h.calls.at(-1).path,/status=COMPLETED&paymentStatus=PAID/);
 h.run("window.orderRecordState.tab='appointments';window.orderRecordState.status='ALL';window.orderRecordState.paymentStatus='NO_ORDER'");const html=await h.run('orders()');assert.match(h.calls.at(-1).path,/api\/admin\/appointments/);assert.match(html,/无关联订单/);assert.match(html,/预约记录/);
});
test('BUG-009 to BUG-012 drawer shows recorded amounts, marks absent refunds and preserves list',async()=>{
 const h=harness();h.run("window.orderRecordState.page=3;window.orderRecordState.status='COMPLETED'");
 h.respond=()=>({id:'9',appointmentId:'1',appointmentDate:'2026-11-17',appointmentStatus:2,amount:'109.00',originalAmount:'120.00',pointsUsed:100,pointsDiscountAmount:'1.00',couponDiscountAmount:'10.00',actualPaidAmount:'109.00',actualRefundAmount:'0.00',payments:[],pointsAdjustments:[]});
 await h.run("window.openOrderRecord('orders','9')");const html=h.element('#order-drawer-body').innerHTML;assert.match(html,/100 分 \/ ¥1.00/);assert.match(html,/120.00/);assert.match(html,/109.00/);assert.match(html,/退款原因：不适用/);assert.doesNotMatch(html,/第三方退款成功/);
 h.element('#close-order-drawer').onclick();assert.equal(h.run('window.orderRecordState.page'),3);assert.equal(h.run('window.orderRecordState.status'),'COMPLETED');assert.equal(h.calls.length,1);
});
test('BUG-010 simulated refund uses real transaction data and never invents reason',()=>{
 const h=harness();h.context.refund={id:'8',appointmentStatus:3,appointmentDate:'2026-11-17',paymentChannel:'MOCK_WALLET',payments:[{id:'99',kind:'REFUND',amount:'109.00',channel:'MOCK_WALLET',status:'SIMULATED_SUCCESS',createdAt:'2026-11-17T10:00:00',requestId:'refund-8'}]};
 const html=h.run('window.renderOrderDetail(refund)');assert.match(html,/退款原因：未记录/);assert.match(html,/refund-8/);assert.match(html,/2026-11-17T10:00:00/);assert.match(html,/不代表真实资金交易/);
});


test('BUG-006 empty submit bypasses native popup, never sends or disables, and accepts later valid data',async()=>{
 const h=harness();await h.click({action:'staff'});let requests=h.calls.length;
 const blank={name:'name',type:'text',required:true,value:'',disabled:false};
 const form={data:{name:'',status:'1'},elements:[blank],checkValidity:()=>false,reportValidity:()=>{throw new Error('Native validation UI must never open')}};
 const submit=()=>h.element('#editor-form').onsubmit({preventDefault(){},target:form});
 for(let i=0;i<5;i++){await submit();assert.equal(h.element('#save-dialog').disabled,false);assert.equal(h.calls.length,requests)}
 assert(h.element('#dialog-error').textContent);assert.equal(h.element('#editor').open,true);
 form.data.name=blank.value='新技师';form.checkValidity=()=>true;
 h.run("render=async()=>{};saveAction=async data=>{globalThis.savedName=data.name}");await submit();assert.equal(h.context.savedName,'新技师');assert.equal(h.element('#editor').open,false);assert.equal(h.element('#notice').textContent,'新增成功');
 const html=fs.readFileSync(__dirname+'/index.html','utf8');assert.match(html,/<form id="editor-form" novalidate>/);
});
test('BUG-005 error never becomes success and shared toast takes no page space',()=>{
 const h=harness(),delays=[];h.context.setTimeout=(_,ms)=>{delays.push(ms);return 1};h.context.clearTimeout=()=>{};
 h.run("notice('保存失败',true)");assert.equal(h.element('#notice').textContent,'保存失败');assert.equal(delays.at(-1),5000);
 assert.equal(h.run("operationSuccessMessage('编辑技师',{status:'0'})"),'停用成功');assert.equal(h.run("operationSuccessMessage('编辑服务',{status:'1'})"),'修改成功');
 const html=fs.readFileSync(__dirname+'/index.html','utf8'),css=fs.readFileSync(__dirname+'/admin.css','utf8');assert.equal((html.match(/id="notice"/g)||[]).length,1);assert(html.indexOf('id="notice"')>html.indexOf('</main>'));
 assert.match(css,/\.operation-toast\{position:fixed;/);assert.match(css,/left:50%;transform:translateX\(-50%\)/);
});
test('BUG-014 new login resets overview but restoring an authenticated refresh preserves route',async()=>{
 const h=harness('#catalog');h.run("render=async()=>{};state.view='catalog';state.token='old';window.orderRecordState.page=4");
 h.respond=()=>({token:'new-token',user:{role:'ADMIN',nickname:'管理员'}});
 await h.element('#login-form').handlers.submit({preventDefault(){},submitter:{disabled:false},target:{data:{loginRole:'ADMIN',username:'admin',password:'test-password'},reset(){}}});
 assert.equal(h.run('state.view'),'overview');assert.equal(h.context.location.hash,'overview');assert.equal(h.run('window.orderRecordState.page'),1);
 const restored=harness('#catalog',{token:'valid-token'});await new Promise(resolve=>setImmediate(resolve));assert.equal(restored.run('state.view'),'catalog');assert.equal(restored.context.location.hash,'#catalog');assert(restored.calls.some(c=>c.path==='/api/auth/me'));
});
test('BUG-014 logout or expired login and identity switching cannot restore previous identity page',async()=>{
 const h=harness('#orders');h.run("render=async()=>{};state.view='orders';state.token='old'");h.run('logout()');
 h.run("showWorkspace({role:'ADMIN'},{freshLogin:true})");assert.equal(h.run('state.view'),'overview');
 h.run("state.view='catalog';state.token='expired'");h.httpStatus=401;await assert.rejects(h.run("api('/api/admin/catalog/services')"),/登录已失效/);
 h.run("showWorkspace({role:'ADMIN'},{freshLogin:true})");assert.equal(h.run('state.view'),'overview');
 h.run("showWorkspace({role:'STAFF'},{freshLogin:true})");assert.equal(h.run('state.view'),'leaves');
 h.run("showWorkspace({role:'ADMIN'},{freshLogin:true})");assert.equal(h.run('state.view'),'overview');
 await h.run("navigate('catalog')");assert.equal(h.run('state.view'),'catalog');
});


test('BUG-015 states have fixed semantic names and separate restrained badge classes',()=>{
 const h=harness();h.context.sample={id:'9',appointmentStatus:3,status:'CANCELLED',paymentStatus:'UNPAID'};
 const html=h.run('relatedStates(sample,true)');assert.match(html,/订单：/);assert.match(html,/支付：/);assert.match(html,/预约：/);assert.equal((html.match(/record-status-line/g)||[]).length,3);assert.match(html,/record-neutral/);assert.match(html,/record-pending/);
 for(const [domain,code,label,color] of [['order','WAITING_PAYMENT','待支付','pending'],['payment','PAID','已支付','success'],['order','COMPLETED','已完成','success'],['order','REFUNDING','退款中','refunding'],['payment','REFUNDED','已退款','refunded'],['transaction','FAILED','失败','error']]){
  h.context.domain=domain;h.context.code=code;const badge=h.run('recordBadge(domain,code)');assert(badge.includes(label));assert(badge.includes('record-'+color));
 }
 h.context.sample.status='<script>bad</script>';const safe=h.run('relatedStates(sample)');assert(!safe.includes('<script>'));assert(safe.includes('&lt;script&gt;'));
});
test('BUG-015 no order is distinct from unpaid and missing status never fabricates payment',()=>{
 const h=harness();h.context.legacy={appointmentId:'7',appointmentStatus:3};const absent=h.run('relatedStates(legacy)');assert.match(absent,/无关联订单/);assert.doesNotMatch(absent,/未支付/);assert.match(absent,/支付：/);
 h.context.unpaid={id:'8',status:'CANCELLED',paymentStatus:'UNPAID'};assert.match(h.run('relatedStates(unpaid)'),/未支付/);
 h.context.missing={id:'8',status:null,paymentStatus:null};const missing=h.run('relatedStates(missing)');assert.match(missing,/未记录/);assert.doesNotMatch(missing,/未支付|已支付/);
});
test('UX-001 list fields emphasize fulfillment versus transactions without guessing paid amounts',async()=>{
 const h=harness();const data={id:'9',orderNo:'order-9',appointmentId:'7',appointmentNo:'appointment-7',appointmentAmount:'120.00',originalAmount:'120.00',discountAmount:'11.00',amount:'109.00',appointmentStatus:3,status:'REFUNDED',paymentStatus:'REFUNDED',paymentChannel:'MOCK_WALLET',serviceName:'护理'};
 h.respond=()=>({items:[data],total:1});h.run("window.orderRecordState.tab='appointments'");const appointments=await h.run('orders()');assert.match(appointments,/到店时间/);assert.match(appointments,/预约金额，非实际收款/);assert.match(appointments,/¥120.00/);assert.match(appointments,/查看预约详情/);
 h.run("window.orderRecordState.tab='orders'");const orders=await h.run('orders()');assert.match(orders,/交易订单/);assert.match(orders,/实际应付 ¥109.00/);assert.match(orders,/订单原价 ¥120.00/);assert.match(orders,/实付及退款金额请查看详情/);assert.match(orders,/模拟支付，不代表真实资金交易/);assert.doesNotMatch(orders,/实付 ¥109.00|真实到账/);assert.equal(h.calls.length,2);
});
test('UX-001 each tab retains its own filters and page and reset affects only active tab',async()=>{
 const h=harness();h.run('render=async()=>{}');h.run("window.orderRecordState.status='REFUNDED';window.orderRecordState.paymentStatus='REFUNDED';window.orderRecordState.page=3;window.orderRecordState.keyword='order-a'");
 await h.run("window.switchRecordView('appointments')");assert.equal(h.run('window.orderRecordState.status'),'ALL');assert.equal(h.run('window.orderRecordState.keyword'),'');
 h.run("window.orderRecordState.status='3';window.orderRecordState.paymentStatus='NO_ORDER';window.orderRecordState.page=2;window.orderRecordState.keyword='appointment-b'");
 await h.run("window.switchRecordView('orders')");assert.equal(h.run('window.orderRecordState.status'),'REFUNDED');assert.equal(h.run('window.orderRecordState.page'),3);assert.equal(h.run('window.orderRecordState.keyword'),'order-a');
 h.run('window.bindOrderRecords()');await h.element('#record-reset').onclick();assert.equal(h.run('window.orderRecordState.status'),'ALL');
 await h.run("window.switchRecordView('appointments')");assert.equal(h.run('window.orderRecordState.paymentStatus'),'NO_ORDER');assert.equal(h.run('window.orderRecordState.page'),2);assert.equal(h.run('window.orderRecordState.keyword'),'appointment-b');
});
test('UX-001 authenticated refresh restores the correct tab while fresh login clears previous identity filters',async()=>{
 const saved={owner:'ADMIN:99',tab:'appointments',views:{appointments:{status:'3',paymentStatus:'NO_ORDER',keyword:'apt-id',page:2},orders:{status:'REFUNDED',paymentStatus:'REFUNDED',page:3}}};
 const storage=new Map([['qixin-admin-record-view',JSON.stringify(saved)]]);
 const h=harness('#orders',{token:'valid-token',user:{id:'99',role:'ADMIN'},storage});await new Promise(resolve=>setImmediate(resolve));assert.equal(h.run('state.view'),'orders');assert.equal(h.run('window.orderRecordState.tab'),'appointments');assert.equal(h.run('window.orderRecordState.keyword'),'apt-id');assert.equal(h.run('window.orderRecordState.page'),2);
 h.run("render=async()=>{};showWorkspace({id:'99',role:'ADMIN'},{freshLogin:true})");assert.equal(h.run('state.view'),'overview');assert.equal(h.run('window.orderRecordState.tab'),'orders');assert.equal(h.run('window.orderRecordState.page'),1);assert.equal(h.storage.has('qixin-admin-record-view'),false);
 const other=harness('#orders',{token:'valid-token',user:{id:'100',role:'ADMIN'},storage:new Map([['qixin-admin-record-view',JSON.stringify(saved)]])});await new Promise(resolve=>setImmediate(resolve));assert.equal(other.run('window.orderRecordState.keyword'),'');assert.equal(other.run('window.orderRecordState.tab'),'orders');
});
test('UX-002 one detail renderer changes section order and uses existing monetary values unchanged',()=>{
 const h=harness();const data={id:'9',appointmentId:'7',appointmentNo:'A7',appointmentStatus:3,status:'REFUNDED',paymentStatus:'REFUNDED',appointmentAmount:'120.00',originalAmount:'120.00',discountAmount:'11.00',pointsUsed:100,pointsDiscountAmount:'1.00',couponDiscountAmount:'10.00',amount:'109.00',actualPaidAmount:'108.99',actualRefundAmount:'50.00',paymentChannel:'MOCK_WALLET',payments:[{kind:'REFUND',amount:'50.00',channel:'MOCK_WALLET',status:'SIMULATED_SUCCESS',createdAt:'2026-11-18T10:00:00',id:'31',requestId:'refund-9'}]};h.context.data=data;const original=JSON.stringify(data);
 const apt=h.run("window.renderOrderDetail(data,'appointments')");assert(apt.indexOf('预约与服务履约')<apt.indexOf('关联交易概况'));assert.match(apt,/查看关联交易订单/);assert.doesNotMatch(apt,/<h3>支付交易|<h3>退款详情/);
 const order=h.run("window.renderOrderDetail(data,'orders')");assert(order.indexOf('交易订单与金额')<order.indexOf('关联预约信息'));assert.match(order,/实际支付金额（成功流水合计）/);assert.match(order,/¥108.99/);assert.match(order,/¥50.00/);assert.match(order,/退款原因：未记录/);assert.match(order,/refund-9/);assert.match(order,/查看关联预约/);assert.equal(JSON.stringify(data),original);
});
test('UX-002 legacy appointment details do not require a transaction and missing money stays unrecorded',()=>{
 const h=harness();h.context.old={appointmentId:'7',appointmentStatus:1,appointmentAmount:'120.00'};const html=h.run("window.renderOrderDetail(old,'appointments')");assert.match(html,/无关联交易订单/);assert.doesNotMatch(html,/data-record-related/);assert.doesNotMatch(html,/未支付/);
 h.context.incomplete={id:'9',appointmentId:'7',payments:[]};const missing=h.run("window.renderOrderDetail(incomplete,'orders')");assert.match(missing,/未记录/);assert.doesNotMatch(missing,/undefined|¥0.00/);
});
test('UX-002 related navigation reuses one drawer, validates actual relation and preserves list state',async()=>{
 const h=harness();let opened=0;h.element('#order-drawer').showModal=function(){this.open=true;opened++};h.run("window.orderRecordState.page=3;window.orderRecordState.status='REFUNDED'");h.respond=path=>({id:'9',appointmentId:'7',orderNo:'O9',appointmentNo:'A7',appointmentStatus:1,status:'WAITING_PAYMENT',paymentStatus:'UNPAID',payments:[]});
 await h.run("window.openOrderRecord('appointments','7')");assert.equal(h.element('#order-drawer-title').textContent,'预约详情');
 const click=relatedId=>h.element('#order-drawer-body').onclick({target:{closest:()=>({dataset:{recordRelated:'orders',relatedId}})}});
 await click('another-user-order');assert.equal(h.calls.length,1);await click('9');assert.equal(h.element('#order-drawer-title').textContent,'交易订单详情');assert.equal(h.calls.at(-1).path,'/api/admin/order-records/9');assert.equal(opened,1);
 h.element('#close-order-drawer').onclick();assert.equal(h.run('window.orderRecordState.page'),3);assert.equal(h.run('window.orderRecordState.status'),'REFUNDED');assert.equal(h.calls.length,2);
});


test('UX-002 mismatched related response is rejected instead of displaying another customer',async()=>{
 const h=harness();h.respond=()=>({id:'9',appointmentId:'7',userId:'1'});await h.run("window.openOrderRecord('appointments','7')");
 h.respond=()=>({id:'9',appointmentId:'7',userId:'2',userName:'other-customer'});
 await h.element('#order-drawer-body').onclick({target:{closest:()=>({dataset:{recordRelated:'orders',relatedId:'9'}})}});
 assert.match(h.element('#order-drawer-body').innerHTML,/关联记录不一致/);assert.doesNotMatch(h.element('#order-drawer-body').innerHTML,/other-customer/);
});
test('UX-002 late response cannot replace a newer detail or reopen a closed drawer',async()=>{
 const h=harness();let finish;h.respond=path=>path.endsWith('/7')?new Promise(resolve=>finish=resolve):{id:'9',appointmentId:'8',orderNo:'latest-order'};
 const older=h.run("window.openOrderRecord('appointments','7')");await h.run("window.openOrderRecord('orders','9')");finish({id:'3',appointmentId:'7',appointmentNo:'stale-appointment'});await older;assert.match(h.element('#order-drawer-body').innerHTML,/latest-order/);assert.doesNotMatch(h.element('#order-drawer-body').innerHTML,/stale-appointment/);
 let resolveClosed;h.respond=()=>new Promise(resolve=>resolveClosed=resolve);const pending=h.run("window.openOrderRecord('appointments','10')");h.element('#close-order-drawer').onclick();resolveClosed({appointmentId:'10'});await pending;assert.equal(h.element('#order-drawer').open,false);
});
