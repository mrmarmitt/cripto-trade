package com.marmitt.application.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CTradeApplication.class, args);
    }
}
