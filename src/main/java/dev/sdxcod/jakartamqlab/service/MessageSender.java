package dev.sdxcod.jakartamqlab.service;

import dev.sdxcod.jakartamqlab.transport.MqSessionFactory;
import jakarta.jms.JMSException;

import java.io.IOException;

public final class MessageSender {
    private final MqSessionFactory factory;

    public MessageSender(MqSessionFactory factory) {
        this.factory = factory;
    }

    public String send(String queue, String text, Outcome outcome) throws JMSException, IOException {
        try (var session = factory.create()) {
            session.connect();
            String id = session.putMessage(queue, text);
            outcome.complete(session);
            return id;
        }
    }
}
