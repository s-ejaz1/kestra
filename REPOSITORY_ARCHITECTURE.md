# Kestra Repository Architecture

Analysis of the Kestra Open Source Edition repository, version `2.1.0-SNAPSHOT` (`gradle.properties`), branch `dev` at commit `f6dbbb49f`.

**How to read the paths.** Java sources live in `<module>/src/main/java/<package path>`. Tests live in `<module>/src/test/java/...`. When a path below is written as `core/.../services/FlowService.java`, the `...` stands for `src/main/java/io/kestra/core`. Section 25 gives full paths for the most important classes.

**How this was verified.** Every component below was traced by reading its source, not inferred from file names. Items that were only partly traced are marked **Needs Further Investigation (NFI)** and are collected in section 28. Line numbers are deliberately omitted because they drift. Search for the class and method names instead.

---

## 1. Repository Overview

Kestra is an event-driven orchestration platform. A user declares a **flow** in YAML: a list of **tasks** plus optional **triggers**. Kestra creates an **execution** for each run, and the tasks inside it run on **workers**.

This repository is the open-source (OSS) edition. It is a monorepo with three parts:

- **Gradle multi-project backend**, 22 modules: Java 25 on Micronaut 5.
- **Vue 3 single-page app** in `ui/`, an npm workspace with five internal packages.
- **Playwright end-to-end suite** in `e2e/`, a separate npm package.

The 2.x architecture is significantly different from older Kestra versions (1.x), so older blog posts and docs can be misleading:

- Executions are created by sending **`ExecutionCommand`** messages to a queue, not by writing an `Execution` directly.
- Workers do **not** read the queue. They pull jobs from the **worker-controller** over **gRPC**.
- There is one generic **`queues`** table. The older per-queue tables and the `executorstate` table are gone.
- Database migrations use a **custom migration framework**, not Flyway.
- Some features exist only in the Enterprise Edition (EE). The OSS code exposes interfaces and stubs where EE plugs in (section 27).

Repository-level guidance for contributors is in `AGENTS.md` (conventions, test policy, build commands) and `ui/AGENTS.md` (design-system rules). `CLAUDE.md` only includes `AGENTS.md`.

---

## 2. Technology Stack

Every version below was read from `platform/build.gradle` (the BOM), the root `build.gradle`, or `ui/package.json`.

### Backend
| Concern | Technology |
|---|---|
| Language | Java 25 (toolchain in root `build.gradle`). Lombok 1.18.48 |
| Framework | Micronaut 5.1.5 (`io.micronaut.platform:micronaut-platform`) with Netty HTTP server and Picocli CLI |
| Serialization | Jackson 2.22.3 for Kestra's own `JacksonMapper`; Jackson 3 (`tools.jackson`) pulled in by Micronaut 5; Jackson Ion for KV values |
| Templating | Pebble 4.1.2 (expressions such as `{{ outputs.x }}`) |
| Persistence | jOOQ 3.21.9 (through `micronaut-jooq`, dynamic SQL, no code generation), HikariCP |
| Databases | H2 2.5.252, MySQL (`mysql-connector-j` 26.7.0), PostgreSQL (`postgresql` 42.7.13) |
| Migrations | Custom framework (`core/.../migration/MigrationRunner`) plus SQL files. Not Flyway |
| Messaging | Kestra's own queue abstraction (`queue` module) over a JDBC `queues` table (`queue-jdbc`). gRPC 1.84.0 with protobuf 4.36.1 between the worker and the worker-controller |
| Containers in tasks | docker-java (in `script`) for the `Docker` task runner |
| AI / MCP | langchain4j 1.18.1, MCP SDK 1.1.3 (AI copilot, MCP server in `webserver`) |
| Observability | Micrometer (Prometheus endpoint), OpenTelemetry (disabled by default), logback |
| Other | Guava 33.7.1, slf4j 2.0.20 |

### Frontend (`ui/`)
| Concern | Technology |
|---|---|
| Runtime | Node >= 24, npm >= 11.7 (`engine-strict=true`, `packageManager: npm@11.16.0`, `.nvmrc` = 24) |
| Framework | Vue ^3.5, vue-router ^5.1, Pinia ^3, vue-i18n ^11 |
| UI kit | Element Plus (>= 2.13.5), wrapped by `@kestra-io/design-system` as `Ks*` components |
| Build | Vite ^8, TypeScript ^6, vue-tsc, `@module-federation/vite`, vite-plugin-pwa |
| Editors and charts | monaco-editor, monaco-yaml, echarts ^6, @vue-flow |
| API client | `@kestra-io/kestra-sdk`, generated from `openapi.yml` with `@hey-api/openapi-ts` (fetch client) |
| Tests | Vitest ^4 (jsdom and browser modes), Storybook ^10, @vue/test-utils, Playwright ^1.61 |
| Lint | oxlint, eslint 10, stylelint 16 |

### Build, test and infrastructure
- **Gradle 9.8.0** (`gradle/wrapper/gradle-wrapper.properties`). There is no `libs.versions.toml`; all versions are pinned in the `platform` BOM.
- **Java testing:** JUnit 5, Micronaut Test, Mockito, AssertJ, Hamcrest, Awaitility, WireMock, ArchUnit, junit-pioneer, Allure, JaCoCo. Testcontainers is used in `core` tests only.
- **Containers and deploy:** Docker (`Dockerfile*`, base image `eclipse-temurin:25-jre-noble`), Docker Compose, Helm charts in `charts/`.
- **CI:** GitHub Actions in `.github/workflows/`. Most heavy jobs call reusable workflows from the `kestra-io/actions` repository.

---

## 3. Repository Structure

### Gradle modules (`settings.gradle`)
| Module | Responsibility |
|---|---|
| `platform` | Bill of materials. Pins every third-party version. Consumed by every module as `enforcedPlatform(project(":platform"))`. No sources. |
| `model` | Tiny API jar: `io.kestra.core.models.Plugin` and the plugin annotations (`@Plugin`, `@PluginProperty`, `@Example`, `@Metric`, `@PluginSubGroup`). Re-exported by `core` through `api`. |
| `processor` | Java annotation processor (`PluginProcessor`). Writes `META-INF/services/io.kestra.core.models.Plugin` for every concrete `@Plugin` class, which is how plugins are discovered at runtime. |
| `core` | The domain: models (Flow, Execution, TaskRun, State, triggers), repository, queue and storage **interfaces**, services (`FlowService`, `ExecutionService`, …), the run context and Pebble runtime, plugin loading, migrations, secrets, KV, tenant and namespace services, and **all built-in core plugins** (`io.kestra.plugin.core.*`). |
| `script` | Base classes for script tasks (`AbstractExecScript`) and the `Docker` task runner. |
| `queue` | Backend-agnostic queue framework: dispatch, broadcast, keyed and vnode queue semantics, pollers, `QueueFactoryInterface`. |
| `queue-jdbc` | JDBC implementation of the queues on the single `queues` table (`JdbcQueueFactory`, `JdbcQueueClient`, `JdbcQueueCleaner`). |
| `jdbc` | Generic jOOQ repositories (`AbstractJdbc*Repository`), executor and scheduler state stores, the JDBC migration history store and the shared Java migrations. |
| `jdbc-h2` / `jdbc-mysql` / `jdbc-postgres` | Dialect subclasses of every repository and queue, plus the per-dialect SQL migrations in `src/main/resources/migrations/`. |
| `repository-memory` | The "memory" backend. It is embedded H2 (`jdbc:h2:mem:public`), created by `DatasourceProvider` when no datasource is configured. |
| `runner-memory` | Aggregator only (`api project(":repository-memory")`). No main sources. |
| `storage-local` | Local-filesystem internal storage plugin (`LocalStorage`, plugin id `local`). |
| `executor` | The execution state machine (`DefaultExecutor`, `ExecutorCore`, `ExecutorService`, message handlers), flow triggers, concurrency limits, delays, SLA and liveness coordination. |
| `scheduler` | Trigger evaluation loops, partitioned by vNodes (`DefaultScheduler`, `TriggerScheduler`, `TriggerEventHandler`). |
| `worker-controller` | gRPC server that workers connect to. It dispatches worker jobs (`WorkerJobDispatcher`) and proxies KV, namespace files, flow metadata, logs and liveness for workers. Default port 50051. |
| `worker` | gRPC client that pulls jobs and runs tasks and polling/realtime triggers (`WorkerAgent`, `WorkerTaskProcessor`, `WorkerTriggerProcessor`), plus the `SystemWorker`. |
| `indexer` | Batch-consumes the log, metric and execution-statistic queues into their repositories (`DefaultIndexer`). It does **not** write executions. |
| `webserver` | REST API, SSE streams, OpenAPI generation, basic auth, CSRF, serving of the built UI, AI copilot and MCP server. Not published to Maven. |
| `cli` | Picocli entry point `io.kestra.cli.Kestra`. Aggregates every module into the runnable application and holds the main `application.yml`. |
| `tests` | Shared test library: `@KestraTest`, JUnit extensions, `TestRunner`, assertions, base test suites. |
| `jmh-benchmarks` | JMH micro-benchmarks for `core`. |

### Other root directories
| Directory | Contents |
|---|---|
| `ui/` | Vue frontend. Workspaces in `ui/packages/`: `design-system`, `topology`, `kestra-sdk`, `hey-api-plugin`, `slot-contracts`. |
| `e2e/` | Playwright suite with its own `package.json`, page objects, API helpers and fixtures. |
| `charts/` | Helm charts `kestra` and `kestra-starter`. |
| `docker/` | Filesystem overlay copied into the image (`docker-entrypoint.sh`, empty `app/confs`, `app/plugins`, `app/secrets`). |
| `plugins/` | Only `.gitkeep`. The Dockerfile copies it into `/app/plugins`; it is not a source module. |
| `docs/architecture/` | `METRICS_GUIDELINES.md`, `PLUGIN_AUTO_DOWNLOAD.md`. |
| `dev-tools/` | `setup-worktree.sh`, `copy-plugin.sh`, release helper scripts. |
| `.github/` | Workflows, `CONTRIBUTING.md`, PR template, git hooks in `.hooks/`. |
| `.devcontainer/`, `.gitpod.yml` | Ready-made development environments. |
| `gradle/` | Wrapper, plus `jar/selfrun.{sh,bat}` that turn the shadow jar into a self-executing binary. |

---

## 4. High-Level Architecture

