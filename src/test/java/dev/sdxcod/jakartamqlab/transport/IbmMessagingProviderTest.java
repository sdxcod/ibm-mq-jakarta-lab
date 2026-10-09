package dev.sdxcod.jakartamqlab.transport;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQQueue;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import dev.sdxcod.jakartamqlab.config.MqSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IbmMessagingProviderTest {
    @Test
    void configuresIbmProviderWithoutOpeningConnection() throws Exception {
        var settings = new MqSettings(
                "localhost",
                1414,
                "QM1",
                "DEV.APP.SVRCONN",
                "app",
                "x",
                null,
                "DEV.LAB.IN",
                "DEV.LAB.OUT",
                "DEV.LAB.TEST",
                1048576);
        var factory = (MQConnectionFactory) IbmMessagingProvider.connectionFactory(settings);
        assertEquals("localhost", factory.getHostName());
        assertEquals(1414, factory.getPort());
        assertEquals("QM1", factory.getQueueManager());
        assertEquals("DEV.APP.SVRCONN", factory.getChannel());
        assertEquals(WMQConstants.WMQ_CM_CLIENT, factory.getTransportType());
        assertTrue(factory.getBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP));
        assertEquals(WMQConstants.WMQ_CLIENT_RECONNECT_DISABLED, factory.getClientReconnectOptions());
    }

    @Test
    void plainMqDestinationHasUtf8AndNoRfh2Target() throws Exception {
        var queue = (MQQueue) IbmMessagingProvider.queue("DEV.LAB.IN");
        assertEquals(1208, queue.getCCSID());
        assertEquals(WMQConstants.WMQ_CLIENT_NONJMS_MQ, queue.getTargetClient());
        assertThrows(IllegalArgumentException.class, () -> IbmMessagingProvider.queue("bad?targetClient=0"));
    }

    @Test
    void normalizesNativeAndJakartaMessageIdsAndRejectsInvalidSelectors() {
        String hex = "af".repeat(24), expected = "JMSMessageID = 'ID:" + hex.toUpperCase() + "'";
        assertEquals(expected, MessageIds.selector(hex));
        assertEquals(expected, MessageIds.selector("ID:" + hex));
        assertNull(MessageIds.selector(null));
        assertNull(MessageIds.selector(""));
        assertThrows(IllegalArgumentException.class, () -> MessageIds.selector("ID:' OR TRUE"));
    }
}
