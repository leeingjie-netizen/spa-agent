// Read-only resource fingerprint: helps distinguish source, target/classes and live server.
const fs=require('node:fs'),crypto=require('node:crypto');
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
async function main(){
 const resources=[];
 for(const name of ['index.html','admin.js','admin.css','order-records.js']){
  const response=await fetch('http://127.0.0.1:18080/admin/'+name,{signal:AbortSignal.timeout(5000)}),bytes=Buffer.from(await response.arrayBuffer());
  const source=fs.readFileSync('src/main/resources/static/admin/'+name),compiled=fs.readFileSync('target/classes/static/admin/'+name);
  resources.push({name,http:response.status,servedSha256:hash(bytes),sourceSha256:hash(source),compiledSha256:hash(compiled),matchesSource:hash(bytes)===hash(source),matchesTarget:hash(bytes)===hash(compiled)});
 }
 const report={checkedAt:new Date().toISOString(),base:'http://127.0.0.1:18080',resources,verified:resources.every(r=>r.http===200&&r.matchesSource&&r.matchesTarget),renderedInBrowser:false};
 fs.writeFileSync('target/bug014-resource-verification.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(!report.verified)process.exitCode=1;
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
