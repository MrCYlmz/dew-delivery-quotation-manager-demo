package de.dew.backend.config;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class BrowserLauncher implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final boolean enabled;

    public BrowserLauncher(DewProperties properties) {
        this.enabled = properties.openBrowser();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            return;
        }
        String url = resolveUrl(event);
        if (url == null) {
            return;
        }
        log.info("Delivery archive ready at {}", url);
        if (!open(url)) {
            log.info("Could not open a browser automatically — open {} yourself.", url);
        }
    }

    private String resolveUrl(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext context)
                || context.getWebServer() == null) {
            return null;
        }
        int port = context.getWebServer().getPort();
        if (port <= 0) {
            return null;
        }
        String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "");
        if (contextPath.endsWith("/")) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }
        return "http://localhost:" + port + contextPath + "/";
    }

    private boolean open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException
                | IllegalArgumentException e) {
            log.debug("Desktop.browse failed, trying the platform command", e);
        } catch (Throwable t) {
            log.debug("Desktop API unavailable, trying the platform command", t);
        }
        return openWithPlatformCommand(url);
    }

    private boolean openWithPlatformCommand(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> command;
        if (os.contains("win")) {
            command = List.of("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            command = List.of("open", url);
        } else {
            command = List.of("xdg-open", url);
        }
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException | SecurityException e) {
            log.debug("Could not run {}", command, e);
            return false;
        }
    }
}
