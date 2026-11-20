const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const env=Object.fromEntries(fs.readFileSync('.env.local','utf8').replace(/^\uFEFF/,'').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const p=l.indexOf('=');return [l.slice(0,p),l.slice(p+1)]}));
const base='http://127.0.0.1:18080';
async function request(path,token,body){const r=await fetch(base+path,{method:body?'POST':'GET',headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});return {http:r.status,body:await r.json()}}
async function main(){
 const password=process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD;if(!password)throw Error('Set SPA_ACCEPTANCE_ADMIN_PASSWORD to your local admin password when .env.local contains a hash.');
 const credentials={username:env.SPA_AUTH_ADMIN_USERNAME,password};
 assert.equal((await request('/api/auth/admin-login',null,{...credentials,password:'deliberately-wrong'})).http,401);
 const login=await request('/api/auth/admin-login',null,credentials);assert.equal(login.body.code,200);const token=login.body.data.token;
 try{
  assert.equal((await request('/api/admin/analytics')).http,401);
  const a=await request('/api/admin/analytics?mode=REAL',token),b=await request('/api/admin/analytics?mode=SIMULATED',token);
  assert.equal(a.body.code,200);assert.equal(b.body.code,200);assert.equal(a.body.data.summary.netAll,'0.00');
  if(!process.env.MYSQL_PWD)throw Error('MYSQL_PWD required for independent database comparison.');
  const actual=execFileSync('mysql',['--protocol=TCP','-h','127.0.0.1','-u','root','--batch','--skip-column-names','spa','-e',"SELECT CAST(COALESCE(SUM(CASE WHEN kind='PAY' THEN amount WHEN kind='REFUND' THEN -amount ELSE 0 END),0) AS DECIMAL(20,2)) FROM spa_payment WHERE status='SIMULATED_SUCCESS' AND channel IN('MOCK','MOCK_WALLET') AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)"],{encoding:'utf8'}).trim();
  assert.equal(b.body.data.summary.netAll,actual);
  assert.equal((await request('/api/admin/analytics?mode=SIMULATED&from=2090-01-01&to=2090-01-02',token)).body.data.ledger.total,'0');
  const report={realNet:a.body.data.summary.netAll,simulatedNet:b.body.data.summary.netAll,databaseNet:actual,ledgerTotal:b.body.data.ledger.total,passed:true};fs.writeFileSync('target/dashboard-http.json',JSON.stringify(report,null,2));console.log(report);
 }finally{await request('/api/auth/logout',token,{})}
 assert.equal((await request('/api/admin/analytics',token)).http,401);
 const again=await request('/api/auth/admin-login',null,credentials);assert.equal(again.body.code,200);await request('/api/auth/logout',again.body.data.token,{});
 console.log('PASS: wrong password, login, database totals, empty range, logout revocation and repeat login');
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
