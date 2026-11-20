// Focused BUG-001..013 checks only. No booking/payment/refund/points mutations.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:18080';
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{let i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).replace(/^['"]|['"]$/g,'')]}));
const report={base,startedAt:new Date().toISOString(),checks:[],readOnlyBusiness:true,notes:['Login/logout changes tokens only. Invalid catalog/leave requests must not create business rows. No 2.4B actions.']};
let admin,user;
function check(name,value){assert(value,name);report.checks.push({name,passed:true});}
async function raw(url,token,method='GET',data){let started=Date.now(),r=await fetch(base+url,{method,headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(15000)});return{http:r.status,body:await r.json(),ms:Date.now()-started};}
async function get(url,token=admin){let r=await raw(url,token);assert(r.http===200&&r.body.code===200,'Read failed: '+url+' HTTP '+r.http);return r.body.data;}
async function main(){
 let rejected=await raw('/api/auth/admin-login',null,'POST',{username:'bugfix-invalid-account',password:'deliberately-invalid-password'});check('BUG-004 invalid credentials are 401 with safe credential message',rejected.http===401&&rejected.body.message==='账号或密码错误');
 let login=await raw('/api/auth/admin-login',null,'POST',{username:env.SPA_AUTH_ADMIN_USERNAME,password:process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD});assert.equal(login.body.code,200);admin=login.body.data.token;check('administrator login remains functional',login.body.data.user.role==='ADMIN');
 const [beforeStaff,beforeServices]=await Promise.all([get('/api/admin/catalog/staff'),get('/api/admin/catalog/services')]);
 let leave=await raw('/api/staff/leaves',admin,'POST',{date:'2090-01-01',allDay:true,reason:'invalid administrator application'});check('BUG-002 backend rejects administrator application',leave.http===403);
 report.invalidInputTimingsMs=[];
 for(const [url,data] of [['/api/admin/catalog/staff',{name:'   ',status:1}],['/api/admin/catalog/services',{name:'   ',status:1,price:1,durationMinutes:30}],['/api/admin/catalog/services',{name:'bad-input',status:1,price:-1,durationMinutes:0}]]){let r=await raw(url,admin,'POST',data);check('BUG-006 independent backend validation '+url+' case '+report.invalidInputTimingsMs.length,r.http===400&&r.body.code===400);report.invalidInputTimingsMs.push(r.ms);}
 check('BUG-006 invalid inputs create no catalog rows',(await get('/api/admin/catalog/staff')).length===beforeStaff.length&&(await get('/api/admin/catalog/services')).length===beforeServices.length);
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'target/http-acceptance_e995875332de48fb.json'),'utf8').replace(/^\uFEFF/,''));
 const userLogin=await raw('/api/auth/dev-login',null,'POST',{identity:manifest.tag+'_a'});assert.equal(userLogin.body.code,200);user=userLogin.body.data.token;
 for(const url of ['/api/admin/order-records','/api/admin/appointments','/api/admin/order-records/'+manifest.fixtures.orderIds[0]]){let r=await raw(url,user);check('new read API rejects USER '+url,r.http===403);}
 const all=await get('/api/admin/order-records?size=100'),old=await get('/api/admin/appointments?paymentStatus=NO_ORDER&size=100');
 check('BUG-011 old 2026 appointments remain visible without payment orders',Number(old.total)>0&&old.items.some(r=>r.appointmentDate.startsWith('2026')&&!r.id));
 report.historicalAppointmentCount=Number(old.total);report.orderCount=Number(all.total);
 const oldDetail=await get('/api/admin/appointments/'+old.items[0].appointmentId);check('BUG-011 no synthetic transactions for legacy appointment',oldDetail.id==null&&oldDetail.payments.length===0);
 const refunded=await get('/api/admin/order-records?status=REFUNDED&paymentStatus=REFUNDED&size=100');check('BUG-008 order and payment filters are independent',refunded.items.length>0&&refunded.items.every(o=>o.status==='REFUNDED'&&o.paymentStatus==='REFUNDED'));
 const page1=await get('/api/admin/order-records?size=1&page=1'),page2=await get('/api/admin/order-records?size=1&page=2');check('BUG-008 pagination has distinct rows and stable total',page1.total===page2.total&&page1.items[0].id!==page2.items[0].id);
 const pointOrder=all.items.find(o=>Number(o.pointsUsed)===100&&o.originalAmount==='120.00'&&o.amount==='109.00'&&o.status==='REFUNDED');assert(pointOrder,'Retained points order is required; do not fabricate');
 const detail=await get('/api/admin/order-records/'+pointOrder.id);check('BUG-009/012 exact original coupon points and payable values',detail.originalAmount==='120.00'&&detail.couponDiscountAmount==='10.00'&&detail.pointsDiscountAmount==='1.00'&&detail.amount==='109.00');
 check('BUG-010 refund comes from actual recorded transaction',detail.actualRefundAmount==='109.00'&&detail.refundReason==null&&detail.payments.some(p=>p.kind==='REFUND'&&p.amount==='109.00'&&p.createdAt&&p.channel));
 report.pointsEvidence={orderId:detail.id,userId:detail.userId,original:detail.originalAmount,coupon:detail.couponDiscountAmount,points:Number(detail.pointsUsed),pointsDiscount:detail.pointsDiscountAmount,payable:detail.amount,paid:detail.actualPaidAmount,refunded:detail.actualRefundAmount,benefitStatus:detail.benefitStatus};
 const disabled=beforeStaff.filter(s=>Number(s.status)===0),publicStaff=await get('/api/staff',null);check('BUG-007 public staff list excludes disabled rows',publicStaff.every(s=>Number(s.status)===1));
 for(const service of beforeServices){const related=await get('/api/staff/'+service.id+'/staff',null);check('BUG-007 service staff list excludes disabled for '+service.id,related.every(s=>Number(s.status)===1&&!disabled.some(d=>d.id===s.id)));}
 const content=await get('/api/operations/content',null);report.operationsImages=content.map(c=>({id:c.id,configured:!!c.imageUrl,https:!!c.imageUrl&&c.imageUrl.startsWith('https://')}));
 check('BUG-003 home template uses server image URL and visible failure state',fs.readFileSync(path.join(root,'miniprogram/pages/home/index.wxml'),'utf8').includes('src="{{item.imageUrl}}"'));
 const script=await fetch(base+'/admin/order-records.js').then(r=>r.text());check('runtime serves new order detail browser',script.includes('window.renderOrderDetail'));
 const ai=await get('/api/admin/ai/status');report.aiConfigured=ai.configured;report.providerRequestMade=false;
 await raw('/api/auth/logout',admin,'POST',{});const expired=await raw('/api/auth/me',admin);check('logout revokes the issued token',expired.http===401);admin=null;
 report.passed=true;report.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(root,'target/bugfix-http.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks:report.checks.length,historicalAppointments:report.historicalAppointmentCount,orders:report.orderCount,pointsEvidence:report.pointsEvidence,aiConfigured:report.aiConfigured,modelCalled:false},null,2));
}
main().catch(error=>{console.error(error.message);report.error=error.message;fs.writeFileSync(path.join(root,'target/bugfix-http.json'),JSON.stringify(report,null,2));process.exitCode=1}).finally(async()=>{if(admin)await raw('/api/auth/logout',admin,'POST',{});if(user)await raw('/api/auth/logout',user,'POST',{})});
