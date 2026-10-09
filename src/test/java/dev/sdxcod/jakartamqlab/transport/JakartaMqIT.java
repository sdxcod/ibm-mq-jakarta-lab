package dev.sdxcod.jakartamqlab.transport;

import dev.sdxcod.jakartamqlab.config.MqSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt in with -Pmq-it against the Docker MQ. Never clears a queue or consumes unrelated messages.
 */
class JakartaMqIT {
    private final MqSettings settings = new MqSettings(
            env("LAB_MQ_HOST", "localhost"),
            Integer.parseInt(env("LAB_MQ_PORT", "1414")),
            env("LAB_MQ_QMGR", "QM1"),
            env("LAB_MQ_CHANNEL", "DEV.APP.SVRCONN"),
            env("LAB_MQ_USER", "app"), env("LAB_MQ_PASSWORD", ""),
            Path.of(env("LAB_MQ_PASSWORD_FILE", ".secrets/mqAppPassword")),
            "DEV.LAB.IN",
            "DEV.LAB.OUT",
            env("LAB_MQ_TEST_QUEUE", "DEV.LAB.TEST"),
            1048576);
    private final List<String> ownIds = new ArrayList<>();

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private JakartaMqSession open() throws Exception {
        var s = new JakartaMqSession(settings);
        s.connect();
        return s;
    }

    @AfterEach
    void removeOnlyOwnMessages() throws Exception {
        try (var s = open()) {
            for (String id : ownIds) s.getMessage(settings.testQueue(), Duration.ZERO, id);
            s.commit();
        }
    }

    @Test
    void committedPersistentUtf8MessageCanBeReadAndRemoved() throws Exception {
        String text = "درود IBM MQ | " + UUID.randomUUID();
        String id;
        try (var s = open()) {
            id = s.putMessage(settings.testQueue(), text);
            ownIds.add(id);
            s.commit();
            assertTrue(s.isConnected());
            assertEquals(1, s.browse(settings.testQueue(), 10, id).observedMessages());
        }
        try (var s = open()) {
            var m = s.getMessage(settings.testQueue(), Duration.ofSeconds(3), id).orElseThrow();
            assertEquals(text, m.text());
            assertEquals(id, m.messageId());
            assertTrue(m.persistent());
            s.commit();
        }
        try (var s = open()) {
            assertTrue(s.getMessage(settings.testQueue(), Duration.ZERO, id).isEmpty());
        }
    }

    @Test
    void putBackoutDoesNotPublishMessage() throws Exception {
        String id;
        try (var s = open()) {
            id = s.putMessage(settings.testQueue(), UUID.randomUUID().toString());
            ownIds.add(id);
            s.backout();
        }
        try (var s = open()) {
            assertTrue(s.getMessage(settings.testQueue(), Duration.ZERO, id).isEmpty());
        }
    }

    @Test
    void getRollbackReturnsMessageWithRedeliveryMetadata() throws Exception {
        String id;
        try (var s = open()) {
            id = s.putMessage(settings.testQueue(), "redelivery");
            ownIds.add(id);
            s.commit();
        }
        try (var s = open()) {
            assertTrue(s.getMessage(settings.testQueue(), Duration.ofSeconds(3), id).isPresent());
            s.backout();
        }
        try (var s = open()) {
            var again = s.getMessage(settings.testQueue(), Duration.ofSeconds(3), id).orElseThrow();
            assertTrue(again.redelivered() && again.deliveryCount() >= 2);
            s.commit();
        }
    }

    @Test
    void closeWithoutCommitRollsBackPut() throws Exception {
        String id;
        try (var s = open()) {
            id = s.putMessage(settings.testQueue(), "unfinished");
            ownIds.add(id);
        }
        try (var s = open()) {
            assertTrue(s.getMessage(settings.testQueue(), Duration.ZERO, id).isEmpty());
        }
    }
}
