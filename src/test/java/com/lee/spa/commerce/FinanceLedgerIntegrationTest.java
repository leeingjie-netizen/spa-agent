package com.lee.spa.commerce;
import com.lee.spa.auth.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.ai.openai.api-key=local-test-placeholder","spa.scheduling.enabled=false","spa.orders.timeout-enabled=false"})
@EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
@Transactional
class FinanceLedgerIntegrationTest {
 @Autowired FinanceLedgerController finance;@Autowired AnalyticsController analytics;@Autowired LoyaltyService loyalty;@Autowired JdbcTemplate jdbc;
 private static final LocalDate DATE=LocalDate.of(2095,2,5);
 @AfterEach void clear(){AuthContext.clear();}
 @Test void allLedgerSourcesPaginateAndTopupsRemainSeparateFromRevenue(){
  assertEquals("spa",jdbc.queryForObject("SELECT DATABASE()",String.class));
  long id=-Math.abs(System.nanoTime());AuthContext.set(new AuthPrincipal(id,"ADMIN",null));
  jdbc.update("INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,status,created_at,expires_at) VALUES(?,?,?,?,130,'WAITING_SERVICE','2095-02-05','2095-02-06')",id,id,"finance-"+(-id),id);
  for(var payment:List.of(new Object[]{id,"PAY",130},new Object[]{id-1,"REFUND",30}))jdbc.update("INSERT INTO spa_payment(id,order_id,request_id,kind,channel,amount,status,created_at) VALUES(?,?,?,?,'MOCK_WALLET',?,'SIMULATED_SUCCESS','2095-02-05')",payment[0],id,"finance-"+payment[0],payment[1],payment[2]);
  for(var wallet:List.of(new Object[]{id,"TEST_TOPUP",200,"topup:fixture"},new Object[]{id-1,"PAYMENT",-130,"pay:"+id},new Object[]{id-2,"REFUND",30,"refund:"+id}))jdbc.update("INSERT INTO spa_mock_wallet_ledger(id,user_id,request_id,amount,balance_after,kind,created_at) VALUES(?,?,?,?,100,?,'2095-02-05')",wallet[0],id,wallet[3],wallet[2],wallet[1]);
  Map<?,?> all=(Map<?,?>)finance.ledger(DATE,DATE,"SIMULATED","ALL","ALL",1,10).getData();assertEquals(5L,all.get("total"));
  var items=(List<Map<String,Object>>)all.get("items");assertEquals(1,items.stream().filter(r->r.get("kind").equals("TOPUP")).count());
  assertEquals("200.00",items.stream().filter(r->r.get("kind").equals("TOPUP")).findFirst().orElseThrow().get("amount"));
  assertTrue(items.stream().filter(r->r.get("kind").equals("WALLET_DEBIT")).allMatch(r->String.valueOf(id).equals(r.get("orderId"))));
  assertEquals(3L,((Map<?,?>)finance.ledger(DATE,DATE,"SIMULATED","WALLET","ALL",1,10).getData()).get("total"));
  assertEquals(1,((List<?>)((Map<?,?>)finance.ledger(DATE,DATE,"SIMULATED","ALL","ALL",3,2).getData()).get("items")).size());
  assertEquals(0L,((Map<?,?>)finance.ledger(DATE,DATE,"REAL","ALL","ALL",1,10).getData()).get("total"));
  var revenue=(Map<?,?>)analytics.dashboard(DATE,DATE,"SIMULATED",1,10).getData();assertEquals("100.00",((Map<?,?>)((List<?>)revenue.get("daily")).get(0)).get("net"));
 }
 @Test void consumersAndStaffCannotReadAnyFinancialLedger(){
  for(String role:List.of("USER","STAFF")){
   AuthContext.set(new AuthPrincipal(1L,role,1L));
   assertThrows(AuthException.class,()->finance.ledger(DATE,DATE,"SIMULATED","ALL","ALL",1,10));
   assertThrows(AuthException.class,loyalty::ledger);
  }
 }
}
