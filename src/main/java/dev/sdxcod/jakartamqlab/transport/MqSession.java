package dev.sdxcod.jakartamqlab.transport;

import jakarta.jms.JMSException;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * One application-managed Jakarta context / transaction, confined to one thread.
 */
public interface MqSession extends AutoCloseable {
    void connect() throws JMSException, IOException;

    void disconnect();

    boolean isConnected();

    String putMessage(String queue, String text) throws JMSException;

    Optional<ReceivedMessage> getMessage(String queue, Duration wait, String messageId) throws JMSException;

    BrowseResult browse(String queue, int limit, String messageId) throws JMSException;

    void commit();

    void backout();

    TransactionState transactionState();

    @Override
    default void close() {
        disconnect();
    }
}
