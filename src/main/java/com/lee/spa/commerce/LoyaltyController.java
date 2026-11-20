package com.lee.spa.commerce;
import com.lee.spa.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor
public class LoyaltyController {
 private final LoyaltyService loyalty;
 public record Topup(BigDecimal amount,String requestId){}
 public record Active(boolean active){}
 @GetMapping("/api/loyalty/summary") public Result<?> summary(){return Result.success(loyalty.summary());}
 @GetMapping("/api/loyalty/coupons") public Result<?> coupons(){return Result.success(loyalty.coupons());}
 @PostMapping("/api/loyalty/coupons/{id}/claim") public Result<?> claim(@PathVariable long id){loyalty.claim(id);return Result.success(loyalty.coupons());}
 @PostMapping("/api/loyalty/wallet/topup") public Result<?> topup(@RequestBody Topup input){return Result.success(loyalty.topup(input.amount(),input.requestId()));}
 @GetMapping("/api/loyalty/wallet/ledger") public Result<?> ledger(){return Result.success(loyalty.ledger());}
 @GetMapping("/api/admin/coupons") public Result<?> templates(){return Result.success(loyalty.templates());}
 @PostMapping("/api/admin/coupons") public Result<?> create(@RequestBody LoyaltyService.Template input){loyalty.createTemplate(input);return Result.success();}
 @PutMapping("/api/admin/coupons/{id}/active") public Result<?> active(@PathVariable long id,@RequestBody Active input){loyalty.setTemplateActive(id,input.active());return Result.success();}
}
