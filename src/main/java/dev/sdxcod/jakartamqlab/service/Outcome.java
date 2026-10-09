package dev.sdxcod.jakartamqlab.service;

import dev.sdxcod.jakartamqlab.transport.MqSession;
import jakarta.jms.JMSException;

import java.util.Locale;

public enum Outcome {
    COMMIT, BACKOUT;

    public static Outcome parse(String value) {
        return value.equalsIgnoreCase("rollback") ? BACKOUT : valueOf(value.toUpperCase(Locale.ROOT));
    }

    public void complete(MqSession session) throws JMSException {
        if (this == COMMIT)
            session.commit();
        else session.backout();
    }
}
