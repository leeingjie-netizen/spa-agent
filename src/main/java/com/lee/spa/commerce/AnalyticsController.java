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

/** Cash-basis payment reporting. Wallet topups are never service revenue. */
@RestController @RequiredArgsConstructor
public class AnalyticsController {
 private final JdbcTemplate jdbc;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 @GetMapping("/api/admin/analytics")
 public Result<?> dashboard(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
     @RequestParam(defaultValue="REAL") String mode,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){
  AuthContext.requireRole("ADMIN");LocalDate today=LocalDate.now(ZONE);
  if(from==null)from=today.minusDays(6);if(to==null)to=today;
  if(from.isAfter(to)||from.plusDays(365).isBefore(to)||page<1||page>100000||size<1||size>100)throw new BusinessException("日期范围最多366天，分页参数须有效");
  String scope=scope(mode);LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
  Map<String,Object> summary=new LinkedHashMap<>();
  summary.put("netToday",net(scope,today.atStartOfDay(),today.plusDays(1).atStartOfDay()));
  summary.put("netMonth",net(scope,today.withDayOfMonth(1).atStartOfDay(),today.plusDays(1).atStartOfDay()));
  summary.put("netAll",net(scope,LocalDate.of(1970,1,1).atStartOfDay(),today.plusDays(1).atStartOfDay()));
  summary.put("refundRange",money(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM spa_payment p WHERE "+scope+" AND p.kind='REFUND' AND p.created_at>=? AND p.created_at<?",BigDecimal.class,start,end)));
  summary.put("ordersToday",jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE created_at>=? AND created_at<?",Long.class,today.atStartOfDay(),today.plusDays(1).atStartOfDay()));
  summary.put("waitingService",jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE status='WAITING_SERVICE'",Long.class));
  summary.put("completed",jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE status='COMPLETED'",Long.class));
  summary.put("activeAppointments",jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE status IN(0,1) AND appointment_date>=?",Long.class,today));
  var totals=jdbc.queryForList("SELECT DATE_FORMAT(created_at,'%Y-%m-%d') date,SUM(CASE WHEN kind='PAY' THEN amount ELSE -amount END) net FROM spa_payment p WHERE "+scope+" AND kind IN('PAY','REFUND') AND created_at>=? AND created_at<? GROUP BY DATE_FORMAT(created_at,'%Y-%m-%d')",start,end);
  var daily=new ArrayList<Map<String,Object>>();for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){String key=day.toString();BigDecimal amount=totals.stream().filter(r->key.equals(r.get("date"))).map(r->(BigDecimal)r.get("net")).findFirst().orElse(BigDecimal.ZERO);daily.add(Map.of("date",key,"net",money(amount)));}
  var states=jdbc.queryForList("SELECT status,COUNT(*) count FROM spa_order WHERE created_at>=? AND created_at<? GROUP BY status",start,end);
  var popular=jdbc.queryForList("SELECT CAST(a.service_id AS CHAR) id,COALESCE(s.name,'历史服务') name,COUNT(*) orders,SUM(o.status='COMPLETED') completed FROM spa_order o JOIN appointment a ON a.id=o.appointment_id LEFT JOIN spa_service s ON s.id=a.service_id WHERE o.created_at>=? AND o.created_at<? GROUP BY a.service_id,s.name ORDER BY orders DESC LIMIT 8",start,end);
  var technicians=jdbc.queryForList("SELECT CAST(a.staff_id AS CHAR) id,COALESCE(s.name,'历史技师') name,COUNT(*) orders,SUM(o.status='COMPLETED') completed FROM spa_order o JOIN appointment a ON a.id=o.appointment_id LEFT JOIN staff s ON s.id=a.staff_id WHERE o.created_at>=? AND o.created_at<? GROUP BY a.staff_id,s.name ORDER BY orders DESC LIMIT 10",start,end);
  Long total=jdbc.queryForObject("SELECT COUNT(*) FROM spa_payment p WHERE "+scope+" AND p.created_at>=? AND p.created_at<?",Long.class,start,end);
  var ledger=jdbc.queryForList("SELECT CAST(p.id AS CHAR) id,DATE_FORMAT(p.created_at,'%Y-%m-%d %H:%i:%s') time,CAST(p.order_id AS CHAR) orderId,o.order_no orderNo,CAST(o.user_id AS CHAR) userId,p.kind,p.amount,p.channel,p.status FROM spa_payment p JOIN spa_order o ON o.id=p.order_id WHERE "+scope+" AND p.created_at>=? AND p.created_at<? ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",start,end,size,(page-1)*size);
  ledger.forEach(r->r.put("amount",money((BigDecimal)r.get("amount"))));
  return Result.success(Map.of("mode",mode,"from",from,"to",to,"summary",summary,"daily",daily,"orderStates",states,"popularServices",popular,"technicians",technicians,"ledger",Map.of("items",ledger,"total",total,"page",page,"size",size),"definition","金额按成功支付/成功退款流水时间统计；净收款=支付-退款；不计充值。订单分布与排行含所选创建日期内的全部开发/真实订单。"));
 }
 private static String scope(String mode){return switch(mode){case "REAL"->"p.channel NOT IN ('MOCK','MOCK_WALLET') AND p.status='SUCCESS'";case "SIMULATED"->"p.channel IN ('MOCK','MOCK_WALLET') AND p.status='SIMULATED_SUCCESS'";default->throw new BusinessException("请选择真实或开发模拟数据");};}
 private String net(String scope,LocalDateTime from,LocalDateTime to){return money(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN kind='PAY' THEN amount WHEN kind='REFUND' THEN -amount ELSE 0 END),0) FROM spa_payment p WHERE "+scope+" AND p.created_at>=? AND p.created_at<?",BigDecimal.class,from,to));}
 private static String money(BigDecimal value){return value.setScale(2).toPlainString();}
}
