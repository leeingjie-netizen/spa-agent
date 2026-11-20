package com.lee.spa.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.exception.*;
import com.lee.spa.dto.ScheduleManagementRequests.*;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
@Service
@RequiredArgsConstructor
public class ScheduleManagementService {
 private final StaffMapper staff;
 private final StaffScheduleMapper schedules;
 private final StaffWeeklyRuleMapper rules;
 private final StaffLeaveMapper leaves;
 private final ScheduleExceptionMapper exceptions;
 private final BusinessDayMapper business;
 private final ScheduleChangeLogMapper changes;
 private final AppointmentMapper appointments;
 private final ScheduleConstraintService constraints;
 private final ObjectMapper json;
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 private static LocalDate today(){return LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE);}
 private static void future(LocalDate date){if(date==null || date.isBefore(today()) || date.isAfter(today().plusYears(1))) throw new BusinessException("日期必须在今天至未来一年内");}
 private static void reason(String reason){if(reason==null || reason.isBlank() || reason.length()>500) throw new BusinessException("请填写不超过500字的原因");}
 private static void interval(LocalTime start,LocalTime end){if(start==null || end==null || !start.isBefore(end) || start.getSecond()!=0 || end.getSecond()!=0 || start.getNano()!=0 || end.getNano()!=0) throw new BusinessException("时间范围必须有效且精确到分钟");}
 private void technician(Long id){if(id==null || staff.selectById(id)==null) throw new BusinessException("技师不存在");}
 public Map<String,Object> calendar(LocalDate from,int days,Long staffId){
  AuthContext.requireRole("ADMIN");
  if(from==null || days<1 || days>90) throw new BusinessException("日历范围须为1至90天");
  LocalDate to=from.plusDays(days-1);
  return Map.of("staff",staff.selectList(new LambdaQueryWrapper<Staff>().eq(staffId!=null,Staff::getId,staffId)),
   "schedules",schedules.selectList(new LambdaQueryWrapper<StaffSchedule>().between(StaffSchedule::getWorkDate,from,to).eq(staffId!=null,StaffSchedule::getStaffId,staffId)),
   "weeklyRules",rules.selectList(new LambdaQueryWrapper<StaffWeeklyRule>().eq(staffId!=null,StaffWeeklyRule::getStaffId,staffId)),
   "exceptions",exceptions.calendar(from,to,staffId),
   "leaves",leaves.selectList(new LambdaQueryWrapper<StaffLeave>().between(StaffLeave::getWorkDate,from,to).eq(staffId!=null,StaffLeave::getStaffId,staffId)),
   "businessDays",business.selectList(new LambdaQueryWrapper<BusinessDay>().between(BusinessDay::getWorkDate,from,to)));
 }
 private void conflict(Long staffId,LocalDate date,Predicate<Appointment> incompatible){
  List<Long> ids=appointments.selectList(new LambdaQueryWrapper<Appointment>().eq(staffId!=null,Appointment::getStaffId,staffId)
    .eq(Appointment::getAppointmentDate,date).ne(Appointment::getStatus,3).last("FOR UPDATE")).stream().filter(incompatible).map(Appointment::getId).toList();
  if(!ids.isEmpty()) throw new ScheduleConflictException(ids);
 }
 @Transactional
 public StaffSchedule manual(Long staffId,LocalDate date,Manual request){
  AuthContext.requireRole("ADMIN"); future(date);technician(staffId); reason(request.reason());
  if(request.status()==null || (request.status()!=0&&request.status()!=1)) throw new BusinessException("排班状态必须为工作或休息");
  StaffSchedule next=new StaffSchedule();next.setStaffId(staffId);next.setWorkDate(date);next.setStatus(request.status());next.setSource("MANUAL");
  if(request.status()==1){
   interval(request.startTime(),request.endTime());
   if((request.breakStartTime()==null)!=(request.breakEndTime()==null)) throw new BusinessException("午休开始和结束时间必须同时填写");
   if(request.breakStartTime()!=null){interval(request.breakStartTime(),request.breakEndTime());if(request.breakStartTime().isBefore(request.startTime())||request.breakEndTime().isAfter(request.endTime())) throw new BusinessException("午休必须位于工作时间内");}
   next.setStartTime(request.startTime());next.setEndTime(request.endTime());next.setBreakStartTime(request.breakStartTime());next.setBreakEndTime(request.breakEndTime());
  }
  constraints.lockDay(date);
  StaffSchedule previous=schedules.selectForUpdate(staffId,date);
  if(previous!=null && "MANUAL".equals(previous.getSource()) && !Boolean.TRUE.equals(request.confirmManual())) throw new BusinessException("已有人工排班，必须明确确认本次调班");
  conflict(staffId,date,a->!ScheduleAvailabilityPolicy.isAvailable(next,date.atTime(a.getStartTime()),(int)Duration.between(a.getStartTime(),a.getEndTime()).toMinutes()));
  if(previous==null) schedules.insert(next);else{
   next.setId(previous.getId());
   schedules.update(null,new LambdaUpdateWrapper<StaffSchedule>().eq(StaffSchedule::getId,previous.getId()).set(StaffSchedule::getStatus,next.getStatus())
    .set(StaffSchedule::getSource,"MANUAL").set(StaffSchedule::getStartTime,next.getStartTime()).set(StaffSchedule::getEndTime,next.getEndTime())
    .set(StaffSchedule::getBreakStartTime,next.getBreakStartTime()).set(StaffSchedule::getBreakEndTime,next.getBreakEndTime()));
  }
  ScheduleChangeLog log=new ScheduleChangeLog();log.setStaffId(staffId);log.setWorkDate(date);log.setPreviousValue(serialize(previous));log.setNewValue(serialize(next));log.setReason(request.reason());log.setOperatorId(AuthContext.requireUserId());changes.insert(log);
  return next;
 }
 private String serialize(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
 @Transactional
 public Map<String,Object> revokeException(Long id,String explanation){
  AuthContext.requireRole("ADMIN");reason(explanation);
  ScheduleException item=exceptions.selectById(id);
  if(item==null)throw new BusinessException("临时休息记录不存在");
  future(item.getWorkDate());constraints.lockDay(item.getWorkDate());
  jdbc.update("INSERT INTO schedule_exception_revocation(exception_id,revoked_by,reason) VALUES(?,?,?) ON DUPLICATE KEY UPDATE exception_id=exception_id",id,AuthContext.requireUserId(),explanation);
  return Map.of("id",id,"revoked",true);
 }
 @Transactional
 public ScheduleException block(ExceptionRequest request){
  AuthContext.requireRole("ADMIN");future(request.date());technician(request.staffId());reason(request.reason());interval(request.startTime(),request.endTime());
  constraints.lockDay(request.date());
  conflict(request.staffId(),request.date(),a->ScheduleConstraintService.overlaps(a.getStartTime(),a.getEndTime(),request.startTime(),request.endTime()));
  ScheduleException item=new ScheduleException();item.setStaffId(request.staffId());item.setWorkDate(request.date());item.setStartTime(request.startTime());item.setEndTime(request.endTime());item.setReason(request.reason());item.setCreatedBy(AuthContext.requireUserId());exceptions.insert(item);return item;
 }
 @Transactional
 public BusinessDay businessDay(LocalDate date,Business request){
  AuthContext.requireRole("ADMIN");future(date);reason(request.reason());if(request.closed()==null) throw new BusinessException("请指定是否停业");
  if(!request.closed()) interval(request.openTime(),request.closeTime());
  constraints.lockDay(date);
  conflict(null,date,a->request.closed() || a.getStartTime().isBefore(request.openTime()) || a.getEndTime().isAfter(request.closeTime()));
  BusinessDay day=new BusinessDay();day.setWorkDate(date);day.setClosed(request.closed());day.setOpenTime(request.closed()?null:request.openTime());day.setCloseTime(request.closed()?null:request.closeTime());day.setReason(request.reason());day.setUpdatedBy(AuthContext.requireUserId());
  if(business.selectById(date)==null) business.insert(day);else business.update(null,new LambdaUpdateWrapper<BusinessDay>().eq(BusinessDay::getWorkDate,date).set(BusinessDay::getClosed,day.getClosed()).set(BusinessDay::getOpenTime,day.getOpenTime()).set(BusinessDay::getCloseTime,day.getCloseTime()).set(BusinessDay::getReason,day.getReason()).set(BusinessDay::getUpdatedBy,day.getUpdatedBy()));
  return day;
 }
 public List<StaffLeave> leaveList(){
  AuthContext.requireRole("ADMIN","STAFF");
  boolean admin="ADMIN".equals(AuthContext.current().role());
  Long staffId=AuthContext.current().staffId();if(!admin&&staffId==null) throw new BusinessException("技师账号尚未绑定员工");
  return leaves.selectList(new LambdaQueryWrapper<StaffLeave>().eq(!admin,StaffLeave::getStaffId,staffId).orderByDesc(StaffLeave::getWorkDate));
 }
 @Transactional
 public StaffLeave submit(Leave request){
  AuthContext.requireRole("STAFF");future(request.date());reason(request.reason());
  Long staffId=AuthContext.current().staffId();technician(staffId);
  if(request.allDay()==null) throw new BusinessException("请指定全天或部分时段请假");if(!request.allDay()) interval(request.startTime(),request.endTime());
  constraints.lockDay(request.date());
  StaffLeave leave=new StaffLeave();leave.setStaffId(staffId);leave.setWorkDate(request.date());leave.setAllDay(request.allDay());leave.setStartTime(request.allDay()?null:request.startTime());leave.setEndTime(request.allDay()?null:request.endTime());leave.setReason(request.reason());leave.setStatus("PENDING");leave.setRequestedBy(AuthContext.requireUserId());leaves.insert(leave);return leave;
 }
 @Transactional
 public StaffLeave review(Long id,Review request){
  AuthContext.requireRole("ADMIN");reason(request.reason());if(request.approved()==null) throw new BusinessException("请指定审批结果");
  StaffLeave initial=leaves.selectById(id);if(initial==null) throw new BusinessException("请假申请不存在");constraints.lockDay(initial.getWorkDate());
  StaffLeave leave=leaves.selectOne(new LambdaQueryWrapper<StaffLeave>().eq(StaffLeave::getId,id).last("FOR UPDATE"));
  if(!"PENDING".equals(leave.getStatus())) throw new BusinessException("仅待审批申请可审批，请刷新当前状态");future(leave.getWorkDate());
  if(request.approved()) conflict(leave.getStaffId(),leave.getWorkDate(),a->Boolean.TRUE.equals(leave.getAllDay())||ScheduleConstraintService.overlaps(a.getStartTime(),a.getEndTime(),leave.getStartTime(),leave.getEndTime()));
  leave.setStatus(request.approved()?"APPROVED":"REJECTED");leave.setReviewedBy(AuthContext.requireUserId());leave.setReviewedAt(LocalDateTime.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE));leave.setReviewReason(request.reason());leaves.updateById(leave);return leave;
 }
 @Transactional
 public StaffLeave withdraw(Long id){
  AuthContext.requireRole("ADMIN","STAFF");StaffLeave initial=leaves.selectById(id);if(initial==null) throw new BusinessException("请假申请不存在");constraints.lockDay(initial.getWorkDate());
  StaffLeave leave=leaves.selectOne(new LambdaQueryWrapper<StaffLeave>().eq(StaffLeave::getId,id).last("FOR UPDATE"));
  if(!"ADMIN".equals(AuthContext.current().role())&&!Objects.equals(leave.getStaffId(),AuthContext.current().staffId())) throw new com.lee.spa.auth.AuthException(403,"无权撤回他人的请假申请");
  if(!"PENDING".equals(leave.getStatus())) throw new BusinessException("仅待审批申请可撤回，已批准请假需由管理员协调调整");leave.setStatus("WITHDRAWN");leaves.updateById(leave);return leave;
 }

}
