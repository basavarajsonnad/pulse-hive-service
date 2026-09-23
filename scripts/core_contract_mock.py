#!/usr/bin/env python3
"""
Core partner-gateway mock for the tenant-provisioning contract.

POST /v1/tenants  then  GET /v1/tenants/{jobId}
Auth: X-P26-Internal-Client-Id  (MOCK_CORE_CLIENT_ID, default test-secret)
Null fields are omitted. steps[] is always the eight names in order.

Hive mapping (what you are testing):
  1) Any of steps 1–3 fail     → job+item failed,               customer failed
  2) 1–3 all ok, any of 4–8 fail → job+item completed_with_errors, customer completed
  3) All 8 succeed             → job+item completed,            customer completed

Pick the case with the customer_name prefix (3–25 chars, letters/digits/hyphens):

  Prefix   Which step fails     Hive job / item           Hive customer
  (other)  none                 completed                 completed
  run-     (never finishes)     running                   in_progress
  c1-      CREATE_TENANT        failed                    failed
  c2-      AWAIT_PROVISIONING   failed                    failed
  c3-      RESOLVE_TENANT_NAME  failed                    failed
  b4-      TURBO_AND_MDM        completed_with_errors     completed
  b5-      LD_SEGMENTS          completed_with_errors     completed
  b6-      LD_UI_FLAGS          completed_with_errors     completed
  b7-      LD_BACKEND_FLAGS     completed_with_errors     completed
  b8-      SAML_REGISTRATION    completed_with_errors     completed
           (no SAML detail persisted)

First GET is in_progress (except run- stays there). Second GET is terminal.
Use a new name each time, e.g. ok-acme, c1-acme, b4-acme, run-acme.

  MOCK_CORE_CLIENT_ID=test-secret python3 scripts/core_contract_mock.py

Hive .env:
  CORE_BASE_URL=http://localhost:8081
  CORE_INTERNAL_CLIENT_ID=test-secret
"""
from __future__ import annotations

import json
import os
import re
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer

CLIENT_ID = os.environ.get("MOCK_CORE_CLIENT_ID", "test-secret")
NAME_PATTERN = re.compile(r"^[a-zA-Z0-9-]{3,25}$")
ALLOWED_BODY_KEYS = {"customer_name", "sso"}
SSO_KEYS = {"metadata_url", "provider_name", "email_attribute", "groups_attribute"}

STEP_NAMES = [
    "CREATE_TENANT",
    "AWAIT_PROVISIONING",
    "RESOLVE_TENANT_NAME",
    "TURBO_AND_MDM",
    "LD_SEGMENTS",
    "LD_UI_FLAGS",
    "LD_BACKEND_FLAGS",
    "SAML_REGISTRATION",
]
CRITICAL = {"CREATE_TENANT", "AWAIT_PROVISIONING", "RESOLVE_TENANT_NAME"}

JOBS: dict[str, dict] = {}

T0 = "2026-01-15T09:31:00Z"
T1 = "2026-01-15T09:31:05Z"
T2 = "2026-01-15T09:50:40Z"
T3 = "2026-01-15T09:50:41Z"
T4 = "2026-01-15T09:52:10Z"
T5 = "2026-01-15T09:52:40Z"
T6 = "2026-01-15T09:54:50Z"

SKIP_TURBO = "Turbo PAC and MDM are not supported in dev-test — skipped"
SKIP_LD = "no flags configured — skipped"


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def scenario_of(customer_name: str) -> str:
    lower = customer_name.lower()
    for prefix in ("run", "c1", "c2", "c3", "b4", "b5", "b6", "b7", "b8"):
        if lower == prefix or lower.startswith(prefix + "-"):
            return prefix
    return "ok"


def validation_error(message: str) -> dict:
    return {"code": "VALIDATION_FAILED", "message": message, "details": None}


def job_not_found() -> dict:
    return {"code": "JOB_NOT_FOUND", "message": "No job with that id.", "details": None}


def drop_nulls(obj: dict) -> dict:
    return {k: v for k, v in obj.items() if v is not None}


