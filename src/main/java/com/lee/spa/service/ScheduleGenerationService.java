package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.entity.Staff;
import com.lee.spa.entity.StaffWeeklyRule;
import com.lee.spa.mapper.StaffMapper;
import com.lee.spa.mapper.StaffWeeklyRuleMapper;
import com.lee.spa.vo.ScheduleGenerationResult;
import com.lee.spa.vo.ScheduleGenerationResult.Entry;
import com.lee.spa.vo.ScheduleGenerationResult.Outcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Conservative generation: existing AUTO and MANUAL schedules are equally protected. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleGenerationService {
    private final StaffMapper staffMapper;
    private final StaffWeeklyRuleMapper rules;
    private final ScheduleGenerationWriter writer;

    public ScheduleGenerationResult generateNext30Days(Long staffId) {
        return generate(staffId, LocalDate.now(ZoneId.of("Asia/Shanghai")), 30);
    }

    public ScheduleGenerationResult generate(Long staffId, LocalDate firstDate, int days) {
        if (staffId == null || firstDate == null || days < 1 || days > 30) {
            throw new BusinessException("必须指定技师、开始日期及1至30天的范围");
        }
        Staff staff = staffMapper.selectById(staffId);
        if (staff == null || !Integer.valueOf(1).equals(staff.getStatus())) {
            throw new BusinessException("技师不存在或已停用");
        }
        List<StaffWeeklyRule> weeklyRules = rules.selectList(new LambdaQueryWrapper<StaffWeeklyRule>()
                .eq(StaffWeeklyRule::getStaffId, staffId));
        List<Entry> entries = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = firstDate.plusDays(offset);
            List<StaffWeeklyRule> matching = weeklyRules.stream()
                    .filter(rule -> Integer.valueOf(date.getDayOfWeek().getValue()).equals(rule.getDayOfWeek())).toList();
            if (matching.isEmpty()) {
                entries.add(new Entry(staffId, date, Outcome.MISSING_RULE, "未配置周规则，未推断为休息日", List.of()));
                continue;
            }
            if (matching.size() != 1) {
                entries.add(new Entry(staffId, date, Outcome.FAILED, "周规则重复，需检查唯一约束", List.of()));
                continue;
            }
            try {
                entries.add(writer.insertMissing(staffId, date, matching.get(0)));
            } catch (DuplicateKeyException concurrentInsert) {
                // Exception has left the REQUIRES_NEW proxy and its rollback has completed.
                try {
                    boolean exists = writer.scheduleExists(staffId, date);
                    entries.add(new Entry(staffId, date, exists ? Outcome.EXISTING : Outcome.FAILED,
                            exists ? "并发写入，保留已有排班" : "唯一约束冲突但日期记录不存在，请检查日志", List.of()));
                } catch (RuntimeException verificationFailure) {
                    log.warn("排班并发写入验证失败 staffId={} date={}", staffId, date, verificationFailure);
                    entries.add(new Entry(staffId, date, Outcome.FAILED, "无法确认并发写入结果，请检查日志", List.of()));
                }
            } catch (RuntimeException failure) {
                log.warn("排班生成失败 staffId={} date={}", staffId, date, failure);
                entries.add(new Entry(staffId, date, Outcome.FAILED, "生成失败，当前日期事务已回滚，请检查日志", List.of()));
            }
        }
        return new ScheduleGenerationResult(entries);
    }
}
