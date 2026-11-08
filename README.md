# AI Nutrition Planner

A sample project demonstrating how to build AI agents with different Java/Spring frameworks. The same nutrition planning use case is implemented across three frameworks to compare their programming models, abstractions, and features.

[Slides: Comparing Agentic AI Frameworks for Java](slides.pdf)

## Use Case

The agent creates a personalized weekly meal plan:

1. Fetches the user profile and seasonal ingredients in parallel
2. Asks the user for meal preferences to tailor the plan (human-in-the-loop)
3. Generates recipes for the requested days and meals using seasonal produce
4. Validates the plan against the user profile (allergens, calorie limits, dietary restrictions)
5. Revises recipes based on feedback and re-validates — looping until the plan passes or a maximum number of iterations is reached

## Implementations

| Framework | Folder | Key Pattern |
|-----------|--------|-------------|
| [Embabel](https://github.com/embabel/embabel-agent) | [`embabel/`](embabel/) | GOAP planner over `@Agent` / `@Action` / `@State` with a goal-driven state machine |
| [LangChain4j](https://docs.langchain4j.dev) | [`langchain4j/`](langchain4j/) | `langchain4j-agentic` declarative API — `@SequenceAgent` / `@ParallelAgent` / `@LoopAgent` composing `@Agent` sub-agents |
| [Spring AI](https://spring.io/projects/spring-ai) | [`spring-ai/`](spring-ai/) | Fluent `ChatClient` API with custom advisors and code-orchestrated workflow |

## Agentic AI Patterns Implemented

| Pattern | Embabel | LangChain4j | Spring AI |
|---------|:-------:|:-----------:|:---------:|
| **Parallel Execution** — independent steps run concurrently | ✅ framework-managed (GOAP planner) | ✅ `@ParallelAgent` | ✅ plain `CompletableFuture` — no direct framework support |
| **Validation / Reflection Loop** — iterate until output passes quality checks | ✅ `@State` machine (NutritionAudit → ReviseWeeklyPlan) | ✅ `@LoopAgent` + `@ExitCondition` | ✅ `ValidationRetryAdvisor` |
| **Tool Use** — agent calls external functions | ✅ `withToolObject()` | ✅ `@ToolsSupplier` | ✅ `.tools()` on `ChatClient` |
| **Tool Search** — dynamic tool discovery at runtime | ✅ `@UnfoldingTools` — a facade tool progressively discloses its inner tools on invocation | ✅ `@ToolSearchStrategySupplier` (`SimpleToolSearchStrategy`) — only the tool-search tool is sent to the LLM, which discovers matching tools by keywords | ✅ `tool-search-advisor` — a separate search step discovers tools via metadata before each call |
| **Human-in-the-Loop** — pause workflow for user input | ✅ `WaitFor.formSubmission()` | ✅ `@HumanInTheLoop` + `SuspendedResponse` | ✅ `AskUserQuestionTool` |
| **Agent Skills** — invoke pre-packaged executable skills | ✅ `Skills` with script execution engine | ✅ `Skills.from()` via `CustomSkillLoader` | ✅ `SkillsTool` |
| **Multi-Agent Orchestration** — specialized sub-agents collaborate | ✅ goal-driven action graph | ✅ `@SequenceAgent` with sub-agents | ✅ custom orchestration in code — no direct framework support |
| **Persona** — role-based system prompts per agent | ✅ first-class `Persona` object | ✅ custom implementation via `@SystemMessage` | ✅ custom implementation via `.system()` on `ChatClient` |
| **MCP Server** — expose agent as a Model Context Protocol tool | ✅ `@Export(remote = true)` | ❌ | ✅ `@McpTool` |

## Prerequisites

- **Java 25**
- **Maven** (Maven wrapper included)
- **Docker Desktop** (for Grafana observability stack and Ollama)
- An LLM provider: **Azure OpenAI**, **OpenAI**, or **Ollama** (local)

## Setup

### Codespaces (recommended)

Open this repo in a GitHub Codespace. The dev container installs Java 25, Maven, Docker, and Ollama automatically. After the container starts, Ollama is ready at `localhost:11434` with the `qwen2.5` model pre-pulled.

### LLM Providers

**Ollama** (local, no API key required):

```bash
docker compose --profile ollama up -d
```

This starts Ollama and automatically pulls `qwen2.5`. 

**Azure OpenAI**:

```bash
export AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
```

**OpenAI**:

```bash
export OPENAI_API_KEY=sk-...
```

## Run

Change into the implementation directory, set the Spring profile related to an LLM provider and start the app:

```bash
cd langchain4j   # or embabel, spring-ai
export SPRING_PROFILES_ACTIVE=ollama # or openai, azure
./mvnw spring-boot:run
```

The app starts on port `8080`. Basic auth: `alice` / `123456`. UI at [http://localhost:8080](http://localhost:8080), REST API at `http://localhost:8080/api/nutrition-plan`.


### Deploy to Azure
The project supports [Azure Developer CLI](https://learn.microsoft.com/azure/developer/azure-developer-cli/) for one-command deployment to Azure Container Apps:

```bash
azd auth login
azd up
```

This provisions a Container Apps Environment, Azure Container Registry, Azure OpenAI (gpt-4o), and one Container App per framework. See [`infra/`](infra/) for Bicep templates and [`azure.yaml`](azure.yaml) for the service manifest.

## Observability

A Grafana + OTLP stack (Loki, Tempo, Mimir) is included via Docker Compose:
```bash
docker compose --profile observability up -d
```

- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **OTLP collector**: `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

To enable tracing and metrics export from any module, activate the `observability` profile 
in addition to the profile for the LLM provider of choice:

```bash
SPRING_PROFILES_ACTIVE=ollama,observability mvn spring-boot:run
```
The dashboard shows agent invocation rates, execution durations (p95), active agents, HTTP endpoint latency, JVM metrics, and distributed traces.

## Example Request

```bash
curl -s -X POST http://localhost:8080/api/nutrition-plan \
  -u alice:123456 \
  -H "Content-Type: application/json" \
  -d '{
    "days": [
      { "day": "MONDAY",    "meals": ["BREAKFAST", "LUNCH", "DINNER"] },
      { "day": "TUESDAY",   "meals": ["BREAKFAST", "LUNCH", "DINNER"] },
      { "day": "WEDNESDAY", "meals": ["LUNCH", "DINNER"] }
    ],
    "countryCode": "DE",
    "additionalInstructions": "Prefer quick recipes with less than 30 minutes prep time."
  }' | jq .
```
