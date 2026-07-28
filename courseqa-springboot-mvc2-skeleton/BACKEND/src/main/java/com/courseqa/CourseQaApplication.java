package com.courseqa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class CourseQaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseQaApplication.class, args);
    }
}
