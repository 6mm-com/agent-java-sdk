# Changelog

## 0.3.0 — 2026-10-10

- Add six signed perpetual-query methods, request/response models and a command-line usage example.
- Validate public player IDs and filters; sign array parameters as compact JSON; retain decimal strings and opaque cursors.
- Document shared rate limits, explicit retries and asynchronous history limitations.
- Sync production wallet-transfer methods and execution-environment fields; preserve existing defaults and signing behavior.
- Perpetual queries require the server-side feature to be deployed and enabled. They do not accept an environment selector.

The production Agent endpoints were enabled and checked with this SDK on 2026-10-10. This is a GitHub source release; no Maven Central publication is claimed. Historical completeness still depends on the server's retained data and asynchronous ingestion.
