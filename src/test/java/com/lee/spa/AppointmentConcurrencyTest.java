package com.lee.spa;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.service.AppointmentService;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
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
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_LEGACY_INTEGRATION_TESTS",matches="true")
class AppointmentConcurrencyTest {
 @Autowired AppointmentService appointments; @Autowired JdbcTemplate jdbc; @Autowired RedissonClient redis;
 @Test void testConcurrentAppointment() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());RedisTestSupport.real(redis);
  long id=-Math.abs(System.nanoTime());LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(200);
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'round2-legacy-synthetic',1)",id);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
  for(int i=1;i<=2;i++){
   jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES(?,'round2-legacy-synthetic',1)",id-i);
   jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(?,'round2-legacy-synthetic',100,60,1)",id-i);
   jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES(?,?,?)",id-i,id,id-i);
  }
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
  try{
   for(int i=1;i<=2;i++){long user=id-i;futures.add(pool.submit(()->{
    var r=new CreateAppointmentRequest();r.setUserId(user);r.setServiceId(user);r.setStaffId(id);r.setAppointmentDate(date);r.setStartTime(LocalTime.of(10,0));r.setRequestId(UUID.randomUUID().toString());
    gate.await();AuthContext.set(new AuthPrincipal(user,"USER",null));try{appointments.createAppointment(r);return true;}catch(BusinessException|org.springframework.dao.DuplicateKeyException expected){return false;}finally{AuthContext.clear();}
   }));}
   gate.countDown();int successes=0;for(var f:futures)if(f.get(15,TimeUnit.SECONDS))successes++;
   assertEquals(1,successes);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,id));
   assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,id));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE user_id IN (?,?)",Integer.class,id-1,id-2));
  }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
}