def step(
    name: str,
    status: str,
    *,
    started_at: str | None = None,
    ended_at: str | None = None,
    detail: str | None = None,
) -> dict:
    return drop_nulls(
        {
            "name": name,
            "status": status,
            "started_at": started_at,
            "ended_at": ended_at,
            "detail": detail,
        }
    )


def saml_detail(customer_name: str) -> str:
    return (
        "started cognito_idp execution arn:aws:states:... "
        "(NOT persisted server-side and not pollable here). "
        "MANUAL STEP — add these to the Entra app registration or SSO login will fail: "
        f"redirect URI https://{customer_name}-pulse.auth.ap-south-1.amazoncognito.com/saml2/idpresponse ; "
        "identifier urn:amazon:cognito:sp:ap-south-1_XXXX"
    )


def build_steps(scenario: str, customer_name: str) -> tuple[str, str | None, list[dict]]:
    """Return (core_status, tenant_name_or_none, steps)."""
    fail_at = {
        "c1": "CREATE_TENANT",
        "c2": "AWAIT_PROVISIONING",
        "c3": "RESOLVE_TENANT_NAME",
        "b4": "TURBO_AND_MDM",
        "b5": "LD_SEGMENTS",
        "b6": "LD_UI_FLAGS",
        "b7": "LD_BACKEND_FLAGS",
        "b8": "SAML_REGISTRATION",
    }.get(scenario)

    times = {
        "CREATE_TENANT": (T0, T1, "started execution"),
        "AWAIT_PROVISIONING": (T1, T2, "execution SUCCEEDED"),
        "RESOLVE_TENANT_NAME": (
            T2,
            T3,
            f"resolved tenantName {customer_name}.portal26.ai",
        ),
        "TURBO_AND_MDM": (T3, T3, SKIP_TURBO),
        "LD_SEGMENTS": (T3, T5, "run concluded success"),
        "LD_UI_FLAGS": (T5, T5, SKIP_LD),
        "LD_BACKEND_FLAGS": (T5, T5, SKIP_LD),
        "SAML_REGISTRATION": (T5, T6, saml_detail(customer_name)),
    }

    steps: list[dict] = []
    stopped = False
    tenant_name = None
    for name in STEP_NAMES:
        started, ended, ok_detail = times[name]
        if stopped:
            steps.append(step(name, "pending"))
            continue
        if name == fail_at:
            steps.append(
                step(
                    name,
                    "failed",
                    started_at=started,
                    ended_at=ended,
                    detail=f"{name} failed",
                )
            )
            if name in CRITICAL:
                stopped = True
            continue
        if name == "RESOLVE_TENANT_NAME":
            tenant_name = f"{customer_name}.portal26.ai"
        if name == "SAML_REGISTRATION" and scenario == "b8":
            steps.append(
                step(name, "failed", started_at=started, ended_at=ended, detail="SSO metadata rejected")
            )
            continue
        steps.append(
            step(name, "succeeded", started_at=started, ended_at=ended, detail=ok_detail)
        )

    if scenario in {"c1", "c2", "c3"}:
        return "failed", None, steps
    if scenario in {"b4", "b5", "b6", "b7", "b8"}:
        # Hive maps: Core failed + critical ok + best-effort failed → completed_with_errors
        return "failed", tenant_name, steps
    return "complete", tenant_name, steps


