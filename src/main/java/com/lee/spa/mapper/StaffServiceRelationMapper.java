package com.lee.spa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lee.spa.entity.StaffServiceRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper

/**
 * 这个 Mapper 专门负责操作 StaffServiceRelation 对应的表。也就是staff_service表
 * 所以以后： staffServiceRelationMapper.selectList(...) 就是在查staff_service
 */

public interface StaffServiceRelationMapper extends BaseMapper<StaffServiceRelation> {
}
