package com.lee.spa.commerce;

import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;
    public record Payment(String channel,String requestId) { }
    public record Transition(String status) { }
    public record Benefits(Long couponId,Integer points) { }
    @PostMapping("/api/orders/{id}/benefits") public Result<?> benefits(@PathVariable long id,@RequestBody Benefits input) { return Result.success(orders.applyBenefits(id,input.couponId(),input.points()==null?0:input.points())); }
    @GetMapping("/api/orders") public Result<?> list() { return Result.success(orders.list(false)); }
    @GetMapping("/api/orders/{id}") public Result<?> detail(@PathVariable long id) { return Result.success(orders.detail(id)); }
    @PostMapping("/api/orders/{id}/pay") public Result<?> pay(@PathVariable long id,@RequestBody Payment input) {
        if(!java.util.Set.of("MOCK","MOCK_WALLET").contains(input.channel()==null?"":input.channel())) throw new com.lee.spa.common.exception.BusinessException("真实微信支付尚未配置");
        if(!orders.mockEnabled()) throw new com.lee.spa.common.exception.BusinessException("模拟支付仅在显式启用的本地或测试环境开放");
        return Result.success(orders.pay(id,input.channel(),input.requestId()));
    }
    @PostMapping("/api/orders/{id}/cancel") public Result<?> cancel(@PathVariable long id) { return Result.success(orders.cancel(id)); }
    @GetMapping("/api/admin/orders") public Result<?> adminList() { return Result.success(orders.list(true)); }
    @PostMapping("/api/admin/orders/{id}/transition") public Result<?> transition(@PathVariable long id,@RequestBody Transition input) { return Result.success(orders.transition(id,input.status())); }
}
