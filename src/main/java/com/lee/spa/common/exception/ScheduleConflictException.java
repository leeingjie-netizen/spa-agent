package com.lee.spa.common.exception;
import java.util.List;
public class ScheduleConflictException extends RuntimeException {
 private final List<Long> appointmentIds;
 public ScheduleConflictException(List<Long> ids) { super("操作与现有预约冲突，请先与用户协商处理预约"); appointmentIds=List.copyOf(ids); }
 public List<Long> getAppointmentIds(){return appointmentIds;}
}
