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
- **Configuration**: `ConfigReader` loads shared settings from `config.properties` with system property/env overrides.
- **TestNG utilities**: retry and execution listeners are available under `listeners/`.
- **Cucumber hooks**: default hooks are available under `hooks/` (base URL driven by config).

## Project Layout

- `src/main/java` - framework utilities (browser, steps, assertions, hooks, listeners)
- `src/test/java` - sample page objects, step definitions, and runners
- `src/test/resources` - sample features and test configuration

## Setup

1. Install JDK 25.
2. Ensure Maven is available on your PATH.
3. Update test configuration:
    - Edit `src/test/resources/config.properties`
   - Set `url` and any test data required by your step definitions.

## Run Tests

This project uses TestNG via `testng.xml`.

```
mvn test
```

## Reuse In Other Projects

1. Install this framework to your local Maven repo:

```
mvn -DskipTests install
```

2. Add it as a test-scoped dependency in your other project:

```xml
<dependency>
    <groupId>BlackDrongo</groupId>
    <artifactId>BlackDrongo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

3. Add `config.properties` to the consuming project's `src/test/resources` with at least:

```
browser=chrome
url=https://example.com
retry.count=1
```

You can override any config key via system property or environment variable
(e.g., `-Durl=https://example.com` or `URL=https://example.com`).
See `src/main/resources/config.properties` for the full standard template.

## Extension Points

- Add new browser capabilities in `browsers/` and `browserOptions.json`.
- Add reusable UI actions in `steps/ElementActions`.
- Add higher-level flows in page objects under `src/test/java/pageObjects`.
