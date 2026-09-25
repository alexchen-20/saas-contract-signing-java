package dev.learning.contracts;

import dev.learning.contracts.config.InfraiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InfraiProperties.class)
public class ContractSigningApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractSigningApplication.class, args);
    }
}
