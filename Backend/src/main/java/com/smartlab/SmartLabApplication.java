package com.smartlab;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = "com.smartlab", annotationClass = Mapper.class)
public class SmartLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLabApplication.class, args);
    }
}

