package dev.sdxcod.jakartamqlab.transport;

import dev.sdxcod.jakartamqlab.config.MqSettings;
import jakarta.jms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JakartaMqSessionTest {
    private JMSContext context;
    private JMSProducer producer;
    private JMSConsumer consumer;
    private JakartaMqSession session;
    private TextMessage message;
    private final String id = "ID:" + "A1".repeat(24);

    private MqSettings settings() {
        return new MqSettings(
                "localhost",
                1414,
                "QM1",
                "DEV.APP.SVRCONN",
                "app",
                "secret",
                null,
                "DEV.LAB.IN",
                "DEV.LAB.OUT",
                "DEV.LAB.TEST",
                1048576);
    }

    @BeforeEach
    void setup() throws Exception {
        context = mock(JMSContext.class);
        producer = mock(JMSProducer.class);
        consumer = mock(JMSConsumer.class);
        message = mock(TextMessage.class);
        when(context.createProducer()).thenReturn(producer);
        when(producer.setDeliveryMode(anyInt())).thenReturn(producer);
        when(producer.setDisableMessageID(anyBoolean())).thenReturn(producer);
        when(context.createTextMessage(anyString())).thenReturn(message);
        when(message.getJMSMessageID()).thenReturn(id);
        when(message.getText()).thenReturn("درود MQ");
        when(message.getIntProperty("JMSXDeliveryCount")).thenReturn(2);
        when(message.getJMSRedelivered()).thenReturn(true);
        when(message.getJMSDeliveryMode()).thenReturn(DeliveryMode.PERSISTENT);
        when(context.createConsumer(any(Queue.class), nullable(String.class))).thenReturn(consumer);
        session = new JakartaMqSession(settings(), () -> context);
        session.connect();
    }

    @Test
    void sendUsesPersistentTextAndWaitsForExplicitCommit() throws Exception {
        assertEquals(id, session.putMessage("DEV.LAB.IN", "درود MQ"));
        verify(producer).setDeliveryMode(DeliveryMode.PERSISTENT);
        verify(producer).send(any(Queue.class), same(message));
        verify(context, never()).commit();
        assertEquals(TransactionState.PENDING, session.transactionState());
        session.commit();
        session.close();
        var order = inOrder(context);
        order.verify(context).commit();
        order.verify(context).close();
        verify(context, never()).rollback();
    }

    @Test
    void zeroWaitUsesReceiveNoWaitAndEmptyReceiveDoesNotStartPendingWork() throws Exception {
        assertTrue(session.getMessage("DEV.LAB.IN", Duration.ZERO, null).isEmpty());
        verify(consumer).receiveNoWait();
        verify(consumer, never()).receive(anyLong());
        verify(consumer).close();
        assertEquals(TransactionState.CLEAN, session.transactionState());
    }

    @Test
    void positiveWaitUsesSelectorAndReturnsRedeliveryMetadata() throws Exception {
        when(consumer.receive(3000)).thenReturn(message);
        var result = session.getMessage("DEV.LAB.IN", Duration.ofSeconds(3), id).orElseThrow();
        verify(context).createConsumer(any(Queue.class), eq("JMSMessageID = '" + id + "'"));
        assertEquals("درود MQ", result.text());
        assertTrue(result.redelivered());
        assertEquals(2, result.deliveryCount());
        assertTrue(result.persistent());
        session.backout();
        verify(context).rollback();
    }

    @Test
    void unsupportedMessageIsReturnedOnClose() throws Exception {
        when(consumer.receiveNoWait()).thenReturn(mock(BytesMessage.class));
        assertThrows(JMSException.class, () -> session.getMessage("DEV.LAB.IN", Duration.ZERO, null));
        assertEquals(TransactionState.PENDING, session.transactionState());
        session.close();
        verify(context).rollback();
        verify(context).close();
    }

    @Test
    void failedBodyReadIsRolledBackBeforeContextClose() throws Exception {
        when(consumer.receiveNoWait()).thenReturn(message);
        when(message.getText()).thenThrow(new JMSException("cannot decode"));
        assertThrows(JMSException.class, () -> session.getMessage("DEV.LAB.IN", Duration.ZERO, null));
        session.close();
        var order = inOrder(context);
        order.verify(context).rollback();
        order.verify(context).close();
    }

    @Test
    void oversizedReceivedBodyIsNotCommitted() throws Exception {
        when(consumer.receiveNoWait()).thenReturn(message);
        when(message.getText()).thenReturn("a".repeat(1048577));
        assertThrows(IllegalArgumentException.class,
                () -> session.getMessage("DEV.LAB.IN", Duration.ZERO, null));
        session.close();
        verify(context).rollback();
        verify(context, never()).commit();
    }

    @Test
    void uncertainCommitBlocksReplayAndPreservesUnknownAfterClose() throws Exception {
        session.putMessage("DEV.LAB.IN", "hello");
        doThrow(new JMSRuntimeException("connection lost during commit")).when(context).commit();
        assertThrows(JMSRuntimeException.class, session::commit);
        assertEquals(TransactionState.UNKNOWN, session.transactionState());
        assertThrows(java.lang.IllegalStateException.class,
                () -> session.putMessage("DEV.LAB.IN", "retry"));
        assertThrows(java.lang.IllegalStateException.class, session::backout);
        session.close();
        assertEquals(TransactionState.UNKNOWN, session.transactionState());
        verify(producer, times(1)).send(any(Queue.class), same(message));
        verify(context, never()).rollback();
    }

    @Test
    void knownRollbackFromCommitIsNotUnknown() throws Exception {
        session.putMessage("DEV.LAB.IN", "hello");
        doThrow(new TransactionRolledBackRuntimeException("rolled back by provider")).when(context).commit();
        assertThrows(TransactionRolledBackRuntimeException.class, session::commit);
        assertEquals(TransactionState.CLEAN, session.transactionState());
    }

    @Test
    void rollbackAndCloseFailuresAreBothReportedAndContextReleased() throws Exception {
        session.putMessage("DEV.LAB.IN", "hello");
        JMSRuntimeException rollbackFailure = new JMSRuntimeException("rollback failed");
        JMSRuntimeException closeFailure = new JMSRuntimeException("close failed");
        doThrow(rollbackFailure).when(context).rollback();
        doThrow(closeFailure).when(context).close();
        assertSame(rollbackFailure, assertThrows(JMSRuntimeException.class, session::close));
        assertArrayEquals(new Throwable[]{closeFailure}, rollbackFailure.getSuppressed());
        assertFalse(session.isConnected());
        session.close();
        verify(context, times(1)).close();
    }

    @Test
    void boundedBrowserDoesNotConsumeAndDoesNotClaimQueueDepth() throws Exception {
        QueueBrowser browser = mock(QueueBrowser.class);
        when(context.createBrowser(any(Queue.class), nullable(String.class))).thenReturn(browser);
        when(browser.getEnumeration()).thenReturn(Collections.enumeration(List.of(message, message, message)));
        assertEquals(new BrowseResult(2, true),
                session.browse("DEV.LAB.IN", 2, null));
        verify(browser).close();
        verifyNoInteractions(consumer);
        assertEquals(TransactionState.CLEAN, session.transactionState());
    }

    @Test
    void invalidArgumentsAreRejectedBeforeCallingProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> session.getMessage("DEV.LAB.IN", Duration.ofSeconds(61), null));
        assertThrows(IllegalArgumentException.class,
                () -> session.getMessage("DEV.LAB.IN", Duration.ofNanos(-1), null));
        assertThrows(IllegalArgumentException.class,
                () -> session.getMessage("DEV.LAB.IN", Duration.ZERO, "x' OR TRUE"));
        assertThrows(IllegalArgumentException.class,
                () -> session.putMessage("DEV.LAB.IN", "x".repeat(1048577)));
        verify(context, never()).createConsumer(any(Queue.class), anyString());
        verify(context, never()).createProducer();
    }

    @Test
    void sessionCannotBeSharedAcrossThreads() throws Exception {
        AtomicReference<Throwable> result = new AtomicReference<>();
        var thread = new Thread(() -> {
            try {
                session.commit();
            } catch (Throwable e) {
                result.set(e);
            }
        });
        thread.start();
        thread.join();
        assertInstanceOf(java.lang.IllegalStateException.class, result.get());
        verify(context, never()).commit();
    }

    @Test
    void explicitDisconnectAllowsNewLifecycleWithoutReplayingMessages() throws Exception {
        session.putMessage("DEV.LAB.IN", "pending");
        session.disconnect();
        session.connect();
        assertEquals(TransactionState.CLEAN, session.transactionState());
        assertTrue(session.isConnected());
        verify(context).rollback();
        verify(producer, times(1)).send(any(Queue.class), same(message));
    }
}
