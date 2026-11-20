package com.lee.spa;
import com.lee.spa.ai.tool.AppointmentTool;
import com.lee.spa.service.*;
import com.lee.spa.entity.Appointment;
import com.lee.spa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiConfirmationTest {
 @Test void modelCannotWriteWithoutServerConfirmation(){
  StaffService staff=mock(StaffService.class);AppointmentService bookings=mock(AppointmentService.class);var tool=new AppointmentTool(staff,bookings);
  var context=new ToolContext(Map.of("userId",12L,"requestId","test-ai-request"));
  assertThrows(BusinessException.class,()->tool.createAppointment(1L,2L,"2099-01-05","10:00",context));
  assertThrows(BusinessException.class,()->tool.cancelAppointment(3L,context));
  verifyNoInteractions(bookings);
 }
 @Test void confirmedToolUsesServerIdentityAndSameBookingService(){
  StaffService staff=mock(StaffService.class);AppointmentService bookings=mock(AppointmentService.class);var tool=new AppointmentTool(staff,bookings);
  when(bookings.createAppointment(any())).thenReturn(new Appointment());
  tool.createAppointment(1L,2L,"2099-01-05","10:00",new ToolContext(Map.of("userId",12L,"requestId","test-ai-request","bookingConfirmed",true)));
  verify(bookings).createAppointment(argThat(r->r.getUserId().equals(12L)&&r.getRequestId().equals("AI-test-ai-request")));
 }
}
