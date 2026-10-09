package dev.sdxcod.jakartamqlab.service;

import dev.sdxcod.jakartamqlab.transport.MqSession;
import dev.sdxcod.jakartamqlab.transport.ReceivedMessage;
import jakarta.jms.JMSException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    @Test
    void senderConnectsPutsCommitsAndClosesInOrder() throws Exception {
        MqSession s = mock(MqSession.class);
        when(s.putMessage("Q", "hello")).thenReturn("id");
        assertEquals("id", new MessageSender(() -> s).send("Q", "hello", Outcome.COMMIT));
        var order = inOrder(s);
        order.verify(s).connect();
        order.verify(s).putMessage("Q", "hello");
        order.verify(s).commit();
        order.verify(s).close();
    }

    @Test
    void receiverBacksOutOnlyWhenMessageExists() throws Exception {
        MqSession s = mock(MqSession.class);
        when(s.getMessage("Q", Duration.ZERO, null))
                .thenReturn(Optional.of(new ReceivedMessage(
                        "id",
                        "",
                        "x",
                        false,
                        1,
                        true)));
        assertTrue(new MessageReceiver(() -> s)
                .receive("Q", Duration.ZERO, null, Outcome.BACKOUT).isPresent());
        verify(s).backout();
        verify(s, never()).commit();
        verify(s).close();
    }

    @Test
    void receiverClosesAfterDecodeFailureAndDoesNotCommit() throws Exception {
        MqSession s = mock(MqSession.class);
        when(s.getMessage("Q", Duration.ZERO, null))
                .thenThrow(new JMSException("decode failed"));
        assertThrows(JMSException.class, () -> new MessageReceiver(() -> s)
                .receive("Q", Duration.ZERO, null, Outcome.COMMIT));
        verify(s).close();
        verify(s, never()).commit();
    }
}
