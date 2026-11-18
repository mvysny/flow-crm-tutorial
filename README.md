# Spring Boot and Vaadin course source code

This repository contains the source code for the [Building Modern Web Applications With Spring Boot and Vaadin](https://vaadin.com/docs/latest/flow/tutorials/in-depth-course).

*Live demo:* https://crm.demo.vaadin.com

## Running the Application
There are two ways to run the application:  using `mvn` or by running the `Application` class directly from your IDE.

## Branches

- The main branch contains the source code for the latest Vaadin release
- The `v14` branch contains the source code for Vaadin 14


## Text tutorial
You can find a text version of the tutorial in the [Vaadin Documentation](https://vaadin.com/docs/latest/flow/tutorials/in-depth-course).

## Benchmark hooks

This fork is an app for [unified-test-mcp](https://github.com/mvysny/unified-test-mcp)'s benchmark,
which measures how an AI agent drives it through different browser MCP servers.

    ./mvnw package -DskipTests
    java -Duser.language=en -Duser.country=US -jar target/flowcrmtutorial-1.0-SNAPSHOT.jar \
      --server.port=8080 --management.server.port=8081 --benchmark.seed=1

- The demo data (200 contacts) is generated from `benchmark.seed`, no longer `data.sql`.
- With `management.server.port` set, `POST /actuator/benchmark/<scenario>` on that port, no login:
  `{"phase":"setup"}` picks the scenario's parameters from the data and returns them;
  `{"phase":"check","answer":"<the agent's final text>"}` returns `passed`, `details` and the
  `attempts` (Save and Delete clicks) since setup. Without it, nothing new is exposed.
- Scenarios: `crm-add-contact`, `crm-move-contact`, `crm-count`, `crm-delete-closed`,
  `crm-invalid-email` (`BenchmarkEndpoint`).
