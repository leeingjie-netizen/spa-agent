package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lee.spa.entity.Staff;
import com.lee.spa.entity.StaffWeeklyRule;
import com.lee.spa.mapper.StaffMapper;
import com.lee.spa.mapper.StaffWeeklyRuleMapper;
import com.lee.spa.vo.ScheduleGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static com.lee.spa.vo.ScheduleGenerationResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleGenerationServiceTest {
    private final StaffMapper staff = mock(StaffMapper.class);
    private final StaffWeeklyRuleMapper rules = mock(StaffWeeklyRuleMapper.class);
    private final ScheduleGenerationWriter writer = mock(ScheduleGenerationWriter.class);
    private final ScheduleGenerationService service = new ScheduleGenerationService(staff, rules, writer);
    private final LocalDate monday = LocalDate.of(2026, 11, 16);

    @BeforeEach
    void setup() {
        Staff active = new Staff(); active.setId(1L); active.setStatus(1);
        when(staff.selectById(1L)).thenReturn(active);
    }

    @Test
    void missingRulesNeverBecomeDaysOff() {
        when(rules.selectList(any(Wrapper.class))).thenReturn(List.of());
        ScheduleGenerationResult result = service.generate(1L, monday, 30);
        assertEquals(30, result.getSkipped());
        assertEquals(0, result.getGenerated());
        assertTrue(result.entries().stream().allMatch(entry -> entry.outcome() == MISSING_RULE));
        verifyNoInteractions(writer);
    }

    @Test
    void duplicateWithoutExistingDateIsReportedAsFailure() {
        StaffWeeklyRule mon = rule(1);
        when(rules.selectList(any(Wrapper.class))).thenReturn(List.of(mon));
        when(writer.insertMissing(1L, monday, mon)).thenThrow(new DuplicateKeyException("id collision"));
        when(writer.scheduleExists(1L, monday)).thenReturn(false);
        ScheduleGenerationResult result = service.generate(1L, monday, 1);
        assertEquals(1, result.getFailed());
        assertEquals(0, result.getSkipped());
    }

    @Test
    void duplicateAndFailureAreIsolatedAndLaterDaysContinue() {
        StaffWeeklyRule mon = rule(1), tue = rule(2), wed = rule(3);
        when(rules.selectList(any(Wrapper.class))).thenReturn(List.of(mon, tue, wed));
        when(writer.insertMissing(1L, monday, mon)).thenThrow(new DuplicateKeyException("race"));
        when(writer.scheduleExists(1L, monday)).thenReturn(true);
        when(writer.insertMissing(1L, monday.plusDays(1), tue)).thenThrow(new IllegalStateException("unavailable"));
        when(writer.insertMissing(1L, monday.plusDays(2), wed)).thenReturn(
                new ScheduleGenerationResult.Entry(1L, monday.plusDays(2), GENERATED, "ok", List.of()));
        ScheduleGenerationResult result = service.generate(1L, monday, 3);
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getGenerated());
    }

    @Test
    void invalidRangesAndInactiveStaffCannotGenerate() {
        assertThrows(RuntimeException.class, () -> service.generate(1L, monday, 31));
        assertThrows(RuntimeException.class, () -> service.generate(null, monday, 1));
        assertThrows(RuntimeException.class, () -> service.generate(99L, monday, 1));
        verifyNoInteractions(writer);
    }

    private StaffWeeklyRule rule(int day) {
        StaffWeeklyRule rule = new StaffWeeklyRule(); rule.setDayOfWeek(day); return rule;
    }
}