Kestra is a set of services that communicate **only through queues**, with the database as the source of truth for state. In `server standalone` or `server local` mode all services run in one JVM. In a distributed deployment each one is started with its own `server <component>` command.

```
User / API client / external system (webhook)
          │  HTTP (REST + SSE)
          ▼
┌───────────────────────── webserver ─────────────────────────┐
│ Controllers (FlowController, ExecutionController, …)        │
│ AuthenticationFilter (basic auth), CsrfTokenFilter          │
│ UiController serves the built Vue app at /ui/               │
└───────────┬──────────────────────────────┬──────────────────┘
            │ repositories (reads, CRUD)   │ emit commands / subscribe to follow events
            ▼                              ▼
    ┌───────────────┐            ┌──────────────────────┐
    │  Database     │◄──────────►│  Queues ("queues"    │
    │ (H2/MySQL/PG) │            │   table via jOOQ)    │
    └───────▲───────┘            └──┬───────┬───────┬───┘
            │                       │       │       │
   ┌────────┴────────┐   ┌──────────▼──┐ ┌──▼──────┐ ┌▼─────────┐
   │ executor        │◄──┤ executor    │ │scheduler│ │ indexer  │
   │ (state machine, │   │ queues      │ │(triggers│ │(logs,    │
   │  row locks)     │   └─────────────┘ │ vnodes) │ │ metrics) │
   └────────┬────────┘                   └─────────┘ └──────────┘
            │ workerJobEventQueue (keyed by worker queue)
            ▼
   ┌───────────────────┐   gRPC bidi stream  ┌──────────────────┐
   │ worker-controller │◄───────────────────►│ worker(s)        │
   │ WorkerJobDispatch │  jobs ↓  results ↑  │ RunContext →     │
   └───────────────────┘  logs, KV, files    │ task.run()       │
                                             └────────┬─────────┘
                                                      ▼
                                       Plugins → external systems
                                       Internal storage (kestra:// URIs)
```

Key properties:
- **The executor is the only writer of execution state.** Every change happens inside `ExecutionStateStore#lock`, implemented as `SELECT … FOR UPDATE` on the `executions` row.
- **Workers never touch repositories.** They reach KV, namespace files, flow metadata and logs through the worker-controller (`Grpc*Backend` / `Grpc*ControllerService`). This rule is also written in `AGENTS.md`.
- **The UI follows progress through SSE** streams fed by broadcast queues (`followExecutionQueue`, `followLogEventQueue`).

---

## 5. Core Components

| Component | Main classes | Role |
|---|---|---|
| Flow management | `FlowService`, `FlowParsingService`, `YamlParser`, `ModelValidator`, `AbstractJdbcFlowRepository` | Parse, validate, version and store flows; propagate changes to triggers, topology and concurrency limits |
| Flow cache | `core/.../runners/DefaultFlowMetaStore` (worker side: `GrpcWorkerFlowMetaStore`) | Runtime cache of flows, fed by `flowQueue` |
| Execution service | `core/.../services/ExecutionService` | Build executions, restart, resume, pause, kill, terminal-state checks |
| Executor | `DefaultExecutor`, `ExecutorCore`, `ExecutorService`, `handler/*MessageHandler` | Consume execution-related queues and compute the next task runs |
| Scheduler | `DefaultScheduler`, `TriggerSchedulingLoop`, `TriggerScheduler`, `TriggerEventHandler` | Evaluate schedule triggers; dispatch polling and realtime triggers to workers |
| Worker controller | `DefaultController`, `WorkerJobDispatcher`, `GrpcWorkerControllerService` | Dispatch jobs to workers based on their capacity; relay results |
| Worker | `WorkerAgent`, `WorkerJobFetcher`, `WorkerJobExecutor`, `WorkerTaskProcessor`, `WorkerTaskCallable` | Run tasks and triggers |
| Indexer | `DefaultIndexer` | Batch-persist logs, metrics and execution statistics |
| Run context | `RunContext`, `RunContextFactory`, `RunContextInitializer`, `RunContextLogger` | Everything a task sees at runtime: variables, rendering, storage, KV, secrets, logger, metrics |
| Templating | `core/.../runners/pebble/Extension`, `PebbleEngineFactory`, `pebble/functions`, `pebble/filters` | Pebble expressions with Kestra functions (`kv()`, `secret()`, …) |
| Plugins | `PluginScanner`, `PluginClassLoader`, `DefaultPluginRegistry` | Discover and load built-in and external plugins |
| Storage | `StorageInterface`, `LocalStorage`, `InternalStorage`, `StorageContext` | Files, outputs, namespace files, KV values |
| Queues | `QueueFactoryInterface`, `JdbcQueueFactory`, `JdbcQueueClient` | Asynchronous communication between all services |

---

## 6. Flow Execution Lifecycle

### 6.1 Creating and loading a flow
1. `POST /api/v1/{tenant}/flows` (YAML body) reaches `FlowController#createFlow`, which calls `parseFlowSource`, then `doCreate`, then `FlowService#create`.
2. Inside `FlowService#create` (skipped for drafts):
   - `PluginAutoInstallService#installMissingPlugins`.
   - `FlowParsingService#parse(tenant, source, strict=true)`: the YAML is read into a map, `injectPluginVersions` runs, then `YamlParser.parse(map, FlowWithSource.class, strict)`.
   - `ModelValidator#validate` checks Jakarta constraints and the custom validators (for example `FlowValidator`, which also rejects EE-only properties such as task `assets`).
   - `throwOnCyclicDependency`.
3. `FlowRepositoryInterface#create` is implemented by `AbstractJdbcFlowRepository#create`, which calls `save`. `save` computes `revision = last + 1`, skips the write when the source is unchanged, persists JSON plus the raw `source_code`, and publishes a `CrudEvent`.
4. `FlowService#impactDownstreamConsumers` then updates the flow topology, recomputes triggers for the scheduler, emits the flow on the broadcast **`flowQueue`**, and updates concurrency limits.
5. `DefaultFlowMetaStore` consumes `flowQueue` and keeps the runtime cache that the executor reads (`findByIdForRuntime`, `findByExecutionForRuntime`).

Flows can also be loaded from a directory at startup (`server standalone --flow-path`, through `LocalFlowRepositoryLoader`) or watched for file changes (cli `application-file-watch.yml` profile).

### 6.2 Storage of a flow
Each revision is its own row in the `flows` table: `key`, a JSON `value`, a `source_code` column, and generated columns (`id`, `namespace`, `revision`, `tenant_id`, `deleted`, full-text). A deletion is a soft delete. The flow identity is `tenant + namespace + id (+ revision)` (`FlowId.uid`).

### 6.3 Triggering
Every entry point produces an **`ExecutionCommand.Create`** on **`executionCommandQueue`**, except Flow triggers and subflows, which emit an `Execution` on `executionQueue`.

| Source | Path |
|---|---|
| Manual (UI or API) | `ExecutionController#createExecution` → `awaitBlockingAction` → `executionCommandQueue.emit(Create)`. The HTTP call then waits for an `AsyncOperationProcessedEvent`, so the response contains the persisted execution. |
| Webhook | `ExecutionController#triggerExecutionBy*Webhook` → `AbstractWebhookTrigger#evaluate` → `WebhookService` → `Create` |
| Schedule | Scheduler `TriggerScheduler#processSchedulableTrigger` → `SchedulableEvaluator` → `DefaultTriggerExecutionPublisher#send` → `Create` |
| Polling / realtime triggers | Scheduler → `TriggerWorkerJobPublisher` → `workerJobEventQueue` → worker evaluates → result to `GrpcWorkerControllerService#sendWorkerTriggerResults` → `TriggerEvaluated` event or `triggerExecutionPublisher.send` |
| Flow trigger | Executor `ExecutorCore#processFlowTriggers` → `FlowTriggerService#computeExecutionsFromFlowTriggerConditions` → `executionQueue` (or `multipleConditionEventQueue` for `dependsOn`) |
| Subflow | `ExecutorService#handleExecutableTasks` → `Subflow#createSubflowExecutions` → `executionQueue` |

### 6.4 Creating the Execution
`ExecutionCommandMessageHandler#handleCreate` (executor):
1. Load the flow from `flowMetaStore.findByIdForRuntime`.
2. `ExecutionService#create(Create, ProcessedFlow)` calls `Execution.newExecution(flow, inputs, labels, …)` and applies the command's id, schedule date, breakpoints, trigger and trace context. The state is **CREATED**.
3. `persistNewExecutionWithKillSwitch` → `executionStateStore.create` → a row in `executions`.
4. `ExecutionEventMessageHandler#handle(new ExecutionEvent(execution, CREATED))` starts processing immediately.

### 6.5 Scheduling tasks (executor)
`ExecutionEventMessageHandler#handle` runs inside `executionStateStore.lock(executionId, …)`:
- If `scheduleDate` is in the future, it stores an `ExecutionDelay(RESUME_FLOW)` and stops.
- On CREATED it creates SLA monitors, checks quotas (EE stub in OSS), and checks concurrency limits (section 17). The result can be QUEUED, CANCELLED or FAILED.
- It calls **`ExecutorService#process`**, which runs these steps in order:
  1. `handleRestart`
  2. `handleEnd`: if all current tasks are terminated, compute the final state (section 6.9).
  3. `handleNeverRunnedKilling`, `handleKilling`
  4. `handleNext`: `FlowableUtils.resolveSequentialNexts` produces the next top-level `TaskRun`s (CREATED).
  5. `handleAfterExecution`: `afterExecution` tasks.
  6. `handleFlowableTasks`: for running flowable tasks (`If`, `Switch`, `Parallel`, `Sequential`, `Dag`, `Loop`, `LoopUntil`, `AllowFailure`, `Pause`, …), call `FlowableTask#resolveNexts` for children and `FlowableTask#resolveState` to close the parent. Also handles **retries** (`TaskRun#nextRetryDate` → `ExecutionDelay`) and pauses.
  7. `handleWorkerTasks`: each CREATED runnable `TaskRun` becomes a `WorkerTask` (`RunContextFactory#of`), routed with `WorkerQueueService#resolveWorkerQueueForJob`.
  8. `handleExecutionUpdatingTasks`: `ExecutionUpdatableTask`s (`Labels`, `SetVariables`, `Exit`) run directly in the executor.
  9. `handleExecutableTasks`: subflows.
