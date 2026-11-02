package de.dew.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DeliveryQuotationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryQuotationApplication.class, args);
    }
}
