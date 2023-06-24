package com.seliote.aliyunddns;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class AliyunDdnsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AliyunDdnsApplication.class, args);
    }
}
