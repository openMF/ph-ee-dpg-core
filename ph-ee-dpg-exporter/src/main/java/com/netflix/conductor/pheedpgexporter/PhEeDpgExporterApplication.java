package com.netflix.conductor.pheedpgexporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings({ "HideUtilityClassConstructor" })
public class PhEeDpgExporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhEeDpgExporterApplication.class, args);
    }

}
