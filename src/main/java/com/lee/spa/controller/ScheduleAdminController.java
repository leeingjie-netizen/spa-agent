package com.lee.spa.controller;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.result.Result;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.ScheduleManagementRequests.*;
import com.lee.spa.entity.*;
import com.lee.spa.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController
@RequestMapping("/api/admin/scheduling")
@RequiredArgsConstructor
public class ScheduleAdminController {
 private final ScheduleManagementService management;
 private final ScheduleGenerationService generation;
 private final StaffService staff;
 @GetMapping("/calendar") public Result<?> calendar(@RequestParam LocalDate from,@RequestParam(defaultValue="30") int days,@RequestParam(required=false) Long staffId){return Result.success(management.calendar(from,days,staffId));}
 @GetMapping("/leaves") public Result<?> leaves(){return Result.success(management.leaveList());}
 @GetMapping("/business-days") public Result<?> business(@RequestParam LocalDate from,@RequestParam(defaultValue="30") int days){return Result.success(management.calendar(from,days,null).get("businessDays"));}
 @PostMapping("/generate") public Result<?> generate(@RequestBody Generate request){
  AuthContext.requireRole("ADMIN"); LocalDate from=request.from()==null?LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE):request.from();int days=request.days()==null?30:request.days();
  if(from.isBefore(LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE))) throw new BusinessException("Invalid operation; check date, time range, reason, permissions and current booking state");
  if(request.staffId()!=null)return Result.success(generation.generate(request.staffId(),from,days));
  return Result.success(staff.getStaffList().stream().map(s->generation.generate(s.getId(),from,days)).toList());
 }
 @PutMapping("/schedule/{staffId}/{date}") public Result<?> schedule(@PathVariable Long staffId,@PathVariable LocalDate date,@RequestBody Manual request){return Result.success(management.manual(staffId,date,request));}
 @PostMapping("/exceptions") public Result<?> exception(@RequestBody ExceptionRequest request){return Result.success(management.block(request));}
 public record RevokeRequest(String reason) {}
 @PostMapping("/exceptions/{id}/revoke") public Result<?> revoke(@PathVariable Long id,@RequestBody RevokeRequest request){return Result.success(management.revokeException(id,request.reason()));}
 @PutMapping("/business-days/{date}") public Result<?> business(@PathVariable LocalDate date,@RequestBody Business request){return Result.success(management.businessDay(date,request));}
 @PostMapping("/leaves/{id}/review") public Result<?> review(@PathVariable Long id,@RequestBody Review request){return Result.success(management.review(id,request));}
}
