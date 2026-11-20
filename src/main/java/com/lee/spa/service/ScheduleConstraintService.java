package com.lee.spa.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
@Service
@RequiredArgsConstructor
public class ScheduleConstraintService {
 private final ScheduleDayLockMapper locks;
 private final BusinessDayMapper business;
 private final StaffLeaveMapper leaves;
 private final ScheduleExceptionMapper exceptions;
 public void lockDay(LocalDate date) {
  if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Business date lock requires transaction");
  locks.ensure(date); locks.lock(date);
 }
 // In a booking transaction use current locking reads after the day lock, avoiding stale RR snapshots.
 private <T> LambdaQueryWrapper<T> currentRead(LambdaQueryWrapper<T> query) {
  if(TransactionSynchronizationManager.isActualTransactionActive()) query.last("FOR UPDATE");
  return query;
 }
 public boolean isAvailable(Long staffId,LocalDateTime start,int duration) {
  if(start==null || duration<=0) return false;
  LocalDateTime end=start.plusMinutes(duration);
  if(!end.toLocalDate().equals(start.toLocalDate())) return false;
  BusinessDay day=business.selectOne(currentRead(new LambdaQueryWrapper<BusinessDay>().eq(BusinessDay::getWorkDate,start.toLocalDate())));
  if(day!=null && (Boolean.TRUE.equals(day.getClosed()) || day.getOpenTime()==null || day.getCloseTime()==null
   || start.toLocalTime().isBefore(day.getOpenTime()) || end.toLocalTime().isAfter(day.getCloseTime()))) return false;
  for(StaffLeave leave:leaves.selectList(currentRead(new LambdaQueryWrapper<StaffLeave>().eq(StaffLeave::getStaffId,staffId)
   .eq(StaffLeave::getWorkDate,start.toLocalDate()).eq(StaffLeave::getStatus,"APPROVED")))) {
   if(Boolean.TRUE.equals(leave.getAllDay()) || overlaps(start.toLocalTime(),end.toLocalTime(),leave.getStartTime(),leave.getEndTime())) return false;
  }
  for(ScheduleException exception:exceptions.selectList(currentRead(new LambdaQueryWrapper<ScheduleException>()
   .eq(ScheduleException::getStaffId,staffId).eq(ScheduleException::getWorkDate,start.toLocalDate())
   .notInSql(ScheduleException::getId,"SELECT exception_id FROM schedule_exception_revocation")))) {
   if(overlaps(start.toLocalTime(),end.toLocalTime(),exception.getStartTime(),exception.getEndTime())) return false;
  }
  return true;
 }
 public void requireAvailable(Long staffId,LocalDateTime start,int duration) {
  if(!isAvailable(staffId,start,duration)) throw new BusinessException("预约与请假、临时不可约时段或门店营业安排冲突");
 }
 public static boolean overlaps(LocalTime start,LocalTime end,LocalTime blockedStart,LocalTime blockedEnd) {
  return blockedStart==null || blockedEnd==null || (start.isBefore(blockedEnd)&&end.isAfter(blockedStart));
 }
}
