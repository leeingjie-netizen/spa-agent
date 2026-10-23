package com.lee.spa;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lee.spa.mapper")
public class SpaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaAgentApplication.class, args);
    }

}
