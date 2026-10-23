package com.lee.spa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lee.spa.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper

/**
 * Mapper 不应该写：
 * “如果技师不支持服务就抛异常。”
 *
 * 那不是数据库操作，是业务规则。1
 */
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
