package com.lee.spa.dto;
import java.time.*;
public final class ScheduleManagementRequests {
 private ScheduleManagementRequests() {}
 public record Manual(Integer status,LocalTime startTime,LocalTime endTime,LocalTime breakStartTime,LocalTime breakEndTime,Boolean confirmManual,String reason) {}
 public record ExceptionRequest(Long staffId,LocalDate date,LocalTime startTime,LocalTime endTime,String reason) {}
 public record Business(Boolean closed,LocalTime openTime,LocalTime closeTime,String reason) {}
 public record Leave(Long staffId,LocalDate date,Boolean allDay,LocalTime startTime,LocalTime endTime,String reason) {}
 public record Review(Boolean approved,String reason) {}
 public record Generate(Long staffId,LocalDate from,Integer days) {}
}
