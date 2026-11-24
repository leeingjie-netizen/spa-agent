package com.lee.spa.commerce;

import com.lee.spa.AcceptanceIsolation;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.service.AppointmentTransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and Spring transactions. Calls DB reservation layer directly;
 * Redisson is unavailable and is never counted as an integrated dependency. */
@SpringBootTest(properties={"spa.payment.mock-enabled=true","spa.auth.dev-login-enabled=true"})
@ActiveProfiles("test") @AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
class AcceptanceBusinessTest {
 @Autowired JdbcTemplate jdbc; @Autowired AppointmentTransactionService booking;
 @Autowired OrderService orders; @Autowired MockMvc mvc; @Autowired AuthService auth;
 long id; LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(30);
 @BeforeEach void fixture() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());
  id=-Math.abs(System.nanoTime());
  jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES(?,'synthetic-acceptance',1)",id);
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'synthetic-acceptance',1)",id);
  jdbc.update("INSERT INTO spa_service(id,name,duration_minutes,price,status) VALUES(?,'synthetic-acceptance',60,100,1)",id);
  jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES(?,?,?)",id,id,id);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
  AuthContext.set(new AuthPrincipal(id,"USER",null));
 }
 @AfterEach void clear(){AuthContext.clear();}
 CreateAppointmentRequest request(){var r=new CreateAppointmentRequest();r.setUserId(id);r.setStaffId(id);r.setServiceId(id);r.setAppointmentDate(date);r.setStartTime(LocalTime.of(10,0));r.setRequestId(UUID.randomUUID().toString());return r;}
 long book(){var r=request();long aid=booking.creatAppointmentInTransaction(r,null,null,null,null).getId();return jdbc.queryForObject("SELECT id FROM spa_order WHERE appointment_id=?",Long.class,aid);}
 long count(String table,long order){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE order_id=?",Long.class,order);}
 @Test void bookingCreatesOneOrderAndCancelReleasesSlots(){long o=book();assertEquals("WAITING_PAYMENT",orders.detail(o).get("status"));orders.cancel(o);orders.cancel(o);assertEquals("CANCELLED",orders.detail(o).get("status"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,id));assertNotEquals(o,book());}
 @Test void repeatedPaymentAndRefundAreIdempotent(){long o=book();orders.pay(o,"MOCK","payment-"+id);orders.pay(o,"MOCK","payment-"+id);assertThrows(BusinessException.class,()->orders.pay(o,"MOCK","another-"+id));orders.cancel(o);orders.cancel(o);assertEquals("REFUNDED",orders.detail(o).get("status"));assertEquals(2,count("spa_payment",o));assertEquals(0,jdbc.queryForObject("SELECT SUM(CASE WHEN kind='PAY' THEN amount ELSE -amount END) FROM spa_payment WHERE order_id=?",java.math.BigDecimal.class,o).signum());}
 @Test void timeoutClosesOnceAndCannotPay(){long o=book();jdbc.update("UPDATE spa_order SET expires_at='2000-01-01' WHERE id=?",o);orders.closeExpired(o);orders.closeExpired(o);assertEquals("CLOSED",orders.detail(o).get("status"));assertThrows(BusinessException.class,()->orders.pay(o,"MOCK","expired-"+id));assertEquals(0,count("spa_payment",o));}
 @Test void cancelledAndRefundedCannotJumpToService(){for(boolean paid:List.of(false,true)){long o=book();if(paid)orders.pay(o,"MOCK","pay-"+o);orders.cancel(o);AuthContext.set(new AuthPrincipal(id,"ADMIN",null));assertThrows(BusinessException.class,()->orders.transition(o,"IN_SERVICE"));}}
 @Test void otherUserCannotReadPayOrCancelOrder(){long o=book();AuthContext.set(new AuthPrincipal(id-1,"USER",null));assertThrows(AuthException.class,()->orders.detail(o));assertThrows(AuthException.class,()->orders.pay(o,"MOCK","forbidden-"+id));assertThrows(AuthException.class,()->orders.cancel(o));assertEquals(0,count("spa_payment",o));}
 @Test void consumerCannotPerformAdministratorTransition(){long o=book();assertThrows(AuthException.class,()->orders.transition(o,"IN_SERVICE"));}
 @Test void pastAndOccupiedAppointmentsAreRejected(){var r=request();r.setAppointmentDate(LocalDate.of(2000,1,1));assertThrows(BusinessException.class,()->booking.creatAppointmentInTransaction(r,null,null,null,null));book();assertThrows(BusinessException.class,()->book());}
 @Test void fiveConcurrentDatabaseReservationsHaveOneWinner() throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(5);var gate=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
  try{for(int i=0;i<5;i++)futures.add(pool.submit(()->{gate.await();try{book();return true;}catch(BusinessException|org.springframework.dao.DuplicateKeyException e){return false;}}));gate.countDown();int winners=0;for(var f:futures)if(f.get(30,TimeUnit.SECONDS))winners++;assertEquals(1,winners);assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,id));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE user_id=?",Integer.class,id));}
  finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void realHttpFilterRejectsAnonymousAndConsumerAdminAccess() throws Exception {
  long order=book();
  long appointment=jdbc.queryForObject("SELECT appointment_id FROM spa_order WHERE id=?",Long.class,order);
  AuthContext.clear();
  mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
  var login=auth.devLogin("synthetic-http-"+(-id));
  mvc.perform(get("/api/orders/"+order).header("Authorization","Bearer "+login.token())).andExpect(status().isForbidden());
  mvc.perform(get("/api/appointments/"+appointment).header("Authorization","Bearer "+login.token())).andExpect(status().isNotFound());
  mvc.perform(get("/api/admin/orders").header("Authorization","Bearer "+login.token())).andExpect(status().isForbidden());
  mvc.perform(post("/api/appointments").header("Authorization","Bearer "+login.token()).contentType("application/json").content("{")).andExpect(status().isBadRequest());
 }
 @Test void serviceCannotCompleteBeforeScheduledEnd(){long o=book();orders.pay(o,"MOCK","early-"+id);AuthContext.set(new AuthPrincipal(id,"ADMIN",null));assertThrows(BusinessException.class,()->orders.transition(o,"IN_SERVICE"));}
 @Test void paymentRequestCannotReplayWithDifferentChannel(){long o=book();orders.pay(o,"MOCK","replay-"+id);assertThrows(BusinessException.class,()->orders.pay(o,"MOCK_WALLET","replay-"+id));}
 @Test void completionCannotPrecedeEndOrBeRepeatedForExtraPoints(){
  long o=book();orders.pay(o,"MOCK","end-"+id);AuthContext.set(new AuthPrincipal(id,"ADMIN",null));
  long aid=((Number)jdbc.queryForObject("SELECT appointment_id FROM spa_order WHERE id=?",Long.class,o)).longValue();
  jdbc.update("UPDATE appointment SET appointment_date=?,start_time='00:00',end_time='23:59:59' WHERE id=?",LocalDate.now(ZoneId.of("Asia/Shanghai")),aid);
  orders.transition(o,"IN_SERVICE");assertThrows(BusinessException.class,()->orders.transition(o,"COMPLETED"));
  jdbc.update("UPDATE appointment SET end_time='00:00' WHERE id=?",aid);orders.transition(o,"COMPLETED");orders.transition(o,"COMPLETED");
  assertEquals(1,count("spa_points_entry",o));assertThrows(BusinessException.class,()->orders.cancel(o));assertThrows(BusinessException.class,()->orders.transition(o,"IN_SERVICE"));
 }
}
