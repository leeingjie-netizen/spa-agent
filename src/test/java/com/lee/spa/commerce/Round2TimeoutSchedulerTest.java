package com.lee.spa.commerce;
import com.lee.spa.AcceptanceIsolation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual Spring scheduled OrderTimeoutJob; timing override only in isolated test context. */
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
class Round2TimeoutSchedulerTest {
 @Autowired JdbcTemplate jdbc;
 @Test void schedulerAutomaticallyClosesExpiredOrderAndRepeatedTicksDoNotDuplicateEffects() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());long id=-Math.abs(System.nanoTime());
  LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(50);
  jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES(?,'scheduler-synthetic',1)",id);
  jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(?,'scheduler-synthetic',100,60,1)",id);
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'scheduler-synthetic',1)",id);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
  jdbc.update("INSERT INTO appointment(id,appointment_no,user_id,service_id,staff_id,appointment_date,start_time,end_time,amount,status,request_id) VALUES(?,?,?,?,?,?,'10:00','11:00',100,1,?)",id,"scheduler-"+(-id),id,id,id,date,"scheduler-"+(-id));
  jdbc.update("INSERT INTO appointment_slot(id,appointment_id,staff_id,slot_time) VALUES(?,?,?,?)",id,id,id,date.atTime(10,0));
  jdbc.update("INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,expires_at) VALUES(?,?,?,?,100,'2000-01-01')",id,id,"scheduler-"+(-id),id);
  long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
  while(!"CLOSED".equals(jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,id))&&System.nanoTime()<deadline)Thread.sleep(50);
  assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,id));
  Thread.sleep(350);assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,id));
  assertEquals(3,jdbc.queryForObject("SELECT status FROM appointment WHERE id=?",Integer.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE appointment_id=?",Integer.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM spa_payment WHERE order_id=?",Integer.class,id));
 }
}
