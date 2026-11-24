package com.lee.spa.commerce;

import com.fasterxml.jackson.databind.*;
import com.lee.spa.AcceptanceIsolation;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.service.AppointmentTransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual loopback HTTP, authentication, order services and MySQL transactions.
 * Reservation fixture enters the DB transaction layer; does NOT certify Redisson. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spa.payment.mock-enabled=true","spa.auth.dev-login-enabled=true"})
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
class Round2HttpBusinessTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc; @Autowired AppointmentTransactionService booking; @Autowired ObjectMapper json;
 HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 long user,staff,order;String token;LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(40);
 record Reply(int status,JsonNode body){}
 Reply call(String method,String path,String body,String credential) throws Exception {
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
  if(credential!=null)b.header("Authorization","Bearer "+credential);
  if(method.equals("POST"))b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":body));else b.GET();
  var response=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
  return new Reply(response.statusCode(),response.body().startsWith("{")?json.readTree(response.body()):null);
 }
 JsonNode post(String path,String body) throws Exception {var r=call("POST",path,body,token);assertEquals(200,r.status);assertEquals(200,r.body.path("code").asInt(),"Business request must succeed");return r.body.path("data");}
 @BeforeEach void fixture() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());
  staff=-Math.abs(System.nanoTime());
  var login=call("POST","/api/auth/dev-login","{\"identity\":\"round2-http-"+(-staff)+"\"}",null);
  assertEquals(200,login.status);assertEquals(200,login.body.path("code").asInt());
  token=login.body.path("data").path("token").asText();assertFalse(token.isBlank());user=login.body.path("data").path("user").path("id").asLong();assertNotEquals(0,user);
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'round2-http-synthetic',1)",staff);
  jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(?,'round2-http-synthetic',120,60,1)",staff);
  jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES(?,?,?)",staff,staff,staff);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",staff,staff,date);
  var request=new CreateAppointmentRequest();request.setUserId(user);request.setStaffId(staff);request.setServiceId(staff);request.setAppointmentDate(date);request.setStartTime(LocalTime.of(10,0));request.setRequestId(UUID.randomUUID().toString());
  long appointment=booking.creatAppointmentInTransaction(request,null,null,null,null).getId();
  order=jdbc.queryForObject("SELECT id FROM spa_order WHERE appointment_id=?",Long.class,appointment);
  post("/api/loyalty/wallet/topup","{\"amount\":200,\"requestId\":\"topup-"+order+"\"}");
 }
 List<Reply> race(List<String> paths,List<String> bodies) throws Exception {
  var pool=Executors.newFixedThreadPool(paths.size());var gate=new CountDownLatch(1);List<Future<Reply>> futures=new ArrayList<>();
  try {for(int i=0;i<paths.size();i++){int n=i;futures.add(pool.submit(()->{gate.await();return call("POST",paths.get(n),bodies.get(n),token);}));}gate.countDown();List<Reply> replies=new ArrayList<>();for(var f:futures)replies.add(f.get(20,TimeUnit.SECONDS));return replies;}
  finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
 String pay(){return "{\"channel\":\"MOCK_WALLET\",\"requestId\":\"http-pay-"+order+"\"}";}
 long payments(String kind){return jdbc.queryForObject("SELECT COUNT(*) FROM spa_payment WHERE order_id=? AND kind=?",Long.class,order,kind);}
 void balance(String expected){BigDecimal b=jdbc.queryForObject("SELECT balance FROM spa_mock_wallet WHERE user_id=?",BigDecimal.class,user);assertEquals(0,new BigDecimal(expected).compareTo(b));assertEquals(0,b.compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM spa_mock_wallet_ledger WHERE user_id=?",BigDecimal.class,user)));}
 @Test void realSocketStartupStaticAdminAndAuthorization() throws Exception {
  var r=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/admin/index.html")).GET().build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,r.statusCode());assertTrue(r.body().contains("<html"));
  assertEquals(401,call("GET","/api/orders",null,null).status);
  assertEquals(403,call("GET","/api/admin/orders",null,token).status);
  assertEquals("true".equals(System.getenv("SPA_MYSQL_ONLY_TESTS"))?503:200,call("GET","/health/ready",null,null).status);
 }
 @Test void fiveConcurrentPaymentsAndRefundsDebitAndCreditExactlyOnce() throws Exception {
  for(var r:race(Collections.nCopies(5,"/api/orders/"+order+"/pay"),Collections.nCopies(5,pay()))){assertEquals(200,r.status);assertEquals(200,r.body.path("code").asInt());}
  assertEquals(1,payments("PAY"));balance("80");
  for(var r:race(Collections.nCopies(5,"/api/orders/"+order+"/cancel"),Collections.nCopies(5,"{}"))){assertEquals(200,r.status);assertEquals(200,r.body.path("code").asInt());}
  assertEquals(1,payments("REFUND"));balance("200");assertEquals("REFUNDED",jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,order));
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM spa_mock_wallet_ledger WHERE user_id=?",Integer.class,user));
 }
 @Test void concurrentPaymentAndCancellationCannotLeaveLostWalletMoney() throws Exception {
  for(var r:race(List.of("/api/orders/"+order+"/pay","/api/orders/"+order+"/cancel"),List.of(pay(),"{}"))){assertEquals(200,r.status);assertTrue(Set.of(200,500).contains(r.body.path("code").asInt()));}
  String state=jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,order);
  assertTrue(Set.of("CANCELLED","REFUNDED").contains(state));assertEquals(payments("PAY"),payments("REFUND"));balance("200");
  assertEquals(state.equals("REFUNDED")?1:0,payments("PAY"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,staff));
 }
 @Test void couponPointsAndWalletRefundAreConsistentOverRealHttp() throws Exception {
  jdbc.update("INSERT INTO spa_points_entry(id,user_id,order_id,points,reason) VALUES(?,?,?,100,'synthetic round2 credit')",staff,user,staff);
  jdbc.update("INSERT INTO spa_coupon_template(id,title,discount_amount,min_amount,expires_at,remaining) VALUES(?,'synthetic round2',10,50,?,1)",staff,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
  post("/api/loyalty/coupons/"+staff+"/claim","{}");
  long coupon=jdbc.queryForObject("SELECT id FROM spa_user_coupon WHERE user_id=?",Long.class,user);
  var benefits=post("/api/orders/"+order+"/benefits","{\"couponId\":"+coupon+",\"points\":100}");assertEquals(0,new BigDecimal("109").compareTo(benefits.path("amount").decimalValue()));
  post("/api/orders/"+order+"/pay",pay());balance("91");
  post("/api/orders/"+order+"/cancel","{}");balance("200");
  assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM spa_user_coupon WHERE id=?",String.class,coupon));
  assertEquals(0,jdbc.queryForObject("SELECT SUM(points) FROM spa_points_adjustment WHERE order_id=?",Integer.class,order));assertEquals(1,payments("PAY"));assertEquals(1,payments("REFUND"));
 }
}
