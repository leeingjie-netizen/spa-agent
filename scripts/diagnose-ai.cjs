// Local configuration/authentication diagnostics only. Never send a valid chat completion request.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
const root=path.resolve(__dirname,'..'),base='http://127.0.0.1:18080'
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/).filter(x=>/^[A-Z_]+=/.test(x)).map(x=>{let i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1).trim().replace(/^(['"])(.*)\1$/,'$2')]}))
async function call(url,token,data){const response=await fetch(base+url,{method:data===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(15000)});return{http:response.status,...await response.json()}}
async function main(){
 let token,userToken
 try{
  const login=await call('/api/auth/admin-login',null,{username:env.SPA_AUTH_ADMIN_USERNAME,password:process.env.SPA_ACCEPTANCE_ADMIN_PASSWORD||env.SPA_AUTH_ADMIN_PASSWORD});assert.equal(login.code,200);token=login.data.token
  assert.equal((await call('/api/auth/me',token)).code,200)
  const status=await call('/api/admin/ai/status',token);assert.equal(status.code,200)
  const anonymous=await call('/api/ai/chat',null,{conversationId:'config-check',requestId:'diagnosis-check',message:''});assert.equal(anonymous.http,401)
  // Existing retained fixture identity only; no appointments or account roles are changed.
  const manifest=JSON.parse(fs.readFileSync(path.join(root,'target/http-acceptance_e995875332de48fb.json'),'utf8').replace(/^\uFEFF/,''))
  const user=await call('/api/auth/dev-login',null,{identity:manifest.tag+'_a'});assert.equal(user.code,200);userToken=user.data.token
  assert.equal((await call('/api/admin/ai/status',userToken)).http,403)
  const invalid=await call('/api/ai/chat',userToken,{conversationId:'config-check',requestId:'diagnosis-check',message:''});assert.notEqual(invalid.code,200);assert.match(invalid.message,/不合法/)
  const result={runtime:status.data,authenticatedRouteReachable:true,anonymousHttp:anonymous.http,userDiagnosticsHttp:403,invalidChatHttp:invalid.http,invalidChatBusinessCode:invalid.code,providerCompletionCalled:false,realModelVerified:false,multiTurnBookingVerified:false}
  fs.writeFileSync(path.join(root,'target/ai-runtime-diagnosis.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
 }finally{if(token)await call('/api/auth/logout',token,{});if(userToken)await call('/api/auth/logout',userToken,{})}
}
main().catch(e=>{console.error('AI diagnosis failed: '+e.message);process.exitCode=1})
