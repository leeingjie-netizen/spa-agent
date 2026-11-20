package com.lee.spa.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lee.spa.common.exception.GlobalExceptionHandler;
import com.lee.spa.controller.AppointmentController;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.service.AppointmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSecurityTest {
    private AppointmentService appointments;
    private AuthService auth;
    private MockMvc mvc;
    private final String bearer = "Bearer " + "a".repeat(43);

    @BeforeEach void setup() {
        appointments = mock(AppointmentService.class);
        auth = mock(AuthService.class);
        when(auth.authenticate(anyString())).thenReturn(new AuthPrincipal(11L, "USER", null));
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointments))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new AuthFilter(auth, new ObjectMapper())).build();
    }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void anonymousCannotReadAppointments() throws Exception {
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(appointments);
    }
    @Test void forgedUserIdIsForbidden() throws Exception {
        mvc.perform(get("/api/appointments/user/12/summaries").header("Authorization", bearer)).andExpect(status().isForbidden());
        verifyNoInteractions(appointments);
    }
    @Test void ownIdentityComesFromTokenAndContextIsCleared() throws Exception {
        when(appointments.getAppointmentSummariesByUserId(11L)).thenReturn(List.of());
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer)).andExpect(status().isOk());
        verify(appointments).getAppointmentSummariesByUserId(11L);
        assertThrows(AuthException.class, AuthContext::current);
    }
    @Test void createReplacesForgedIdentity() throws Exception {
        mvc.perform(post("/api/appointments").header("Authorization", bearer).contentType("application/json")
                .content("{\"userId\":999,\"requestId\":\"auth-test\"}")).andExpect(status().isOk());
        verify(appointments).createAppointment(argThat((CreateAppointmentRequest request) -> request.getUserId().equals(11L)));
    }
    @Test void regularUserCannotEnterAdminApi() throws Exception {
        mvc.perform(get("/api/admin/schedules").header("Authorization", bearer)).andExpect(status().isForbidden());
    }
    @Test void expiredTokenCannotReachService() throws Exception {
        when(auth.authenticate(anyString())).thenThrow(new AuthException(401, "expired"));
        mvc.perform(get("/api/appointments/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        verifyNoInteractions(appointments);
    }
    @Test void simulationClosedInDefaultAndProductionProfiles() {
        MockEnvironment environment = new MockEnvironment().withProperty("spa.auth.dev-login-enabled", "true");
        AuthService service = new AuthService(mock(JdbcTemplate.class), environment);
        assertThrows(AuthException.class, () -> service.devLogin("test-user"));
        environment.setActiveProfiles("local", "production");
        assertThrows(AuthException.class, () -> service.devLogin("test-user"));
    }
    @Test void missingAdministratorConfigurationDoesNotCreateAccount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService service = new AuthService(jdbc, new MockEnvironment());
        assertThrows(AuthException.class, () -> service.adminLogin("admin", "arbitrarypassword"));
        verifyNoInteractions(jdbc);
    }
    @Test void passwordHashesAreSaltedAndTokenDigestsAreOneWay() {
        String encoded = PasswordHasher.hash("a-long-test-password");
        assertTrue(PasswordHasher.matches("a-long-test-password", encoded));
        assertFalse(PasswordHasher.matches("wrong-password", encoded));
        assertNotEquals(encoded, PasswordHasher.hash("a-long-test-password"));
        assertFalse(PasswordHasher.matches("password", "broken"));
        assertEquals(64, AuthService.digest("opaque-token").length());
        assertFalse(AuthService.digest("opaque-token").contains("opaque-token"));
    }
}
