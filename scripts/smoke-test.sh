#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_URL="${BASE_URL}/api/v1/employee"

for command in curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

echo "Checking application readiness at ${BASE_URL}"
ready=false
for attempt in {1..30}; do
  if curl -fsS "${BASE_URL}/actuator/health/readiness" \
    | jq -e '.status == "UP"' >/dev/null 2>&1; then
    ready=true
    break
  fi

  sleep 2
done

if [[ "${ready}" != "true" ]]; then
  echo "Application did not become ready within 60 seconds" >&2
  exit 1
fi

echo "Creating smoke-test employee"
create_response="$(curl -fsS -X POST "${API_URL}/saveEmployee" \
  -H 'Content-Type: application/json' \
  -d '{"empId":0,"empName":"Smoke Test","empAddress":"Local","mNumber":"9000000099"}')"

employee_id="$(jq -er 'select(.code == "00") | .content.empId | select(. > 0)' <<<"${create_response}")"

echo "Reading employee ${employee_id}"
curl -fsS "${API_URL}/searchEmployee/${employee_id}" \
  | jq -e --argjson id "${employee_id}" '.code == "00" and .content.empId == $id' >/dev/null

echo "Updating employee ${employee_id}"
curl -fsS -X PUT "${API_URL}/updateEmployee" \
  -H 'Content-Type: application/json' \
  -d "{\"empId\":${employee_id},\"empName\":\"Smoke Test\",\"empAddress\":\"Updated\",\"mNumber\":\"9000000099\"}" \
  | jq -e '.code == "00"' >/dev/null

echo "Verifying employee ${employee_id} in the employee list"
curl -fsS "${API_URL}/getAllEmployees" \
  | jq -e --argjson id "${employee_id}" '.code == "00" and any(.content[]; .empId == $id and .empAddress == "Updated" and .mNumber == "9000000099")' >/dev/null

echo "Deleting employee ${employee_id}"
curl -fsS -X DELETE "${API_URL}/deleteEmployee/${employee_id}" \
  | jq -e '.code == "00"' >/dev/null

not_found_response="$(curl -sS -w $'\n%{http_code}' "${API_URL}/searchEmployee/${employee_id}")"
not_found_status="${not_found_response##*$'\n'}"
not_found_body="${not_found_response%$'\n'*}"

if [[ "${not_found_status}" != "400" ]]; then
  echo "Expected HTTP 400 after deletion, received ${not_found_status}" >&2
  exit 1
fi

jq -e '.code == "01"' <<<"${not_found_body}" >/dev/null
echo "Smoke test passed"
