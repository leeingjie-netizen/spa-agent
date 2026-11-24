package com.lee.spa;
import com.lee.spa.auth.*;
import com.lee.spa.common.exception.*;
import com.lee.spa.dto.ScheduleManagementRequests.*;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
/** Real Spring proxies + MySQL; every fixture and operation is rolled back. */
@SpringBootTest(properties={"spring.ai.openai.api-key=local-test-placeholder","spa.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named="SPA_LOCAL_BUSINESS_TESTS",matches="true")
@Transactional
@Rollback
class ScheduleManagementIntegrationTest {
 @Autowired ScheduleManagementService management;
 @Autowired ScheduleConstraintService constraints;
 @Autowired StaffMapper staff;
 @Autowired AppointmentMapper appointments;
 @Autowired BusinessDayMapper business;
 @Autowired javax.sql.DataSource dataSource;
 Long staffId;LocalDate date;
 @BeforeEach void setup() throws Exception {
  try(var connection=dataSource.getConnection()) {
   String url=connection.getMetaData().getURL();
   com.lee.spa.AcceptanceIsolation.verify(dataSource);
  }
  AuthContext.set(new AuthPrincipal(-90001L,"ADMIN",null));
  Staff technician=new Staff();technician.setId(-Math.abs(System.nanoTime()));technician.setName("transaction-test-fixture");technician.setStatus(1);staff.insert(technician);staffId=technician.getId();
  date=LocalDate.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE).plusDays(60);
  while(business.selectById(date)!=null)date=date.plusDays(1);
 }
 @AfterEach void clear(){AuthContext.clear();}
 @Test void pendingLeaveDoesNotBlockThenApprovalBlocksOnlyApprovedInterval(){
  StaffLeave item=submitAsStaff(new Leave(staffId,date,false,LocalTime.of(12,0),LocalTime.of(13,0),"integration-fixture"));
  assertTrue(constraints.isAvailable(staffId,date.atTime(12,0),30));
  management.review(item.getId(),new Review(true,"integration approval"));
  assertFalse(constraints.isAvailable(staffId,date.atTime(12,0),30));
  assertTrue(constraints.isAvailable(staffId,date.atTime(13,0),30));
 }
 @Test void approvalWithExistingBookingReturnsConflictAndLeavesBookingUnchanged(){
  Appointment booking=booking();StaffLeave item=submitAsStaff(new Leave(staffId,date,true,null,null,"integration-fixture"));
  ScheduleConflictException error=assertThrows(ScheduleConflictException.class,()->management.review(item.getId(),new Review(true,"integration-fixture")));
  assertEquals(java.util.List.of(booking.getId()),error.getAppointmentIds());assertEquals(1,appointments.selectById(booking.getId()).getStatus());
 }
 @Test void manualConfirmationIsRequiredAndStoreClosureRejectsConflicts(){
  Manual change=new Manual(1,LocalTime.of(9,0),LocalTime.of(18,0),null,null,false,"integration-fixture");
  management.manual(staffId,date,change);
  assertThrows(BusinessException.class,()->management.manual(staffId,date,change));
 }
 @Test void partialExceptionsAndStoreSpecialHoursAffectQueries(){
  management.block(new ExceptionRequest(staffId,date,LocalTime.of(10,0),LocalTime.of(11,0),"integration-fixture"));
  assertFalse(constraints.isAvailable(staffId,date.atTime(10,0),30));
  management.businessDay(date,new Business(false,LocalTime.of(9,0),LocalTime.of(17,0),"integration-fixture"));
  assertTrue(constraints.isAvailable(staffId,date.atTime(11,0),30));assertFalse(constraints.isAvailable(staffId,date.atTime(16,30),60));
 }
 @Test void closedStoreCannotSilentlyCancelBookings(){
  Appointment booking=booking();assertThrows(ScheduleConflictException.class,()->management.businessDay(date,new Business(true,null,null,"integration-fixture")));
  assertEquals(1,appointments.selectById(booking.getId()).getStatus());
 }
 @Test void exceptionRevocationRestoresAvailabilityWithoutDeletingTheOriginal(){
  var exception=management.block(new ExceptionRequest(staffId,date,LocalTime.of(14,0),LocalTime.of(15,0),"revocation integration"));
  assertFalse(constraints.isAvailable(staffId,date.atTime(14,0),30));
  management.revokeException(exception.getId(),"temporary restriction removed");
  management.revokeException(exception.getId(),"idempotent retry");
  assertTrue(constraints.isAvailable(staffId,date.atTime(14,0),30));
  var calendar=management.calendar(date,1,staffId);
  var originals=(java.util.List<ScheduleException>)calendar.get("exceptions");
  assertEquals(1,originals.size());assertEquals(exception.getId(),originals.get(0).getId());assertTrue(originals.get(0).getRevoked());
  AuthContext.set(new AuthPrincipal(-90003L,"USER",null));
  assertThrows(AuthException.class,()->management.revokeException(exception.getId(),"unauthorized"));
 }
 @Test void staffCannotWithdrawAnotherTechniciansLeave(){
  StaffLeave item=submitAsStaff(new Leave(staffId,date,true,null,null,"integration-fixture"));
  AuthContext.set(new AuthPrincipal(-90002L,"STAFF",staffId-1));
  assertThrows(AuthException.class,()->management.withdraw(item.getId()));
 }
 private StaffLeave submitAsStaff(Leave request){
  AuthContext.set(new AuthPrincipal(-90002L,"STAFF",staffId));
  try{return management.submit(request);}finally{AuthContext.set(new AuthPrincipal(-90001L,"ADMIN",null));}
 }
 @Test void administratorCannotSubmitEmployeeLeave(){
  assertThrows(AuthException.class,()->management.submit(new Leave(staffId,date,true,null,null,"administrator must approve only")));
 }
 private Appointment booking(){
  Appointment a=new Appointment();a.setId(-Math.abs(System.nanoTime()));a.setAppointmentNo(UUID.randomUUID().toString().replace("-",""));a.setRequestId(UUID.randomUUID().toString());a.setUserId(-90001L);a.setServiceId(-90001L);a.setStaffId(staffId);a.setAppointmentDate(date);a.setStartTime(LocalTime.of(10,0));a.setEndTime(LocalTime.of(11,0));a.setAmount(BigDecimal.ONE);a.setStatus(1);appointments.insert(a);return a;
 }
}
