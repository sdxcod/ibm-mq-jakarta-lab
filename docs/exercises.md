# Exercises: manual Jakarta Messaging

Use the running native broker and export its exact password-file path before starting.

1. Run `demo`, then follow `JakartaMqSession` calls: create context, producer send, consumer receive, explicit commit/rollback, close.
2. In one shell put one message in each of `in` and `out`, then commit once. Repeat with rollback. Explain why both queues are in one context transaction.
3. Open two shells. Put without commit in the first and attempt receive in the second. Commit the first, then retry the second. Do not use browse/depth as proof of transactional visibility.
4. Receive, rollback and receive the same ID. Compare `JMSRedelivered` and `JMSXDeliveryCount`. Then commit and verify the exact ID is absent.
5. Disconnect with pending send/receive and reconnect. Observe rollback on graceful close. This does not simulate or resolve a lost commit response.
6. Compare `browse --lab.browse-limit=1` with Web Console Current depth and explain `limitReached`. Browse never commits a destructive receive.
7. Run `receive --lab.wait=0s` against an empty queue. Inspect why `receiveNoWait()` is used instead of Jakarta `receive(0)`.
8. Commit a persistent message, restart **the existing broker from its original directory**, wait until healthy, then receive by ID.
9. Run live tests using `-Pmq-it`, then run the application container with `compose.existing.yaml` and the Kubernetes Job against the same broker installation.
10. Investigate a deliberately wrong app password with an ignored local environment override. Restore it; do not disable authentication.

## Native → Jakarta text interoperability

From the native project's directory, send:

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='Native to Jakarta | درود'
```

Copy its printed **48-hex message ID**. From the Jakarta project's directory, with its password env configured:

```bash
./scripts/lab.sh receive --lab.queue=in --lab.message-id='PASTE_NATIVE_48_HEX_ID'
```

The payload should match; Jakarta prints `ID:` followed by the same bytes (hex case may differ).

## Jakarta → Native text interoperability

From the Jakarta directory:

```bash
./scripts/lab.sh send --lab.queue=out --lab.message='Jakarta to Native | درود'
```

Copy the printed ID, **remove only the leading `ID:`**, then from the native directory:

```bash
./scripts/lab.sh receive --lab.queue=out --lab.message-id='PASTE_48_HEX_WITHOUT_PREFIX'
```

This is enabled by the Jakarta destination's non-JMS target mode and UTF-8 CCSID. Sending with a default JMS-compliant destination may add MQRFH2, which the native lab intentionally does not decode. Do not change both the target mode and reader at once when comparing behavior.

Record actual command output and observations for your portfolio. Use only your local lab; do not clear queues or run fault experiments against shared business environments.
