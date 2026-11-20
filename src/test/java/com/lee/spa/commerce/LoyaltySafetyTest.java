package com.lee.spa.commerce;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class LoyaltySafetyTest {
 JdbcTemplate jdbc;MockEnvironment environment;LoyaltyService service;
 @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);environment=new MockEnvironment().withProperty("spa.payment.mock-enabled","true");environment.setActiveProfiles("local");service=new LoyaltyService(jdbc,environment);AuthContext.set(new AuthPrincipal(11L,"USER",null));}
 @AfterEach void clear(){AuthContext.clear();}
 @Test void productionCannotTopupEvenWithFlag(){environment.setActiveProfiles("local","production");assertThrows(AuthException.class,()->service.topup(new BigDecimal("10"),"request-123"));verifyNoInteractions(jdbc);}
 @Test void rejectsNegativeOrSubCentTopups(){assertThrows(BusinessException.class,()->service.topup(new BigDecimal("-1"),"request-123"));assertThrows(BusinessException.class,()->service.topup(new BigDecimal("1.001"),"request-123"));verifyNoInteractions(jdbc);}
 @Test void cannotReuseTopupRequestForDifferentAmount(){when(jdbc.queryForList(contains("FROM spa_mock_wallet_ledger"),eq(11L),eq("topup:request-123"))).thenReturn(List.of(Map.of("amount",new BigDecimal("20"),"kind","TEST_TOPUP")));assertThrows(BusinessException.class,()->service.topup(new BigDecimal("10"),"request-123"));verify(jdbc,never()).update(startsWith("UPDATE spa_mock_wallet"),any(),any());}
 @Test void cannotConfigureCouponsAsUser(){assertThrows(AuthException.class,()->service.createTemplate(new LoyaltyService.Template("a",BigDecimal.ONE,BigDecimal.ZERO,java.time.LocalDateTime.now().plusDays(1),10)));verifyNoInteractions(jdbc);}
 @Test void cannotApplyBenefitsAfterPayment(){var order=new HashMap<String,Object>();order.put("user_id",11L);order.put("id",1L);order.put("status","WAITING_SERVICE");assertThrows(BusinessException.class,()->service.apply(order,null,0));verify(jdbc,never()).update(startsWith("UPDATE spa_order SET amount"),any(),any());}
}
