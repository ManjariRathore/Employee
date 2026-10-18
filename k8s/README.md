# Local Kubernetes deployment

The local environment uses one `kind` node and a Kustomize overlay. The MySQL password is created as a Kubernetes Secret at deployment time and is not stored in Git.

## Deploy

Build the local image if it is not already available:

```bash
docker build -t employee-management-service:local emloyeems
```

Create the cluster and deploy the stack:

```bash
DB_PASSWORD='<local-password>' ./scripts/k8s-deploy-local.sh
```

The script creates or updates the `employee-db` Secret, loads the local image into `kind`, applies the Kustomize overlay, and waits for MySQL and the API.

The normal deployment uses Kubernetes rolling updates. The active `employee-api` Deployment is the blue slot, and the Service initially selects that slot.

The kind cluster maps the API, Prometheus UI, and Jaeger UI to loopback-only host ports. If the cluster existed before these mappings were configured, recreate it once. This deletes data stored in the cluster's local PVC:

```bash
kind delete cluster --name employee-ms
DB_PASSWORD='<local-password>' ./scripts/k8s-deploy-local.sh
```

## Verify

The API is available directly at `http://localhost:8080`; no port-forward process is required.

Run the CRUD smoke test:

```bash
./scripts/smoke-test.sh
```

Inspect health, logs, and storage:

```bash
kubectl --context kind-employee-ms --namespace employee-ms get pods,services,persistentvolumeclaims
kubectl --context kind-employee-ms --namespace employee-ms logs deployment/employee-api
kubectl --context kind-employee-ms --namespace employee-ms describe persistentvolumeclaim mysql-data-mysql-0
```

Follow the active application's request logs during a blue-green release:

```bash
active_slot="$(kubectl --context kind-employee-ms --namespace employee-ms \
  get service employee-api \
  --output='jsonpath={.spec.selector.app\.kubernetes\.io/slot}')"
deployment="employee-api"
if [[ "${active_slot}" == "green" ]]; then deployment="employee-api-green"; fi
kubectl --context kind-employee-ms --namespace employee-ms logs \
  --follow deployment/"${deployment}"
```

API request lines contain the method, route, response status, duration, trace ID, and span ID. Payloads are excluded to avoid writing employee personal data to logs.

## Observability

Prometheus scrapes application metrics from `/actuator/prometheus`. Jaeger receives OTLP traces from the application. Prometheus is available at `http://localhost:9090`, and the Jaeger UI is available at `http://localhost:16686` without port forwarding.

After generating traffic with the smoke test, verify the Prometheus target and Jaeger service from another terminal:

```bash
curl -fsS --get \
  --data-urlencode 'query=up{job="employee-api"}' \
  http://localhost:9090/api/v1/query | jq

curl -fsS http://localhost:16686/api/services | jq
```

## Release control

Every release image should use an immutable commit-SHA tag. For a normal rolling release, load or pull the image and run the local deployment script with its source revision:

```bash
IMAGE='employee-management-service:<commit-sha>' \
SOURCE_REVISION='<commit-sha>' \
DB_PASSWORD='<local-password>' \
./scripts/k8s-deploy-local.sh
```

Inspect the recorded running version:

```bash
curl -fsS http://localhost:8080/actuator/info | jq
kubectl --context kind-employee-ms --namespace employee-ms \
  get deployment employee-api \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

Kubernetes retains three Deployment revisions. A failed rolling release can be recovered with:

```bash
kubectl --context kind-employee-ms --namespace employee-ms \
  rollout undo deployment/employee-api
kubectl --context kind-employee-ms --namespace employee-ms \
  rollout status deployment/employee-api
```

### Blue-green demonstration

The release script deploys the new image to the inactive slot, runs the CRUD smoke test directly against it, and switches the main Service only after the test succeeds:

```bash
./scripts/blue-green-release.sh status

SOURCE_REVISION='<commit-sha>' \
./scripts/blue-green-release.sh deploy \
  'employee-management-service:<commit-sha>'
```

If the new release is unhealthy after the switch, return traffic immediately to the retained slot:

```bash
./scripts/blue-green-release.sh rollback
```

Blue and green share the same MySQL database, so database schema changes must remain compatible with both versions. The inactive Deployment can be removed after the observation period to return to the normal one-pod footprint.

## Security controls

- The real MySQL password is created as the `employee-db` Secret at deployment time and is never stored in a manifest.
- Root `.gitignore` rules exclude local environment files, secret manifests, private keys, and private evidence.
- Application, Prometheus, and Jaeger containers run as non-root with privilege escalation disabled, a read-only root filesystem, dropped Linux capabilities, and the runtime-default seccomp profile.
- MySQL uses a read-only root filesystem with explicit writable mounts for its data, runtime socket, and temporary files. It drops all Linux capabilities and adds back only the small set its official entrypoint needs to initialize files and change to its database user.
- Workloads do not mount Kubernetes service-account tokens because they do not call the Kubernetes API.
- MySQL and Jaeger collector traffic remain on internal `ClusterIP` Services. The local overlay uses fixed NodePorts for the API and monitoring UIs, with kind host mappings bound only to `127.0.0.1`; there is no public ingress or load balancer.
- CI blocks committed secrets and critical image vulnerabilities. Kubernetes/Dockerfile misconfigurations are reported for review without blocking justified MySQL exceptions.
- The local cluster relies on internal Services rather than enforced pod-to-pod NetworkPolicies. A shared or production cluster should first use a policy-capable CNI and then allow only API-to-MySQL, Prometheus-to-API, and API-to-Jaeger traffic.
