package com.lee.spa.commerce;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Operational audit ledger. Wallet movements and service revenue remain separate. */
@RestController @RequiredArgsConstructor
public class FinanceLedgerController {
 private final JdbcTemplate jdbc;
 private static final String LEDGER="""
  SELECT 'PAYMENT' source,CAST(p.id AS CHAR) id,p.created_at time,CAST(o.user_id AS CHAR) userId,u.nickname userName,
   CAST(p.order_id AS CHAR) orderId,o.order_no orderNo,p.kind,p.amount,
   CASE WHEN p.kind='REFUND' THEN -p.amount ELSE p.amount END signedAmount,p.channel,p.status,NULL balanceAfter
   FROM spa_payment p LEFT JOIN spa_order o ON o.id=p.order_id LEFT JOIN spa_user u ON u.id=o.user_id
  UNION ALL
  SELECT 'WALLET',CAST(w.id AS CHAR),w.created_at,CAST(w.user_id AS CHAR),u.nickname,CAST(o.id AS CHAR),o.order_no,
   CASE w.kind WHEN 'TEST_TOPUP' THEN 'TOPUP' WHEN 'PAYMENT' THEN 'WALLET_DEBIT' WHEN 'REFUND' THEN 'WALLET_REFUND' ELSE w.kind END,
   ABS(w.amount),w.amount,'MOCK_WALLET','SIMULATED_SUCCESS',w.balance_after
   FROM spa_mock_wallet_ledger w LEFT JOIN spa_user u ON u.id=w.user_id
   LEFT JOIN spa_order o ON w.kind IN ('PAYMENT','REFUND') AND w.request_id=CONCAT(CASE w.kind WHEN 'PAYMENT' THEN 'pay:' ELSE 'refund:' END,o.id)
  """;
 @GetMapping("/api/admin/finance/ledger")
 public Result<?> ledger(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
   @RequestParam(defaultValue="SIMULATED") String mode,@RequestParam(defaultValue="ALL") String source,
   @RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){
  AuthContext.requireRole("ADMIN");LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
  if(from==null)from=today.minusDays(6);if(to==null)to=today;
  if(from.isAfter(to)||from.plusDays(365).isBefore(to)||page<1||page>100000||size<1||size>100
    ||!Set.of("REAL","SIMULATED").contains(mode)||!Set.of("ALL","PAYMENT","WALLET").contains(source)
    ||!Set.of("ALL","SUCCESS","SIMULATED_SUCCESS","PENDING","FAILED","REFUNDING").contains(status))throw new BusinessException("资金流水查询条件不正确，日期最多366天");
  String where=" WHERE time>=? AND time<? AND "+("REAL".equals(mode)?"channel NOT IN ('MOCK','MOCK_WALLET')":"channel IN ('MOCK','MOCK_WALLET')");
  List<Object> args=new ArrayList<>(List.of(from.atStartOfDay(),to.plusDays(1).atStartOfDay()));
  if(!source.equals("ALL")){where+=" AND source=?";args.add(source);}if(!status.equals("ALL")){where+=" AND status=?";args.add(status);}
  String query=" FROM ("+LEDGER+") movements"+where;
  Long total=jdbc.queryForObject("SELECT COUNT(*)"+query,Long.class,args.toArray());
  var summary=jdbc.queryForList("SELECT source,kind,status,SUM(amount) amount,COUNT(*) count"+query+" GROUP BY source,kind,status",args.toArray());
  List<Object> paging=new ArrayList<>(args);paging.add(size);paging.add((page-1)*size);
  var items=jdbc.queryForList("SELECT source,id,DATE_FORMAT(time,'%Y-%m-%d %H:%i:%s') time,userId,userName,orderId,orderNo,kind,amount,signedAmount,channel,status,balanceAfter"+query+" ORDER BY time DESC,id DESC,source LIMIT ? OFFSET ?",paging.toArray());
  for(var row:items)for(String key:List.of("amount","signedAmount","balanceAfter"))if(row.get(key) instanceof BigDecimal value)row.put(key,value.setScale(2).toPlainString());
  summary.forEach(row->row.put("amount",((BigDecimal)row.get("amount")).setScale(2).toPlainString()));
  return Result.success(Map.of("items",items,"total",total,"page",page,"size",size,"summary",summary,"definition","支付账本与钱包账本分别展示；充值、钱包扣款/退回不再累计为服务收入。只有成功服务支付减成功退款计入净收款。"));
 }
}