def in_progress_steps() -> list[dict]:
    return [
        step("CREATE_TENANT", "succeeded", started_at=T0, ended_at=T1, detail="started execution"),
        step("AWAIT_PROVISIONING", "running", started_at=T1),
        step("RESOLVE_TENANT_NAME", "pending"),
        step("TURBO_AND_MDM", "pending"),
        step("LD_SEGMENTS", "pending"),
        step("LD_UI_FLAGS", "pending"),
        step("LD_BACKEND_FLAGS", "pending"),
        step("SAML_REGISTRATION", "pending"),
    ]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"{self.command} {self.path} {fmt % args}")

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/tenants":
            self.send_error(404)
            return
        if not self._auth_ok():
            return
        body_bytes = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            body = json.loads(body_bytes or b"{}")
        except json.JSONDecodeError:
            self._json(400, validation_error("Request body is not valid JSON."))
            return
        if not isinstance(body, dict):
            self._json(400, validation_error("Request body is not valid JSON."))
            return

        err = validate_create_body(body)
        if err:
            self._json(400, err)
            return

        customer = body["customer_name"]
        job_id = f"job_{uuid.uuid4().hex[:8]}"
        JOBS[job_id] = {
            "customer_name": customer,
            "scenario": scenario_of(customer),
            "seen_get": False,
            "started_at": now_iso(),
        }
        print(f"  accepted {job_id} customer={customer} scenario={JOBS[job_id]['scenario']}")
        self._json(
            202,
            {
                "job_id": job_id,
                "customer_name": customer,
                "status": "in_progress",
            },
        )

    def do_GET(self):
        if not self.path.startswith("/v1/tenants/"):
            self.send_error(404)
            return
        if not self._auth_ok():
            return
        job_id = self.path.rstrip("/").rsplit("/", 1)[-1]
        job = JOBS.get(job_id)
        if job is None:
            self._json(404, job_not_found())
            return

        scenario = job["scenario"]
        customer = job["customer_name"]
        if scenario == "run" or not job["seen_get"]:
            job["seen_get"] = True
            payload = {
                "job_id": job_id,
                "customer_name": customer,
                "status": "in_progress",
                "started_at": job["started_at"],
                "updated_at": now_iso(),
                "steps": in_progress_steps(),
            }
            self._json(200, payload)
            return

        core_status, tenant_name, steps = build_steps(scenario, customer)
        payload = drop_nulls(
            {
                "job_id": job_id,
                "customer_name": customer,
                "tenant_name": tenant_name,
                "status": core_status,
                "started_at": job["started_at"],
                "updated_at": now_iso(),
                "steps": steps,
            }
        )
        self._json(200, payload)

    def _auth_ok(self) -> bool:
        provided = self.headers.get("X-P26-Internal-Client-Id", "")
        if provided != CLIENT_ID:
            self._json(400, validation_error("Invalid internal client credential."))
            return False
        return True

    def _json(self, code: int, payload: dict):
        request_id = self.headers.get("X-P26-Request-Id") or str(uuid.uuid4())
        data = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-P26-Request-Id", request_id)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def validate_create_body(body: dict) -> dict | None:
    unknown = set(body.keys()) - ALLOWED_BODY_KEYS
    if unknown:
        return validation_error(f"Unknown field: {sorted(unknown)[0]}")

    if "customer_name" not in body:
        return validation_error("customer_name is required.")
    customer = body["customer_name"]
    if not isinstance(customer, str) or not customer.strip():
        return validation_error("customer_name is required.")
    if not NAME_PATTERN.fullmatch(customer):
        return validation_error(
            "customer_name must be 3-25 characters and contain only letters, digits, and hyphens."
        )

    if "sso" not in body:
        return None

    sso = body["sso"]
    if sso is None:
        return None
    if not isinstance(sso, dict):
        return validation_error("sso must be an object.")

    unknown_sso = set(sso.keys()) - SSO_KEYS
    if unknown_sso:
        return validation_error(f"Unknown sso field: {sorted(unknown_sso)[0]}")

    for key in SSO_KEYS:
        if key not in sso:
            return validation_error(f"sso.{key} is required when sso is provided.")
        value = sso[key]
        if not isinstance(value, str) or not value.strip():
            return validation_error(f"sso.{key} must not be blank.")

    return None


if __name__ == "__main__":
    print(f"Core contract mock  http://127.0.0.1:8081  client-id={CLIENT_ID!r}")
    print("Customer name prefixes:")
    print("  acme / ok-xxx     -> job completed, customer completed, SAML detail stored")
    print("  run-xxx           -> stays in_progress / job running")
    print("  c1-xxx / c2 / c3  -> job failed, customer failed")
    print("  b4-xxx … b8-xxx   -> job completed_with_errors, customer completed")
    print("  b8-xxx            -> no registration_output (SAML step failed)")
    HTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
