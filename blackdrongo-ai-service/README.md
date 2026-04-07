# BlackDrongo AI Service

Separate Python service for AI-assisted test generation and self-healing suggestions on top of the Java BlackDrongo
automation framework.

## Scope

This service does not modify the Java framework directly. It exposes HTTP APIs that the Java framework or
`blackdrongo-ui-generator` can call.

## MVP capabilities

- Convert plain text into:
    - Gherkin feature content
    - Java step-definition skeletons
    - a page object skeleton
- Analyze failed test artifacts and return self-healing suggestions for:
    - broken locators
    - missing waits
    - stale element style failures
    - likely assertion mismatches
- Persist generation and healing history to local JSONL files
- Optionally call OpenAI without changing the API surface

## Project structure

- `app/main.py` - FastAPI entrypoint
- `app/config.py` - environment-backed settings
- `app/models.py` - request and response schemas
- `app/routers/generate.py` - text-to-test APIs
- `app/routers/heal.py` - self-healing APIs
- `app/services/generator.py` - deterministic generation logic with optional OpenAI hook
- `app/services/healer.py` - deterministic healing analysis with optional OpenAI hook
- `app/services/artifact_reader.py` - reads TestNG/Cucumber/log artifacts to enrich failure analysis
- `app/services/history_store.py` - JSONL persistence for generation and healing history
- `prompts/` - prompt templates used by the OpenAI-backed mode
- `data/history/` - generated audit trail

## Run locally

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

To enable live model calls:

```bash
set AI_PROVIDER=openai
set OPENAI_API_KEY=your-key
set OPENAI_MODEL=gpt-4.1-mini
```

If the OpenAI package or key is unavailable, the service falls back to deterministic logic.

## API overview

### Health

`GET /health`

### Generate tests

`POST /api/v1/generate/tests`

Example body:

```json
{
  "project_name": "Amazon",
  "base_url": "https://example.com",
  "feature_name": "Login",
  "scenario_name": "User logs in successfully",
  "plain_text": "Open login page. Enter valid username. Enter valid password. Click login. Verify dashboard is displayed.",
  "package_name": "steps.generated"
}
```

### Heal failures

`POST /api/v1/heal/failure`

Example body:

```json
{
  "scenario_name": "User logs in successfully",
  "failed_step": "When user clicks login button",
  "error_message": "NoSuchElementException: Unable to locate element: //button[@id='login-btn']",
  "dom_excerpt": "<button data-testid='login-submit'>Sign in</button>",
  "current_locator": "//button[@id='login-btn']",
  "screenshot_path": "target/screenshots/User_logs_in_successfully.png",
  "artifact_paths": {
    "failure_artifact_dir": "../target/surefire-reports",
    "cucumber_report_path": "../target/cucumber-reports/cucumber.json",
    "console_log_path": "../blackdrongo-ui-generator/ui-generator.out.log"
  }
}
```

If `error_message`, `dom_excerpt`, or `current_locator` are omitted, the service will try to infer them from the
supplied artifact paths and configured artifact roots.

## Integration approach

1. `blackdrongo-ui-generator` calls the generate endpoint when a user submits natural-language steps.
2. Java hooks or listeners POST failure details to this service after a failed scenario.
3. The Java side can pass artifact paths instead of manually assembling all failure text.

## Notes

- Current implementation uses deterministic generation and healing by default to keep the MVP predictable.
- OpenAI-backed mode is wired behind the same API shape and enabled through environment variables.
- Each generate/heal call is appended to `data/history/*.jsonl`.
