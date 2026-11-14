package com.inspectfirst.guide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages={"com.inspectfirst.guide","com.inspectfirst.shared"})
public class GuideApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuideApplication.class, args);
    }
}
