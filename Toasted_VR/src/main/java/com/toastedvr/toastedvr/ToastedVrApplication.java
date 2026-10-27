package com.toastedvr.toastedvr;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ToastedVrApplication {

    // La aplicación corre siempre en UTC (IDE, mvnw, jar o Docker) para que las
    // fechas LocalDateTime se comporten igual en todos los entornos.
    private static final String APPLICATION_TIME_ZONE = "UTC";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_TIME_ZONE));
        SpringApplication.run(ToastedVrApplication.class, args);
    }
}
