package dev.sdxcod.jakartamqlab;

import dev.sdxcod.jakartamqlab.config.MqSettings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MqSettings.class)
public class MqLabApplication {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(MqLabApplication.class, args)) {
            // CLI commands complete in the runner; the shell keeps the context open until quit.
        }
    }
}
