package de.dew.backend.config;

import de.dew.parser.DefaultPackingListParser;
import de.dew.parser.PackingListParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParserConfig {

    @Bean
    public PackingListParser packingListParser() {
        return new DefaultPackingListParser();
    }
}