- Back in the handler, `runIf` is evaluated for each worker task. If `task.isSendToWorkerTask()`, the TaskRun is set to **SUBMITTED** and `workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTask, workerQueueId))` is called.
- The lock commits the updated execution JSON.
- `ExecutorCore#toExecution` emits `ExecutionEvent(UPDATED)` on `executionEventQueue` and a `FollowExecutionEvent` for the UI. **This re-enqueue is what drives the next cycle.**

Flowable tasks never go to a worker; the executor resolves them. `WorkingDirectory` is the exception: it is flowable but runs on a worker.

### 6.6 Workers executing tasks
1. **Controller:** `WorkerJobDispatcher` subscribes to `workerJobEventQueue` for each worker queue (paused until workers send permits). `handleIncomingJob` checks that the execution was not killed, calls `findAndReserveWorker`, then `dispatchJobToWorker`, which saves a `WorkerJobRunning` row (for recovery when a worker dies) and sends the job over the gRPC stream (`GrpcWorkerControllerService#streamWorkerJobs`).
2. **Worker:** `WorkerJobFetcher#handleJobResponse` → local queue → `WorkerJobExecutor.WorkerJobConsumer#doOnLoop` → `WorkerTaskProcessor#doProcess` → `runTask`:
   - Build the RunContext with `RunContextInitializer#forWorker(workerTask)`.
   - `runAttempt` sends a RUNNING `WorkerTaskResult`, then creates a `WorkerTaskCallable`.
   - `WorkerTaskCallable#doCall` calls `callWithTimeout(task.timeout, () -> task.run(runContext))`. **This is where `RunnableTask#run` is invoked and where the task timeout is enforced.**
   - It applies `allowFailure`, `allowWarning` and `warningOnRetry`, publishes metrics, and emits a final `WorkerTaskResult(taskRun, dynamicTaskRuns, outputs)`.
3. Results go back through `GrpcWorkerIOSender` → `GrpcWorkerControllerService#sendWorkerTaskResults` → `workerTaskResultQueue.emit`. On a terminal result the `WorkerJobRunning` row is deleted.

### 6.7 Storing task state
`WorkerTaskResultMessageHandler#handle` (executor, under the lock) → `ExecutorService#addWorkerTaskResult`: it replaces the `TaskRun` in `execution.taskRunList`, adds dynamic task runs, and, when the task run is terminal, calls `TaskOutputService#saveOutputs` (a `task_outputs` row, or internal storage when the outputs are large). It also handles assets (EE) and metrics.

`TaskRun`s are **embedded in the Execution JSON**. There is no task-run table. Each `TaskRun` holds a `State` with its history and a list of `TaskRunAttempt`s.

States (`core/.../models/flows/State.java`, enum `State.Type`): CREATED, SUBMITTED, RUNNING, PAUSED, RESTARTED, KILLING, SUCCESS, WARNING, FAILED, KILLED, CANCELLED, QUEUED, RETRYING, RETRIED, SKIPPED, BREAKPOINT, RESUBMITTED.

### 6.8 Logs
`runContext.logger()` returns a logback logger (`RunContextLogger`) with a `ContextAppender` that turns events into `LogEntry`s, **masking secret values**, and passes them to a `LogEntryEmitter`.
- On a worker, `WorkerLogEntryEmitter` → gRPC → `GrpcWorkerControllerService#sendWorkerLogEntries` → `DefaultLogEntryEmitter`.
- `DefaultLogEntryEmitter#emits` sends each entry to `logEntryQueue`, which the **indexer** persists through `LogDataStoreInterface` (JDBC: `AbstractJdbcLogDataStore`, table `logs`), and to `followLogEventQueue` (broadcast), which the UI follows through `LogController#followLogsFromExecution` (SSE) and `LogStreamingService`.
- A `FileAppender` also writes a per-attempt log file.

### 6.9 Updating status and finishing
- `ExecutorService#handleEnd` → `Execution#isTerminated(resolvedTasks)` → `onEnd` → `Execution#guessFinalState(flow)` (SUCCESS, WARNING, FAILED, KILLED, …). Flow outputs are rendered and saved with `ExecutionOutputService`. `ExecutionService#isTerminated` additionally waits for `afterExecution` tasks.
- In the terminal branch of `ExecutorCore#toExecution`:
  - release the concurrency slot (`ConcurrencySlotReleaseProcessor` pops the next QUEUED execution);
  - release working-directory leases;
  - notify a parent (subflow end) or a loop;
  - purge SLA monitors;
  - send `TriggerExecutionTerminated` so the scheduler unlocks the trigger;
  - emit `ExecutionEvent(TERMINATED)`, `FollowExecutionEvent(TERMINATED)` and an `ExecutionStatistic`;
  - call `ExecutionTerminatedNotifier` (no-op in OSS).
- Flow triggers fire on every state transition through `processFlowTriggers`, including the final one.

---

## 7. Scheduler and Triggers

### Trigger types (`core/.../models/triggers/`)
| Interface | Evaluated by | Example |
|---|---|---|
| `Schedulable` (`eval`, `nextEvaluationDate`) | Scheduler, in place | `plugin/core/trigger/Schedule`, `ScheduleOnDates` |
| `PollingTriggerInterface` (`getInterval`) | Worker | `plugin/core/http/Trigger` |
| `RealtimeTriggerInterface` | Worker (long-running) | Realtime triggers live in external plugins |
| `AbstractWebhookTrigger` | Webserver | `plugin/core/trigger/Webhook`, `McpToolTrigger` |
| Flow trigger | Executor | `plugin/core/trigger/Flow` (`conditions` / `dependsOn`) |

All triggers extend `AbstractTrigger`, which has `when` (a Pebble gate, default `"true"`), `disabled`, `workerSelector`, `labels`, and so on. **There is no `Condition` plugin type in 2.x.** Gating uses `when`. Flow dependencies use `models/triggers/multipleflows/` (windows stored in the `multipleconditions` table).

### Scheduler internals (`scheduler/src/main/java/io/kestra/scheduler/`)
- `DefaultScheduler` runs one or more `TriggerSchedulingLoop`s on the `scheduler-scheduling-loop` executor.
- Work is partitioned by **vNodes**: `core/.../scheduler/vnodes/` contains `VNodesAssigner`, `DefaultVNodesAssigner`, `StaticVNodesAssigner` and `VNodeConsistentHashRing`. Each loop owns a set of vNodes, and the `triggers` table has a `vnode` column. This is how several scheduler instances avoid evaluating the same trigger.
- Every second (`SCHEDULE_INTERVAL_MILLIS`) the loop calls `TriggerScheduler#onSchedule`, which fetches due triggers (`DefaultSchedulableTriggerFetcher`, using `next_evaluation_epoch`) and dispatches by type in `evaluate`. Between ticks it drains trigger events into `TriggerEventHandler#handle`.
- **Trigger state:** `core/.../scheduler/model/TriggerState`, stored through `TriggerStateStore` (JDBC: `AbstractJdbcTriggerRepository`, table `triggers` with the columns `vnode`, `locked` and `next_evaluation_epoch`), cached by `scheduler/.../stores/CachedTriggerStateStore`.
- **Locking:** a trigger is locked while its execution runs when `allowConcurrent` is false, and always for realtime triggers. The executor unlocks it with `TriggerExecutionTerminated`.
- **Events** (`core/.../scheduler/events/`): created, updated, deleted, evaluated, received, execution-terminated, worker-lost, backfill create/pause/delete, disable, reset.
- **Backfill:** a `Backfill` object stored on `TriggerState`, driven by `TriggerEventHandler` and `NextEvaluationDate`. The API is in `TriggerController`.
- **Monitoring:** `SchedulerEndpoint` (`@Endpoint(id="scheduler")`), `TriggerSchedulerMonitor`.

---

## 8. Workers and Task Execution

- **Worker kinds:** `WorkerAgent` (gRPC worker, the normal one) and `SystemWorker` (`worker/.../systemworker/`, runs `SystemTask`s through `DirectQueueJobFetcher` without gRPC).
- **Job types:** `WorkerTask` (task run), `WorkerTrigger` (polling or realtime trigger), and working-directory jobs.
- **Processors:** `worker/.../processors/WorkerTaskProcessor`, `WorkerTriggerProcessor`. Callables: `internals/WorkerTaskCallable`, `WorkerTriggerCallable`, `WorkerTriggerRealtimeCallable`.
- **Capacity:** the worker thread count comes from `server worker --thread` (default `Worker.defaultNumThreads()`). Workers advertise permits on their gRPC stream; the controller dispatches with a pull/ack model and pauses a worker queue's subscription when no worker has capacity (`WorkerCapacityPolicy`, `SinglePoolCapacityPolicy`).
- **Routing:** `WorkerSelector` (tags and match mode) on a task or flow picks a worker queue. Whether several worker queues are EE-gated is **NFI** (no EE marker was found).
- **Recovery:** `WorkerJobRunningStateStore` (table `worker_job_running`) records dispatched jobs. `DefaultServiceLivenessCoordinator` (executor module) re-emits jobs of workers that stopped sending heartbeats.
- **Kill:** `ExecutionController#killExecution` → `killQueue` (broadcast) → `ExecutionKilledExecutionMessageHandler` (state KILLING, cascades to subflows) → `WorkerJobDispatcher#onExecutionKilled` → workers' `ExecutionKilledManager` → `WorkerTaskCallable#kill`.
- **Task runners** (where script commands physically run): `TaskRunner` interface. `core/.../plugin/core/runner/Process` runs commands as local processes; `script/.../runner/docker/Docker` runs them in a container.
- **Configuration:** `WorkerConfig` (`kestra.worker.*`, for example `polling-trigger-timeout` (10m) and `job-buffer-size`), `kestra.worker.controllers.type: STATIC` (localhost by default).

---

## 9. Persistence and Database

### Technology
- Three SQL backends: H2, MySQL and PostgreSQL. "memory" is embedded H2. Elasticsearch is referenced in `MigrationRunner` but is EE.
- Data access uses jOOQ with dynamic field names (`DSL.field("…")`). There are no generated jOOQ classes.

### Table pattern
Every table stores the entity as JSON in a `value` column (JSONB on Postgres, JSON on MySQL) under a `key` primary key. Columns used in queries are generated from the JSON: `id`, `namespace`, `tenant_id`, `deleted`, `revision`, full-text vectors, and so on. Deletes are soft (`deleted` flag). `defaultFilter(tenantId)` always adds the tenant and `deleted` conditions.

