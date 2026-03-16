# BlackDrongo

Java-based automation framework using Selenium, Cucumber, and TestNG, with support for API tests (Rest Assured).

## Tech Stack

- Java 25
- Selenium 4
- Cucumber 7
- TestNG 7
- Maven
- Rest Assured

## Framework Overview

This framework provides a thin automation layer on top of Selenium and Cucumber with reusable building blocks:

- **Browser management**: Centralized driver creation and lifecycle via `BrowserManager` and `DriverFactory`.
- **Action layer**: `Step`, `ElementActions`, and `WaitActions` encapsulate common UI interactions and waits.
- **Assertion layer**: `ElementAssertions` offers consistent, readable assertions for UI state.
- **Configuration**: `ConfigReader` loads shared settings from `config.properties`.
- **API utilities**: REST helpers in `api/*` support request building and response validation.

## Project Layout

- `src/main/java` - framework utilities (browser, steps, assertions, api helpers)
- `src/test/java` - page objects, step definitions, and runners
- `src/test/resources` - features and test configuration

## Setup

1. Install JDK 25.
2. Ensure Maven is available on your PATH.
3. Update test configuration:
    - Edit `src/test/resources/config.properties`
    - Set `resume.path` to an absolute file path for your resume.

Example:

```
resume.path=C:\Users\YourName\Documents\Resume.pdf
```

## Run Tests

This project uses TestNG via `testng.xml`.

```
mvn test
```

## Extension Points

- Add new browser capabilities in `browsers/` and `browserOptions.json`.
- Add reusable UI actions in `steps/ElementActions`.
- Add higher-level flows in page objects under `src/test/java/pageObjects`.
- Add API request specs and builders under `src/main/java/api`.
