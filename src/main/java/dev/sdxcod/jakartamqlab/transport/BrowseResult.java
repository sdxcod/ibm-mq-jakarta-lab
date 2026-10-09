package dev.sdxcod.jakartamqlab.transport;

/** limitReached means more messages MAY exist; this is never exact MQ queue depth. */
public record BrowseResult(int observedMessages, boolean limitReached) { }
