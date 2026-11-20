package com.lee.spa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.common.result.Result;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/** Catalog writes are explicit administrator actions; existing appointments retain their snapshots. */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class AdminCatalogController {
    private final StaffMapper staff;
    private final SpaServiceMapper services;
    private final StaffServiceRelationMapper relations;

    public record ServiceInput(@NotBlank @Size(max=100) String name, @Size(max=500) String description,
        @NotNull @Min(1) @Max(720) Integer durationMinutes,
        @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal price,
        @Size(max=255) String coverUrl, @NotNull @Min(0) @Max(1) Integer status) { }
    public record StaffInput(@NotBlank @Size(max=50) String name, @Size(max=500) String introduction,
        @Size(max=255) String avatarUrl, @NotNull @Min(0) @Max(1) Integer status) { }
    public record SupportedInput(@NotNull @Size(max=100) List<@NotNull Long> serviceIds) { }

    @GetMapping("/staff") public Result<List<Staff>> staff() {
        AuthContext.requireRole("ADMIN");
        return Result.success(staff.selectList(new LambdaQueryWrapper<Staff>().orderByAsc(Staff::getId)));
    }
    @GetMapping("/services") public Result<List<SpaService>> services() {
        AuthContext.requireRole("ADMIN");
        return Result.success(services.selectList(new LambdaQueryWrapper<SpaService>().orderByAsc(SpaService::getId)));
    }
    @PostMapping("/services") public Result<SpaService> createService(@Valid @RequestBody ServiceInput input) { return saveService(null,input); }
    @PutMapping("/services/{id}") public Result<SpaService> updateService(@PathVariable Long id,@Valid @RequestBody ServiceInput input) { return saveService(id,input); }
    private Result<SpaService> saveService(Long id, ServiceInput input) {
        AuthContext.requireRole("ADMIN");
        SpaService value=id==null?new SpaService():services.selectById(id);
        if(value==null)throw new BusinessException("服务不存在");
        value.setName(input.name().strip());value.setDescription(input.description());value.setDurationMinutes(input.durationMinutes());
        value.setPrice(input.price());value.setCoverUrl(safeUrl(input.coverUrl()));value.setStatus(input.status());
        if(id==null)services.insert(value);else services.updateById(value);
        return Result.success(value);
    }
    @PostMapping("/staff") public Result<Staff> createStaff(@Valid @RequestBody StaffInput input) { return saveStaff(null,input); }
    @PutMapping("/staff/{id}") public Result<Staff> updateStaff(@PathVariable Long id,@Valid @RequestBody StaffInput input) { return saveStaff(id,input); }
    private Result<Staff> saveStaff(Long id,StaffInput input) {
        AuthContext.requireRole("ADMIN");
        Staff value=id==null?new Staff():staff.selectById(id);
        if(value==null)throw new BusinessException("技师不存在");
        value.setName(input.name().strip());value.setIntroduction(input.introduction());value.setAvatarUrl(safeUrl(input.avatarUrl()));value.setStatus(input.status());
        if(id==null)staff.insert(value);else staff.updateById(value);
        return Result.success(value);
    }
    @PutMapping("/staff/{id}/services") @Transactional
    public Result<List<SpaService>> supported(@PathVariable Long id,@Valid @RequestBody SupportedInput input) {
        AuthContext.requireRole("ADMIN");
        if(staff.selectById(id)==null)throw new BusinessException("技师不存在");
        List<Long> wanted=input.serviceIds().stream().distinct().toList();
        for(Long serviceId:wanted)if(services.selectById(serviceId)==null)throw new BusinessException("选择的服务不存在");
        List<StaffServiceRelation> previous=relations.selectList(new LambdaQueryWrapper<StaffServiceRelation>().eq(StaffServiceRelation::getStaffId,id));
        for(StaffServiceRelation relation:previous)if(!wanted.contains(relation.getServiceId()))relations.deleteById(relation.getId());
        for(Long serviceId:wanted)if(previous.stream().noneMatch(r->r.getServiceId().equals(serviceId))){
            StaffServiceRelation relation=new StaffServiceRelation();relation.setStaffId(id);relation.setServiceId(serviceId);relations.insert(relation);
        }
        return Result.success(wanted.isEmpty()?List.of():services.selectBatchIds(wanted));
    }
    private String safeUrl(String value) {
        return com.lee.spa.common.ImageUrlPolicy.validate(value,255,true);
    }
}
