# IBM MQ Jakarta Messaging Lab

A Java 21 agent lab using **application-managed Jakarta Messaging** and the IBM MQ provider. Package: `dev.sdxcod.jakartamqlab`. A companion to `ibm-mq-native-lab`, with the same queue manager, queues, CLI and explicit transaction exercises.

Spring Boot only starts the CLI, binds settings and wires sender/receiver components. Application code creates/closes `JMSContext`, calls `JMSProducer.send()` and `JMSConsumer.receive()`, and explicitly calls `commit()` / `rollback()`. No `spring-jms`, IBM Spring starter, `JmsTemplate`, listener container, `@JmsListener`, Spring transaction manager or `@Transactional` is used.

## Stack

| Component               | Version / role                                          |
|-------------------------|---------------------------------------------------------|
| Java                    | 21 LTS                                                  |
| Spring Boot             | 4.1.1; bootstrap, property binding and wiring           |
| Maven Wrapper           | 3.9.9                                                   |
| IBM provider            | `com.ibm.mq:com.ibm.mq.jakarta.client:9.4.0.25`         |
| Messaging API           | 3.0.0; explicitly pinned to IBM provider's declared API |
| Optional Docker MQ      | `icr.io/ibm-messaging/mq:9.4.0.25-r2`                   |
| Queue manager / channel | `QM1` / `DEV.APP.SVRCONN`                               |
| Queues                  | `DEV.LAB.IN`, `DEV.LAB.OUT`, `DEV.LAB.TEST`             |

The Jakarta API is a contract, not an MQ network driver. IBM's provider implements that contract. IBM-specific connection and destination settings are isolated in `IbmMessagingProvider`; operational code uses `jakarta.jms` interfaces. Enforcer rejects Spring JMS, the IBM Spring starter, legacy `javax.jms` and the old `allclient` artifact.

## Quick start: reuse your running MQ

Prerequisites: JDK 21, Bash and the native lab's running broker. Maven is downloaded by the wrapper. Windows: use WSL2 with Docker Desktop integration, or run the JAR with Java directly. On macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## Run locally

