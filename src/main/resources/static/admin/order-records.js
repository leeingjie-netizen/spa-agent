// Shared read-only record views. Never infer payment from payable amount.
const recordDefaults=()=>({status:'ALL',paymentStatus:'ALL',from:'',to:'',keyword:'',page:1,size:20});
window.orderRecordState={tab:'orders',...recordDefaults()};
const recordViewState={appointments:recordDefaults(),orders:recordDefaults()};
const recordStorageKey='qixin-admin-record-view';
let recordStateOwner=null,recordDetailSequence=0;
const recordOwner=()=>state.user?.id!=null?`${state.role}:${state.user.id}`:null;
function recordSnapshot(value){return {status:String(value.status||'ALL'),paymentStatus:String(value.paymentStatus||'ALL'),from:String(value.from||''),to:String(value.to||''),keyword:String(value.keyword||'').slice(0,100),page:Number.isInteger(value.page)&&value.page>0&&value.page<=100000?value.page:1,size:20};}
function restoreRecordView(){
 const owner=recordOwner();if(!owner||owner===recordStateOwner)return;recordStateOwner=owner;
 Object.assign(recordViewState,{appointments:recordDefaults(),orders:recordDefaults()});
 let tab='orders';try{const stored=JSON.parse(sessionStorage.getItem(recordStorageKey)||'null');if(stored?.owner===owner){for(const type of ['appointments','orders'])if(stored.views?.[type])recordViewState[type]=recordSnapshot(stored.views[type]);if(stored.tab==='appointments')tab='appointments';}}catch{}
 Object.assign(window.orderRecordState,{tab},recordViewState[tab]);
}
function persistRecordView(){
 const s=window.orderRecordState;recordViewState[s.tab]=recordSnapshot(s);const owner=recordOwner();
 if(owner)try{sessionStorage.setItem(recordStorageKey,JSON.stringify({owner,tab:s.tab,views:recordViewState}));}catch{}
}
window.resetOrderRecordState=function(){
 Object.assign(recordViewState,{appointments:recordDefaults(),orders:recordDefaults()});Object.assign(window.orderRecordState,{tab:'orders'},recordDefaults());recordStateOwner=recordOwner();sessionStorage.removeItem(recordStorageKey);
};
window.switchRecordView=function(tab){
 if(!['appointments','orders'].includes(tab))return;persistRecordView();Object.assign(window.orderRecordState,{tab},recordViewState[tab]);persistRecordView();return render();
};
const recordStatusMaps={appointment:{0:['待确认','pending'],1:['已预约','active'],2:['已完成','success'],3:['已取消','neutral']},order:{WAITING_PAYMENT:['待支付','pending'],WAITING_SERVICE:['待服务','active'],IN_SERVICE:['服务中','active'],COMPLETED:['已完成','success'],CANCELLED:['已取消','neutral'],CLOSED:['已关闭','neutral'],REFUNDING:['退款中','refunding'],REFUNDED:['已退款','refunded']},payment:{UNPAID:['未支付','pending'],PAID:['已支付','success'],REFUNDING:['退款中','refunding'],REFUNDED:['已退款','refunded'],FAILED:['支付失败','error']},transaction:{SUCCESS:['成功','success'],SIMULATED_SUCCESS:['模拟成功','success'],REFUNDING:['退款中','refunding'],PENDING:['处理中','pending'],FAILED:['失败','error']}};
const appointmentStatus=value=>recordStatusMaps.appointment[value]?.[0]||(value==null||value===''?'未记录':String(value));
function recordBadge(domain,value){const mapped=recordStatusMaps[domain]?.[value];return `<span class="badge record-badge record-${mapped?.[1]||'neutral'}">${e(mapped?.[0]||(value==null||value===''?'未记录':String(value)))}</span>`;}
function recordStatusLine(name,domain,value){return `<div class="record-status-line"><span class="record-status-name">${e(name)}：</span>${recordBadge(domain,value)}</div>`;}
const hasOrder=o=>o.id!=null&&String(o.id)!=='';
const simulated=o=>String(o.paymentChannel||'').startsWith('MOCK')||(o.payments||[]).some(p=>String(p.channel||'').startsWith('MOCK'));
const simulationNote=o=>simulated(o)?'<p class="record-simulation">模拟支付，不代表真实资金交易</p>':'';
const recordMoney=value=>value==null||value===''?'未记录':`¥${value}`;
const recordChannel=value=>({MOCK:'开发模拟',MOCK_WALLET:'模拟余额',WECHAT:'微信支付'})[value]||(value==null||value===''?'未记录':String(value));
function relatedStates(o,includeAppointment=false){
 if(!hasOrder(o))return '<div class="record-status-stack"><div class="record-status-line"><span class="record-status-name">订单：</span><span class="record-no-order">无关联订单</span></div><div class="record-status-line"><span class="record-status-name">支付：</span><span>—</span></div></div>';
 return `<div class="record-status-stack">${recordStatusLine('订单','order',o.status)}${recordStatusLine('支付','payment',o.paymentStatus)}${includeAppointment?recordStatusLine('预约','appointment',o.appointmentStatus):''}</div>`;
}
const recordOptions=(values,current)=>values.map(([value,label])=>`<option value="${e(value)}" ${String(value)===String(current)?'selected':''}>${e(label)}</option>`).join('');
function appointmentRecordRow(o){return `<tr><td><strong class="record-number">${e(o.appointmentNo||o.appointmentId||'未记录')}</strong><small class="record-secondary">${e(o.serviceName||'未记录服务名称')}</small></td><td>${e(o.userName||'未记录客户姓名')}<small class="record-secondary">客户ID ${e(o.userId)}</small><span class="record-secondary">技师 ${e(o.staffName||'未记录')}</span></td><td>${e(o.appointmentDate||'未记录')}<small class="record-secondary">${e(timeLabel(o.startTime))}—${e(timeLabel(o.endTime))}</small></td><td><span class="record-money">${e(recordMoney(o.appointmentAmount))}</span><small class="record-secondary">预约金额，非实际收款</small></td><td>${recordStatusLine('预约','appointment',o.appointmentStatus)}</td><td>${relatedStates(o)}${simulationNote(o)}</td><td><button class="text-button" data-action="appointment-detail" data-id="${e(o.appointmentId)}">查看预约详情</button></td></tr>`;}
function transactionRecordRow(o){return `<tr><td><strong class="record-number">${e(o.orderNo||o.id||'未记录')}</strong><small class="record-secondary">关联预约 ${e(o.appointmentNo||o.appointmentId||'未记录')}</small><small class="record-secondary">${e(o.serviceName||'未记录服务名称')}</small></td><td>${e(o.userName||'未记录客户姓名')}<small class="record-secondary">客户ID ${e(o.userId)}</small></td><td><span class="record-secondary">订单原价 ${e(recordMoney(o.originalAmount))}</span><span class="record-secondary">合计优惠 ${e(recordMoney(o.discountAmount))}</span><strong class="record-money">实际应付 ${e(recordMoney(o.amount))}</strong></td><td>${relatedStates(o,true)}</td><td>${e(recordChannel(o.paymentChannel))}${simulationNote(o)}<small class="record-secondary">实付及退款金额请查看详情</small></td><td><div class="record-actions"><button class="text-button" data-action="order-detail" data-id="${e(o.id)}">查看订单详情</button>${orderRecordActions(o)}</div></td></tr>`;}
window.renderOrderRecords=async function(){
 restoreRecordView();const version=state.version,s={...window.orderRecordState},history=s.tab==='appointments';
 const query=new URLSearchParams({status:s.status,paymentStatus:s.paymentStatus,page:s.page,size:s.size,keyword:s.keyword,...(s.from?{from:s.from}:{}),...(s.to?{to:s.to}:{})});
 const data=await api('/api/admin/'+(history?'appointments':'order-records')+'?'+query);
 const items=Array.isArray(data)?data:data.items,total=Array.isArray(data)?data.length:Number(data.total);
 if(!history&&version===state.version&&window.orderRecordState.tab==='orders')state.orders=items;
 const statusOptions=history?[['0','待确认'],['1','已预约'],['2','已完成'],['3','已取消']]:Object.entries(recordStatusMaps.order).map(([value,info])=>[value,info[0]]);
 return `<div class="record-tabs" role="tablist"><button data-record-tab="appointments" role="tab" aria-selected="${history}">预约记录</button><button data-record-tab="orders" role="tab" aria-selected="${!history}">交易订单</button></div>
 <p class="help">${history?'查看全部预约及服务履约情况，包含无关联交易订单的历史预约。':'管理真实创建的交易订单及支付、退款情况；实际应付不等于实际支付。'}</p>
 <form id="record-filters" class="toolbar"><label>${history?'预约':'订单'}状态<select id="record-status">${recordOptions([['ALL','全部'],...statusOptions],s.status)}</select></label><label>支付状态<select id="record-payment">${recordOptions([['ALL','全部'],...['UNPAID','PAID','REFUNDING','REFUNDED'].map(v=>[v,recordStatusMaps.payment[v][0]]),...(history?[['NO_ORDER','无关联订单']]:[])],s.paymentStatus)}</select></label><label>预约起始日期<input id="record-from" type="date" value="${e(s.from)}"></label><label>截至日期<input id="record-to" type="date" value="${e(s.to)}"></label><label>订单 / 预约编号 / 客户ID<input id="record-keyword" maxlength="100" value="${e(s.keyword)}" placeholder="完整编号或ID"></label><button class="primary" type="submit">查询</button><button class="secondary" id="record-reset" type="button">重置全部</button></form>
 <div class="record-list ${history?'appointment-record-list':'transaction-record-list'}">${items.length?rows(history?['预约编号 / 服务','客户 / 技师','到店时间','预约金额','服务履约','关联交易概况','操作']:['交易订单 / 关联预约','客户','金额明细','交易状态','渠道 / 说明','操作'],items,history?appointmentRecordRow:transactionRecordRow):empty('暂无符合条件的记录','可调整状态、编号或日期；重置全部可恢复当前入口的完整记录。')}</div>
 <div class="ledger-pagination"><button id="record-prev" class="secondary" ${s.page<=1?'disabled':''}>上一页</button><span>第 ${s.page} 页 · 共 ${total} 条</span><button id="record-next" class="secondary" ${s.page*s.size>=total?'disabled':''}>下一页</button></div>`;
};
function orderRecordActions(o){return `${o.status==='WAITING_SERVICE'?`<button class="text-button" data-action="order-start" data-id="${e(o.id)}">开始服务</button>`:o.status==='IN_SERVICE'?`<button class="text-button" data-action="order-complete" data-id="${e(o.id)}">完成服务</button>`:''}${['WAITING_PAYMENT','WAITING_SERVICE'].includes(o.status)?`<button class="text-button danger-text" data-action="order-cancel" data-id="${e(o.id)}">${o.paymentStatus==='PAID'?'取消并退款':'取消订单'}</button>`:''}`;}

