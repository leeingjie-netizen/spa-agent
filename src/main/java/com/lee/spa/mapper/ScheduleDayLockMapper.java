package com.lee.spa.mapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
@Mapper
public interface ScheduleDayLockMapper {
 @Insert("INSERT INTO schedule_day_lock(work_date) VALUES(#{date}) ON DUPLICATE KEY UPDATE work_date=VALUES(work_date)")
 void ensure(@Param("date") LocalDate date);
 @Select("SELECT work_date FROM schedule_day_lock WHERE work_date=#{date} FOR UPDATE")
 LocalDate lock(@Param("date") LocalDate date);
}
