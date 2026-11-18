package com.connecta.backend;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

    public static void main(String[] args) {
        // spring-dotenv is not auto-registered in Spring Boot 4, so load ./.env explicitly.
        new SpringApplicationBuilder(BackendApplication.class)
                .initializers(new DotenvApplicationInitializer())
                .run(args);
    }

}
