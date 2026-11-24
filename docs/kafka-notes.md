# Kafka Fundamentals & Candidate Event Contract

## 1. Phase 0 Lab Takeaways
- **Topics & Partitions:** A topic (`demo`) splits its append-only log across partitions (`0, 1, 2`) so multiple consumers in a group can read in parallel.
- **Key-Based Ordering:** Kafka hashes the message key (`murmur2(key) % partition_count`) so identical keys always land on the same partition. Ordering is guaranteed strictly *within* a single partition, never across partitions.
- **Consumer Groups & Scaling:** Within one consumer group (`g1`), each partition is assigned to at most one consumer (`max parallelism = partition count`). Independent consumer groups (`g1` vs. `g2`) maintain separate offsets in `__consumer_offsets` and each receive a full copy of all events.
- **Offsets & Replay:** Offsets are tracked per `(group, topic, partition)`. Stopping all consumers in a group allows resetting offsets (`--reset-offsets --to-earliest`) to replay historical events.
- **Rebalancing:** When a consumer leaves or joins a group, Kafka triggers a rebalance and reassigns partitions to surviving consumers, resuming from the last committed offset.

## 2. `candidate-events` Contract (v1)
- **Topic Name:** `candidate-events`
- **Partitions:** `3` (local development)
- **Replication Factor:** `1` (single-broker local development; production standard is `3` with `min.insync.replicas=2`)
- **Partition Key:** `String.valueOf(candidateId)` — ensures `CANDIDATE_CREATED`, `CANDIDATE_UPDATED`, and `CANDIDATE_DELETED` for the same candidate always route to the same partition in strict chronological order.

### JSON Payload Schema
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "CANDIDATE_CREATED",
  "occurredAt": "2026-11-25T10:15:30Z",
  "candidateId": 42,
  "version": 0,
  "requestId": "c9bf9e57-1685-4c89-bafb-ff5af830be8a",
  "payload": {
    "email": "asha@example.com",
    "firstName": "Asha",
    "lastName": "Rao",
    "status": "NEW",
    "skills": ["java", "spring"]
  }
}
```

### Event Types
- `CANDIDATE_CREATED` — emitted after `POST /api/v1/candidates` creates a record (`version = 0`).
- `CANDIDATE_UPDATED` — emitted after `PUT /api/v1/candidates/{id}` updates a record (carries incremented `version`).
- `CANDIDATE_DELETED` — emitted after `DELETE /api/v1/candidates/{id}` soft-deletes a record.

### Design Notes
- **`eventId` (UUID):** Enables downstream consumers (`audit-service`) to deduplicate retried deliveries for idempotent processing.
- **`version`:** Mirrors the candidate's optimistic-locking `@Version` so consumers can detect out-of-order states if needed.
- **`requestId`:** Propagates the HTTP `X-Request-Id` from MDC across asynchronous service boundaries for end-to-end log tracing.
