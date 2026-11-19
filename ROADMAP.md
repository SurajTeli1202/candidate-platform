# Roadmap

Extending the candidate API into a small event-driven system with Kafka.
The `v1-baseline` tag is the starting point.

- [ ] **Phase 0: Kafka concepts.** CLI experiments, notes in `docs/kafka-notes.md`
- [ ] **Phase 1: First producer and consumer.** docker-compose, `spring-kafka`,
  naive publish from the service, a second app with `@KafkaListener`
- [ ] **Phase 2: Transactional outbox.** Outbox table, poller, PostgreSQL profile,
  idempotent consumers
- [ ] **Phase 3: Real consumers.** Audit service, consumer groups, partitioning by candidate id
- [ ] **Phase 4: Failure handling.** Retries, backoff, dead-letter topic, replay, lag metrics
- [ ] **Phase 5: Tests and CI.** Testcontainers, Awaitility, GitHub Actions
- [ ] **Stretch.** Search read model, Avro and schema registry, Kafka Streams, Kubernetes