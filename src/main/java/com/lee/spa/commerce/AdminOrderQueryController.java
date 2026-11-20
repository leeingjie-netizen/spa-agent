package com.lee.spa.commerce;

import com.lee.spa.auth.AuthContext;
import com.lee.spa.auth.AuthException;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Read-only projections. Old appointments never cause synthetic orders or payments. */
@RestController
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdminOrderQueryController {
    private final JdbcTemplate jdbc;
    private static final Set<String> ORDER_STATES=Set.of("WAITING_PAYMENT","WAITING_SERVICE","IN_SERVICE","COMPLETED","CANCELLED","CLOSED","REFUNDING","REFUNDED");
    private static final Set<String> PAYMENT_STATES=Set.of("UNPAID","PAID","REFUNDING","REFUNDED");
    private static final String JOIN=" FROM appointment a LEFT JOIN spa_order o ON o.appointment_id=a.id LEFT JOIN spa_service s ON s.id=a.service_id LEFT JOIN staff t ON t.id=a.staff_id LEFT JOIN spa_user u ON u.id=a.user_id LEFT JOIN spa_order_benefit b ON b.order_id=o.id";
    private static final String FIELDS="SELECT CAST(a.id AS CHAR) appointmentId,a.appointment_no appointmentNo,CAST(a.user_id AS CHAR) userId,u.nickname userName,CAST(a.service_id AS CHAR) serviceId,s.name serviceName,CAST(a.staff_id AS CHAR) staffId,t.name staffName,a.appointment_date appointmentDate,a.start_time startTime,a.end_time endTime,a.status appointmentStatus,a.amount appointmentAmount,a.remark,CAST(o.id AS CHAR) id,o.order_no orderNo,o.status,o.payment_status paymentStatus,o.payment_channel paymentChannel,o.amount,COALESCE(b.original_amount,o.amount,a.amount) originalAmount,COALESCE(b.original_amount,o.amount,a.amount)-COALESCE(o.amount,a.amount) discountAmount,COALESCE(b.points_used,0) pointsUsed,b.status benefitStatus,o.created_at createdAt,o.expires_at expiresAt";

    @GetMapping({"/api/admin/order-records","/api/admin/appointments"})
    public Result<?> list(jakarta.servlet.http.HttpServletRequest request,
        @RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="ALL") String paymentStatus,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
        @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        AuthContext.requireRole("ADMIN");
        boolean appointments=request.getRequestURI().equals("/api/admin/appointments");
        if(page<1||page>100000||size<1||size>100||keyword.length()>100||from!=null&&to!=null&&from.isAfter(to)
            ||!status.equals("ALL")&&!(appointments?Set.of("0","1","2","3"):ORDER_STATES).contains(status)
            ||!paymentStatus.equals("ALL")&&!PAYMENT_STATES.contains(paymentStatus)&&!(appointments&&paymentStatus.equals("NO_ORDER")))
            throw new BusinessException("预约或订单查询条件不正确");
        String where=appointments?" WHERE 1=1":" WHERE o.id IS NOT NULL";
        List<Object> args=new ArrayList<>();
        if(!status.equals("ALL")){where+=appointments?" AND a.status=?":" AND o.status=?";args.add(appointments?Integer.valueOf(status):status);}
        if(paymentStatus.equals("NO_ORDER"))where+=" AND o.id IS NULL";
        else if(!paymentStatus.equals("ALL")){where+=" AND o.payment_status=?";args.add(paymentStatus);}
        if(from!=null){where+=" AND a.appointment_date>=?";args.add(from);}
        if(to!=null){where+=" AND a.appointment_date<=?";args.add(to);}
        if(!keyword.isBlank()){
            where+=" AND (CAST(a.id AS CHAR)=? OR CAST(o.id AS CHAR)=? OR a.appointment_no=? OR o.order_no=? OR CAST(a.user_id AS CHAR)=?)";
            for(int i=0;i<5;i++)args.add(keyword.trim());
        }
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+JOIN+where,Long.class,args.toArray());
        args.add(size);args.add((page-1)*size);
        var items=jdbc.queryForList(FIELDS+JOIN+where+" ORDER BY a.appointment_date DESC,a.start_time DESC,a.id DESC LIMIT ? OFFSET ?",args.toArray());
        return Result.success(Map.of("items",safe(items),"total",total,"page",page,"size",size));
    }

    @GetMapping({"/api/admin/order-records/{id}","/api/admin/appointments/{id}"})
    public Result<?> detail(jakarta.servlet.http.HttpServletRequest request,@PathVariable long id) {
        AuthContext.requireRole("ADMIN");
        boolean appointment=request.getRequestURI().startsWith("/api/admin/appointments/");
        var found=jdbc.queryForList(FIELDS+JOIN+(appointment?" WHERE a.id=?":" WHERE o.id=?"),id);
        if(found.isEmpty())throw new AuthException(404,"预约或订单不存在");
        var row=found.get(0);
        List<Map<String,Object>> payments=new ArrayList<>(),adjustments=new ArrayList<>();
        if(row.get("id")!=null){
            payments=jdbc.queryForList("SELECT CAST(id AS CHAR) id,request_id requestId,kind,channel,amount,status,created_at createdAt FROM spa_payment WHERE order_id=? ORDER BY created_at,id",row.get("id"));
            adjustments=jdbc.queryForList("SELECT CAST(id AS CHAR) id,points,kind,created_at createdAt FROM spa_points_adjustment WHERE order_id=? ORDER BY created_at,id",row.get("id"));
        }
        BigDecimal paid=BigDecimal.ZERO,refunded=BigDecimal.ZERO;
        for(var payment:payments)if(Set.of("SUCCESS","SIMULATED_SUCCESS").contains(payment.get("status"))){
            if("PAY".equals(payment.get("kind")))paid=paid.add((BigDecimal)payment.get("amount"));
            if("REFUND".equals(payment.get("kind")))refunded=refunded.add((BigDecimal)payment.get("amount"));
        }
        BigDecimal points=BigDecimal.valueOf(((Number)row.get("pointsUsed")).longValue(),2);
        row.put("pointsDiscountAmount",points);
        row.put("couponDiscountAmount",((BigDecimal)row.get("discountAmount")).subtract(points));
        row.put("actualPaidAmount",paid);row.put("actualRefundAmount",refunded);
        row.put("refundReason",null); // Existing schema never recorded a reason. Do not invent one.
        row.put("payments",safe(payments));row.put("pointsAdjustments",safe(adjustments));
        return Result.success(safe(found).get(0));
    }
    private List<Map<String,Object>> safe(List<Map<String,Object>> rows) {
        for(var row:rows)row.replaceAll((key,value)->{
            if(value instanceof BigDecimal amount)return amount.toPlainString();
            if(value instanceof Long)return value.toString();
            if(value instanceof java.sql.Date date)return date.toLocalDate().toString();
            if(value instanceof java.sql.Time time)return time.toLocalTime().toString();
            if(value instanceof java.sql.Timestamp time)return time.toLocalDateTime().toString();
            return value;
        });
        return rows;
    }
}
