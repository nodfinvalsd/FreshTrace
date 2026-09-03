package com.freshtrace;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "com.freshtrace", annotationClass = Mapper.class)
public class FreshTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshTraceApplication.class, args);
    }

}
