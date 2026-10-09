package dev.sdxcod.jakartamqlab.transport;

import java.util.Locale;

/**
 * IBM JMS IDs and native MQ hex IDs; selector injection is rejected.
 */
public final class MessageIds {
    private MessageIds() {
    }

    public static String selector(String id) {
        if (id == null || id.isBlank())
            return null;
        String hex = id.startsWith("ID:") ? id.substring(3) : id;

        if (!hex.matches("[0-9a-fA-F]{48}"))
            throw new IllegalArgumentException("IBM JMSMessageID must be ID: plus 48 hex characters (or native 48-hex ID)");

        return "JMSMessageID = 'ID:" + hex.toUpperCase(Locale.ROOT) + "'";
    }
}