### Migrations
- The framework is in `core/.../migration/` (`MigrationRunner`, `MigrationScript`, `MigrationHistoryStore`, `MigrationLock`) and `jdbc/.../jdbc/migration/` (`AbstractSQLMigrationScript`, `JdbcMigrationHistoryStore` → table `kestra_migration_history`).
- Shared Java migrations: `V2_0_03TriggerMigration`, `V2_0_04BasicAuthPasswordMigration`, `V2_0_11PluginAutoInstallMigration`, `V2_0_26PurgeLegacyWorkerJobRunningMigration`.
- Per dialect: Java scripts in `jdbc-<db>/.../repository/<db>/migration/` and SQL in `jdbc-<db>/src/main/resources/migrations/`: `baseline-<db>.sql`, `baseline-queue-<db>.sql`, `logs-<db>.sql`, then versioned files up to `2.1.02-trigger-source-disabled-<db>.sql`.
- Databases created by the old Flyway setup are detected, and their init scripts are marked as applied.
- CLI: `kestra migrate plan|run|repair|unlock|execution-resubmit`.

### Tables
| Table | Model / purpose |
|---|---|
| `flows` | Every flow revision, plus `source_code` |
| `executions` | `Execution` including the embedded `taskRunList` |
| `execution_outputs`, `task_outputs` | Flow outputs and task outputs |
| `logs` | `LogEntry` (the table name is a template; the log store is pluggable, external stores are EE) |
| `metrics` | `MetricEntry` |
| `execution_statistics` | Pre-aggregated statistics (`ExecutionStatisticsCompactor`) |
| `triggers` | `TriggerState` |
| `multipleconditions` | Flow-trigger `dependsOn` windows |
| `executordelayed` | `ExecutionDelay` (scheduled start, retry, pause timeout) |
| `execution_queued`, `concurrency_limit` | Flow concurrency |
| `worker_job_running` | Jobs dispatched to workers |
| `sla_monitor` | SLA monitors |
| `service_instance` | Cluster membership and liveness |
| `locks` | Distributed leases |
| `settings` | Key/value settings (for example basic-auth credentials) |
| `flow_topologies` | Flow dependency graph |
| `kv_metadata`, `namespace_file_metadata` | Metadata for KV entries and namespace files |
| `mcp`, `mcp_session` | MCP servers and sessions |
| `queues` | The single JDBC queue table: `offset`, `type`, `routing_key`, `key`, `value`, `created` |
| `dashboards` | Exists from the baseline, but OSS has no repository for it (stored dashboards are EE) |
| `kestra_migration_history` | Migration bookkeeping |

`templates` and `executorstate` are dropped by `2.0.01-schema-*.sql`.

### Repository layers
- Interfaces are in `core/.../repositories/`: `FlowRepositoryInterface`, `ExecutionRepositoryInterface`, `TriggerRepositoryInterface`, `MetricRepositoryInterface`, `SettingRepositoryInterface`, `KvMetadataRepositoryInterface`, and others (`LogDataStoreInterface` is under `repositories/log/`).
- Generic implementations are `jdbc/.../repository/AbstractJdbc<Name>Repository`, with a thin dialect subclass per database such as `PostgresFlowRepository` (annotated `@RepositoryBean`).
- There are two helper classes named `AbstractJdbcRepository`:
  1. `io.kestra.jdbc.AbstractJdbcRepository<T>` is the table helper (`persist`, `fetchPage`, …), subclassed per dialect (`PostgresRepository`, created per table by `@EachBean(JdbcTableConfig.class)`). Table names come from `JdbcTableConfigsFactory`.
  2. `io.kestra.jdbc.repository.AbstractJdbcRepository` is the query and filter base (tenant, deleted, `NamespaceAccessControl`).
- Executor state stores are in `jdbc/.../runner/`: `AbstractJdbcConcurrencyLimitStateStore`, `…ExecutionDelayStateStore`, `…ExecutionQueuedStateStore`, `…MultipleConditionStateStore`, `…SLAMonitorStateStore`, `…WorkerJobRunningStateStore`.
- `AbstractJdbcExecutionRepository` implements both `ExecutionRepositoryInterface` (API reads) and `ExecutionStateStore` (executor writes, `lock`).

### Entity relationships
```
Tenant ("main" in OSS)
 └─ Namespace (dotted string, not a stored entity in OSS)
     ├─ Flow (id, revision) ──1:N──► Execution (flowId, flowRevision)
     │                                 ├─ TaskRun[] (embedded; parentTaskRunId for nesting)
     │                                 │    └─ TaskRunAttempt[]
     │                                 ├─ parentId → parent Execution (subflow)
     │                                 └─ trigger (who started it)
     ├─ TriggerState (namespace + flowId + triggerId)
     ├─ KV entries (kv_metadata + files in storage)
     └─ Namespace files (namespace_file_metadata + files in storage)
LogEntry / MetricEntry → denormalized tenantId, namespace, flowId, executionId, taskRunId
```

### Internal storage
- `core/.../storages/StorageInterface` is a **plugin** selected by `kestra.storage.type` (`KestraBeansFactory#storageInterface` → `StorageInterfaceFactory`).
- OSS ships `LocalStorage` (`storage-local`), which stores files under `basePath/<tenantId>/…` and protects against path traversal. Cloud storages (S3, GCS, Azure) are external plugins.
- Files are addressed by `kestra://` URIs built by `StorageContext`: execution and task outputs, trigger files, input files, cache, `/{namespace}/_files` (namespace files) and `/{namespace}/_kv` (KV values).

---

## 10. KV Store

The KV store is hybrid: the **value is a file** in internal storage and the **metadata is a database row**.

- **Core classes** (`core/.../storages/kv/`):
  - `KVStore` (interface): values are stored at `kestra:///<ns>/_kv/<key>.ion`, with revisions as `.ion.vN`. Keys must match `[a-zA-Z0-9][a-zA-Z0-9._-]*`.
  - `InternalKVStore` serializes values with Jackson Ion and delegates to a `KVBackend`.
  - `StorageKVBackend` writes the value to `StorageInterface` and the metadata (`PersistedKvMetadata`: description, revision, expiration date, deleted) to `kv_metadata`.
  - On workers it is replaced by `worker/.../stores/GrpcKVBackend` (`@Replaces(StorageKVBackend)`), which goes through the worker-controller.
- **Services:** `KVStoreService` (`findValueWithInheritance` walks up parent namespaces), `KVService` (list and purge), `KVPurgeCleaner`.
- **TTL:** `expirationDate` is checked on read; an expired value throws `ResourceExpiredException`.
- **Pebble:** `kv('key', namespace=…, errorOnMissing=…)` (`pebble/functions/KvFunction`).
- **Tasks:** `io.kestra.plugin.core.kv.{Set, Put, Get, GetKeys, Delete, PurgeKV}`.
- **API:** `KVController`: `GET /api/v1/{tenant}/kv`, `GET|PUT|DELETE /namespaces/{ns}/kv/{key}`, `DELETE /namespaces/{ns}/kv`, `GET /namespaces/{ns}/kv/inheritance`.
- **UI:** `ui/src/components/kv/` (`KVs.vue`, `KVTable.vue`, `InheritedKVs.vue`), route `/:tenant?/kv`.

---

## 11. Secrets

- **OSS implementation:** `core/.../secret/SecretService`. At startup it reads environment variables prefixed with `SECRET_`, **Base64-decodes** their values, and keys them by the rest of the name. It is read-only. Tenant and namespace are ignored.
- **Usage:** `{{ secret('MY_KEY') }}` (`pebble/functions/SecretFunction`; arguments `key`, `namespace`, `subkey`, `full`, `errorOnMissing`). Values are masked in logs by `RunContextLogger`.
- **API:** `SecretController` (`GET /api/v1/{tenant}/secrets`, list only) and `NamespaceSecretController` (`GET /namespaces/{ns}/inherited-secrets`).
- **UI:** `ui/src/components/secrets/`.
- **EE boundary:** `SecretPluginInterface` is an empty plugin marker. Secret CRUD and external secret managers (Vault, AWS, …) are not in OSS.

---

## 12. Namespaces and Tenants

### Namespaces
- In OSS a namespace is **not a stored entity**. `core/.../models/namespaces/Namespace` only has an `id`. A namespace "exists" when a flow exists in it or below it.
- Hierarchy: `NamespaceInterface.asTree("a.b.c")` gives `[a, a.b, a.b.c]`, which is used for KV and secret inheritance.
- `DefaultNamespaceService`: `isAllowedNamespace` and `areAllowedAllNamespaces` always return true in OSS (Javadoc: "namespace management is an EE feature").
- API: `NamespaceController` (search, autocomplete, dependencies; the list is built from `flowRepository.findDistinctNamespace`), `NamespaceFileController` (namespace files: CRUD, directories, revisions, export, search).
- Namespace files: `core/.../storages/Namespace`, `NamespaceFile`, `StorageNamespaceFileBackend`, `NamespaceFileService`; tasks `io.kestra.plugin.core.namespace.*`.
- UI: list `ui/src/override/components/namespaces/Namespaces.vue`, detail `ui/src/components/namespaces/Namespace.vue`.

### Tenants
- `core/.../tenant/TenantService`: `MAIN_TENANT = "main"`, `resolveTenant()` always returns `"main"`.
- Every API route has the shape `/api/v1/{tenant}/…`, but controllers ignore the path variable and call `tenantService.resolveTenant()`.
- `webserver/.../rooting/TenantAliasingRooter` (`@Replaces(DefaultRouter)`) rewrites tenant-less `/api/v1/...` paths to `/api/v1/main/...`. It is disabled when the EE class `TenantAliasingRooterEE` is present.
- `webserver/.../tenants/TenantValidationFilter` returns 400 for any tenant other than `main`.
- Every entity still carries `tenantId`, and every index includes `tenant_id`, so the data model is multi-tenant-ready.
- UI: all routes are `/:tenant?/…`; `ui/src/composables/useTenant.ts` (`tenantGuard`, `setupTenantRouter`) fills in `main`. Tenant administration is a demo page.

---

## 13. Plugins

### Structure
Everything pluggable implements `io.kestra.core.models.Plugin` (`model` module) and is annotated `@Plugin`. Properties use `@PluginProperty` and Swagger `@Schema`, and documentation examples use `@Example`.