window.bindOrderRecords=function(){
 const s=window.orderRecordState;
 document.querySelectorAll('[data-record-tab]').forEach(button=>button.onclick=()=>window.switchRecordView(button.dataset.recordTab));
 const apply=event=>{event?.preventDefault();Object.assign(s,{status:$('#record-status').value,paymentStatus:$('#record-payment').value,from:$('#record-from').value,to:$('#record-to').value,keyword:$('#record-keyword').value.trim(),page:1});persistRecordView();return render()};
 $('#record-filters').onsubmit=apply;$('#record-status').onchange=apply;$('#record-payment').onchange=apply;
 $('#record-reset').onclick=()=>{Object.assign(s,recordDefaults());persistRecordView();return render()};
 $('#record-prev').onclick=()=>{if(s.page>1)s.page--;persistRecordView();return render()};$('#record-next').onclick=()=>{s.page++;persistRecordView();return render()};
};
function detailFields(fields){return `<dl class="record-fields">${fields.map(([label,value])=>`<div><dt>${e(label)}</dt><dd>${e(value==null||value===''?'未记录':value)}</dd></div>`).join('')}</dl>`;}
function appointmentDetailSection(o){return `<section class="record-appointment-section"><h3>预约与服务履约</h3>${recordStatusLine('预约','appointment',o.appointmentStatus)}${detailFields([['预约编号',o.appointmentNo],['预约ID',o.appointmentId],['预约服务',o.serviceName],['服务技师',o.staffName],['客户',o.userName],['客户ID',o.userId],['到店时间',o.appointmentDate?`${o.appointmentDate} ${timeLabel(o.startTime)}—${timeLabel(o.endTime)}`:null],['预约金额（非实际收款）',recordMoney(o.appointmentAmount)],['备注',o.remark]])}${String(o.appointmentStatus)==='3'?'<p class="help">预约已取消；取消原因与取消时间未记录。</p>':''}</section>`;}
function relationLink(tab,id,label){return id==null||id===''?'':`<button class="secondary" data-record-related="${tab}" data-related-id="${e(id)}">${e(label)}</button>`;}
function orderSummarySection(o){return `<section class="record-order-summary"><h3>关联交易概况</h3>${hasOrder(o)?relatedStates(o)+detailFields([['交易订单编号',o.orderNo],['订单原价',recordMoney(o.originalAmount)],['实际应付',recordMoney(o.amount)]])+simulationNote(o)+relationLink('orders',o.id,'查看关联交易订单'):'<p class="help">无关联交易订单。支付与退款不适用；历史预约可独立查看，不补造订单。</p>'}</section>`;}
function transactionRows(items){return items.length?rows(['类型 / 状态','金额 / 渠道','记录时间 / 本地交易记录号'],items,p=>`<tr><td>${e(p.kind==='PAY'?'支付':p.kind==='REFUND'?'退款':p.kind)}<br>${recordBadge('transaction',p.status)}</td><td>${e(recordMoney(p.amount))}<br>${e(recordChannel(p.channel))}</td><td>${e(p.createdAt||'未记录')}<br><span class="record-number">${e(p.id)}</span><small class="record-secondary">本地请求号 ${e(p.requestId||'未记录')}</small></td></tr>`):'<p class="muted">尚无相关交易记录</p>';}
function refundDetailSection(o){const refunds=(o.payments||[]).filter(p=>p.kind==='REFUND');const refundState=['REFUNDING','REFUNDED'].includes(o.paymentStatus)?o.paymentStatus:['REFUNDING','REFUNDED'].includes(o.status)?o.status:null;const initiated=refunds.length||refundState;return `<section class="record-refund-section"><h3>退款详情</h3>${initiated?recordStatusLine('退款','order',refundState):'<p class="muted">无退款记录</p>'}<p>退款原因：${e(o.refundReason||(initiated?'未记录':'不适用'))}</p>${transactionRows(refunds)}</section>`;}
function orderDetailSections(o){return `<section class="record-order-section"><h3>交易订单与金额</h3>${relatedStates(o)}${detailFields([['交易订单编号',o.orderNo],['订单ID',o.id],['关联预约编号',o.appointmentNo],['订单原始金额',recordMoney(o.originalAmount)],['优惠券抵扣',recordMoney(o.couponDiscountAmount)],['积分抵扣',o.pointsUsed==null?'未记录':`${o.pointsUsed} 分 / ${recordMoney(o.pointsDiscountAmount)}`],['合计优惠',recordMoney(o.discountAmount)],['实际应付金额',recordMoney(o.amount)],['实际支付金额（成功流水合计）',o.actualPaidAmount==null?'未记录':recordMoney(o.actualPaidAmount)],['已退款金额（成功流水合计）',o.actualRefundAmount==null?'未记录':recordMoney(o.actualRefundAmount)],['权益状态',({RESERVED:'冻结待支付',USED:'已使用',RELEASED:'已退回'})[o.benefitStatus]||'无权益记录'],['创建时间',o.createdAt],['支付截止',o.expiresAt]])}<p class="fine">实际支付及已退款金额仅统计成功交易；应付金额不代表已经支付。</p></section>
 ${simulationNote(o)}<section><h3>支付交易</h3>${transactionRows((o.payments||[]).filter(p=>p.kind==='PAY'))}</section>${refundDetailSection(o)}
 <section><h3>积分变动记录</h3>${(o.pointsAdjustments||[]).length?rows(['类型','积分','时间'],o.pointsAdjustments,p=>`<tr><td>${e(p.kind==='RESERVE'?'抵扣冻结':p.kind==='RELEASE'?'退回':p.kind)}</td><td>${e(p.points)}</td><td>${e(p.createdAt)}</td></tr>`):'<p class="muted">无积分调整记录</p>'}</section>`;}
