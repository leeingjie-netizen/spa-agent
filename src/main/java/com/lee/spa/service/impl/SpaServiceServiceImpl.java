package com.lee.spa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.entity.SpaService;
import com.lee.spa.mapper.SpaServiceMapper;
import com.lee.spa.service.SpaServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaServiceServiceImpl
        implements SpaServiceService {

    private final SpaServiceMapper spaServiceMapper;

    /**
     * 获得可用的服务列表
     * @return
     */
    @Override
    public List<SpaService> getAvailableServices() {
        // 1.构造查询条件对象
        LambdaQueryWrapper<SpaService> wrapper =
                new LambdaQueryWrapper<>();

        // 2.添加查询条件
        wrapper.eq(SpaService::getStatus, 1);
        // 3.执行查询

        return spaServiceMapper.selectList(wrapper);
    }

    /**
     * 根据id查询服务
     * @param id
     * @return
     */
    @Override
    public SpaService getServiceById(Long id) {

        return spaServiceMapper.selectById(id);
    }
}
