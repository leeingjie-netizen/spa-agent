package com.lee.spa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lee.spa.entity.StaffSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;

@Mapper
public interface StaffScheduleMapper extends BaseMapper<StaffSchedule> {
    @Select("SELECT * FROM staff_schedule WHERE staff_id = #{staffId} AND work_date = #{date} FOR UPDATE")
    StaffSchedule selectForUpdate(@Param("staffId") Long staffId, @Param("date") LocalDate date);
}
