package dev.sdxcod.jakartamqlab.transport;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQQueue;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import dev.sdxcod.jakartamqlab.config.MqSettings;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;

/**
 * IBM-specific configuration; messaging operations use Jakarta interfaces.
 */
public final class IbmMessagingProvider {
    private IbmMessagingProvider() {
    }

    public static ConnectionFactory connectionFactory(MqSettings settings) throws JMSException {
        var factory = new MQConnectionFactory();
        factory.setHostName(settings.host());
        factory.setPort(settings.port());
        factory.setQueueManager(settings.queueManager());
        factory.setChannel(settings.channel());
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        factory.setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT_DISABLED);
        return factory;
    }

    public static Queue queue(String name) throws JMSException {

        if (name == null || !name.matches("[A-Za-z0-9._/%]{1,48}"))
            throw new IllegalArgumentException("Queue name must be a valid 1..48 character MQ object name");

        var queue = new MQQueue(name);
        queue.setTargetClient(WMQConstants.WMQ_CLIENT_NONJMS_MQ);
        queue.setCCSID(1208);
        // This describes an existing queue; it does not DEFINE QLOCAL.
        return queue;
    }
}
