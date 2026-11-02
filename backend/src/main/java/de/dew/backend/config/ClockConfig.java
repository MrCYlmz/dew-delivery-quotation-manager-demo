package de.dew.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // "Today" for the estimate's ages, the board's one-week window and the day a
    // delivery moves to Delivered comes from here, so tests can pin it.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