| Plugin type | Interface | Built-in example |
|---|---|---|
| Runnable task | `RunnableTask<O extends Output>` (`run(RunContext)`) on top of `Task` | `plugin/core/log/Log` |
| Flowable task | `FlowableTask<O>` (`childTasks`, `resolveNexts`, `resolveState`) | `plugin/core/flow/If`, `Parallel`, `Loop` |
| Executable task | `ExecutableTask` | `plugin/core/flow/Subflow` |
| Execution-updating task | `ExecutionUpdatableTask` | `plugin/core/execution/Labels`, `SetVariables` |
| Triggers | `AbstractTrigger` + `Schedulable` / `PollingTriggerInterface` / `RealtimeTriggerInterface` / `AbstractWebhookTrigger` | `Schedule`, `http/Trigger`, `Webhook` |
| Task runner | `TaskRunner` | `plugin/core/runner/Process`, `script/.../runner/docker/Docker` |
| Internal storage | `StorageInterface` | `storage-local/.../LocalStorage` |
| Secret backend | `SecretPluginInterface` | none in OSS |
| Log exporter / log store | `LogExporter`, `LogDataStoreInterface` | `AbstractJdbcLogDataStore` |
| Dashboard | `Chart`, `DataFilter`, `DataFilterKPI` | `plugin/core/dashboard/*` |
| File preview | `FileRenderer` | `plugin/core/preview/*` |
| Plugin HTTP endpoint | `PluginEndpoint` | — |
| Others | `AdditionalPlugin`, `Asset`, `AssetExporter`, `AppPluginInterface`, `AppBlockInterface`, `RulePluginInterface` | mostly EE consumers |

The full list of types is the `switch` in `PluginScanner#scanClassLoader`.

### Built-in core plugins (`core/src/main/java/io/kestra/plugin/core/`)
`flow` (If, Switch, Parallel, Sequential, Dag, Loop, LoopUntil, Subflow, Pause, Sleep, WorkingDirectory, AllowFailure, PurgeFlows), `log`, `kv`, `execution` (Assert, Exit, Fail, Labels, SetVariables, UnsetVariables, PurgeExecutions), `trigger`, `http` (Request, Download, SseRequest, Trigger), `storage`, `namespace`, `output`, `debug`, `metric`, `templating`, `runner`, `dashboard`, `preview`, `purge`.

Most integrations (databases, clouds, Python, dbt, …) are **external plugin repositories**, not part of this repo (see `make clone-plugins`).

### Discovery and loading
1. At compile time, `processor/.../PluginProcessor` writes `META-INF/services/io.kestra.core.models.Plugin` listing every concrete `@Plugin` class.
2. At runtime, `core/.../plugins/PluginScanner`:
   - `scan()` reads built-in plugins from the application classloader;
   - `scan(Path)` reads each external plugin jar into its own child-first `PluginClassLoader` and runs `ServiceLoader.load(Plugin.class, …)`. It also picks up `doc/guides` and `plugin-ui/manifest.json`.
3. The results go into a `RegisteredPlugin`, held by `DefaultPluginRegistry`. Related classes: `PluginManager`, `LocalPluginManager`, `MavenPluginDownloader`, `PluginAutoInstallService`.
4. The plugin directory comes from `KESTRA_PLUGINS_PATH` (`ExternalPluginsPath`) or the CLI option `-p/--plugins`. There is no `kestra.plugins.path` property. Plugins can also be installed with `kestra plugins install`, `POST /api/v1/plugins/install`, or auto-install (`kestra.plugins.auto-install`, see `docs/architecture/PLUGIN_AUTO_DOWNLOAD.md`).
5. JSON schemas and docs: `core/.../docs/JsonSchemaGenerator`, served by `PluginController` (`/api/v1/plugins/...`) and used by the editor for autocompletion.

### How plugins talk to the core
Plugins only see the **`RunContext`**: `render()` for Pebble expressions, `logger()`, `metric()`, `storage()`, `namespaceKv()`, `pluginConfiguration()`, `workingDir()`, and so on. A task returns an `Output`; it never touches repositories or queues. Flowable tasks return lists of `NextTaskRun` / states to the executor.

---

## 14. API Architecture

- **Framework:** Micronaut HTTP (Netty). Controllers are in `webserver/.../controllers/api/`.
- **Routes:** `/api/v1/{tenant}/<resource>`. `PluginController`, `/api/v1/configs` and login/logout have no tenant.
- **Main controllers:** `FlowController`, `ExecutionController` (create, webhooks, restart, replay, kill, resume, pause, labels, follow SSE, files), `LogController`, `TriggerController`, `KVController`, `NamespaceController`, `NamespaceFileController`, `SecretController`, `MetricController`, `OutputController`, `DashboardController`, `BlueprintController`, `PluginController`, `MiscController` (configs, basic auth, login/logout, Pebble function list), `ClusterController`, `ConcurrencyLimitController`, `ExpressionController`, `AiController`, `AiAgentController`, `McpServerController`, `McpToolController`. Also `UiController`, `ApiController` (RapiDoc page) and `RootController` (`/ping`).
- **Paging:** `responses/PagedResults<T>{results, total}`, `CursorOrOffsetPagedResults`, `utils/PageableUtils`.
- **Errors:** `ErrorController` has global `@Error` handlers. All errors are RFC 9457 problem documents (`errors/ProblemFactory`, `ProblemMapperRegistry`, `ProblemResponseFilter`).
- **OpenAPI:** generated at compile time by `micronaut-openapi` into `META-INF/swagger/kestra.yml`, and served at `/swagger/**`. `./gradlew :webserver:generateOpenapiSpec` copies it to the root `openapi.yml`, which the UI SDK is generated from.
- **SSE:** `ExecutionController#followExecution`, `#followDependenciesExecutions`, `LogController#followLogsFromExecution`, and the AI chat endpoints.
- **Async commands:** mutating execution endpoints emit an `ExecutionCommand` and wait for the executor through `asyncOperationWaiter` / `AsyncOperationProcessedEvent`.

---

## 15. Frontend Architecture

- **Entry:** `ui/src/main.ts` → `initApp(...)` (`ui/src/utils/init.ts`) creates the router (`createWebHistory`, base `/ui`), Pinia, i18n, the design-system plugin and the guards. Then `setupTenantRouter`, `setupAxios` → `setupKestraHttp`, and a CSRF interceptor.
- **Routes:** `ui/src/routes/routes.ts`. All paths start with `/:tenant?/`: flows, executions, logs, kv, secrets, namespaces, plugins, blueprints, dashboards (`home`), admin (triggers, stats, concurrency limits, MCP servers), `setup`, `login`, plus EE demo routes.
- **State:** Pinia stores in `ui/src/stores/` (`flow`, `executions`, `logs`, `plugins`, `dashboard`, `core`, `namespaces`, …). Overridable stores (`auth`, `misc`, `namespaces`) are in `ui/src/override/stores/`.
- **API client:** `ui/packages/kestra-sdk` is generated (and committed) from `openapi.yml` with `@hey-api/openapi-ts`, exposing per-tag modules such as `@kestra-io/kestra-sdk/flows`. `useClient()` (`src/client-facade.ts`) is an axios-like facade for ad-hoc calls. The tenant is injected as `main`.
- **Design system:** `ui/packages/design-system` wraps Element Plus as `Ks*` components and exposes `--ks-*` CSS tokens. Feature code must not use raw Element Plus, hex colors or `:deep()` (see `ui/AGENTS.md`).
- **Other packages:** `topology` (Vue Flow graph), `hey-api-plugin` (SDK generator plugin and runtime), `slot-contracts` (prop contracts for plugin UI slots).
- **Editors:** Monaco (`KsEditor.vue` in the design system; YAML and Pebble language support in `src/composables/monaco/`), no-code editor in `src/components/no-code/`, topology editor in `src/components/inputs/LowCodeEditor.vue`.
- **EE swap point:** `ui/src/override/`, aliased as `override` in `vite.config.js`. EE replaces the files under this alias. How that replacement is wired on the EE side is **NFI**, because the EE repo is not here.
- **Plugin UI:** module federation (`src/remoteComponents/useFederatedModule.ts`) loads UI modules shipped inside plugin jars (`plugin-ui/manifest.json`).
- **i18n:** `ui/src/translations/` (`en.json` plus 12 generated languages). Use `npm run translations:generate` and `npm run translations:check`.

### Real trace 1: Flows list
1. `ui/src/components/flows/Flows.vue`: `KsDataTable :loadData="loadData"`.
2. `ui/src/stores/flow.ts`: `findFlows(options)` → `toFlowSearchParams`.
3. SDK `FlowsAPI.searchFlows` → **`GET /api/v1/main/flows/search?page=&size=&sort=&filters[...]`**.
4. `FlowController#searchFlows` → `flowRepository.find(PageableUtils.from(...), tenantService.resolveTenant(), filters)`.
5. `AbstractJdbcFlowRepository#find` → `getFindFlowSelect` (latest revision, full text, filters) → `jdbcRepository.fetchPage` → dialect subclass (`PostgresFlowRepository`, …) → table `flows`.
6. `PagedResults{results, total}` → `flowStore.flows` / `flowStore.total` → table re-renders.

### Real trace 2: Execute button
1. `ui/src/components/flows/TriggerFlow.vue` opens `FlowRun.vue`.
2. `executeTask` (`src/utils/submitTask.ts`) → `useExecutionsStore().triggerExecution` (`src/stores/executions.ts`).
3. SDK `ExecutionsAPI.createExecution` → **`POST /api/v1/main/executions/{namespace}/{id}`** (multipart inputs).
4. `ExecutionController#createExecution` → `executionCommandQueue.emit(Create)` → waits → `executionRepository.findByIdWithoutAcl`.
5. The executor persists the execution (section 6.4). The UI then opens the SSE stream `GET /executions/{id}/follow`.

---

## 16. Authentication / Authorization

### Backend (OSS)
- **Basic auth only.** `webserver/.../services/BasicAuthService` stores salted credentials in the `settings` table (key `kestra.server.basic-auth`). They are seeded from `kestra.server.basic-auth.*` config if present. The service issues an HttpOnly `BASIC_AUTH` cookie plus a readable `kestraBasicAuthenticated` flag.
- **`webserver/.../filter/AuthenticationFilter`** (`/**`) protects `/api/v1/**` for the WEBSERVER and STANDALONE server types. It is active only when `micronaut.security.enabled` is not true (that is how EE replaces it).
  - Open without credentials: login and configs, setup while not initialized, management endpoints, MCP requests (handled by `McpServerAuthenticationFilter`), and `kestra.server.basic-auth.open-urls` routes that are also annotated `@AnonymousAccess` (only the webhook endpoints).
  - Otherwise it returns 401 (without `WWW-Authenticate` for XHR, so the browser does not pop up its native dialog).
