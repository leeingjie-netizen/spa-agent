package com.lee.spa;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.redisson.api.RedissonClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_REAL_CONCURRENCY_TESTS",matches="true")
class Round3LockFailureTest {
 @Autowired JdbcTemplate jdbc;@Autowired AppointmentService appointments;@Autowired RedissonClient redis;
 @Test void realLockTimeoutWritesNothingAndBookingRecoversAfterUnlock() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());RedisTestSupport.real(redis);
  long id=-Math.abs(System.nanoTime());LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(240);
  jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES(?,'round3-synthetic',1)",id);
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'round3-synthetic',1)",id);
  jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(?,'round3-synthetic',100,60,1)",id);
  jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES(?,?,?)",id,id,id);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
  var request=new CreateAppointmentRequest();request.setUserId(id);request.setStaffId(id);request.setServiceId(id);request.setAppointmentDate(date);request.setStartTime(LocalTime.of(10,0));request.setRequestId(UUID.randomUUID().toString());
  var lock=redis.getLock("spa:appointment:lock:"+id+":"+date);assertTrue(lock.tryLock(1,20,TimeUnit.SECONDS));var pool=Executors.newSingleThreadExecutor();
  try{pool.submit(()->{AuthContext.set(new AuthPrincipal(id,"USER",null));try{assertThrows(BusinessException.class,()->appointments.createAppointment(request));}finally{AuthContext.clear();}}).get(10,TimeUnit.SECONDS);
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE user_id=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,id));
  }finally{if(lock.isHeldByCurrentThread())lock.unlock();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
  AuthContext.set(new AuthPrincipal(id,"USER",null));try{assertNotNull(appointments.createAppointment(request));}finally{AuthContext.clear();}assertFalse(lock.isLocked());
 }
}
