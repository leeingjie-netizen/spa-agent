package com.lee.spa.vo;

import java.time.LocalDate;
import java.util.List;

/** Every requested staff/day has exactly one outcome. */
public record ScheduleGenerationResult(List<Entry> entries) {
    public ScheduleGenerationResult {
        entries = List.copyOf(entries);
    }

    public long getGenerated() { return count(Outcome.GENERATED); }
    public long getSkipped() { return count(Outcome.EXISTING) + count(Outcome.MISSING_RULE); }
    public long getConflicts() { return count(Outcome.CONFLICT); }
    public long getFailed() { return count(Outcome.FAILED); }
    private long count(Outcome outcome) {
        return entries.stream().filter(entry -> entry.outcome() == outcome).count();
    }

    public enum Outcome { GENERATED, EXISTING, MISSING_RULE, CONFLICT, FAILED }

    public record Entry(Long staffId, LocalDate date, Outcome outcome, String message,
                        List<Long> conflictingAppointmentIds) {
        public Entry {
            conflictingAppointmentIds = List.copyOf(conflictingAppointmentIds);
        }
    }
}
