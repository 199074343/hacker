package com.gdtech.hackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GDTech第八届骇客大赛 - 主应用类
 *
 * @author GDTech
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class HackathonApplication {

    public static void main(String[] args) {
        SpringApplication.run(HackathonApplication.class, args);
    }
}
