package com.example.AIMockInterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiMockInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiMockInterviewApplication.class, args);
    }
}
