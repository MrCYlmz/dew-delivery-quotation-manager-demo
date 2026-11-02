package de.dew.backend.config;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BrowserLauncherConfigTest {

    @Test
    @DisplayName("Default profile keeps the browser shut — tests and headless servers")
    void defaultProfileDisablesIt() throws IOException {
        assertEquals("${DEW_OPEN_BROWSER:false}", property("application.yml", "dew.open-browser"),
                "A default of true would open a browser per test class, and on a hosted server");
    }

    @Test
    @DisplayName("windows profile opens it — the packaged app has no window of its own")
    void windowsProfileEnablesIt() throws IOException {
        assertEquals("${DEW_OPEN_BROWSER:true}", property("application-windows.yml", "dew.open-browser"),
                "The jpackage launcher activates this profile; without it Alice sees only a console");
    }

    @Test
    @DisplayName("windows profile turns off Spring Boot's headless default, or AWT reports no Desktop")
    void windowsProfileDisablesHeadless() throws IOException {
        assertEquals(false, property("application-windows.yml", "spring.main.headless"),
                "Spring Boot defaults java.awt.headless to true for web apps, which disables Desktop.browse");
    }

    private static Object property(String yaml, String key) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(yaml, new ClassPathResource(yaml));
        for (PropertySource<?> source : sources) {
            if (source.containsProperty(key)) {
                return source.getProperty(key);
            }
        }
        assertNotNull(null, key + " is not set in " + yaml);
        return null;
    }
}