- **First-run setup:** `MiscController#createBasicAuth` (`POST /api/v1/{tenant}/basicAuth`), `POST /api/v1/login`, `POST /api/v1/logout`.
- **CSRF:** `filter/CsrfTokenFilter` checks `X-CSRF-TOKEN` on unsafe `/api/**` methods when a `BASIC_AUTH` or `JWT` cookie is present. The token is injected into `index.html` by `UiIndexService`.
- **Security headers:** `filter/SecurityHeadersFilter` (`kestra.webserver.security-headers`). No CORS defaults are shipped.
- **Authorization: none in OSS.** There is no `@HasAnyPermission`, `@Secured` or `@RolesAllowed` in the OSS Java code (searched). `NamespaceAccessControl.GLOBAL` is the default. `AGENTS.md` mentions `@HasAnyPermission`, which refers to EE.

### Frontend
- `beforeResolve` guard in `ui/src/main.ts`: load the login config, then go to `setup` or `login` when needed, then check the `kestraBasicAuthenticated` cookie, then load configs.
- `ui/src/utils/kestraHttp.ts`: a 401 opens `ReauthDialog` (then retries the request); network errors and 502/503/504 show `ServerUnreachableBanner`.
- `ui/src/override/stores/auth.ts`: the `Me` permission object returns `true` for every check (EE replaces it).

---

## 17. Concurrency

| Level | Mechanism | Where |
|---|---|---|
| Flow concurrency limit | `concurrency: {limit, behavior: QUEUE \| CANCEL \| FAIL}` (`core/.../models/flows/Concurrency`) | Enforced on CREATED in `ExecutionEventMessageHandler` → `ConcurrencyLimitStateStore#countThenProcess` → `ExecutorService#processExecutionRunning`. Queued executions go to `execution_queued`. On termination `ConcurrencySlotReleaseProcessor#release` pops the next one. API: `ConcurrencyLimitController` |
| Namespace / tenant limits | `ConcurrencyLimitResolver`, `ScopedConcurrencyLimit` | OSS supports only FLOW scope; other scopes throw `UnsupportedOperationException` (EE) |
| One execution processed at a time | `ExecutionStateStore#lock` = `SELECT … FOR UPDATE` on the `executions` row (`AbstractJdbcExecutionRepository#lock`). `DefaultExecutor` also groups batches by `executionId` | `executor`, `jdbc` |
| Competing queue consumers | `SELECT … FOR UPDATE SKIP LOCKED` then `DELETE` (`JdbcQueueClient#subscribeDispatch`) | `queue-jdbc` |
| Scheduler instances | vNode partitioning plus trigger `locked` flag | `scheduler`, `core/.../scheduler/vnodes` |
| Trigger overlap | `allowConcurrent` on triggers | `TriggerScheduler` |
| Worker parallelism | Thread count plus permit-based dispatch | `worker`, `worker-controller` |
| Cluster liveness | `ServiceLivenessManager` heartbeats into `service_instance`; `DefaultServiceLivenessCoordinator` detects dead instances and resubmits their jobs | `core/.../server`, `executor` |
| Distributed leases | `locks` table (`AbstractJdbcLockRepository`) | `jdbc` |

A task-level concurrency limit was not found in OSS (**NFI**).

---

## 18. Logging and Observability

- **Application logs:** logback, `cli/src/main/resources/logback.xml`. Use `--logging.level.io.kestra=DEBUG` for debug output.
- **Execution logs:** section 6.8 (`RunContextLogger` → `logEntryQueue` → indexer → `logs` table; SSE follow).
- **Metrics:** `core/.../metrics/MetricRegistry` wraps Micrometer (about 200 `METRIC_*` names; guidelines in `docs/architecture/METRICS_GUIDELINES.md`). Task metrics are published with `runContext.metric(Counter|Timer|Gauge)` into `metricQueue` → indexer → `metrics` table.
- **Management port 8081** (`endpoints.all.port`): health, `/metrics`, `/prometheus`, plus custom endpoints `SchedulerEndpoint`, `WorkerEndpoint` and `VersionEndpoint`.
- **Tracing:** `core/.../trace/` (`Tracer`, `DefaultTracer`, `NoopTracer`). Micronaut OpenTelemetry and OTLP export are disabled by default in `application.yml`.
- **Cluster view:** `ClusterController` and the UI admin pages.

---

## 19. Testing Architecture

### Backend
- **Frameworks:** JUnit 5, Micronaut Test, Mockito, AssertJ (preferred by `AGENTS.md`), Awaitility, WireMock, ArchUnit.
- **`@KestraTest`** (`tests/src/main/java/io/kestra/core/junit/annotations/KestraTest.java`) is tagged `integration`. It starts a Micronaut context, and optionally the full runner (`startRunner = true`: executor, controller, worker, scheduler and indexer through `tests/.../runners/TestRunner`).
- **Extensions:** `@LoadFlows`, `@ExecuteFlow("flows/valids/x.yaml")`, `@EvaluateTrigger`, `@WithFlow`, `@FlakyTest`.
- **Utilities:** `TestRunnerUtils#runOne`, `TestRunContextFactory`, `TestsUtils`, base suites `StorageTestSuite`, `AbstractTaskRunnerTest`, `AbstractLogDataStoreTest`.
- **Fixtures:** `core/src/test/resources/flows/valids/` (about 350 flows) and `invalids/`. Other modules reuse them through the `testArtifacts` configuration.
- **Backend reuse pattern:** abstract tests in `core` / `jdbc` (for example `core/src/test/java/io/kestra/core/runners/AbstractRunnerTest.java`) with concrete subclasses per database (`H2RunnerTest`, `MysqlRunnerTest`, `PostgresRunnerTest`).
- **Test configs:** `src/test/resources/application-test.yml` per module (H2, local storage in `/tmp/unittest`).
- **Gradle tasks:** `test` (excludes `flaky`), `unitTest` (excludes `flaky` and `integration`), `integrationTest`, `flakyTest`, `testCodeCoverageReport`. MySQL and Postgres tests need `docker compose -f docker-compose-ci.yml up -d`.

### Frontend and end-to-end
- Vitest unit tests in `ui/tests/unit/` (jsdom). Storybook stories and tests in `ui/tests/storybook/` and `ui/packages/design-system/tests/storybook/`. Guard tests enforce the color rules and the i18n rules.
- `e2e/`: Playwright (`playwright.config.ts`, base URL `localhost:9011`) with `pages/` (page objects), `api/` helpers and `fixtures/`. It runs against a Docker image (`npm run test:e2e`).
- `jmh-benchmarks/`: `ExecutionsBenchmark`, `MapUtilsBenchmark`.

### Where to add tests when you change code
| Change | Test |
|---|---|
| New or changed task | Same package under `src/test/java`. Use `@KestraTest` + `TestRunContextFactory`; for flow behavior add a YAML to `core/src/test/resources/flows/valids/` and use `@ExecuteFlow` |
| Task runner | Extend `AbstractTaskRunnerTest` |
| Controller | `webserver/src/test/java/.../controllers/api/*ControllerTest`, including the authorization allow and deny cases |
| Executor | Scenario in `AbstractRunnerTest` (or a core runner test); **`./gradlew :jdbc-h2:test --tests "H2RunnerTest"` is mandatory** |
| Repository | Abstract test in `jdbc` + dialect subclasses |
| Vue component | A Storybook story test first; Vitest only for composables and helpers; assert on `data-test` attributes |

Per `AGENTS.md`, a test must be able to fail for a reason a reviewer cares about. Don't write tests for getters, builders, or framework behavior.

---

## 20. Configuration

### How configuration flows
1. `cli/src/main/resources/application.yml` (bundled defaults: server timeouts, management port 8081, CSRF, metrics, `kestra.*` defaults, queue poll intervals, plugin repositories, `ENV_` variable prefix).
2. Micronaut environments: `cli/.../services/DefaultEnvironmentProvider`. The Gradle `run*` tasks add `override`, which loads the **gitignored** `cli/src/main/resources/application-override.yml`.
3. User config file: `-c/--config` (default `~/.kestra/config.yml`), or `MICRONAUT_CONFIG_FILES`.
4. Environment: `KESTRA_CONFIGURATION` contains a full YAML document; `gradle/jar/selfrun.sh` writes it to `confs/application.yml` and exports `MICRONAUT_CONFIG_FILES`. `KESTRA_PLUGINS_PATH` and `KESTRA_JAVA_OPTS` are also read.
5. CLI command overrides (`propertiesOverrides()`), for example `kestra.server-type` and `server local`'s forced H2 settings.
6. Bound to typed beans with `@ConfigurationProperties`: `KestraConfiguration` (`kestra`), `RepositoryConfiguration`, `StorageConfiguration`, `PluginsConfiguration`, `TasksConfiguration`, `VariableConfiguration`, `SchedulerConfiguration`, `ExecutorConfiguration`, `WorkerConfig`, `ServerConfig`, `MetricConfig`, `QueueConfiguration`, and others.
7. Backend selection: beans are annotated `@Requires(property = "kestra.repository.type" / "kestra.queue.type", value = h2|memory|mysql|postgres)`.

### Key properties
| Property | Meaning |
|---|---|
| `kestra.repository.type`, `kestra.queue.type` | `h2`, `memory`, `mysql` or `postgres` (not set in the bundled `application.yml`) |
| `datasources.<name>.*` | JDBC connection |
| `kestra.storage.type` (+ `kestra.storage.<type>.*`) | Internal storage plugin, for example `local` with `base-path` |
| `kestra.server.basic-auth.*` | Credentials and open URLs |
| `kestra.server.liveness.*` | Heartbeats |
| `kestra.controller.port`, `kestra.worker.controllers.*` | gRPC between worker and controller |
| `kestra.plugins.*` | Plugin defaults, repositories, auto-install |
| `kestra.tasks.tmp-dir` | Working directory for tasks |
| `kestra.url` | Public URL |
| `kestra.jdbc.queues.*` | Queue polling intervals |

