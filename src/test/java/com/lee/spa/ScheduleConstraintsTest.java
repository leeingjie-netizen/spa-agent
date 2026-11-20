package com.lee.spa;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ScheduleConstraintsTest {
 @Mock ScheduleDayLockMapper locks;
 @Mock BusinessDayMapper business;
 @Mock StaffLeaveMapper leaves;
 @Mock ScheduleExceptionMapper exceptions;
 @InjectMocks ScheduleConstraintService service;
 LocalDateTime start=LocalDate.of(2035,1,2).atTime(10,0);
 @Test void approvedPartialLeaveBlocksOverlapButAllowsExactBoundary(){
  StaffLeave leave=new StaffLeave();leave.setAllDay(false);leave.setStartTime(LocalTime.of(10,30));leave.setEndTime(LocalTime.of(11,30));
  when(leaves.selectList(any(Wrapper.class))).thenReturn(List.of(leave));
  assertFalse(service.isAvailable(1L,start,45));assertTrue(service.isAvailable(1L,start,30));assertTrue(service.isAvailable(1L,start.withHour(11).withMinute(30),45));
 }
 @Test void allDayLeaveBlocksEverything(){
  StaffLeave leave=new StaffLeave();leave.setAllDay(true);when(leaves.selectList(any(Wrapper.class))).thenReturn(List.of(leave));
  assertFalse(service.isAvailable(1L,start,30));
 }
 @Test void storeClosureAndSpecialHoursAreEnforced(){
  BusinessDay day=new BusinessDay();day.setClosed(true);when(business.selectOne(any(Wrapper.class))).thenReturn(day);
  assertFalse(service.isAvailable(1L,start,30));
  day.setClosed(false);day.setOpenTime(LocalTime.of(10,0));day.setCloseTime(LocalTime.of(11,0));
  assertTrue(service.isAvailable(1L,start,60));assertFalse(service.isAvailable(1L,start,90));assertFalse(service.isAvailable(1L,start.minusMinutes(30),30));
 }
 @Test void temporaryExceptionsUseHalfOpenIntervals(){
  ScheduleException item=new ScheduleException();item.setStartTime(LocalTime.of(10,30));item.setEndTime(LocalTime.of(11,0));
  when(exceptions.selectList(any(Wrapper.class))).thenReturn(List.of(item));
  assertFalse(service.isAvailable(1L,start,45));assertTrue(service.isAvailable(1L,start,30));assertTrue(service.isAvailable(1L,start.withHour(11),30));
 }
 @Test void dateLockRequiresActiveTransaction(){assertThrows(IllegalStateException.class,()->service.lockDay(start.toLocalDate()));verifyNoInteractions(locks);}
}
