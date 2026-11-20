const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path')
test('financial filters reset pagination and reload instead of silently keeping stale data',()=>{
 const controls={};let renders=0
 const context={window:{},$:selector=>controls[selector]||(controls[selector]={value:''}),render:()=>renders++}
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'analytics.js'),'utf8'),context)
 const state=context.window.analyticsState;state.page=4;context.window.bindAnalytics()
 controls['#finance-source'].onchange({target:{value:'WALLET'}})
 assert.equal(state.financeSource,'WALLET');assert.equal(state.page,1)
 state.page=3;controls['#finance-status'].onchange({target:{value:'FAILED'}})
 assert.equal(state.financeStatus,'FAILED');assert.equal(state.page,1);assert.equal(renders,2)
})
test('financial rows escape user content and preserve exact server amounts',()=>{
 const context={window:{},e:value=>String(value??'').replaceAll('<','&lt;').replaceAll('>','&gt;'),empty:()=>'',rows:(_,rows,render)=>rows.map(render).join('')}
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'finance.js'),'utf8'),context)
 const html=context.window.renderFinance({summary:[],total:1,definition:'Separate ledgers',items:[{source:'WALLET',id:'12',userName:'<script>bad</script>',kind:'TOPUP',amount:'999999.99',signedAmount:'999999.99',channel:'MOCK_WALLET',status:'SIMULATED_SUCCESS',balanceAfter:'999999.99'}]},{mode:'SIMULATED',page:1})
 assert(!html.includes('<script>'));assert(html.includes('&lt;script&gt;'));assert(html.includes('999999.99'));assert(html.includes('非真实资金'))
})
test('pagination anchors the ledger only after asynchronous rendering finishes',async()=>{
 let complete,anchors=0;const controls={};const context={window:{},state:{view:'overview'},$:selector=>controls[selector]||(controls[selector]={closest:()=>({scrollIntoView:()=>anchors++})}),render:()=>new Promise(resolve=>complete=resolve)}
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'analytics.js'),'utf8'),context)
 context.window.bindAnalytics();const pending=controls['#ledger-next'].onclick();assert.equal(anchors,0)
 complete();await pending;assert.equal(anchors,1);assert.equal(context.window.analyticsState.page,2)
 const changed=controls['#finance-status'].onchange({target:{value:'FAILED'}});context.state.view='orders';complete();await changed;assert.equal(anchors,1)
})