Prerequisites: JDK 21, Docker with Docker Compose v2 (`--wait` support), Bash, OpenSSL, and internet access for dependencies/images. Maven is downloaded by the wrapper; it need not be installed. On Windows, use WSL2 with Docker Desktop integration. Run commands from the repository root. On macOS select Java 21 with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` and include `$JAVA_HOME/bin` in `PATH`.

```bash
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./scripts/lab.sh demo
./scripts/lab.sh send --lab.queue=in --lab.message='درود Jakarta Messaging'
./scripts/lab.sh receive --lab.queue=in
./scripts/lab.sh shell
```

Adjust the absolute password-file path if the native project lives elsewhere. Existing defaults connect to `localhost:1414`, `QM1`, channel `DEV.APP.SVRCONN`, user `app`. No server configuration changes or new queues are required. **Do not start this project's `mq` service while the previous one occupies ports 1414/9443. Do not generate a new password and expect it to authenticate against your existing broker.**

The demo uses exact IDs of its own messages on `DEV.LAB.TEST`. It checks canceled send, committed UTF-8 send, receive/rollback/redelivery and committed removal. Expected: three `PASS:` lines. It never purges a queue.

## Commands and manual transaction shell

```bash
./scripts/lab.sh send --lab.queue=out --lab.message='discard this' --lab.outcome=rollback
./scripts/lab.sh send --lab.queue=out --lab.message='read twice'
./scripts/lab.sh receive --lab.queue=out --lab.outcome=backout
./scripts/lab.sh receive --lab.queue=out --lab.outcome=commit
./scripts/lab.sh browse --lab.queue=in --lab.browse-limit=100
./scripts/lab.sh receive --lab.queue=in --lab.wait=0s --lab.message-id='ID:PUT_THE_48_HEX_HERE'
```

`backout` and `rollback` are equivalent CLI outcome names; both call Jakarta `JMSContext.rollback()`. Every one-shot command opens/closes its own context. For a unit of work containing several puts/gets, use `shell`:

```text
connect
status
put in Hello from Jakarta
put out A second pending message
commit
get in
rollback
get in
commit
browse out
get out
commit
disconnect
quit
```

Shell `put` and `get` remain pending until commit/rollback. Both queues participate in the **same context transaction**. `quit`, EOF or disconnect rolls back known pending work. `JMSContext` is confined to the creating thread; create independent contexts for concurrent workers.

`--lab.wait=0s` uses `receiveNoWait()`. A positive wait uses `receive(milliseconds)` with a maximum of 60 seconds. Jakarta `receive(0)` means wait forever; this lab deliberately does not pass zero to that method. The consumer is closed after each receive while the context stays open until completion. Closing the consumer is not a commit.

`send` returns IBM's `JMSMessageID`: `ID:` plus 48 hexadecimal characters. `--lab.message-id` accepts that form or the 48 hex characters returned by the native lab, and builds a validated exact-ID selector. The `ID:` prefix is a representation of the same IBM MQ message ID, not a new UUID generated by this application.

## Browser versus queue depth

Jakarta Messaging has **no standard queue-depth inquiry**. `browse` counts up to `--lab.browse-limit` (default 100, max 5000) using `QueueBrowser`. It reports `observedMessages` and `limitReached`. Reaching the limit means additional messages **may** exist. Browsing under concurrent activity is not a stable snapshot or a guaranteed count of messages another consumer can receive.

For familiar CLI usage, `depth` is an alias for `browse`; it is **not** native MQ `Current depth`. Use Web Console or MQSC for that metric:

```bash
# Run from the NATIVE project's directory when reusing its broker:
docker compose exec -T mq runmqsc QM1 <<'MQSC'
DISPLAY QLOCAL('DEV.LAB.IN') CURDEPTH
DISPLAY QLOCAL('DEV.LAB.OUT') CURDEPTH
MQSC
```

## Wire compatibility with the native lab

`IbmMessagingProvider.queue()` sets `targetClient=MQ` (`WMQ_CLIENT_NONJMS_MQ`) and CCSID 1208. This suppresses the JMS MQRFH2 header for this simple text scenario, so committed messages can be exchanged with the native lab's `MQFMT_STRING` transport. Queue objects describe an existing destination; constructing one does not create a physical queue.

Try native send → Jakarta receive, and Jakarta send → native receive. Use the matching ID so you do not consume unrelated messages. Detailed commands are in [exercises](docs/exercises.md). This plain MQ mode does not promise preservation of every custom JMS property; it is a deliberate interoperable text baseline. The app reports `JMSRedelivered` and `JMSXDeliveryCount`; these are not the same field as native `BackoutCount` (delivery count starts at 1).

## Configuration

Defaults are in [application.yml](src/main/resources/application.yml). Command-line overrides such as `--lab.mq.port=1514` work.

| Variable                                                     | Default                                       |
|--------------------------------------------------------------|-----------------------------------------------|
| `LAB_MQ_HOST` / `LAB_MQ_PORT`                                | `localhost` / `1414`                          |
| `LAB_MQ_QMGR` / `LAB_MQ_CHANNEL`                             | `QM1` / `DEV.APP.SVRCONN`                     |
| `LAB_MQ_USER`                                                | `app`                                         |
| `LAB_MQ_PASSWORD_FILE`                                       | `.secrets/mqAppPassword`;                     |
| `LAB_MQ_PASSWORD`                                            | Empty; optional override of the password file |
| `LAB_MQ_IN_QUEUE` / `LAB_MQ_OUT_QUEUE` / `LAB_MQ_TEST_QUEUE` | `DEV.LAB.IN` / `DEV.LAB.OUT` / `DEV.LAB.TEST` |

Passwords are read exactly, including any newline in a manually created file. Prefer the original setup script's file (no trailing newline). `.secrets`, `.env` and `target` are ignored by Git and Docker build contexts. The config record redacts passwords. The send/receive services and transport have no Spring messaging annotations.

## Tests

```bash
./mvnw -B -ntp verify                 # No broker required
./mvnw -B -ntp -Pmq-it verify         # Live MQ required, same password env variable
./mvnw -B -ntp dependency:tree        # Inspect Jakarta provider/API and dependency guard
```

Unit tests cover explicit completion, empty receive/no-wait, exact selector validation, rollback after unreadable/oversized messages, resource cleanup, uncertain commit, known provider rollback, bounded browsing and thread confinement. Live tests cover persistent Persian text, committed removal, send rollback, receive rollback/redelivery and rollback on close. Live tests select only messages they created; they never clear a queue. CI has unit and live Docker MQ jobs. See [verification record](docs/verification.md) for what was actually executed while preparing this archive.

## Run the app container against the same existing broker

The existing broker's Compose project has the name `ibm-mq-native-lab`; its default network is `ibm-mq-native-lab_default`. `compose.existing.yaml` joins that external network and uses service DNS `mq`, so this works without host-network or loopback routing tricks.

```bash
export LAB_MQ_PASSWORD_FILE="$PWD/.secrets/mqAppPassword"
# Override LAB_MQ_DOCKER_NETWORK if you used a different original Compose project name.
docker compose -f compose.existing.yaml build agent
docker compose -f compose.existing.yaml run --rm agent
docker compose -f compose.existing.yaml run --rm agent --lab.command=shell
```

Use `docker network ls` to inspect your actual network name. These commands create only the application container; the existing broker and its data volume remain in their original project. The application is a CLI, exits after its command and has no HTTP port. Rebuild the JAR after source changes: `./mvnw verify`. `scripts/lab.sh` reuses an existing built JAR.

## Optional independent MQ installation

Use this only when ports 1414/9443 are free and you want a separate server. Unset the previous password path first:

```bash
unset LAB_MQ_PASSWORD_FILE
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./scripts/lab.sh demo
docker compose --profile app build agent
docker compose --profile app run --rm agent
```

The setup script preserves existing local secrets. MQ uses mounted `mqAppPassword` / `mqAdminPassword` files and `MQ_CONNAUTH_USE_HTP=true`, as in the native lab. It needs no security bypass. `LICENSE=accept` accepts the IBM developer image's terms; review those terms. This is local development infrastructure, not a production cluster. The image is amd64; Apple Silicon uses emulation. Kubernetes MQ requires amd64 nodes. Allow roughly 4 GiB for a local Docker/Kubernetes VM.

`docker compose down` stops this independent stack and preserves its volume. `docker compose down -v` deliberately deletes **this** stack's MQ data; use only for an intended reset. For a reused broker, manage its lifecycle from the native project's directory.

## Web Console

Open [https://localhost:9443/ibmmq/console](https://localhost:9443/ibmmq/console). Login: `admin`, password from the **broker-owning project's** `.secrets/mqAdminPassword`. The local HTTPS certificate is self-signed. Open `QM1` → Queues → `DEV.LAB.IN` / `DEV.LAB.OUT` / `DEV.LAB.TEST`, then refresh to inspect current depth and available browse options. UI labels can vary by image version. The console shows MQ state, not this CLI's command history. See the full Persian walkthrough in [quickstart-fa](docs/quickstart-fa.md).

## Kubernetes

To reuse MQ already installed by the native lab, load the new agent image and apply **only** the new Job. It uses the existing `ibm-mq-lab` namespace, `mq` service and `mq-lab-passwords` Secret:

```bash
docker build -t ibm-mq-jakarta-lab:0.1.0 .
kind load docker-image ibm-mq-jakarta-lab:0.1.0 --name mq-lab
kubectl apply -f k8s/demo-job.yaml
kubectl -n ibm-mq-lab wait --for=condition=complete job/jakarta-mq-demo --timeout=180s
kubectl -n ibm-mq-lab logs job/jakarta-mq-demo
```

For minikube: `minikube image load ibm-mq-jakarta-lab:0.1.0`. `imagePullPolicy: Never` uses your local image; Job `backoffLimit: 0` avoids automatic command replay. Delete only the old Job before rerunning it. A fresh cluster may install the optional MQ StatefulSet/PVC via `./scripts/k8s-up.sh`; the script preserves an existing Kubernetes password Secret. A default storage class and amd64 node are required. Do not replace the existing broker's Secret with newly generated passwords.

Host access to Kubernetes MQ: `kubectl -n ibm-mq-lab port-forward svc/mq 1514:1414`, then `--lab.mq.port=1514` with the matching password. Console forwarding: `kubectl -n ibm-mq-lab port-forward svc/mq 9444:9443`, then open `https://localhost:9444/ibmmq/console`. The Compose and Kubernetes MQSC copies must remain identical. Deleting `ibm-mq-lab` also affects the native lab's resources if shared.

