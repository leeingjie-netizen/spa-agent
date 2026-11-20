package com.lee.spa.commerce;

import com.lee.spa.auth.*;
import com.lee.spa.common.exception.*;
import com.lee.spa.controller.AdminCatalogController;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.dto.ScheduleManagementRequests.Leave;
import com.lee.spa.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** BUG-002/006/007/008-012. All new fixtures and bookings roll back. No pay/cancel/refund operations. */
@SpringBootTest(properties={"spring.ai.openai.api-key=local-test-placeholder","spa.scheduling.enabled=false","spa.orders.timeout-enabled=false","logging.level.org.springframework.web=INFO"})
@EnabledIfEnvironmentVariable(named="SPA_BUGFIX_DB_TESTS",matches="true")
@Transactional
class BugfixRegressionIntegrationTest {
 @Autowired JdbcTemplate jdbc;@Autowired javax.sql.DataSource dataSource;
 @Autowired AdminOrderQueryController records;@Autowired AdminCatalogController catalog;
 @Autowired AppointmentService appointments;@Autowired StaffService staff;@Autowired ScheduleManagementService scheduling;
 long id;LocalDate date;
 @BeforeEach void localOnly() throws Exception {
  try(var c=dataSource.getConnection()){String url=c.getMetaData().getURL();assertTrue(url.startsWith("jdbc:mysql://localhost:3306/spa?")||url.startsWith("jdbc:mysql://127.0.0.1:3306/spa?"));}
  AuthContext.set(new AuthPrincipal(-90001L,"ADMIN",null));id=-Math.abs(System.nanoTime());date=LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE).plusDays(150);
 }
 @AfterEach void clear(){AuthContext.clear();}
 MockHttpServletRequest request(String path){return new MockHttpServletRequest("GET",path);}
 @SuppressWarnings("unchecked") Map<String,Object> list(String path,String status,String paid,int page,int size){return (Map<String,Object>)records.list(request(path),status,paid,null,null,"",page,size).getData();}
 @SuppressWarnings("unchecked") Map<String,Object> detail(String path,long key){return (Map<String,Object>)records.detail(request(path+"/"+key),key).getData();}
 @Test void historicalAppointmentsAreVisibleWithoutSyntheticOrders(){
  Long before=jdbc.queryForObject("SELECT COUNT(*) FROM spa_order",Long.class);
  var result=list("/api/admin/appointments","ALL","NO_ORDER",1,100);
  long expected=jdbc.queryForObject("SELECT COUNT(*) FROM appointment a LEFT JOIN spa_order o ON o.appointment_id=a.id WHERE o.id IS NULL",Long.class);
  assertTrue(expected>0,"Retained historical appointments are required");assertEquals(expected,((Number)result.get("total")).longValue());
  var rows=(List<Map<String,Object>>)result.get("items");assertTrue(rows.stream().anyMatch(r->String.valueOf(r.get("appointmentDate")).startsWith("2026")));
  var first=rows.get(0);var old=detail("/api/admin/appointments",Long.parseLong((String)first.get("appointmentId")));
  assertNull(old.get("id"));assertTrue(((List<?>)old.get("payments")).isEmpty());
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order",Long.class));
 }
 @Test void independentOrderAndPaymentFiltersAndPaginationMatchSql(){
  var page=list("/api/admin/order-records","REFUNDED","REFUNDED",1,1);
  long expected=jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE status='REFUNDED' AND payment_status='REFUNDED'",Long.class);
  assertTrue(expected>1);assertEquals(expected,((Number)page.get("total")).longValue());
  var first=(Map<?,?>)((List<?>)page.get("items")).get(0);var second=(Map<?,?>)((List<?>)list("/api/admin/order-records","REFUNDED","REFUNDED",2,1).get("items")).get(0);
  assertNotEquals(first.get("id"),second.get("id"));
  assertEquals(0L,list("/api/admin/order-records","COMPLETED","UNPAID",1,20).get("total"));
 }
 @Test void retainedPointsAndRefundRecordsExposeExactAmountsWithoutFabricatingReasons(){
  long key=jdbc.queryForObject("SELECT o.id FROM spa_order o JOIN spa_order_benefit b ON b.order_id=o.id WHERE b.points_used=100 AND b.original_amount=120 AND o.amount=109 AND o.status='REFUNDED' ORDER BY o.id LIMIT 1",Long.class);
  var order=detail("/api/admin/order-records",key);
  assertEquals("120.00",order.get("originalAmount"));assertEquals("1.00",order.get("pointsDiscountAmount"));assertEquals("10.00",order.get("couponDiscountAmount"));assertEquals("109.00",order.get("amount"));
  assertEquals("109.00",order.get("actualPaidAmount"));assertEquals("109.00",order.get("actualRefundAmount"));assertNull(order.get("refundReason"));
  assertEquals(2,((List<?>)order.get("payments")).size());assertFalse(((List<?>)order.get("pointsAdjustments")).isEmpty());
 }
 @Test void readQueriesRejectInvalidFiltersAndNonAdminRoles(){
  assertThrows(BusinessException.class,()->list("/api/admin/order-records","1 OR 1=1","ALL",1,20));
  assertThrows(BusinessException.class,()->list("/api/admin/appointments","ALL","ALL",0,20));
  for(String role:List.of("USER","STAFF")){AuthContext.set(new AuthPrincipal(id,role,id));assertThrows(AuthException.class,()->list("/api/admin/appointments","ALL","ALL",1,20));assertThrows(AuthException.class,()->detail("/api/admin/order-records",1));}
 }
 @Test void invalidCatalogInputsReturn400QuicklyAndCreateNoRows() throws Exception {
  long services=jdbc.queryForObject("SELECT COUNT(*) FROM spa_service",Long.class),technicians=jdbc.queryForObject("SELECT COUNT(*) FROM staff",Long.class);
  var mvc=MockMvcBuilders.standaloneSetup(catalog).setControllerAdvice(new GlobalExceptionHandler()).build();
  for(String json:List.of("{\"name\": \"   \", \"status\": 1, \"price\": 1, \"durationMinutes\": 30}","{\"name\": \"bad\", \"status\": 1, \"price\": -1, \"durationMinutes\": 0}","{\"name\": \"bad\", \"status\": 1, \"price\": \"NaN\", \"durationMinutes\": 30}"))
   mvc.perform(post("/api/admin/catalog/services").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  mvc.perform(post("/api/admin/catalog/staff").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \", \"status\": 1}")).andExpect(status().isBadRequest());
  assertEquals(services,jdbc.queryForObject("SELECT COUNT(*) FROM spa_service",Long.class));assertEquals(technicians,jdbc.queryForObject("SELECT COUNT(*) FROM staff",Long.class));
  var created=catalog.createStaff(new AdminCatalogController.StaffInput("BUG-006-rollback-only","valid",null,1)).getData();assertNotNull(created.getId());
 }
 void fixture(){
  jdbc.update("INSERT INTO staff(id,name,status) VALUES (?,'bugfix-rollback-only',0)",id);
  jdbc.update("INSERT INTO spa_service(id,name,price,duration_minutes,status) VALUES (?,'bugfix-rollback-only',1,30,1)",id);
  jdbc.update("INSERT INTO staff_service(id,staff_id,service_id) VALUES (?,?,?)",id,id,id);
  while(jdbc.queryForObject("SELECT COUNT(*) FROM business_day WHERE work_date=?",Integer.class,date)>0)date=date.plusDays(1);
  jdbc.update("INSERT INTO staff_schedule(id,staff_id,work_date,start_time,end_time,status,source) VALUES (?,?,?,'09:00','18:00',1,'MANUAL')",id,id,date);
 }
 @Test void disabledStaffIsHiddenAndBookingRejectedThenReenabledBookingSucceeds(){
  fixture();assertTrue(staff.getStaffByService(id).isEmpty());assertTrue(staff.getAvailableSlots(id,date,id).isEmpty());
  CreateAppointmentRequest input=new CreateAppointmentRequest();input.setUserId(id);input.setStaffId(id);input.setServiceId(id);input.setAppointmentDate(date);input.setStartTime(LocalTime.of(10,0));input.setRequestId("bugfix-"+UUID.randomUUID());
  assertThrows(BusinessException.class,()->appointments.createAppointment(input));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE staff_id=?",Integer.class,id));
  catalog.updateStaff(id,new AdminCatalogController.StaffInput("bugfix-rollback-only",null,null,1));
  assertEquals(1,staff.getStaffByService(id).size());assertTrue(staff.getAvailableSlots(id,date,id).contains(LocalTime.of(10,0)));
  var created=appointments.createAppointment(input);assertNotNull(created.getId());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM spa_order WHERE appointment_id=?",Integer.class,created.getId()));
 }
 @Test void administratorCannotApplyAndStaffIdentityCannotBeSpoofed(){
  fixture();Leave input=new Leave(1L,date,true,null,null,"bugfix-rollback-only");
  assertThrows(AuthException.class,()->scheduling.submit(input));
  AuthContext.set(new AuthPrincipal(id,"STAFF",id));var submitted=scheduling.submit(input);assertEquals(id,submitted.getStaffId());
  assertEquals("PENDING",submitted.getStatus());
 }
}
