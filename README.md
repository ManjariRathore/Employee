# Employee Management System — Advanced DevOps Project

A Java 17 Spring Boot REST API for employee CRUD operations, packaged as a non-root container and deployed with MySQL to a local `kind` Kubernetes cluster. The project includes repeatable Kustomize configuration, GitHub Actions CI/CD, blue-green release control, Prometheus metrics, Jaeger tracing, security scans, and automated verification.

The preserved starting point and permanent commit link are recorded in [docs/BASELINE.md](docs/BASELINE.md). The component design, request path, decisions, failure boundaries, and limitations are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Prerequisites

- Java 17
- Docker Desktop
- `kind`
- `kubectl`
- `curl`, `jq`, and `rg`

Maven does not need to be installed because the repository includes the Maven wrapper.

## Verify the application

Run the unit and integration tests with the H2 test database:

```bash
cd emloyeems
sh mvnw clean verify
```

The test suite covers application startup, Actuator endpoints, service behavior, failure cases, and a complete CRUD journey through the HTTP controller.

## Run locally with MySQL

Create the `employeems` database in MySQL, then provide the password through the environment rather than storing it in Git:

```bash
cd emloyeems
DB_PASSWORD='<local-password>' sh mvnw spring-boot:run
```

Optional database overrides are `DB_URL` and `DB_USERNAME`. The API is available at `http://localhost:8080`.

## Build the container

Use a local tag for development or a commit SHA for a traceable release:

```bash
docker build \
  --build-arg APP_VERSION=local \
  --build-arg VCS_REF="$(git rev-parse HEAD)" \
  -t employee-management-service:local \
  emloyeems
```

The multi-stage image uses Java 17, runs as user `10001`, and contains OCI version and source-revision labels.

## Deploy to local Kubernetes

The deployment script creates the one-node `kind` cluster when needed, creates the database Secret from the supplied environment value, loads the image, applies the Kustomize overlay, and waits for all workloads:

```bash
IMAGE='employee-management-service:local' \
SOURCE_REVISION="$(git rev-parse HEAD)" \
DB_PASSWORD='<local-password>' \
./scripts/k8s-deploy-local.sh
```

The local overlay maps three loopback-only ports, so no recurring `kubectl port-forward` commands are needed:

- Employee API: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Jaeger: `http://localhost:16686`

If the `employee-ms` cluster was created before these mappings were added, it must be recreated once. Deleting the cluster also deletes its local PVC data:

```bash
kind delete cluster --name employee-ms
DB_PASSWORD='<local-password>' ./scripts/k8s-deploy-local.sh
```

Verify the deployed version and CRUD behavior:

```bash
curl -fsS http://localhost:8080/actuator/info | jq
./scripts/smoke-test.sh
```

Detailed Kubernetes, diagnostics, observability, and rollback commands are in [k8s/README.md](k8s/README.md).

## API routes

All employee routes begin with `/api/v1/employee`.

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/saveEmployee` | Create an employee |
| `GET` | `/getAllEmployees` | List employees |
| `GET` | `/searchEmployee/{empId}` | Find one employee |
| `PUT` | `/updateEmployee` | Update an employee |
| `DELETE` | `/deleteEmployee/{empId}` | Delete an employee |

Example create request:

```bash
curl -i -X POST http://localhost:8080/api/v1/employee/saveEmployee \
  -H 'Content-Type: application/json' \
  -d '{"empName":"Meera","empAddress":"Bengaluru","mNumber":"9000000004"}'
```

## Observability

The application exposes only `health`, `info`, and `prometheus` Actuator endpoints. Prometheus scrapes API metrics, structured API request and controller error logs are available through `kubectl logs`, and OpenTelemetry traces are sent to Jaeger. Request logs include method, route, status, duration, trace ID, and span ID; request payloads are intentionally excluded.

- Prometheus: `http://localhost:9090`
- Jaeger: `http://localhost:16686`
- Kubernetes workload UI: Docker Desktop's Kubernetes view, or `kubectl get pods -n employee-ms`

## CI/CD and releases

The GitHub Actions workflow runs Maven verification, secret and configuration scans, builds and scans the image, deploys it to a temporary `kind` cluster, and runs the CRUD smoke test. Successful pushes publish an SBOM-enabled GHCR image tagged with the immutable Git commit SHA.

The normal path uses a Kubernetes rolling update. The optional blue-green script deploys the new image to the inactive slot, smoke-tests it before switching traffic, and retains the previous slot for immediate rollback:

```bash
./scripts/blue-green-release.sh status

SOURCE_REVISION='<commit-sha>' \
./scripts/blue-green-release.sh deploy \
  'employee-management-service:<commit-sha>'

./scripts/blue-green-release.sh rollback
```

## Security controls

- Database credentials are created as a runtime Kubernetes Secret and ignored by Git.
- Workloads disable service-account token mounts and use restricted security contexts.
- Application, Prometheus, and Jaeger run non-root with read-only root filesystems and dropped capabilities.
- MySQL and collector traffic remain internal. The local overlay exposes only the API, Prometheus UI, and Jaeger UI through kind ports bound to `127.0.0.1`.
- CI blocks committed secrets and fixed critical image vulnerabilities and audits deployment misconfigurations.

## Troubleshooting

```bash
kubectl --context kind-employee-ms --namespace employee-ms get pods,services,pvc
kubectl --context kind-employee-ms --namespace employee-ms describe pod <pod-name>
kubectl --context kind-employee-ms --namespace employee-ms logs deployment/employee-api
kubectl --context kind-employee-ms --namespace employee-ms rollout status deployment/employee-api
```

If port `8080`, `9090`, or `16686` is already in use, stop the existing process before creating the kind cluster, or change the matching `hostPort` in `infra/kind/cluster.yaml`.

## Known limitations

- The one-node local cluster and single MySQL pod are not highly available.
- The PVC protects data from pod replacement, but not cluster deletion or host-disk loss.
- Prometheus and Jaeger data are ephemeral.
- Loopback-only kind mappings do not demonstrate public ingress, TLS, or cloud infrastructure.
- Blue and green application versions share one database, so schema changes must remain backward compatible during a release.
