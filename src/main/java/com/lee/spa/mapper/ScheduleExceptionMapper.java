package com.lee.spa.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lee.spa.entity.ScheduleException;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ScheduleExceptionMapper extends BaseMapper<ScheduleException> {
 @org.apache.ibatis.annotations.Select("<script>SELECT e.*, (r.exception_id IS NOT NULL) AS revoked FROM schedule_exception e LEFT JOIN schedule_exception_revocation r ON r.exception_id=e.id WHERE e.work_date BETWEEN #{from} AND #{to}<if test='staffId != null'> AND e.staff_id=#{staffId}</if> ORDER BY e.work_date,e.id</script>")
 java.util.List<ScheduleException> calendar(@org.apache.ibatis.annotations.Param("from") java.time.LocalDate from,@org.apache.ibatis.annotations.Param("to") java.time.LocalDate to,@org.apache.ibatis.annotations.Param("staffId") Long staffId);
}
