package com.lee.spa;
import com.lee.spa.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import com.lee.spa.common.exception.ScheduleConflictException;
import com.lee.spa.controller.ScheduleConflictHandler;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class SafeErrorResponseTest {
 @RestController static class ConflictEndpoint {
  @GetMapping("/test-conflict") void conflict(){throw new ScheduleConflictException(List.of(42L));}
 }
 @Test void businessConflictKeeps409WhenGenericSafetyAdviceIsRegistered() throws Exception {
  MockMvcBuilders.standaloneSetup(new ConflictEndpoint())
   .setControllerAdvice(new GlobalExceptionHandler(),new ScheduleConflictHandler()).build()
   .perform(get("/test-conflict")).andExpect(status().isConflict())
   .andExpect(jsonPath("$.data.appointmentIds[0]").value(42));
 }
 @Test void internalSqlDetailsAreNeverReturnedToTheClient(){
  var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
  var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
  try {
  var response=new GlobalExceptionHandler().unexpected(new IllegalStateException("jdbc:mysql://internal password=secret SELECT * FROM spa_account"));
  assertEquals(500,response.getStatusCode().value());
  assertNotNull(response.getBody());
  assertFalse(response.getBody().getMessage().contains("secret"));
  assertFalse(response.getBody().getMessage().contains("SELECT"));
  assertNull(response.getBody().getData());
  assertFalse(appender.list.isEmpty());
  assertTrue(appender.list.stream().allMatch(e->!e.getFormattedMessage().contains("secret")&&e.getThrowableProxy()==null));
  }finally{logger.detachAppender(appender);appender.stop();}
 }
}
