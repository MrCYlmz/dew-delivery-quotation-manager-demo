package de.dew.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DewPropertiesTest {

    private static final String LOCALAPPDATA = "C:\\Users\\alice\\AppData\\Local";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DewPropertiesConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DewProperties.class)
    static class DewPropertiesConfiguration {
    }

    @Test
    void s6Ac4_defaultsAreTheRelativeUploadsDirAndNoBrowser() {
        withApplicationYaml(Map.of()).run(context -> {
            DewProperties properties = context.getBean(DewProperties.class);
            assertThat(properties.storageDir()).isEqualTo("./data/uploads");
            assertThat(properties.openBrowser()).isFalse();
        });
    }

    @Test
    void s6Ac4_envVarsOverrideTheDefaults() {
        withApplicationYaml(Map.of(
                "DEW_STORAGE_DIR", "/srv/dew/uploads",
                "DEW_OPEN_BROWSER", "true")).run(context -> {
            DewProperties properties = context.getBean(DewProperties.class);
            assertThat(properties.storageDir()).isEqualTo("/srv/dew/uploads");
            assertThat(properties.openBrowser()).isTrue();
        });
    }

    @Test
    void s6Ac4_windowsProfileStoresUnderLocalAppDataAndOpensTheBrowser() {
        withApplicationYaml(Map.of("LOCALAPPDATA", LOCALAPPDATA))
                .withPropertyValues("spring.profiles.active=windows")
                .run(context -> {
                    DewProperties properties = context.getBean(DewProperties.class);
                    assertThat(properties.storageDir())
                            .isEqualTo(LOCALAPPDATA + "/DewDeliveryArchive/uploads");
                    assertThat(properties.openBrowser()).isTrue();
                });
    }

    @Test
    void s6Ac4_envVarsOverrideTheWindowsProfile() {
        withApplicationYaml(Map.of(
                "LOCALAPPDATA", LOCALAPPDATA,
                "DEW_STORAGE_DIR", "D:\\DewUploads",
                "DEW_OPEN_BROWSER", "false"))
                .withPropertyValues("spring.profiles.active=windows")
                .run(context -> {
                    DewProperties properties = context.getBean(DewProperties.class);
                    assertThat(properties.storageDir()).isEqualTo("D:\\DewUploads");
                    assertThat(properties.openBrowser()).isFalse();
                });
    }

    @Test
    void s6Ac3_blankStorageDirFailsStartupNamingTheSetting() {
        withApplicationYaml(Map.of("DEW_STORAGE_DIR", "")).run(context ->
                assertFailsNamingStorageDir(context));
    }

    @Test
    void s6Ac3_whitespaceStorageDirFailsStartupNamingTheSetting() {
        runner.withPropertyValues("dew.storage-dir=   ").run(context ->
                assertFailsNamingStorageDir(context));
    }

    @Test
    void s6Ac3_missingStorageDirFailsStartupNamingTheSetting() {
        runner.run(context -> assertFailsNamingStorageDir(context));
    }

    private ApplicationContextRunner withApplicationYaml(Map<String, Object> environment) {
        return runner
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("testEnvironment", environment)))
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    private static void assertFailsNamingStorageDir(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(messagesOf(context.getStartupFailure())).contains("dew.storage-dir");
    }

    private static String messagesOf(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
