# Project Baseline

## Student and project

- **Student name:** Manjari Rathore
- **Student identifier:** manjari.23bcs10192@sst.scaler.com
- **Project name:** Employee Management System — Advanced DevOps Project
- **Repository:** https://github.com/ManjariRathore/Employee

## Preserved baseline

- **Baseline commit:** `db781700e2937b0ef74450c8eb4f0d74d97b2f35`
- **Permanent commit link:** https://github.com/ManjariRathore/Employee/commit/db781700e2937b0ef74450c8eb4f0d74d97b2f35
- **Original branch:** `main`


## Application description

The project is a Java 17 Spring Boot REST API for managing employee records. It provides create, read, update, and delete operations and stores employee data in MySQL through Spring Data JPA.

### Baseline components

- Spring Boot 3.2 web application
- Employee REST controller and service layer
- Spring Data JPA repository
- MySQL database dependency
- Maven wrapper and Spring Boot Maven plugin
- One Spring application-context test

### Baseline API operations

- Create an employee
- Update an employee
- List all employees
- Find an employee by ID
- Delete an employee by ID

## Existing DevOps features

- Source code is maintained in Git and hosted on GitHub.
- Maven provides a repeatable Java build and dependency definition.
- The repository contains a Maven wrapper, although its Unix executable bit is not currently set.
- A basic Spring context test exists.
- Generated build output and common IDE files are ignored by Git.

## Baseline limitations

- Database credentials are stored directly in tracked application configuration.
- Tests depend on a locally available MySQL database and matching credentials.
- No container image or Docker build is defined.
- No Kubernetes deployment configuration exists.
- No CI/CD workflow exists.
- No dedicated health probes, metrics, tracing, or dashboards are configured.
- No automated dependency, secret, manifest, or container-image security scanning exists.
- Release versioning, deployment traceability, and rollback procedures are not defined.

