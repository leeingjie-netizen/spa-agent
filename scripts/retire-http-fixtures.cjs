// Retire only manifest-owned local acceptance fixtures; keep business/audit history.
const fs=require('node:fs'), path=require('node:path'), cp=require('node:child_process'), assert=require('node:assert/strict')
const {validateFixtureOrder}=require('./fixture-ownership.cjs')
const root=path.resolve(__dirname,'..'), connection=['--protocol=TCP','-h','127.0.0.1','-P','3306','-u','root','--default-character-set=utf8mb4','--batch','--skip-column-names','spa']
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>{let i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).replace(/^"|"$/g,'')]}))
function sql(query){return cp.execFileSync('mysql',[...connection,'-e',query],{encoding:'utf8',windowsHide:true}).trim()}
async function api(url,token,data){let r=await fetch('http://127.0.0.1:18080'+url,{method:data===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data)});let b=await r.json();assert(r.ok&&b.code===200,`API failed ${url}: ${r.status}`);return b.data}
async function main(){
 assert(process.env.MYSQL_PWD,'MYSQL_PWD required; run a verified backup before this script')
 const plans=process.argv.slice(2).map(name=>{
  assert(/^http-acceptance_[a-f0-9]{16}\.json$/.test(name),'Expected local manifest basename')
  const m=JSON.parse(fs.readFileSync(path.join(root,'target',name),'utf8').replace(/^\uFEFF/,'')), f=m.fixtures
  assert(/^acceptance_[a-f0-9]{16}$/.test(m.tag)&&m.base==='http://127.0.0.1:18080')
  for(let id of [f.staffId,f.serviceId,...(f.users||[]),...(f.appointmentIds||[]),...(f.orderIds||[]),...(f.couponTemplateId?[f.couponTemplateId]:[])]) assert(/^\d{1,19}$/.test(id))
  assert(f.dates.every(d=>/^\d{4}-\d{2}-\d{2}$/.test(d)))
  assert.equal(sql(`SELECT COUNT(*) FROM staff WHERE id=${f.staffId} AND name='${m.tag}'`),'1')
  assert.equal(sql(`SELECT COUNT(*) FROM spa_service WHERE id=${f.serviceId} AND name='${m.tag}'`),'1')
  assert.equal(sql(`SELECT COUNT(*) FROM spa_account WHERE user_id IN (${f.users.join(',')}) AND identity_key IN ('dev:${m.tag}_a','dev:${m.tag}_b')`),String(f.users.length),'Manifest identities mismatch')
  const statements=[`UPDATE staff SET status=0 WHERE id=${f.staffId} AND name='${m.tag}';`,`UPDATE spa_service SET status=0 WHERE id=${f.serviceId} AND name='${m.tag}';`]
  if(f.couponTemplateId) statements.push(`UPDATE spa_coupon_template SET active=0 WHERE id=${f.couponTemplateId} AND title='${m.tag}';`)
  statements.push(`DELETE FROM business_day WHERE work_date IN (${f.dates.map(d=>`'${d}'`).join(',')}) AND reason='${m.tag}';`)
  return {m,statements}
 })
 assert(plans.length,'No manifests specified')
 const file=path.join(root,'db/verification',plans.map(p=>p.m.tag).join('_')+'_retirement.sql')
 const migration='-- Only independently tagged fixtures, after verified backup. Historical orders and schedules retained.\nSTART TRANSACTION;\n'+plans.flatMap(p=>p.statements).join('\n')+'\nCOMMIT;\n'
 if(fs.existsSync(file))assert.equal(fs.readFileSync(file,'utf8'),migration,'Saved retirement SQL differs; manual review required')
 else fs.writeFileSync(file,migration,{flag:'wx'})
 let token
 try {
  token=(await api('/api/auth/admin-login',null,{username:env.SPA_AUTH_ADMIN_USERNAME,password:process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD})).token
  const orders=[]
  for(const {m} of plans) for(const id of m.fixtures.orderIds||[]) {
   const order=await api('/api/orders/'+id,token)
   validateFixtureOrder(m,order);orders.push(order)
  }
  for(const order of orders)if(order.status==='WAITING_PAYMENT')await api('/api/orders/'+order.id+'/cancel',token,{})
  sql(fs.readFileSync(file,'utf8'))
  for(const {m} of plans){const f=m.fixtures;assert.equal(sql(`SELECT status FROM staff WHERE id=${f.staffId}`),'0');assert.equal(sql(`SELECT status FROM spa_service WHERE id=${f.serviceId}`),'0');assert.equal(sql(`SELECT COUNT(*) FROM business_day WHERE reason='${m.tag}'`),'0')}
  fs.writeFileSync(path.join(root,'target',plans.map(p=>p.m.tag).join('_')+'_retirement-result.json'),JSON.stringify({verifiedAt:new Date().toISOString(),tags:plans.map(p=>p.m.tag),ordersVerified:orders.map(o=>o.id),sqlFile:file,retired:true,historyRetained:true},null,2))
  console.log('Retired '+plans.map(p=>p.m.tag).join(', ')+'. Preserved orders, payments, leave and schedule history. SQL: '+file)
 } finally {if(token) await api('/api/auth/logout',token,{})}
}
main().catch(e=>{console.error(e.message);process.exitCode=1})
