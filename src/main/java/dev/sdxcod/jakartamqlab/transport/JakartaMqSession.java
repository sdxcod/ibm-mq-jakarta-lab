package dev.sdxcod.jakartamqlab.transport;

import dev.sdxcod.jakartamqlab.config.MqSettings;
import jakarta.jms.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Application-managed, thread-confined Jakarta context. No Spring messaging lifecycle.
 */
public final class JakartaMqSession implements MqSession {
    private final MqSettings settings;
    private final ContextConnector connector;
    private final Thread owner = Thread.currentThread();
    private JMSContext context;
    private TransactionState state = TransactionState.CLEAN;

    public JakartaMqSession(MqSettings settings) {
        this(settings, () -> IbmMessagingProvider.connectionFactory(settings)
                .createContext(settings.user(), settings.resolvePassword(), JMSContext.SESSION_TRANSACTED));
    }

    public JakartaMqSession(MqSettings settings, ContextConnector connector) {
        this.settings = settings;
        this.connector = connector;
    }

    private void checkThread() {
        if (Thread.currentThread() != owner)
            throw new java.lang.IllegalStateException("Use this context only from its owning thread");
    }

    private void requireReady() {
        checkThread();

        if (state == TransactionState.UNKNOWN)
            throw new java.lang.IllegalStateException("Transaction outcome unknown. Disconnect and investigate before retrying.");

        if (context == null)
            throw new java.lang.IllegalStateException("Connect first");
    }

    @Override
    public void connect() throws JMSException, IOException {
        checkThread();

        if (context != null)
            throw new java.lang.IllegalStateException("Disconnect before reconnecting");
        context = connector.connect();
        state = TransactionState.CLEAN;
    }

    @Override
    public boolean isConnected() {
        checkThread();
        // Jakarta has no isConnected/network probe. Report only our local lifecycle state.
        return context != null && state != TransactionState.UNKNOWN;
    }

    @Override
    public String putMessage(String queue, String text) throws JMSException {
        requireReady();
        validateText(text);
        var destination = IbmMessagingProvider.queue(queue);
        try {
            TextMessage message = context.createTextMessage(text);
            context.createProducer().setDeliveryMode(DeliveryMode.PERSISTENT)
                    .setDisableMessageID(false).send(destination, message);
            state = TransactionState.PENDING;
            return message.getJMSMessageID();
        } catch (JMSRuntimeException e) {
            state = TransactionState.UNKNOWN; // Conservative: no automatic replay of a failed send.
            throw e;
        }
    }

    @Override
    public Optional<ReceivedMessage> getMessage(String queue, Duration wait, String id)
            throws JMSException {
        requireReady();

        if (wait == null || wait.isNegative() || wait.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Wait must be 0..60 seconds");

        String selector = MessageIds.selector(id);
        var destination = IbmMessagingProvider.queue(queue);

        try (JMSConsumer consumer = context.createConsumer(destination, selector)) {
            long millis = wait.toMillis();

            // receive(0) waits indefinitely in Jakarta. Zero/sub-millisecond means NO WAIT here.
            Message message = millis == 0 ? consumer.receiveNoWait() : consumer.receive(millis);

            if (message == null)
                return Optional.empty();

            state = TransactionState.PENDING; // Before type/header/body validation.

            if (!(message instanceof TextMessage textMessage))
                throw new JMSException("This lab expects TextMessage; rollback returns an unsupported message");

            String text = textMessage.getText();
            validateText(text);

            return Optional.of(new ReceivedMessage(message.getJMSMessageID(), message.getJMSCorrelationID(),
                    text, message.getJMSRedelivered(), message.getIntProperty("JMSXDeliveryCount"),
                    message.getJMSDeliveryMode() == DeliveryMode.PERSISTENT));
        } catch (JMSRuntimeException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    private void validateText(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > settings.maxPayloadBytes())
            throw new IllegalArgumentException("Text is null or exceeds max-payload-bytes (UTF-8)");
    }

    @Override
    public BrowseResult browse(String queue, int limit, String id) throws JMSException {
        requireReady();

        if (limit < 1 || limit > 5000)
            throw new IllegalArgumentException("Browse limit must be 1..5000");

        String selector = MessageIds.selector(id);

        try (QueueBrowser browser = context.createBrowser(IbmMessagingProvider.queue(queue), selector)) {
            var messages = browser.getEnumeration();
            int seen = 0;
            while (seen < limit && messages.hasMoreElements()) {
                messages.nextElement();
                seen++;
            }
            return new BrowseResult(seen, seen == limit);
        } catch (JMSRuntimeException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    @Override
    public void commit() {
        requireReady();
        try {
            context.commit();
            state = TransactionState.CLEAN;
        } catch (TransactionRolledBackRuntimeException e) {
            state = TransactionState.CLEAN;
            throw e;
        } catch (JMSRuntimeException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    @Override
    public void backout() {
        requireReady();
        try {
            context.rollback();
            state = TransactionState.CLEAN;
        } catch (JMSRuntimeException e) {
            state = TransactionState.UNKNOWN;
            throw e;
        }
    }

    @Override
    public TransactionState transactionState() {
        checkThread();
        return state;
    }

    @Override
    public void disconnect() {
        checkThread();

        if (context == null)
            return;

        RuntimeException failure = null;

        try {
            if (state == TransactionState.PENDING) {
                try {
                    context.rollback();
                    state = TransactionState.CLEAN;
                } catch (JMSRuntimeException e) {
                    state = TransactionState.UNKNOWN;
                    failure = e;
                }
            }
            try {
                context.close();
            } catch (RuntimeException e) {
                state = TransactionState.UNKNOWN;
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        } finally {
            context = null;
        }
        if (failure != null) throw failure;
    }
}
