package com.toastedvr.ai;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiServiceApplication {

    // Igual que el backend: el servicio corre siempre en UTC.
    private static final String APPLICATION_TIME_ZONE = "UTC";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_TIME_ZONE));
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
