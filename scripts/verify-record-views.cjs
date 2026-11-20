// BUG-015 / UX-001/002: local read-only business verification. No business POST/PUT/DELETE.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto'),assert=require('node:assert/strict'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:18080';
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{let i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).replace(/^['"]|['"]$/g,'')]}));
const report={startedAt:new Date().toISOString(),base,checks:[],businessWrites:false,coreJavaChanged:false};let token;
const check=(name,value)=>{assert(value,name);report.checks.push({name,passed:true});};
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
function sql(query){return cp.execFileSync('mysql',['--protocol=TCP','-h','127.0.0.1','-P','3306','-u','root','--default-character-set=utf8mb4','--batch','--skip-column-names','spa','-e',query],{encoding:'utf8',windowsHide:true,timeout:15000,maxBuffer:16*1024*1024}).trimEnd();}
function snapshot(){
 assert(process.env.MYSQL_PWD,'MYSQL_PWD required');assert.equal(sql('SELECT DATABASE()'),'spa');const result={schema:{},rows:{},tokenRowsExcluded:true};
 for(const table of sql('SHOW TABLES').split(/\r?\n/)){
  assert(/^[A-Za-z0-9_]+$/.test(table),'Unexpected table name');result.schema[table]=hash(sql('SHOW CREATE TABLE `'+table+'`'));
  if(table==='spa_auth_token')continue;
  const rows=sql('SELECT * FROM `'+table+'`').split(/\r?\n/).filter(Boolean).sort();result.rows[table]={count:rows.length,sha256:hash(rows.join('\n'))};
 }
 return result;
}
async function request(url,method='GET',data,auth=token){const r=await fetch(base+url,{method,headers:{'content-type':'application/json',...(auth?{Authorization:'Bearer '+auth}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(15000)});return{http:r.status,body:await r.json()};}
async function get(url){const r=await request(url);assert(r.http===200&&r.body.code===200,'Read failed '+url);return r.body.data;}
async function all(url){let items=[],total=0;for(let page=1;page<=100;page++){const data=await get(url+(url.includes('?')?'&':'?')+'size=100&page='+page);total=Number(data.total);items.push(...data.items);if(items.length>=total)break;assert(data.items.length,'Unexpected empty page');}assert.equal(items.length,total,'List pagination incomplete');return items;}
const escape=v=>String(v==null?'':v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function main(){
 const before=snapshot();
 const login=await request('/api/auth/admin-login','POST',{username:env.SPA_AUTH_ADMIN_USERNAME,password:process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD},null);assert.equal(login.body.code,200);token=login.body.data.token;
 const [appointments,orders]=await Promise.all([all('/api/admin/appointments'),all('/api/admin/order-records')]);
 check('appointment count matches database',appointments.length===Number(sql('SELECT COUNT(*) FROM appointment')));
 check('transaction count matches database',orders.length===Number(sql('SELECT COUNT(*) FROM spa_order o JOIN appointment a ON a.id=o.appointment_id')));
 check('both lists contain unique records',new Set(appointments.map(r=>r.appointmentId)).size===appointments.length&&new Set(orders.map(r=>r.id)).size===orders.length);
 const legacy=appointments.filter(a=>a.id==null);check('legacy appointments remain and are absent from transaction list',legacy.length>0&&legacy.every(a=>!orders.some(o=>o.appointmentId===a.appointmentId)));
 for(const status of ['0','1','2','3']){const list=await all('/api/admin/appointments?status='+status);check('appointment status '+status+' is independent',list.every(r=>String(r.appointmentStatus)===status));}
 report.statusPresence={};for(const status of ['WAITING_PAYMENT','WAITING_SERVICE','IN_SERVICE','COMPLETED','CANCELLED','CLOSED','REFUNDING','REFUNDED']){const list=await all('/api/admin/order-records?status='+status);report.statusPresence[status]=list.length;check('order status '+status+' filter',list.every(r=>r.status===status));}
 for(const paid of ['UNPAID','PAID','REFUNDING','REFUNDED']){const list=await all('/api/admin/order-records?paymentStatus='+paid);check('payment status '+paid+' filter',list.every(r=>r.paymentStatus===paid));}
 check('ALL restores complete transaction list',(await all('/api/admin/order-records?status=ALL&paymentStatus=ALL')).length===orders.length);
 const filteredLegacy=await all('/api/admin/appointments?paymentStatus=NO_ORDER');check('NO_ORDER filter never fabricates unpaid orders',filteredLegacy.length===legacy.length&&filteredLegacy.every(r=>r.id==null));
 const context=vm.createContext({window:{},state:{role:'ADMIN',user:login.body.data.user,version:0,orders:[]},sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},URLSearchParams,api:get,e:escape,timeLabel:v=>String(v||'').slice(0,5),empty:title=>'<p>'+escape(title)+'</p>',rows:(headers,items,render)=>'<table><thead>'+headers.map(h=>'<th>'+escape(h)+'</th>').join('')+'</thead><tbody>'+items.map(render).join('')+'</tbody></table>'});context.window=context;
 vm.runInContext(fs.readFileSync(path.join(root,'src/main/resources/static/admin/order-records.js'),'utf8'),context);vm.runInContext('restoreRecordView()',context);
 context.window.orderRecordState.tab='appointments';const aptHtml=await context.window.renderOrderRecords();check('real appointment response renders fulfillment labels',aptHtml.includes('服务履约')&&aptHtml.includes('预约金额，非实际收款')&&aptHtml.includes('查看预约详情'));
 context.window.orderRecordState.tab='orders';const orderHtml=await context.window.renderOrderRecords();check('real transaction response renders transaction and payable labels',orderHtml.includes('交易订单')&&orderHtml.includes('实际应付')&&orderHtml.includes('查看订单详情')&&orderHtml.includes('订单：')&&orderHtml.includes('支付：'));
 const old=await get('/api/admin/appointments/'+legacy[0].appointmentId);const oldHtml=context.window.renderOrderDetail(old,'appointments');check('legacy detail renders without transactions',old.payments.length===0&&oldHtml.includes('无关联交易订单')&&!oldHtml.includes('data-record-related'));
 const sample=orders.find(o=>o.originalAmount!==o.amount);assert(sample,'Retained discounted order required; do not fabricate');
 const [appointment,order]=await Promise.all([get('/api/admin/appointments/'+sample.appointmentId),get('/api/admin/order-records/'+sample.id)]);
 check('related detail IDs and customer identity match',appointment.id===order.id&&appointment.appointmentId===order.appointmentId&&appointment.userId===order.userId);
 check('amounts come from distinct existing fields',appointment.appointmentAmount===order.appointmentAmount&&sample.amount===order.amount&&sample.originalAmount===order.originalAmount);
 const appointmentHtml=context.window.renderOrderDetail(appointment,'appointments'),detailHtml=context.window.renderOrderDetail(order,'orders');
 check('one data source produces contextual detail sections',appointmentHtml.includes('预约与服务履约')&&!appointmentHtml.includes('<h3>支付交易')&&detailHtml.indexOf('交易订单与金额')<detailHtml.indexOf('关联预约信息'));
 check('recorded successful payment and refund amounts are rendered',detailHtml.includes(escape('¥'+order.actualPaidAmount))&&detailHtml.includes(escape('¥'+order.actualRefundAmount)));
 for(const payment of order.payments)check('existing payment/refund record '+payment.id+' remains visible',detailHtml.includes(escape(payment.id))&&detailHtml.includes(escape(payment.createdAt)));
 check('simulation warning remains',!order.paymentChannel?.startsWith('MOCK')||detailHtml.includes('模拟支付，不代表真实资金交易'));
 const denied=await request('/api/admin/appointments','GET',undefined,null);check('existing anonymous access guard retained',denied.http===401);
 await request('/api/auth/logout','POST',{});token=null;
 const after=snapshot();check('database schema unchanged',JSON.stringify(before.schema)===JSON.stringify(after.schema));check('all non-token rows unchanged',JSON.stringify(before.rows)===JSON.stringify(after.rows));
 report.dataSafety={schemaUnchanged:true,businessAndAccountRowsUnchanged:true,protectedRowCount:Object.values(before.rows).reduce((sum,t)=>sum+t.count,0),tokenOnlyLoginLogout:true};
 report.counts={appointments:appointments.length,transactionOrders:orders.length,legacyWithoutOrder:legacy.length};
 report.amountEvidence={appointmentId:order.appointmentId,orderId:order.id,appointmentAmount:order.appointmentAmount,originalAmount:order.originalAmount,discountAmount:order.discountAmount,payable:order.amount,paid:order.actualPaidAmount,refunded:order.actualRefundAmount};
 report.passed=true;report.finishedAt=new Date().toISOString();fs.writeFileSync(path.join(root,'target/bug015-readonly-http.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({passed:true,checks:report.checks.length,counts:report.counts,amountEvidence:report.amountEvidence,dataSafety:report.dataSafety},null,2));
}
main().catch(e=>{report.passed=false;report.error=e.message;fs.writeFileSync(path.join(root,'target/bug015-readonly-http.json'),JSON.stringify(report,null,2));console.error(e.message);process.exitCode=1}).finally(async()=>{if(token)await request('/api/auth/logout','POST',{});});
