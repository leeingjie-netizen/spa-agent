package com.lee.spa.service;

import com.lee.spa.entity.SpaService;

import java.util.List;

public interface SpaServiceService {

    /**
     * 服务列表
     * @return
     */
    List<SpaService> getAvailableServices();

    /**
     * 根据id获取服务
     * @param id
     * @return
     */
    SpaService getServiceById(Long id);



}