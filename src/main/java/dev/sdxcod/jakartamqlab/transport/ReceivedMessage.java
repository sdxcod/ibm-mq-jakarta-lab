package dev.sdxcod.jakartamqlab.transport;

public record ReceivedMessage(
        String messageId,
        String correlationId,
        String text,
        boolean redelivered,
        int deliveryCount,
        boolean persistent) {
}
