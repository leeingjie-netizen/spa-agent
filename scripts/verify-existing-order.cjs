// Read-only business checks against a retained acceptance order; no new business fixtures.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
const root=path.resolve(__dirname,'..'),m=JSON.parse(fs.readFileSync(path.join(root,'target/http-acceptance_e995875332de48fb.json'),'utf8').replace(/^\uFEFF/,''))
async function request(url,token,data){let r=await fetch('http://127.0.0.1:18080'+url,{method:data===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data)});return {http:r.status,...await r.json()}}
async function main(){
 let token
 try{
  let login=await request('/api/auth/dev-login',null,{identity:m.tag+'_a'});assert.equal(login.code,200);token=login.data.token
  const list=await request('/api/orders',token);assert.equal(list.code,200)
  let order=list.data.find(o=>o.status==='REFUNDED');assert(order,'Retained refunded fixture is required')
  let detail=await request('/api/orders/'+order.id,token);assert.equal(detail.code,200);let o=detail.data
  assert.equal(Number(o.originalAmount),120);assert.equal(Number(o.discountAmount),11);assert.equal(Number(o.amount),109);assert.equal(Number(o.pointsUsed),100)
  // Empty input is rejected before inference; this script must never spend model tokens.
  let ai=await request('/api/ai/chat',token,{conversationId:'parameter-check',requestId:'readonly-ai-check',message:''})
  assert.notEqual(ai.code,200);assert.match(ai.message,/不合法/)
  const result={passed:true,orderId:o.id,originalAmount:o.originalAmount,discountAmount:o.discountAmount,amount:o.amount,pointsUsed:o.pointsUsed,aiParameterValidation:true,providerCompletionCalled:false}
  fs.writeFileSync(path.join(root,'target/existing-order-http.json'),JSON.stringify(result,null,2));console.log(result)
 } finally {if(token) await request('/api/auth/logout',token,{})}
}
main().catch(e=>{console.error(e.message);process.exitCode=1})
