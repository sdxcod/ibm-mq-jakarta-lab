package dev.sdxcod.jakartamqlab.transport;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import java.io.IOException;

@FunctionalInterface
public interface ContextConnector {
    JMSContext connect() throws JMSException, IOException;
}
