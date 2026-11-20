package com.lee.spa.commerce;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class OperationsController {
    private final JdbcTemplate jdbc;
    public record Content(String title,String description,String imageUrl,Integer sortOrder,Boolean active) { }
    public record Review(Integer rating,String comment) { }
    @GetMapping("/api/operations/content") public Result<?> content() {return Result.success(contents(false));}
    @GetMapping("/api/admin/content") public Result<?> adminContent() {AuthContext.requireRole("ADMIN");return Result.success(contents(true));}
    private List<Map<String,Object>> contents(boolean all) {
        var rows=jdbc.queryForList("SELECT CAST(id AS CHAR) id,title,description,image_url imageUrl,sort_order sortOrder,active FROM spa_content "+(all?"":"WHERE active=1 ")+"ORDER BY sort_order,id DESC");return rows;
    }
    @PostMapping("/api/admin/content") public Result<?> create(@RequestBody Content input) {
        AuthContext.requireRole("ADMIN");validate(input);long id=IdWorker.getId();
        jdbc.update("INSERT INTO spa_content(id,title,description,image_url,sort_order,active) VALUES(?,?,?,?,?,?)",id,input.title(),input.description(),com.lee.spa.common.ImageUrlPolicy.validate(input.imageUrl(),500,false),input.sortOrder()==null?0:input.sortOrder(),Boolean.FALSE.equals(input.active())?0:1);
        return Result.success(Map.of("id",Long.toString(id)));
    }
    @PutMapping("/api/admin/content/{id}") public Result<?> update(@PathVariable long id,@RequestBody Content input) {
        AuthContext.requireRole("ADMIN");validate(input);
        if(jdbc.update("UPDATE spa_content SET title=?,description=?,image_url=?,sort_order=?,active=? WHERE id=?",input.title(),input.description(),com.lee.spa.common.ImageUrlPolicy.validate(input.imageUrl(),500,false),input.sortOrder()==null?0:input.sortOrder(),Boolean.FALSE.equals(input.active())?0:1,id)!=1) throw new BusinessException("活动不存在");return Result.success();
    }
    @GetMapping("/api/operations/points") public Result<?> points() {
        long user=AuthContext.requireUserId();
        return Result.success(jdbc.queryForList("SELECT CAST(id AS CHAR) id,CAST(order_id AS CHAR) orderId,points,reason,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') createdAt FROM spa_points_entry WHERE user_id=? UNION ALL SELECT CAST(id AS CHAR) id,CAST(order_id AS CHAR) orderId,points,CASE kind WHEN 'RESERVE' THEN '订单积分抵扣（含冻结）' ELSE '订单取消或调整退回积分' END reason,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s') createdAt FROM spa_points_adjustment WHERE user_id=? ORDER BY createdAt DESC",user,user));
    }
    @PostMapping("/api/orders/{id}/review") @Transactional public Result<?> review(@PathVariable long id,@RequestBody Review input) {
        Long user=AuthContext.requireUserId();
        var order=jdbc.queryForList("SELECT id FROM spa_order WHERE id=? AND user_id=? AND status='COMPLETED' FOR UPDATE",id,user);
        if(order.isEmpty()) throw new BusinessException("仅能评价本人已完成的订单");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM spa_review WHERE order_id=?",Long.class,id)>0) throw new BusinessException("该订单已评价");
        if(input.rating()==null||input.rating()<1||input.rating()>5||input.comment()==null||input.comment().length()>1000) throw new BusinessException("评分需为1至5分，评价最多1000字");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE id=? AND user_id=? AND status='COMPLETED'",Long.class,id,user)!=1) throw new BusinessException("仅能评价自己已完成的订单");
        jdbc.update("INSERT INTO spa_review(id,order_id,user_id,rating,content) VALUES(?,?,?,?,?)",IdWorker.getId(),id,user,input.rating(),input.comment());return Result.success();
    }
    private void validate(Content input) {
        if(input.title()==null||input.title().isBlank()||input.title().length()>100||input.description()==null||input.description().length()>1000) throw new BusinessException("请填写有效活动内容和HTTPS图片地址");
        com.lee.spa.common.ImageUrlPolicy.validate(input.imageUrl(),500,false);
    }
}
