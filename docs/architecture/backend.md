# Backend choices and Spring setup

Choosing KMP/CMP does not imply that the product needs its own server.

## No server or an existing server

- Do not generate serverApp.
- Keep existing HTTP APIs behind client repositories and DTO/mapping layers.
- Do not impose a server architecture unless the product contract requires it.

## Adding Spring Boot

- Keep the Spring Boot entry point, configuration, HTTP layer, and database adapters in JVM server modules.
- Do not apply the Spring Boot plugin and KMP plugin to the same Gradle project. Kotlin's compatibility guide describes conflicts with Java ecosystem plugins, including Spring Boot applying Gradle's Application plugin, and recommends a separate non-KMP subproject.
- Share KMP contracts with the server only when that reuse provides a real benefit.
- Record database access, migrations, and transaction boundaries as server decisions.
- Do not reuse client DTOs as persistence entities.

## Questions for the Spring profile

- Who operates the API and responds to incidents?
- Who owns the database and migrations?
- Does sharing KMP contracts actually remove duplication across the runtimes?
- Why Spring Boot: team experience, existing infrastructure, or ecosystem integrations?
- Can the product support deployment, observability, and security requirements?

Spring Boot with KMP is an optional profile, not Weft's default stack.

Reference: [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
