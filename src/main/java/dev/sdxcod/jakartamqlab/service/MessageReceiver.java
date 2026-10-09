package dev.sdxcod.jakartamqlab.service;

import jakarta.jms.JMSException;
import dev.sdxcod.jakartamqlab.transport.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public final class MessageReceiver {

    private final MqSessionFactory factory;

    public MessageReceiver(MqSessionFactory factory) {
        this.factory = factory;
    }

    public Optional<ReceivedMessage> receive(String queue, Duration wait, String id, Outcome outcome)
            throws JMSException, IOException {

        try (var session = factory.create()) {
            session.connect();
            var message = session.getMessage(queue, wait, id);
            if (message.isPresent()) outcome.complete(session);
            return message;
        }
    }
}
