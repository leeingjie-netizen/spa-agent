package com.lee.spa.commerce;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.auth.AuthException;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.entity.Appointment;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final com.lee.spa.service.ScheduleConstraintService constraints;
    private final LoyaltyService loyalty;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final String VIEW="SELECT o.id,o.appointment_id appointmentId,o.order_no orderNo,o.user_id userId,s.name serviceName,t.name staffName,CAST(a.service_id AS CHAR) serviceId,CAST(a.staff_id AS CHAR) staffId,a.appointment_date appointmentDate,a.start_time startTime,a.end_time endTime,o.amount,COALESCE(b.original_amount,o.amount) originalAmount,COALESCE(b.original_amount,o.amount)-o.amount discountAmount,COALESCE(b.points_used,0) pointsUsed,o.status,o.payment_status paymentStatus,o.payment_channel paymentChannel,o.created_at createdAt,o.expires_at expiresAt FROM spa_order o JOIN appointment a ON a.id=o.appointment_id LEFT JOIN spa_service s ON s.id=a.service_id LEFT JOIN staff t ON t.id=a.staff_id LEFT JOIN spa_order_benefit b ON b.order_id=o.id";

    @Transactional public void ensureForAppointment(Appointment appointment) {
        if (appointment == null || !Integer.valueOf(1).equals(appointment.getStatus())) return;
        jdbc.update("INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,expires_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                IdWorker.getId(),appointment.getId(),UUID.randomUUID().toString().replace("-",""),appointment.getUserId(),appointment.getAmount(),LocalDateTime.now(ZONE).plusMinutes(15));
    }
    public List<Map<String,Object>> list(boolean admin) {
        if(admin) { AuthContext.requireRole("ADMIN"); return safe(jdbc.queryForList(VIEW+" ORDER BY o.created_at DESC LIMIT 500")); }
        return safe(jdbc.queryForList(VIEW+" WHERE o.user_id=? ORDER BY o.created_at DESC LIMIT 200",AuthContext.requireUserId()));
    }
    public Map<String,Object> detail(long id) {
        List<Map<String,Object>> found=jdbc.queryForList(VIEW+" WHERE o.id=?",id);
        if(found.isEmpty()) throw new AuthException(404,"订单不存在");
        owner(found.get(0)); return safe(found).get(0);
    }
    public boolean mockEnabled() {
        var profiles=Arrays.asList(environment.getActiveProfiles());
        return (profiles.contains("local")||profiles.contains("test"))&&!profiles.contains("prod")&&!profiles.contains("production")&&environment.getProperty("spa.payment.mock-enabled",Boolean.class,false);
    }
    @Transactional public Map<String,Object> pay(long id,String channel,String requestId) {
        if(channel==null||!Set.of("MOCK","MOCK_WALLET").contains(channel)) throw new BusinessException("真实微信支付尚未配置，当前不可用");
        if(!mockEnabled()) throw new AuthException(403,"模拟支付未开放");
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,64}")) throw new BusinessException("支付请求标识无效");
        Map<String,Object> order=lock(id); owner(order);
        var prior=jdbc.queryForList("SELECT order_id FROM spa_payment WHERE request_id=?",requestId);
        if(!prior.isEmpty()) { if(((Number)prior.get(0).get("order_id")).longValue()!=id) throw new BusinessException("请求标识已被其他订单使用"); return detail(id); }
        if(!"WAITING_PAYMENT".equals(order.get("status"))) throw new BusinessException("当前订单不可支付");
        if(expired(order)) throw new BusinessException("订单支付已超时，请刷新订单");
        loyalty.consume(order,channel);
        payment(order,"PAY",requestId,channel);
        jdbc.update("UPDATE spa_order SET status='WAITING_SERVICE',payment_status='PAID',payment_channel=? WHERE id=?",channel,id);
        return detail(id);
    }
    @Transactional public Map<String,Object> cancel(long id) {
        Map<String,Object> order=lock(id); owner(order);
        if(!"ADMIN".equals(AuthContext.current().role())) {
            Object rawDate=order.get("appointment_date"),rawTime=order.get("start_time");
            LocalDate date=rawDate instanceof java.sql.Date d?d.toLocalDate():(LocalDate)rawDate;
            LocalTime time=rawTime instanceof java.sql.Time t?t.toLocalTime():(LocalTime)rawTime;
            if(!date.atTime(time).isAfter(LocalDateTime.now(ZONE))) throw new BusinessException("服务开始时间已到，请联系门店处理");
        }
        cancelLocked(order,"CANCELLED"); return detail(id);
    }
    @Transactional public Map<String,Object> applyBenefits(long id,Long couponId,int points) {
        Map<String,Object> order=lock(id);owner(order);
        if(expired(order)) throw new BusinessException("订单已超时");
        loyalty.apply(order,couponId,points);return detail(id);
    }
    @Transactional public void syncCancelledAppointment(Appointment appointment) {
        var found=jdbc.queryForList("SELECT id FROM spa_order WHERE appointment_id=?",appointment.getId());
        if(!found.isEmpty()) cancelLocked(lock(((Number)found.get(0).get("id")).longValue()),"CANCELLED");
    }
    @Transactional public Map<String,Object> transition(long id,String target) {
        AuthContext.requireRole("ADMIN");
        if(target==null||target.isBlank()) throw new BusinessException("请指定目标订单状态");
        Map<String,Object> order=lock(id);
        String old=order.get("status").toString();
        if(old.equals(target)) return detail(id);
        if("CANCELLED".equals(target)) {cancelLocked(order,target);return detail(id);}
        boolean allowed=(old.equals("WAITING_SERVICE")&&target.equals("IN_SERVICE"))||(old.equals("IN_SERVICE")&&target.equals("COMPLETED"));
        if(!allowed) throw new BusinessException("不允许的订单状态变更");
        if(expiredAppointment(order)&&!target.equals("COMPLETED")) throw new BusinessException("预约日期已过，请人工核实");
        jdbc.update("UPDATE spa_order SET status=? WHERE id=?",target,id);
        if(target.equals("COMPLETED")) {
            jdbc.update("UPDATE appointment SET status=2 WHERE id=? AND status=1",order.get("appointment_id"));
            int points=((BigDecimal)order.get("amount")).intValue();
            jdbc.update("INSERT INTO spa_points_entry(id,user_id,order_id,points,reason) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",IdWorker.getId(),order.get("user_id"),id,points,"服务完成（模拟订单不代表真实收款）");
        }
        return detail(id);
    }
    @Transactional public void closeExpired(long id) {
        Map<String,Object> order=lock(id);
        if("WAITING_PAYMENT".equals(order.get("status"))&&expired(order)) cancelLocked(order,"CLOSED");
    }
    private Map<String,Object> lock(long id) {
        var links=jdbc.queryForList("SELECT a.staff_id,a.appointment_date FROM spa_order o JOIN appointment a ON a.id=o.appointment_id WHERE o.id=?",id);
        if(links.isEmpty()) throw new AuthException(404,"订单不存在");
        var link=links.get(0);
        Object date=link.get("appointment_date");
        constraints.lockDay(date instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : (LocalDate)date);
        jdbc.queryForList("SELECT id FROM staff_schedule WHERE staff_id=? AND work_date=? FOR UPDATE",link.get("staff_id"),link.get("appointment_date"));
        return jdbc.queryForMap("SELECT o.*,a.appointment_date,a.start_time FROM spa_order o JOIN appointment a ON a.id=o.appointment_id WHERE o.id=? FOR UPDATE",id);
    }
    private void owner(Map<String,Object> order) {
        Object user=order.containsKey("userId")?order.get("userId"):order.get("user_id");
        if(!"ADMIN".equals(AuthContext.current().role())&&((Number)user).longValue()!=AuthContext.requireUserId()) throw new AuthException(403,"不能操作其他用户的订单");
    }
    private void cancelLocked(Map<String,Object> order,String target) {
        String state=order.get("status").toString();
        if(state.equals("CANCELLED")||state.equals("CLOSED")||state.equals("REFUNDED")) return;
        if(!Set.of("WAITING_PAYMENT","WAITING_SERVICE").contains(state)) throw new BusinessException("当前订单不可取消");
        boolean paid="PAID".equals(order.get("payment_status"));
        if(paid) {
            if(!Set.of("MOCK","MOCK_WALLET").contains(order.get("payment_channel"))) throw new BusinessException("真实支付订单需通过退款渠道处理");
            jdbc.update("UPDATE spa_order SET status='REFUNDING',payment_status='REFUNDING' WHERE id=?",order.get("id"));
            payment(order,"REFUND","refund-"+order.get("id"),order.get("payment_channel").toString());
        }
        loyalty.release(order,paid);
        jdbc.update("UPDATE spa_order SET status=?,payment_status=? WHERE id=?",paid?"REFUNDED":target,paid?"REFUNDED":"UNPAID",order.get("id"));
        jdbc.update("UPDATE appointment SET status=3 WHERE id=? AND status IN (0,1)",order.get("appointment_id"));
        jdbc.update("DELETE FROM appointment_slot WHERE appointment_id=?",order.get("appointment_id"));
    }
    private void payment(Map<String,Object> order,String kind,String request,String channel) {
        jdbc.update("INSERT INTO spa_payment(id,order_id,request_id,kind,channel,amount,status) VALUES(?,?,?,?,?,?,?)",IdWorker.getId(),order.get("id"),request,kind,channel,order.get("amount"),"SIMULATED_SUCCESS");
    }
    private boolean expired(Map<String,Object> order) { Object value=order.get("expires_at"); return !(value instanceof java.sql.Timestamp timestamp?timestamp.toLocalDateTime():(LocalDateTime)value).isAfter(LocalDateTime.now(ZONE)); }
    private boolean expiredAppointment(Map<String,Object> order) { Object value=order.get("appointment_date");return (value instanceof java.sql.Date date?date.toLocalDate():(LocalDate)value).isBefore(LocalDate.now(ZONE)); }
    private List<Map<String,Object>> safe(List<Map<String,Object>> rows) {
        for(var row:rows) for(var entry:row.entrySet()) {
            Object value=entry.getValue();
            if(value instanceof Long) entry.setValue(value.toString());
            else if(value instanceof java.sql.Date date) entry.setValue(date.toLocalDate().toString());
            else if(value instanceof java.sql.Time time) entry.setValue(time.toLocalTime().toString());
            else if(value instanceof java.sql.Timestamp time) entry.setValue(time.toLocalDateTime().toString());
        }
        return rows;
    }
}