window.renderOrderDetail=function(o,tab='orders'){
 if(tab==='appointments')return appointmentDetailSection(o)+orderSummarySection(o);
 if(!hasOrder(o))return '<p class="help">无关联交易订单，不展示或补造交易信息。</p>'+appointmentDetailSection(o);
 return orderDetailSections(o)+`<details class="record-linked-appointment"><summary>关联预约信息</summary>${appointmentDetailSection(o)}${relationLink('appointments',o.appointmentId,'查看关联预约')}</details>`;
};
window.openOrderRecord=async function(tab,id,relationGuard=null){
 if(!['appointments','orders'].includes(tab))return;
 const version=++recordDetailSequence,dialog=$('#order-drawer');
 $('#order-drawer-title').textContent=tab==='appointments'?'预约详情':'交易订单详情';$('#order-drawer-body').innerHTML='<div class="loading" role="status">正在读取详情…</div>';$('#order-drawer-body').onclick=null;
 const close=()=>{recordDetailSequence++;dialog.close()};$('#close-order-drawer').onclick=close;dialog.oncancel=()=>{recordDetailSequence++};
 if(!dialog.open)dialog.showModal();
 try{
  const data=await api('/api/admin/'+(tab==='appointments'?'appointments':'order-records')+'/'+encodeURIComponent(id));
  if(version!==recordDetailSequence)return;
  if(String(tab==='appointments'?data.appointmentId:data.id)!==String(id))throw new Error('记录编号不一致，请刷新列表后重试');
  if(relationGuard&&(String(data.appointmentId)!==String(relationGuard.appointmentId)||String(data.id)!==String(relationGuard.orderId)||(relationGuard.userId!=null&&String(data.userId)!==String(relationGuard.userId))))throw new Error('关联记录不一致，请刷新列表后重试');
  $('#order-drawer-body').innerHTML=window.renderOrderDetail(data,tab);dialog.scrollTop=0;
  $('#order-drawer-body').onclick=event=>{
   const button=event.target.closest('[data-record-related]');if(!button)return;
   const next=button.dataset.recordRelated,expected=next==='orders'&&tab==='appointments'?data.id:next==='appointments'&&tab==='orders'?data.appointmentId:null;
   if(expected==null||String(expected)!==String(button.dataset.relatedId))return;
   return window.openOrderRecord(next,expected,{appointmentId:data.appointmentId,orderId:data.id,userId:data.userId});
  };
 }catch(error){if(version===recordDetailSequence)$('#order-drawer-body').innerHTML=empty('详情暂时无法读取',error.message)}
};