## Architecture, learning and GitHub

- [Architecture and API comparison](docs/architecture.md)
- [Exercises, including bidirectional interoperability](docs/exercises.md)
- [Persian quickstart and Web Console](docs/quickstart-fa.md)
- [Verification record](docs/verification.md)

Portfolio statement: “Built a Java 21/Spring Boot IBM MQ lab using application-managed Jakarta Messaging contexts, explicit local transactions, native/Jakarta text interoperability, Docker Compose, Kubernetes Jobs and unit/integration tests.” Claim live integration/CI results after you run them.

Source code is MIT licensed. Maven Wrapper retains Apache 2.0 licensing (`LICENSES/Apache-2.0.txt`). IBM artifacts retain IBM licenses and are downloaded as dependencies; no IBM binaries are redistributed in the source ZIP.

## Official references

- [IBM Jakarta connection factory](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=messaging-mqconnectionfactory)
- [Jakarta JMSContext](https://jakarta.ee/specifications/messaging/3.0/apidocs/jakarta/jms/jmscontext)
- [Jakarta QueueBrowser](https://jakarta.ee/specifications/messaging/3.0/apidocs/jakarta/jms/queuebrowser)
- [IBM MQDestination settings](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=messaging-mqdestination)
- [IBM developer container / Console](https://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md)
- [IBM developer secret authentication](https://github.com/ibm-messaging/mq-container/blob/master/docs/pluggable-connauth.md)
