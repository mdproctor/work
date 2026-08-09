# Design Journal — issue-881-agentic-planning

## 2026-08-09 — Architecture decision: engine-hosted pattern execution

**Decision:** Blocks DSL produces ExecutionModel; engine provides durability via
WorkerFunctionHandler SPI boundary. New `casehub-engine-agentic` module bridges
the two.

**Four options evaluated from first principles:**
1. Blocks owns execution — rejected (duplicates engine persistence/retry/recovery)
2. Engine owns execution (DSL compiles to primitives) — rejected (impedance mismatch:
   blocks' iterative 5-SPI model vs engine's reactive binding dispatch; no aggregation
   equivalent in engine)
3. Clean split by pattern shape — partially right (workflow vs agentic distinction is
   real) but incomplete (doesn't specify how agentic patterns get durability)
4. **Engine as host, blocks driver as worker** — chosen. OrchestratedDriver runs inside
   PatternWorkerFunctionHandler. EngineAgentInvoker bridges AgentRef to
   WorkerRuntime.execute(). ExternalAgent stays direct (no engine overhead).

**Key design review findings (light, 3 dimensions + cross-cutting, 38 issues):**
- WorkerFunctionHandler.execute() takes (WorkerContext, timeoutMs, ExecutionMetadata),
  not WorkerScope — handler creates its own WorkerRuntime via WorkerRuntimeFactory
- Re-planning scoped to HTN patterns only — iteration-based driver has no "plan" to revise
- ReplanPolicy is a separate field on FailurePolicy, not a new RoutingFailureAction
- Engine retry handles per-agent retry; blocks replan handles plan-level recovery
- Checkpoint driverState uses string keys (AgentRef not serializable)
- WorkerRuntime.execute() has no deduplication — mid-iteration crash re-runs the iteration

**Phase 1 (#886) implemented:** PatternWorkerFunction, PatternWorkerFunctionHandler,
EngineAgentInvoker, PatternWorkerFunctionProvider, EngineHostedBackend + ServiceLoader.
21 tests (5 unit classes + 1 integration), all green.