### Configuration by environment
- **Development:** `application-override.yml` (gitignored, copied to worktrees by `dev-tools/setup-worktree.sh`), or `server local` (H2 files in `./data`).
- **Test:** `application-test.yml` per module, plus profiles such as `application-liveness.yml`, `application-queue.yml`, `application-otel.yml`.
- **Docker / production:** `KESTRA_CONFIGURATION` in `docker-compose.yml` (Postgres + local storage) or Helm `values.yaml`.

---

## 21. Build and Development Workflow

| Task | Command |
|---|---|
| Build without tests | `./gradlew build -x test -x integrationTest -x testCodeCoverageReport --parallel` |
| Build the UI into the backend | `cd ui && npm ci && npm run build` (output goes to `webserver/src/main/resources/ui`, gitignored) |
| Executable binary | `make build-exec` (UI + shadow jar + self-run prefix → `build/executable/kestra-<version>`) |
| Run backend, zero setup | `./gradlew runLocal` (`server local`: H2 + local storage) |
| Run backend on Postgres | `docker compose -f docker-compose-ci.yml up -d`, write `application-override.yml`, then `./gradlew runStandalone` |
| Run from an IDE | Main class `io.kestra.cli.Kestra`, args `server local`, env `MICRONAUT_ENVIRONMENTS=override`, `KESTRA_PLUGINS_PATH=…` |
| Run frontend | `cd ui && npm install && npm run dev` → `http://localhost:5173`, proxies `/api` to `VITE_PROXY_URL` (default `http://localhost:8080`) |
| Backend tests | `./gradlew :<module>:test --tests "ClassName"`, then the module; executor changes: `./gradlew :jdbc-h2:test --tests "H2RunnerTest"` |
| Frontend checks | `cd ui && npm run check:types && npm run test:unit && npm run lint` |
| E2E | `cd e2e && npm ci && npm run test:e2e`, or `./build-and-start-e2e-tests.sh` |
| Regenerate OpenAPI and SDK | `npm run generate:openapi-spec` / `npm run generate:sdk` in `ui/` |
| Test failure summary | `npx --yes @kestra-io/kestra-devtools generateTestReportSummary --only-errors $(pwd)` |

**Debugging:** `--logging.level.io.kestra=DEBUG`, JMX is enabled by the `run` task, the management endpoints are on port 8081, and RapiDoc is at `/api`. If the UI build runs out of memory, set `NODE_OPTIONS=--max-old-space-size=4096`. If `npm install` fails with `EBADENGINE`, install npm 11.16.0.

**Git hooks:** `.github/.hooks/setup_hooks.sh` installs a pre-commit hook that runs `spotlessApply` on staged Java files and, for staged UI files, `check:ts-any` and `lint-staged`.

---

## 22. Docker / Deployment

- **`Dockerfile`:** `FROM ghcr.io/kestra-io/kestra-base:latest-slim`. It copies `docker/` to `/` and `plugins/` to `/app/plugins`, runs as user `kestra`, and uses the entrypoint `docker-entrypoint.sh` (`exec /app/kestra "$@"`). The binary must first be placed at `docker/app/kestra` (`make build-docker-from-exec`).
- **`Dockerfile.base`:** `eclipse-temurin:25-jre-noble` + curl, jattach, uv, optional Python. Rebuilt nightly by CI.
- **`Dockerfile.pr`:** PR images based on `kestra/kestra:develop`.
- **`docker-compose.yml`:** Postgres 18 + `kestra/kestra:latest server standalone`, Docker socket mounted, port 8080. `docker-compose-dind.yml` adds a rootless Docker-in-Docker sidecar. `docker-compose-ci.yml` contains only the MySQL and Postgres databases for tests.
- **Helm:** `charts/kestra` (deployment, service, ingress, configmap; helm unit tests) and `charts/kestra-starter` (bundles Postgres).
- **Distributed mode:** run `server webserver`, `server executor`, `server scheduler`, `server indexer`, `server controller` and `server worker` separately against a shared database and internal storage. Workers need network access to the controller's gRPC port (50051).

**Deploying your own fork:** build the UI and the binary (`make build-exec`), then either build an image (`make build-docker-from-exec` or the Dockerfile) or run the binary with `server standalone` and a `KESTRA_CONFIGURATION` that points at Postgres and persistent storage. Don't use H2/`server local` in production.

---

## 23. Module Dependencies

From `project(':x')` declarations in each `build.gradle` (`impl` = implementation; test-only dependencies omitted):

```
platform  (BOM, applied to all modules)
model
 └─ processor (api model)
     core  (api model, annotationProcessor processor)
      ├─ script
      ├─ storage-local
      ├─ queue
      │   └─ queue-jdbc ──────────────┐ (also jdbc)
      ├─ executor ─┐                  │
      ├─ scheduler ┼─ jdbc (impl core, scheduler, executor, worker)
      ├─ worker-controller            │
      │   └─ worker ──────────────────┘
      ├─ indexer
      ├─ webserver
      └─ jdbc-h2 / jdbc-mysql / jdbc-postgres
             (impl core, jdbc, executor, scheduler, queue, queue-jdbc)
                └─ repository-memory (api jdbc, impl jdbc-h2)
                       └─ runner-memory (api repository-memory)
cli  (impl: everything above) ── root project
tests (impl core, worker, scheduler, executor, indexer; compileOnly webserver)
jmh-benchmarks (core)
```

Every module's tests depend on `tests`, usually `storage-local` and `jdbc-h2`, and on the `testArtifacts` of `core`, `jdbc` or `queue`. The **only module that knows about all others is `cli`**. Service modules depend on `core` interfaces rather than on each other, except `worker` → `worker-controller` (the gRPC contract) and `jdbc` → `executor`/`scheduler`/`worker` (it implements their state stores).

---

## 24. Extension Points

Prefer these, in this order, over editing core classes.

| Extension point | What it does | How to extend | Existing examples |
|---|---|---|---|
| **Task plugin** | New action in a flow | Separate Gradle project depending on `core` (compileOnly) with `annotationProcessor` `processor`; class `extends Task implements RunnableTask<MyOutput>`, annotated `@Plugin`, `@Schema`, `@Example`. Put the jar in `KESTRA_PLUGINS_PATH` (`dev-tools/copy-plugin.sh` helps) | `plugin/core/log/Log`, `plugin/core/http/Request` |
| **Flowable task** | Custom control flow | `implements FlowableTask<O>` (`childTasks`, `resolveNexts`, `resolveState`) | `plugin/core/flow/If`, `Parallel` |
| **Trigger plugin** | New event source | `extends AbstractTrigger` + `PollingTriggerInterface` (periodic check on a worker), `RealtimeTriggerInterface` (stream) or `Schedulable` | `plugin/core/http/Trigger`, `trigger/Schedule`, `trigger/Webhook` |
| **Task runner** | Where scripts run | `extends TaskRunner` | `plugin/core/runner/Process`, `script/.../runner/docker/Docker` |
| **Internal storage** | Where files live | `implements StorageInterface`, select with `kestra.storage.type` | `storage-local/.../LocalStorage` |
| **Log exporter / file renderer / chart / plugin endpoint** | Export logs, preview files, dashboard charts, plugin HTTP endpoints | Implement `LogExporter`, `FileRenderer`, `Chart`/`DataFilter`, `PluginEndpoint` | `plugin/core/preview/*`, `plugin/core/dashboard/*` |
| **Plugin defaults** | Default task properties | `kestra.plugins.configurations` or flow `pluginDefaults`; read with `runContext.pluginConfiguration()` | `Schedulable#defaultRecoverMissedSchedules` |
| **Pebble functions and filters** | New expression helpers | Any Micronaut bean of type `io.pebbletemplates.pebble.extension.Extension` is loaded by `PebbleEngineFactory` | `core/.../runners/pebble/Extension` |
| **Micronaut bean replacement** | Swap a service implementation | `@Singleton @Replaces(Original.class)` in a module on the classpath | `TenantAliasingRooter` (`@Replaces(DefaultRouter)`), `GrpcKVBackend` (`@Replaces(StorageKVBackend)`), `PostgresMigrationHistoryStore` |
| **Interfaces designed for replacement** | EE hooks usable by a fork | Provide a bean | `ConcurrencyLimitStateStore`, `NamespaceService`, `ExecutionTerminatedNotifier`, `TestTenantLifecycle`, `McpToolAccessControl` |
| **Plugin UI** | UI shipped inside a plugin | `plugin-ui/manifest.json` in the jar, loaded with module federation into the slots in `@kestra-io/slot-contracts` | `LowCodeEditor.vue` slots |
| **Frontend overrides** | Replace UI parts | Files under `ui/src/override/` | `override/stores/auth.ts`, `override/components/LeftMenu` |
| **Configuration** | Tune behavior | `kestra.*` properties (section 20) | — |

**Recommendation for your fork:** build new capabilities as **external plugins** whenever possible. They don't touch the core, so merging upstream updates stays easy. Use `@Replaces` beans in a separate module for behavior changes, and edit core classes only as a last resort.

---

## 25. Important Classes and Files

