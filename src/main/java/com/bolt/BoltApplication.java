package com.bolt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BoltApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoltApplication.class, args);
    }
}
