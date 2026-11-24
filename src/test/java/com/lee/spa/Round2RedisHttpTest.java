package com.lee.spa;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.redisson.api.RedissonClient;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete socket -> auth -> AppointmentService -> Redisson -> transaction -> MySQL. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spa.auth.dev-login-enabled=true"})
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="SPA_REAL_CONCURRENCY_TESTS",matches="true")
class Round2RedisHttpTest {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired RedissonClient redis;
 HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 JsonNode post(String path,String body,String token) throws Exception {
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");
  if(token!=null)b.header("Authorization","Bearer "+token);
  var r=client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());assertTrue(Set.of(200,409).contains(r.statusCode()));return json.readTree(r.body());
 }
 @Test void fiveAuthenticatedSocketBookingsHaveOneWinnerAndNoOrphanOrders() throws Exception {
  AcceptanceIsolation.verify(jdbc.getDataSource());RedisTestSupport.real(redis);long id=-Math.abs(System.nanoTime());LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(210);
  var login=post("/api/auth/dev-login","{\"identity\":\"r2-full-http-"+(-id)+"\"}",null);assertEquals(200,login.path("code").asInt());String token=login.path("data").path("token").asText();long user=login.path("data").path("user").path("id").asLong();
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,'r2-full-http-synthetic',1)",id);
  jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES(?,'r2-full-http-synthetic',100,60,1)",id);
  jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES(?,?,?)",id,id,id);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
  var pool=Executors.newFixedThreadPool(5);var gate=new CountDownLatch(1);List<Future<JsonNode>> futures=new ArrayList<>();
  try{for(int i=0;i<5;i++)futures.add(pool.submit(()->{gate.await();return post("/api/appointments","{\"serviceId\":"+id+",\"staffId\":"+id+",\"appointmentDate\":\""+date+"\",\"startTime\":\"10:00:00\",\"requestId\":\""+UUID.randomUUID()+"\"}",token);}));gate.countDown();int successes=0;for(var f:futures){int code=f.get(20,TimeUnit.SECONDS).path("code").asInt();if(code==200)successes++;else assertTrue(Set.of(409,500).contains(code));}assertEquals(1,successes);}
  finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,id));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slot WHERE staff_id=?",Integer.class,id));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE user_id=?",Integer.class,user));
  assertFalse(redis.getLock("spa:appointment:lock:"+id+":"+date).isLocked());
 }
}
