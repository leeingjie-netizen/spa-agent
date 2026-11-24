package com.lee.spa.commerce;
import com.lee.spa.auth.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.ai.openai.api-key=local-test-placeholder","spa.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
@Transactional
class AnalyticsIntegrationTest {
 @Autowired AnalyticsController analytics;@Autowired JdbcTemplate jdbc;
 @AfterEach void clear(){AuthContext.clear();}
 @Test void successfulRefundsAreNetAndTopupsNeverBecomeRevenue(){
  try { com.lee.spa.AcceptanceIsolation.verify(jdbc.getDataSource()); } catch(Exception e) { throw new IllegalStateException(e); }
  long id=-Math.abs(System.nanoTime());
  AuthContext.set(new AuthPrincipal(id,"ADMIN",null));
  jdbc.update("INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,status,created_at,expires_at) VALUES(?,?,?,?,130,'WAITING_SERVICE','2096-01-05','2096-01-06')",id,id,"analytics-"+(-id),id);
  jdbc.update("INSERT INTO spa_payment(id,order_id,request_id,kind,channel,amount,status,created_at) VALUES(?,?,?,'PAY','MOCK_WALLET',130,'SIMULATED_SUCCESS','2096-01-05')",id,id,"analytics-pay-"+(-id));
  jdbc.update("INSERT INTO spa_payment(id,order_id,request_id,kind,channel,amount,status,created_at) VALUES(?,?,?,'REFUND','MOCK_WALLET',30,'SIMULATED_SUCCESS','2096-01-05')",id-1,id,"analytics-refund-"+(-id));
  jdbc.update("INSERT INTO spa_mock_wallet_ledger(id,user_id,request_id,amount,balance_after,kind,created_at) VALUES(?,?,?,200,200,'TEST_TOPUP','2096-01-05')",id,id,"analytics-topup-"+(-id));
  Map<?,?> report=(Map<?,?>)analytics.dashboard(LocalDate.of(2096,1,5),LocalDate.of(2096,1,5),"SIMULATED",1,10).getData();
  assertEquals("100.00",((Map<?,?>)((List<?>)report.get("daily")).get(0)).get("net"));
  assertEquals("30.00",((Map<?,?>)report.get("summary")).get("refundRange"));
  assertEquals(2L,((Map<?,?>)report.get("ledger")).get("total"));
  var real=(Map<?,?>)analytics.dashboard(LocalDate.of(2096,1,5),LocalDate.of(2096,1,5),"REAL",1,10).getData();
  assertEquals("0.00",((Map<?,?>)((List<?>)real.get("daily")).get(0)).get("net"));
  var page=(Map<?,?>)analytics.dashboard(LocalDate.of(2096,1,5),LocalDate.of(2096,1,5),"SIMULATED",2,1).getData();
  assertEquals(1,((List<?>)((Map<?,?>)page.get("ledger")).get("items")).size());
 }
 @Test void nonAdministratorsCannotReadBusinessFigures(){
  for(String role:List.of("USER","STAFF")){AuthContext.set(new AuthPrincipal(1L,role,1L));assertThrows(AuthException.class,()->analytics.dashboard(null,null,"REAL",1,10));}
 }
}
