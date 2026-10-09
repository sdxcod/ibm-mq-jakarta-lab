package dev.sdxcod.jakartamqlab.transport;

@FunctionalInterface
public interface MqSessionFactory {
    MqSession create();
}
