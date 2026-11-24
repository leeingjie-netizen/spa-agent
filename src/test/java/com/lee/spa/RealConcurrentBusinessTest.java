package com.lee.spa;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.*;
import com.lee.spa.dto.*;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.AppointmentSlotMapper;
import com.lee.spa.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Real Spring transactions, MySQL and Redisson. Only the slot-failure test injects a fault. */
@SpringBootTest(properties={"spring.ai.openai.api-key=local-test-placeholder","spa.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named="SPA_REAL_CONCURRENCY_TESTS",matches="true")
class RealConcurrentBusinessTest {
 @Autowired DataSource dataSource;
 @Autowired JdbcTemplate jdbc;
 @Autowired TransactionTemplate transaction;
 @Autowired AppointmentService appointments;
 @Autowired org.redisson.api.RedissonClient redis;
 @Autowired ScheduleManagementService management;
 @SpyBean AppointmentSlotMapper slots;
 private static final AtomicLong IDS=new AtomicLong(-Math.abs(System.nanoTime()));
 private Long staffId,userId,serviceId,relationId,scheduleId;
 private LocalDate date;
 private boolean fixtures;
 private boolean dayLockExisted;
 private static long id(){return IDS.decrementAndGet();}
 @BeforeEach void createFixtures() throws Exception {
  try(var c=dataSource.getConnection()){
   String url=c.getMetaData().getURL();
   com.lee.spa.AcceptanceIsolation.verify(dataSource);
  }
  RedisTestSupport.real(redis);
  staffId=id();userId=id();serviceId=id();relationId=id();scheduleId=id();
  date=LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE).plusDays(180);
  while(jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM staff_schedule WHERE work_date=?) + (SELECT COUNT(*) FROM appointment WHERE appointment_date=?) + (SELECT COUNT(*) FROM business_day WHERE work_date=?)",Integer.class,date,date,date)>0) date=date.plusDays(1);
  dayLockExisted=jdbc.queryForObject("SELECT COUNT(*) FROM schedule_day_lock WHERE work_date=?",Integer.class,date)>0;
  transaction.execute(status->{
   jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES (?,'concurrency-fixture',1)",userId);
   jdbc.update("INSERT INTO staff(id,name,status) VALUES (?,'concurrency-fixture',1)",staffId);
   jdbc.update("INSERT INTO spa_service(id,name,duration_minutes,price,status) VALUES (?,'concurrency-fixture',45,1.00,1)",serviceId);
   jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES (?,?,?)",relationId,staffId,serviceId);
   jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES (?,?,?,'09:00','18:00',1,'MANUAL')",scheduleId,staffId,date);
   return null;
  });fixtures=true;
 }
 @AfterEach void cleanupFixtures(){
  AuthContext.clear();
  if(!fixtures)return;
  transaction.execute(status->{
   cleanupCommerce();
   jdbc.update("DELETE FROM appointment_slot WHERE staff_id=?",staffId);
   jdbc.update("DELETE FROM appointment WHERE staff_id=?",staffId);
   jdbc.update("DELETE FROM staff_schedule WHERE staff_id=?",staffId);
   jdbc.update("DELETE FROM staff_service WHERE id=?",relationId);
   jdbc.update("DELETE FROM spa_service WHERE id=?",serviceId);
   jdbc.update("DELETE FROM staff WHERE id=?",staffId);
   jdbc.update("DELETE FROM spa_user WHERE id=?",userId);
   jdbc.update("DELETE FROM business_day WHERE work_date=?",date);
   if(!dayLockExisted)jdbc.update("DELETE FROM schedule_day_lock WHERE work_date=?",date);
   return null;
  });
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,staffId));
 }
 private CreateAppointmentRequest request(String requestId){
  CreateAppointmentRequest req=new CreateAppointmentRequest();req.setUserId(userId);req.setServiceId(serviceId);req.setStaffId(staffId);req.setAppointmentDate(date);req.setStartTime(LocalTime.of(10,0));req.setRequestId(requestId);req.setRemark("concurrency-fixture");return req;
 }
 private Appointment book(String requestId){
  AuthContext.set(new AuthPrincipal(userId,"USER",null));
  try{return appointments.createAppointment(request(requestId));}finally{AuthContext.clear();}
 }
 @Test void fiveContendersYieldExactlyOneBookingAndTwoSlots() throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(5);CountDownLatch gate=new CountDownLatch(1);
  List<Future<Boolean>> futures=new ArrayList<>();
  try{
   for(int i=0;i<5;i++){String requestId=UUID.randomUUID().toString();futures.add(pool.submit(()->{gate.await();try{book(requestId);return true;}catch(BusinessException|org.springframework.dao.DuplicateKeyException expected){return false;}}));}
   gate.countDown();int successes=0;for(Future<Boolean> f:futures)if(f.get(30,TimeUnit.SECONDS))successes++;
   assertEquals(1,successes);assertEquals(1,bookingCount());assertEquals(2,slotCount());assertCommerceCount(1);
  }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
 }
 @Test void retryReturnsSameAppointmentWithoutDuplicateOrderOrSlot(){
  String requestId=UUID.randomUUID().toString();Appointment first=book(requestId);Appointment retried=book(requestId);
  assertEquals(first.getId(),retried.getId());assertEquals(1,bookingCount());assertEquals(2,slotCount());assertCommerceCount(1);
 }
 @Test void slotInsertFailureRollsBackAppointmentAndCommerce(){
  doThrow(new IllegalStateException("intentional-slot-failure")).when(slots).insert(argThat((AppointmentSlot slot)->Objects.equals(staffId,slot.getStaffId())));
  assertThrows(IllegalStateException.class,()->book(UUID.randomUUID().toString()));
  assertEquals(0,bookingCount());assertEquals(0,slotCount());assertCommerceCount(0);
 }
 @Test void closureAndBookingRaceNeverProducesClosedStoreWithBooking() throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
  try{
   Future<Boolean> booked=pool.submit(()->{gate.await();try{book(UUID.randomUUID().toString());return true;}catch(BusinessException expected){return false;}});
   Future<Boolean> closed=pool.submit(()->{gate.await();AuthContext.set(new AuthPrincipal(userId,"ADMIN",null));try{management.businessDay(date,new ScheduleManagementRequests.Business(true,null,null,"concurrency-fixture"));return true;}catch(ScheduleConflictException expected){return false;}finally{AuthContext.clear();}});
   gate.countDown();boolean bookingWon=booked.get(30,TimeUnit.SECONDS),closureWon=closed.get(30,TimeUnit.SECONDS);
   assertNotEquals(bookingWon,closureWon,"One side must succeed and the conflicting side must fail");
   int closures=jdbc.queryForObject("SELECT COUNT(*) FROM business_day WHERE work_date=? AND closed=1",Integer.class,date);
   assertFalse(closures>0&&bookingCount()>0);assertEquals(bookingWon?1:0,bookingCount());
  }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
 }
 private int bookingCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,staffId);}
 private int slotCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,staffId);}
 private void assertCommerceCount(int expected){
  assertEquals(expected,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE appointment_id IN (SELECT id FROM appointment WHERE staff_id=?)",Integer.class,staffId));
  // Owner/user fixture is unique even if an accidental orphan order were created.
  assertEquals(expected,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE user_id=?",Integer.class,userId));
 }
 private void cleanupCommerce(){
  for(String table:List.of("spa_payment","spa_review","spa_points_entry")) {
   jdbc.update("DELETE FROM "+table+" WHERE order_id IN (SELECT id FROM spa_order WHERE user_id=?)",userId);
  }
  jdbc.update("DELETE FROM spa_order WHERE user_id=?",userId);
 }
}
