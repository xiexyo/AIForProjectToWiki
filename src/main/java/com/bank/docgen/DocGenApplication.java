package com.bank.docgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocGenApplication.class, args);
    }
}