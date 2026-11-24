package com.lee.spa.commerce;

import com.lee.spa.auth.*;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.service.ScheduleConstraintService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    JdbcTemplate jdbc;MockEnvironment environment;OrderService orders;Map<String,Object> row;
    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);environment=new MockEnvironment().withProperty("spa.payment.mock-enabled","true");environment.setActiveProfiles("local");
        orders=new OrderService(jdbc,environment,mock(ScheduleConstraintService.class),mock(LoyaltyService.class));
        AuthContext.set(new AuthPrincipal(11L,"USER",null));
        row=new HashMap<>();row.put("id",1L);row.put("user_id",11L);row.put("appointment_id",2L);row.put("amount",new BigDecimal("200.00"));
        row.put("status","WAITING_PAYMENT");row.put("payment_status","UNPAID");row.put("expires_at",LocalDateTime.now().plusDays(1));
        row.put("appointment_date",LocalDate.now().plusDays(1));row.put("start_time",LocalTime.NOON);
    }
    @AfterEach void clear(){AuthContext.clear();}
    private void lockFixture() {
        when(jdbc.queryForList(startsWith("SELECT a.staff_id"),eq(1L))).thenReturn(List.of(Map.of("staff_id",3L,"appointment_date",LocalDate.now().plusDays(1))));
        when(jdbc.queryForMap(contains("FOR UPDATE"),eq(1L))).thenAnswer(i->new HashMap<>(row));
        when(jdbc.queryForList(startsWith("SELECT o.id"),eq(1L))).thenAnswer(i->{var view=new HashMap<>(row);view.put("userId",11L);return List.of(view);});
    }
    @Test void productionCannotEnableMockPaymentByFlagAlone(){
        environment.setActiveProfiles("local","production");
        assertThrows(AuthException.class,()->orders.pay(1L,"MOCK","request-123"));verifyNoInteractions(jdbc);
    }
    @Test void realPaymentFailsExplicitly(){assertThrows(BusinessException.class,()->orders.pay(1L,"WECHAT","request-123"));verifyNoInteractions(jdbc);}
    @Test void usersCannotTransitionOrders(){assertThrows(AuthException.class,()->orders.transition(1,"COMPLETED"));verifyNoInteractions(jdbc);}
    @Test void cannotReadOtherUsersOrder(){
        when(jdbc.queryForList(startsWith("SELECT o.id"),eq(1L))).thenReturn(List.of(new HashMap<>(Map.of("userId",22L))));
        assertThrows(AuthException.class,()->orders.detail(1L));
    }
    @Test void canceledOrderCannotBePaid(){lockFixture();row.put("status","CANCELLED");assertThrows(BusinessException.class,()->orders.pay(1,"MOCK","request-123"));verify(jdbc,never()).update(startsWith("INSERT INTO spa_payment"),any(Object[].class));}
    @Test void illegalStatusJumpDoesNotWrite(){lockFixture();AuthContext.set(new AuthPrincipal(99L,"ADMIN",null));assertThrows(BusinessException.class,()->orders.transition(1,"COMPLETED"));verify(jdbc,never()).update(startsWith("UPDATE spa_order"),any(Object[].class));}
    @Test void completedTransitionCreditsPointsOnce(){
        row.put("appointment_date",LocalDate.now());row.put("end_time",LocalTime.MIDNIGHT);
        lockFixture();AuthContext.set(new AuthPrincipal(99L,"ADMIN",null));row.put("status","IN_SERVICE");
        orders.transition(1,"COMPLETED");
        verify(jdbc).update(startsWith("INSERT INTO spa_points_entry"),any(),eq(11L),eq(1L),eq(200),any());
        clearInvocations(jdbc);row.put("status","COMPLETED");orders.transition(1,"COMPLETED");
        verify(jdbc,never()).update(startsWith("INSERT INTO spa_points_entry"),any(Object[].class));
    }
}
