package de.dew.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SqliteDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceConfig.class);

    private static final String JDBC_SQLITE_PREFIX = "jdbc:sqlite:";

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        createParentDirectory(properties.getUrl());
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    static void createParentDirectory(String url) {
        Path file = databaseFile(url);
        if (file == null) {
            return;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            log.warn("Could not create database directory {}: {}", parent, e.getMessage());
        }
    }

    static Path databaseFile(String url) {
        if (url == null || !url.startsWith(JDBC_SQLITE_PREFIX)) {
            return null;
        }
        String path = url.substring(JDBC_SQLITE_PREFIX.length());
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.isBlank() || path.startsWith(":") || path.startsWith("file:")) {
            return null;
        }
        return Paths.get(path);
    }
}