| File | Why it matters |
|---|---|
| `cli/src/main/java/io/kestra/cli/Kestra.java` | Application entry point |
| `cli/src/main/java/io/kestra/cli/StandAloneRunner.java` | Starts every service in one JVM |
| `cli/src/main/resources/application.yml` | Bundled default configuration |
| `core/src/main/java/io/kestra/core/models/flows/Flow.java` | Flow model |
| `core/src/main/java/io/kestra/core/models/executions/Execution.java` | Execution model (`newExecution`, `isTerminated`, `guessFinalState`) |
| `core/src/main/java/io/kestra/core/models/executions/TaskRun.java` | Task run model |
| `core/src/main/java/io/kestra/core/models/flows/State.java` | States and transitions |
| `core/src/main/java/io/kestra/core/services/FlowService.java` | Flow create, update, validation, side effects |
| `core/src/main/java/io/kestra/core/services/ExecutionService.java` | Execution creation, restart, resume, terminal checks |
| `core/src/main/java/io/kestra/core/runners/RunContext.java`, `RunContextInitializer.java`, `RunContextLogger.java` | Task runtime API, variables and logs |
| `core/src/main/java/io/kestra/core/plugins/PluginScanner.java` | Plugin discovery |
| `core/src/main/java/io/kestra/core/runners/pebble/Extension.java` | Pebble functions and filters |
| `core/src/main/java/io/kestra/core/storages/StorageInterface.java` | Internal storage contract |
| `core/src/main/java/io/kestra/core/storages/kv/KVStore.java` | KV contract |
| `core/src/main/java/io/kestra/core/secret/SecretService.java` | OSS secrets |
| `core/src/main/java/io/kestra/core/tenant/TenantService.java` | OSS tenant (`main`) |
| `core/src/main/java/io/kestra/core/migration/MigrationRunner.java` | Schema migrations |
| `queue/src/main/java/io/kestra/queue/QueueFactoryInterface.java` | List of all queues |
| `queue-jdbc/src/main/java/io/kestra/queue/jdbc/client/JdbcQueueClient.java` | JDBC queue reads and writes |
| `executor/src/main/java/io/kestra/executor/DefaultExecutor.java` | Executor service and queue subscriptions |
| `executor/src/main/java/io/kestra/executor/ExecutorCore.java` | Message routing and terminal handling (`toExecution`) |
| `executor/src/main/java/io/kestra/executor/ExecutorService.java` | `process` pipeline: next tasks, flowables, retries, end |
| `executor/src/main/java/io/kestra/executor/handler/ExecutionEventMessageHandler.java` | Main execution step, sends worker jobs |
| `executor/src/main/java/io/kestra/executor/handler/ExecutionCommandMessageHandler.java` | Create, restart, resume, … commands |
| `scheduler/src/main/java/io/kestra/scheduler/TriggerScheduler.java` | Trigger evaluation |
| `worker-controller/src/main/java/io/kestra/controller/grpc/services/WorkerJobDispatcher.java` | Job dispatch to workers |
| `worker/src/main/java/io/kestra/worker/processors/WorkerTaskProcessor.java` | Runs a task on the worker |
| `indexer/src/main/java/io/kestra/indexer/DefaultIndexer.java` | Persists logs and metrics |
| `jdbc/src/main/java/io/kestra/jdbc/repository/AbstractJdbcExecutionRepository.java` | Execution storage and the execution lock |
| `jdbc/src/main/java/io/kestra/jdbc/repository/AbstractJdbcFlowRepository.java` | Flow storage and revisions |
| `jdbc-postgres/src/main/resources/migrations/` | Reference schema |
| `webserver/src/main/java/io/kestra/webserver/controllers/api/ExecutionController.java` | Execution API, webhooks, SSE |
| `webserver/src/main/java/io/kestra/webserver/controllers/api/FlowController.java` | Flow API |
| `webserver/src/main/java/io/kestra/webserver/filter/AuthenticationFilter.java` | API authentication |
| `webserver/src/main/java/io/kestra/webserver/services/BasicAuthService.java` | Basic-auth credentials |
| `tests/src/main/java/io/kestra/core/junit/annotations/KestraTest.java` | Test bootstrap |
| `core/src/test/java/io/kestra/core/runners/AbstractRunnerTest.java` | Main end-to-end runner tests |
| `ui/src/main.ts`, `ui/src/utils/init.ts`, `ui/src/routes/routes.ts` | Frontend bootstrap and routes |
| `ui/packages/kestra-sdk/` | Generated API client |
| `ui/packages/design-system/` | UI component library |
| `AGENTS.md`, `ui/AGENTS.md` | Contribution rules |

---

## 26. Example End-to-End Execution Flow

A flow `company.team/hello` with one `io.kestra.plugin.core.log.Log` task. The user clicks **Execute**:

1. **UI:** `TriggerFlow.vue` → `FlowRun.vue` → `executionsStore.triggerExecution` → `POST /api/v1/main/executions/company.team/hello`.
2. **Webserver:** `AuthenticationFilter` checks the cookie, then `ExecutionController#createExecution` validates the flow and inputs, builds `ExecutionCommand.Create(id)`, emits it on `executionCommandQueue` (`JdbcQueueClient` inserts a row into `queues`), and waits.
3. **Executor:** `DefaultExecutor` polls the command → `ExecutorCore#onExecutionCommand` → `ExecutionCommandMessageHandler#handleCreate` → `ExecutionService#create` → `Execution.newExecution` (CREATED) → row in `executions` → `AsyncOperationProcessedEvent` releases the HTTP request, which returns the execution.
4. **Executor:** `ExecutionEventMessageHandler#handle(CREATED)` under the row lock: concurrency check → `ExecutorService#process` → `handleNext` creates TaskRun `log` (CREATED) → `handleWorkerTasks` builds a `WorkerTask` → execution RUNNING, TaskRun SUBMITTED → `workerJobEventQueue.emit`. Commit. `ExecutionEvent(UPDATED)` and a follow event are emitted, and the UI's SSE `/executions/{id}/follow` updates.
5. **Controller:** `WorkerJobDispatcher#handleIncomingJob` → reserves a worker → saves `worker_job_running` → sends the job over gRPC.
6. **Worker:** `WorkerJobFetcher` → `WorkerJobExecutor` → `WorkerTaskProcessor#runTask` → `RunContextInitializer#forWorker` → RUNNING result sent → `WorkerTaskCallable#doCall` → **`Log#run(runContext)`** → `runContext.logger().info(...)`.
7. **Log:** `RunContextLogger` → `WorkerLogEntryEmitter` → gRPC → `DefaultLogEntryEmitter` → `logEntryQueue` (the indexer writes to `logs`) and `followLogEventQueue` (the UI's log SSE).
8. **Worker:** `WorkerTaskResult(SUCCESS)` → gRPC → `GrpcWorkerControllerService#sendWorkerTaskResults` → `workerTaskResultQueue`; the `worker_job_running` row is deleted.
9. **Executor:** `WorkerTaskResultMessageHandler` → lock → `ExecutorService#addWorkerTaskResult` (TaskRun SUCCESS, outputs saved) → `ExecutionEvent(UPDATED)`.
10. **Executor:** `ExecutorService#process` → `handleEnd` → `Execution#isTerminated` → `guessFinalState` = **SUCCESS** → persisted.
11. **Executor:** `ExecutorCore#toExecution`, terminal branch → flow triggers checked, concurrency slot released, `TERMINATED` events emitted, `ExecutionStatistic` to the indexer. The UI SSE receives the final state and closes.

---

## 27. Important Findings

1. **2.x is a different architecture from what most public material describes:** command-based execution creation, gRPC workers behind a controller, a single `queues` table, vNode-partitioned scheduler, custom migrations, and no `Condition` plugins.
2. **The database is both the store and the message bus** in OSS. Queue throughput and row locks on `executions` are the main scaling factors. Kafka and Elasticsearch backends appear only as BOM entries and EE references.
3. **The executor is the single writer of execution state**, serialized per execution by a row lock. Changes in `executor` need careful testing; `H2RunnerTest` is mandatory.
4. **Workers are isolated from the database.** Anything a task needs at runtime must reach the worker through `RunContext` and the gRPC controller services.
5. **OSS boundaries are explicit stubs:**
   - Tenancy is fixed to `main`.
   - Namespace ACLs always allow.
   - There is no RBAC.
   - Secrets come only from `SECRET_` environment variables.
   - Stored dashboards, apps, assets, audit logs, quotas, policies, cases and tests are EE (UI demo pages in `ui/src/components/demo/`, backend stubs such as `QuotaService`, `FlowValidator` rejecting `assets`, `DashboardController` serving only defaults).
   - Namespace and tenant concurrency scopes are EE.
6. **SLA works in OSS** (`core/.../models/flows/sla/`, `sla_monitor`, `DefaultExecutor` SLA loop).
7. **The UI is not built by Gradle.** You must run `npm run build` in `ui/` before packaging, otherwise the jar has no UI.
8. **`application-*.yml` in `cli/src/main/resources` is gitignored.** Local overrides never reach the repository.
9. **Licensing:** Apache 2.0 (`LICENSE`). Keep `LICENSE`/`NOTICE`, and don't use the Kestra trademark for your own product.
10. `dev-tools/analyze-test-heap.sh` is referenced in `settings.gradle` and `build.gradle` comments but does not exist in the repository.

---

## 28. Areas That Need Further Investigation

| Topic | What was inspected | What is still unclear |
|---|---|---|
| JDBC broadcast subscriber offsets | `JdbcQueueClient#subscribeDispatch`, broadcast fetch methods | Whether broadcast consumers track offsets durably or only in memory (`JdbcBroadcastSubscriber` not read) |
| Keyed dispatch routing | `routing_key` filter in `subscribeDispatch` | Exact semantics of `JdbcKeyedDispatchQueue` (assumed key = worker queue id) |
| Polling trigger results → scheduler | `GrpcWorkerControllerService#sendWorkerTriggerResults`, event classes | Full wiring from `WorkerTriggerResult` to `TriggerEventHandler` |
| `FlowTriggerService` | Call sites in `ExecutorCore` | Internals of `computeExecutionsFromFlowTriggerConditions` and window handling |
| Queued executions | `ConcurrencySlotReleaseProcessor` call site | How `ExecutionQueuedStateStore` pops and orders queued executions |
| Worker timeouts | `WorkerTaskCallable#doCall` | Internals of `AbstractWorkerCallable#callWithTimeout` and `callJob` |
| RunContext variables | `RunContextInitializer#forWorker` call site | Full variable-building step |
| vNode rebalancing | Class names in `core/.../scheduler/vnodes/` | How `DefaultVNodesAssigner` rebalances when instances join or leave |
| Worker queues / worker groups | `WorkerSelector`, `SinglePoolCapacityPolicy` | Whether more than one worker queue works in OSS or is EE-gated |
| Task-level concurrency | Searched `executor` and `core` | None found besides trigger `allowConcurrent` |
| Purge and SLA violation paths | Seen as classes (`PurgeExecutions`, `SLAMonitorProcessor`) | Not traced end to end |
| `LockRepository` usage | `AbstractJdbcLockRepository` | Which services use the `locks` table besides concurrency |
| Agents tables | `AbstractJdbcAgentMessageRepository`, `AbstractJdbcAgentThreadRepository` | No table or subclass found in OSS migrations |
| `PluginProcessor` internals | Its registration and `ServicesFiles` | Body not read in detail |
| UI SDK with a non-root context path | `configureClient({})` in `main.ts` | Whether generated SDK calls respect `micronaut.server.context-path` |
| EE UI override mechanism | `override` alias in `vite.config.js` | How EE swaps the alias (EE repo not available) |
| CLI server subcommands | Command names confirmed | Bodies of `WebServerCommand`, `WorkerCommand`, `ExecutorCommand` not read |
