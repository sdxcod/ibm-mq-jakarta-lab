package dev.sdxcod.jakartamqlab.config;

import dev.sdxcod.jakartamqlab.service.MessageReceiver;
import dev.sdxcod.jakartamqlab.service.MessageSender;
import dev.sdxcod.jakartamqlab.transport.JakartaMqSession;
import dev.sdxcod.jakartamqlab.transport.MqSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LabConfiguration {
    @Bean
    MqSessionFactory sessionFactory(MqSettings settings) {
        return () -> new JakartaMqSession(settings);
    }

    @Bean
    MessageSender sender(MqSessionFactory factory) {
        return new MessageSender(factory);
    }

    @Bean
    MessageReceiver receiver(MqSessionFactory factory) {
        return new MessageReceiver(factory);
    }
}
