package com.inspectfirst.report;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling
@SpringBootApplication(scanBasePackages={"com.inspectfirst.report","com.inspectfirst.shared"})
public class ReportApplication { public static void main(String[] args) { SpringApplication.run(ReportApplication.class,args); } }
