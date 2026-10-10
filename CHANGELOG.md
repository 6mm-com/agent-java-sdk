# Changelog

## 0.3.1 — 2026-10-10

- Documentation patch only: Java sources, dependencies and API behavior are unchanged from 0.3.0.
- Include the complete public SDK method overview, grouped by integration workflow, in both READMEs.
- Add merchant guidance for seconds trading, Funding wallets, simulated environments and upgrades from older SDKs.
- Add the perpetual-query response-field reference and update stable-tag installation examples to 0.3.1.
- Existing 0.3.0 users do not need to upgrade solely for documentation changes. This remains a GitHub source release; no Maven Central publication is claimed.

## 0.3.0 — 2026-10-10

- Add six signed perpetual-query methods, request/response models and a command-line usage example.
- Validate public player IDs and filters; sign array parameters as compact JSON; retain decimal strings and opaque cursors.
- Document shared rate limits, explicit retries and asynchronous history limitations.
- Sync production wallet-transfer methods and execution-environment fields; preserve existing defaults and signing behavior.
- Perpetual queries require the server-side feature to be deployed and enabled. They do not accept an environment selector.

The production Agent endpoints were enabled and checked with this SDK on 2026-10-10. This is a GitHub source release; no Maven Central publication is claimed. Historical completeness still depends on the server's retained data and asynchronous ingestion.
