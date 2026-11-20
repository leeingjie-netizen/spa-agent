package com.lee.spa.commerce;

import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/spa?serverTimezone=Asia/Shanghai",
 "spring.datasource.username=root","spring.datasource.password=${MYSQL_PWD:}",
 "spring.ai.openai.api-key=local-integration-placeholder",
 "spa.payment.mock-enabled=true","spa.scheduling.enabled=false","spa.orders.timeout-enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
@Transactional @Rollback
class LoyaltyBusinessIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired LoyaltyService loyalty;
 @Autowired OrderService orders;
 @Autowired OperationsController operations;
 long fixture;String request;
 @BeforeEach void fixtures(){
  assertEquals("spa",jdbc.queryForObject("SELECT DATABASE()",String.class));
  fixture=-ThreadLocalRandom.current().nextLong(1000000000L,900000000000L);request="loyalty-it-"+(-fixture);
  jdbc.update("INSERT INTO spa_user(id,nickname,status) VALUES(?,?,1)",fixture,"loyalty-integration");
  jdbc.update("INSERT INTO staff(id,name,status) VALUES(?,?,1)",fixture,"loyalty-integration");
  jdbc.update("INSERT INTO spa_service(id,name,duration_minutes,price,status) VALUES(?,?,60,100,1)",fixture,"loyalty-integration");
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES(?,?,'2097-01-15','09:00','18:00',1,'MANUAL')",fixture,fixture);
  jdbc.update("INSERT INTO appointment(id,appointment_no,user_id,service_id,staff_id,appointment_date,start_time,end_time,amount,status,request_id) VALUES(?,?,?,?,?,'2097-01-15','10:00','11:00',100,1,?)",fixture,request,fixture,fixture,fixture,request);
  jdbc.update("INSERT INTO appointment_slot(id,appointment_id,staff_id,slot_time) VALUES(?,?,?,'2097-01-15 10:00:00')",fixture,fixture,fixture);
  jdbc.update("INSERT INTO spa_order(id,appointment_id,order_no,user_id,amount,expires_at) VALUES(?,?,?,?,100,?)",fixture,fixture,request,fixture,LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
  jdbc.update("INSERT INTO spa_coupon_template(id,title,discount_amount,min_amount,expires_at,remaining) VALUES(?,?,10,50,?,1)",fixture,"loyalty-integration",LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
  AuthContext.set(new AuthPrincipal(fixture,"USER",null));
 }
 @AfterEach void clear(){AuthContext.clear();}
 private long coupon(){return jdbc.queryForObject("SELECT id FROM spa_user_coupon WHERE user_id=? AND template_id=?",Long.class,fixture,fixture);}
 private String state(){return jdbc.queryForObject("SELECT status FROM spa_order WHERE id=?",String.class,fixture);}
 private long count(String sql){return jdbc.queryForObject(sql,Long.class,fixture);}
 private BigDecimal balance(){return (BigDecimal)loyalty.summary().get("walletBalance");}

 @Test void couponStockAndPerUserClaimAreEnforced(){
  loyalty.claim(fixture);
  assertEquals(0,count("SELECT remaining FROM spa_coupon_template WHERE id=?"));
  assertThrows(BusinessException.class,()->loyalty.claim(fixture));
  assertEquals(1,count("SELECT COUNT(*) FROM spa_user_coupon WHERE user_id=?"));
 }
 @Test void couponConsumedByPaymentAndReleasedByRefund(){
  assertEquals(0,new BigDecimal("100").compareTo((BigDecimal)orders.detail(fixture).get("originalAmount")));
  assertEquals(0,BigDecimal.ZERO.compareTo((BigDecimal)orders.detail(fixture).get("discountAmount")));
  loyalty.claim(fixture);long coupon=coupon();
  orders.applyBenefits(fixture,coupon,0);
  assertEquals("RESERVED",jdbc.queryForObject("SELECT status FROM spa_user_coupon WHERE id=?",String.class,coupon));
  assertEquals(0,new BigDecimal("90").compareTo((BigDecimal)orders.detail(fixture).get("amount")));
  assertEquals(0,new BigDecimal("10").compareTo((BigDecimal)orders.detail(fixture).get("discountAmount")));
  orders.pay(fixture,"MOCK",request);
  assertEquals("USED",jdbc.queryForObject("SELECT status FROM spa_user_coupon WHERE id=?",String.class,coupon));
  orders.cancel(fixture);
  assertEquals("REFUNDED",state());
  assertEquals(0,new BigDecimal("100").compareTo((BigDecimal)orders.detail(fixture).get("originalAmount")));
  assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM spa_user_coupon WHERE id=?",String.class,coupon));
  assertEquals(0,count("SELECT COUNT(*) FROM appointment_slot WHERE appointment_id=?"));
 }
 @Test void pointReservationAndCancellationBalanceTheLedger(){
  jdbc.update("INSERT INTO spa_points_entry(id,user_id,order_id,points,reason) VALUES(?,?,?,100,'integration fixture')",fixture,fixture,fixture);
  orders.applyBenefits(fixture,null,50);
  assertEquals(50,Integer.parseInt(orders.detail(fixture).get("pointsUsed").toString()));
  assertEquals(50,((Number)loyalty.summary().get("points")).longValue());
  orders.cancel(fixture);
  assertEquals(100,((Number)loyalty.summary().get("points")).longValue());
  assertEquals(2,count("SELECT COUNT(*) FROM spa_points_adjustment WHERE order_id=?"));
 }
 @Test void mockWalletTopupPaymentReplayAndRefundHaveSingleLedgerRows(){
  loyalty.topup(new BigDecimal("200"),request);loyalty.topup(new BigDecimal("200"),request);
  assertEquals(0,new BigDecimal("200").compareTo(balance()));
  orders.pay(fixture,"MOCK_WALLET",request+"-pay");orders.pay(fixture,"MOCK_WALLET",request+"-pay");
  assertEquals(0,new BigDecimal("100").compareTo(balance()));
  orders.cancel(fixture);orders.cancel(fixture);
  assertEquals(0,new BigDecimal("200").compareTo(balance()));
  assertEquals(3,count("SELECT COUNT(*) FROM spa_mock_wallet_ledger WHERE user_id=?"));
  assertEquals(2,count("SELECT COUNT(*) FROM spa_payment WHERE order_id=?"));
 }
 @Test void insufficientWalletCannotCreatePaymentOrChangeOrder(){
  assertThrows(BusinessException.class,()->orders.pay(fixture,"MOCK_WALLET",request));
  assertEquals("WAITING_PAYMENT",state());
  assertEquals(0,count("SELECT COUNT(*) FROM spa_payment WHERE order_id=?"));
  assertEquals(0,count("SELECT COUNT(*) FROM spa_mock_wallet_ledger WHERE user_id=?"));
  assertEquals(0,BigDecimal.ZERO.compareTo(balance()));
 }
 @Test void completionAwardsPointsOnceAndReviewRequiresOwnerAndCompletion(){
  assertThrows(BusinessException.class,()->operations.review(fixture,new OperationsController.Review(5,"not completed")));
  orders.pay(fixture,"MOCK",request);
  AuthContext.set(new AuthPrincipal(fixture,"ADMIN",null));
  orders.transition(fixture,"IN_SERVICE");orders.transition(fixture,"COMPLETED");orders.transition(fixture,"COMPLETED");
  assertEquals(1,count("SELECT COUNT(*) FROM spa_points_entry WHERE order_id=?"));
  AuthContext.set(new AuthPrincipal(fixture-1,"USER",null));
  assertThrows(BusinessException.class,()->operations.review(fixture,new OperationsController.Review(5,"wrong owner")));
  AuthContext.set(new AuthPrincipal(fixture,"USER",null));
  operations.review(fixture,new OperationsController.Review(5,"真实集成测试评价，事务回滚"));
  assertEquals(1,count("SELECT COUNT(*) FROM spa_review WHERE order_id=?"));
  assertThrows(BusinessException.class,()->operations.review(fixture,new OperationsController.Review(4,"repeat")));
 }
 @Test void expiredCouponCannotBeApplied(){
  loyalty.claim(fixture);long coupon=coupon();
  jdbc.update("UPDATE spa_coupon_template SET expires_at=? WHERE id=?",LocalDateTime.now().minusDays(1),fixture);
  assertThrows(BusinessException.class,()->orders.applyBenefits(fixture,coupon,0));
  assertEquals("WAITING_PAYMENT",state());
  assertEquals(0,count("SELECT COUNT(*) FROM spa_order_benefit WHERE order_id=?"));
 }
}
