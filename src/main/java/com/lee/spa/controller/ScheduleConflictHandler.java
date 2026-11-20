package com.lee.spa.controller;
import com.lee.spa.common.exception.ScheduleConflictException;
import com.lee.spa.common.result.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ScheduleConflictHandler {
 @ExceptionHandler(ScheduleConflictException.class)
 public ResponseEntity<Result<?>> conflict(ScheduleConflictException e) {
  return ResponseEntity.status(409).body(new Result<>(409,e.getMessage(),Map.of("appointmentIds",e.getAppointmentIds())));
 }
}
