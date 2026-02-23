# Firefly Framework - Web

[![CI](https://github.com/fireflyframework/fireflyframework-web/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-web/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Web layer library with global exception handling, idempotency, PII masking, OpenAPI configuration, and HTTP request logging.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Web provides the web layer infrastructure for Firefly-based microservices. It includes a comprehensive global exception handler that converts exceptions to standardized error responses, an idempotency framework for safe request retries, PII masking for log sanitization, and OpenAPI/Swagger configuration.

The exception handler supports a wide range of exception types through pluggable converters, covering validation errors, security exceptions, database errors, resilience failures, and external service errors. Each exception is mapped to an appropriate HTTP status code with structured error responses following the RFC 7807 Problem Details format.

The idempotency subsystem provides a cache-backed idempotency filter that prevents duplicate request processing, with automatic OpenAPI documentation of idempotency headers. The conditions report feature provides runtime visibility into auto-configuration decisions.

## Features

- Global exception handler with 25+ exception converters (validation, security, DB, resilience, WebFlux)
- RFC 7807 Problem Details error response format
- Idempotency framework with `IdempotencyWebFilter` and cache-backed deduplication
- `@DisableIdempotency` annotation for opt-out on specific endpoints
- Automatic OpenAPI documentation of idempotency headers
- PII masking service for structured log sanitization
- PII masking Logback appender for stdout protection
- HTTP request/response logging web filter
- OpenAPI/SpringDoc auto-configuration for WebFlux
- Conditions report for auto-configuration diagnostics
- Error response content negotiation
- Error response caching for repeated error patterns
- Spring Boot auto-configuration for all web layer components

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-web</artifactId>
    <version>26.02.06</version>
</dependency>
```

## Quick Start

```java
import org.fireflyframework.web.error.exceptions.ResourceNotFoundException;
import org.fireflyframework.web.error.exceptions.BusinessException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Mono<Order> findById(@PathVariable String id) {
        return orderService.findById(id)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found: " + id)));
    }

    @PostMapping
    public Mono<Order> create(@Valid @RequestBody OrderRequest request) {
        // Validation errors are automatically converted to 400 responses
        // Business exceptions are converted to appropriate HTTP status codes
        return orderService.create(request);
    }
}
```

## Configuration

```yaml
firefly:
  web:
    error-handling:
      include-stack-trace: false
      include-suggestions: true
    idempotency:
      enabled: true
      ttl: 24h
      header-name: Idempotency-Key
    pii-masking:
      enabled: true
      patterns:
        - email
        - phone
        - ssn
    http-logging:
      enabled: true
      log-headers: true
      log-body: false
```

## OpenAPI Generation Infrastructure

This module provides the reusable infrastructure for automated OpenAPI spec generation and SDK creation at build time. Microservices use these components to generate an OpenAPI spec from their controller annotations without loading any production dependencies.

### Components

| Component | Purpose |
|-----------|---------|
| `@EnableOpenApiGen` | Meta-annotation that combines `@SpringBootConfiguration`, `@EnableWebFlux`, and all required auto-configurations (Springdoc, WebFlux, Jackson) into a single annotation |
| `AutoMockMissingBeansConfig` | `BeanDefinitionRegistryPostProcessor` that automatically creates Mockito mocks for any `@Autowired` dependency not present in the context. Only active under the `openapi-gen` Spring profile |
| `application-openapi-gen.yaml` | Profile-specific config that enables Springdoc and allows bean definition overriding. Auto-discovered from the classpath by Spring Boot |

### How It Works

1. A lightweight Spring Boot app (`OpenApiGenApplication`) starts during the Maven build using the test classpath
2. `@EnableOpenApiGen` imports the minimal set of auto-configurations needed for Springdoc + WebFlux
3. `AutoMockMissingBeansConfig` scans all `@RestController` beans and registers Mockito mocks for their `@Autowired` dependencies — this allows controllers to load without their real service implementations
4. Springdoc reads the controller annotations and exposes the OpenAPI spec at `/v3/api-docs.yaml`
5. The `springdoc-openapi-maven-plugin` fetches the spec and writes it to `target/openapi/openapi.yml`
6. The SDK module uses `openapi-generator-maven-plugin` to generate typed API clients from the spec

### Usage in a Microservice

**1. Create `OpenApiGenApplication` in `src/test/java`:**

```java
@EnableOpenApiGen
@ComponentScan(basePackages = "com.firefly.your.module.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
```

**2. Add properties to the `-web` module's `pom.xml`:**

```xml
<properties>
    <openapi.gen.skip>false</openapi.gen.skip>
    <openapi.gen.mainClass>com.firefly.your.module.web.openapi.OpenApiGenApplication</openapi.gen.mainClass>
</properties>
```

The Maven profile and plugin configuration are inherited from `firefly-parent`. See the [firefly-parent README](https://github.com/firefly-oss/firefly-parent) for details.

## Documentation

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
