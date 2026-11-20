// Read-only probe of current administrator-configured image resources. Never print signed URLs.
const fs=require('node:fs');
async function main(){
 const r=await fetch('http://127.0.0.1:18080/api/operations/content',{signal:AbortSignal.timeout(10000)}),payload=await r.json();if(!r.ok||payload.code!==200)throw new Error('Content read failed');
 const results=[];
 for(const content of payload.data){
  if(!content.imageUrl){results.push({id:content.id,configured:false});continue;}
  let url;try{url=new URL(content.imageUrl)}catch{results.push({id:content.id,configured:true,validUrl:false});continue;}
  const result={id:content.id,configured:true,host:url.hostname,protocol:url.protocol,signed:url.searchParams.has('Signature')||url.searchParams.has('x-oss-signature'),queryRedacted:true};
  if(url.protocol!=='https:'){result.probed=false;result.reason='HTTPS is required';results.push(result);continue;}
  try{const image=await fetch(url,{method:'GET',redirect:'error',signal:AbortSignal.timeout(10000)});result.http=image.status;result.contentType=image.headers.get('content-type');result.imageResponse=image.ok&&String(result.contentType).startsWith('image/');result.statusOnlyNoImageSaved=true;await image.body?.cancel();}catch(error){result.error=error.name==='TimeoutError'?'timeout':'network/TLS/redirect failure';}
  results.push(result);
 }
 fs.writeFileSync('target/bugfix-image-probe.json',JSON.stringify({checkedAt:new Date().toISOString(),results,miniProgramRendered:false},null,2));console.log(JSON.stringify(results,null,2));
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
