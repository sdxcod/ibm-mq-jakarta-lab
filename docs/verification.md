## Executed successfully

- Java 21 / Maven 3.9.9 `verify`: **21 unit tests, 0 failures, 0 errors, 0 skipped**. Spring Boot 4.1.1, IBM Jakarta provider 9.4.0.25 and Jakarta Messaging API 3.0.0 resolved from Maven Central.
- The `mq-it` Maven profile built successfully with `-DskipITs`; this verifies the profile configuration, not live integration behavior.
- Maven Enforcer's Java and dependency rules passed. Source uses Jakarta interfaces; no Spring JMS transport, `javax.jms` API or native `allclient` dependency is present. IBM's provider JAR contains internal IBM MQ machinery, which is expected.
- The source ZIP was extracted into a new directory and built with its Maven Wrapper: again **21 unit tests passed**; scripts retained executable permissions and the archive includes no build outputs or secrets.
- Executable JAR help and interactive shell startup/status/quit passed.
- A real provider connection attempt to an intentionally closed local port exited nonzero and reported `JMSWMQ0018`, with IBM MQ reason 2538 in the cause chain. This verifies provider loading and error propagation; it is not a successful connection test.
- Both standalone and existing-broker Compose files passed `docker-compose config -q` (Compose v2.39.4, configuration only).
- YAML files parsed and Bash scripts passed syntax checks. MQSC copies for Compose/Kubernetes are identical.
- Kustomize rendered the MQ resources. Kubeconform validated the rendered resources plus the Jakarta demo Job against Kubernetes 1.34.0: **5 valid, 0 invalid, 0 errors, 0 skipped**. This is schema validation, not deployment.

## Not executed in this preparation environment

- Live MQ demo or integration tests: Docker daemon/engine is unavailable.
- Native ↔ Jakarta message exchange against a real MQ server.
- Application Docker image build/start or actual Kubernetes deployment.
- GitHub Actions run.

The source includes 4 opt-in live MQ integration tests, a 3-scenario demo and CI with Docker MQ. Run these on your own ready broker using its existing password. Tests/demo select their own IDs and never purge queues. The native lab's successful local results do not establish that this new Jakarta transport has passed live tests.

## Run locally

```bash
export LAB_MQ_PASSWORD_FILE="$PWD/.secrets/mqAppPassword"
./mvnw -B -ntp verify
./scripts/lab.sh demo
./mvnw -B -ntp -Pmq-it verify
```

For bidirectional native/Jakarta text compatibility, follow `docs/exercises.md`. Record actual results before citing them in a portfolio.
