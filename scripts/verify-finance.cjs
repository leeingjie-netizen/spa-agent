// Read-only local finance acceptance, using existing fixture identities and independent SQL counts.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict')
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:18080'
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>{let i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).trim().replace(/^(['"])(.*)\1$/,'$2')]}))
async function req(url,token,data){const r=await fetch(base+url,{method:data===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data)});return{http:r.status,...await r.json()}}
async function main(){
 const tokens=[];let checks=0
 const check=value=>{assert(value,'Finance assertion '+(checks+1));checks++}
 try{
  let login=await req('/api/auth/admin-login',null,{username:env.SPA_AUTH_ADMIN_USERNAME,password:process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD});check(login.code===200);const admin=login.data.token;tokens.push(admin)
  const report=await req('/api/admin/finance/ledger?mode=SIMULATED&size=100',admin);check(report.code===200)
  const sql=cp.execFileSync('mysql',['--protocol=TCP','-h','127.0.0.1','-P','3306','-u','root','--batch','--skip-column-names','spa','-e',"SELECT (SELECT COUNT(*) FROM spa_payment WHERE channel IN ('MOCK','MOCK_WALLET') AND created_at>=DATE_SUB(CURDATE(),INTERVAL 6 DAY) AND created_at<CURDATE()+INTERVAL 1 DAY)+(SELECT COUNT(*) FROM spa_mock_wallet_ledger WHERE created_at>=DATE_SUB(CURDATE(),INTERVAL 6 DAY) AND created_at<CURDATE()+INTERVAL 1 DAY)"],{encoding:'utf8',windowsHide:true}).trim()
  check(Number(report.data.total)===Number(sql));check(report.data.items.some(r=>r.kind==='TOPUP'));check(report.data.items.some(r=>r.kind==='WALLET_DEBIT'&&r.orderId))
  const page1=await req('/api/admin/finance/ledger?size=1&page=1',admin),page2=await req('/api/admin/finance/ledger?size=1&page=2',admin)
  check(page1.data.items.length===1&&page2.data.items.length===1&&page1.data.items[0].id!==page2.data.items[0].id)
  check(Number((await req('/api/admin/finance/ledger?mode=REAL',admin)).data.total)===0)
  const fixture=JSON.parse(fs.readFileSync(path.join(root,'target/http-acceptance_e995875332de48fb.json'),'utf8').replace(/^\uFEFF/,''))
  for(const suffix of ['a','b']){
   const user=await req('/api/auth/dev-login',null,{identity:fixture.tag+'_'+suffix});check(user.code===200);const token=user.data.token;tokens.push(token)
   check((await req('/api/admin/finance/ledger',token)).http===403);check((await req('/api/loyalty/wallet/ledger',token)).http===403)
   const own=await req('/api/loyalty/summary',token),attempt=await req('/api/loyalty/summary?userId='+fixture.fixtures.users[suffix==='a'?1:0],token)
   check(own.code===200&&String(own.data.walletBalance)===String(attempt.data.walletBalance))
  }
  check((await req('/api/admin/finance/ledger')).http===401)
  const evidence={passed:true,checks,totalRows:report.data.total,databaseRows:Number(sql),sources:[...new Set(report.data.items.map(r=>r.source))],consumerLedgerDenied:true,balanceOwnershipVerified:true};fs.writeFileSync(path.join(root,'target/finance-http.json'),JSON.stringify(evidence,null,2));console.log(evidence)
 }finally{for(const token of tokens)await req('/api/auth/logout',token,{})}
}
main().catch(e=>{console.error(e.message);process.exitCode=1})
