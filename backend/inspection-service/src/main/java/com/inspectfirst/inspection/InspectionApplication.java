package com.inspectfirst.inspection;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling
@SpringBootApplication(scanBasePackages={"com.inspectfirst.inspection","com.inspectfirst.shared"})
public class InspectionApplication { public static void main(String[] args) { SpringApplication.run(InspectionApplication.class,args); } }
