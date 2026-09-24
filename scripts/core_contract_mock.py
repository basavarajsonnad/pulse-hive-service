#!/usr/bin/env python3
"""
Core partner-gateway mock for the tenant-provisioning contract.

POST /v1/tenants  then  GET /v1/tenants/{jobId}
Auth: X-P26-Internal-Client-Id  (MOCK_CORE_CLIENT_ID, default test-secret)
Null fields are omitted. steps[] is always the eight names in order.

Job-level status is Core-only: in_progress | complete | failed.
Hive maps those + steps[] in Java. This mock never returns Hive statuses
(running, completed, completed_with_errors).

  Prefix   Which step fails        Core job status
  (other)  none                    complete
  run-     (never finishes)        in_progress
  c1-      CREATE_TENANT           failed
  c2-      AWAIT_PROVISIONING      failed
  c3-      RESOLVE_TENANT_NAME     failed
  b4-      TURBO_AND_MDM           complete
  b5-      LD_SEGMENTS             complete
  b6-      LD_UI_FLAGS             complete
  b7-      LD_BACKEND_FLAGS        complete
  b8-      SAML_REGISTRATION       complete
  bm-      steps 4, 5, 7 fail      complete (6 and 8 ok; SAML detail)
           SAML MANUAL STEP in step 8 detail when that step succeeds (ok-, b4–b7, bm-)

GET 1: in_progress, step 2 running, no tenant_name.
GET 2: in_progress, steps 1–3 succeeded, tenant_name set, step 4 running
        (c1–c3 skip this and go terminal failed; run- stays here).
GET 3: terminal (except run- stays in_progress).
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
    for prefix in ("run", "c1", "c2", "c3", "bm", "b4", "b5", "b6", "b7", "b8"):
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
        "c1": {"CREATE_TENANT"},
        "c2": {"AWAIT_PROVISIONING"},
        "c3": {"RESOLVE_TENANT_NAME"},
        "b4": {"TURBO_AND_MDM"},
        "b5": {"LD_SEGMENTS"},
        "b6": {"LD_UI_FLAGS"},
        "b7": {"LD_BACKEND_FLAGS"},
        "b8": {"SAML_REGISTRATION"},
        "bm": {"TURBO_AND_MDM", "LD_SEGMENTS", "LD_BACKEND_FLAGS"},
    }.get(scenario, set())

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
        # MANUAL STEP whenever step 8 succeeds. Hive persists only then.
        "SAML_REGISTRATION": (
            T5,
            T6,
            saml_detail(customer_name) if scenario != "b8" else None,
        ),
    }

    steps: list[dict] = []
    stopped = False
    tenant_name = None
    for name in STEP_NAMES:
        started, ended, ok_detail = times[name]
        if stopped:
            steps.append(step(name, "pending"))
            continue
        if name in fail_at:
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
    # After step 8, Core is complete whenever 1–3 succeeded (4–8 may have failed).
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


def after_three_steps(customer_name: str) -> tuple[str, list[dict]]:
    tenant_name = f"{customer_name}.portal26.ai"
    return tenant_name, [
        step("CREATE_TENANT", "succeeded", started_at=T0, ended_at=T1, detail="started execution"),
        step("AWAIT_PROVISIONING", "succeeded", started_at=T1, ended_at=T2, detail="execution SUCCEEDED"),
        step(
            "RESOLVE_TENANT_NAME",
            "succeeded",
            started_at=T2,
            ended_at=T3,
            detail=f"resolved tenantName {tenant_name}",
        ),
        step("TURBO_AND_MDM", "running", started_at=T3),
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
            "get_count": 0,
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
        job["get_count"] = int(job.get("get_count", 0)) + 1
        get_count = job["get_count"]

        if get_count == 1:
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

        if scenario == "run" or (get_count == 2 and scenario not in {"c1", "c2", "c3"}):
            tenant_name, steps = after_three_steps(customer)
            payload = {
                "job_id": job_id,
                "customer_name": customer,
                "tenant_name": tenant_name,
                "status": "in_progress",
                "started_at": job["started_at"],
                "updated_at": now_iso(),
                "steps": steps,
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
    print("  Core job status only: in_progress | complete | failed")
    print("  acme / ok-xxx     -> GET2 tenant_name + in_progress; GET3 complete (all 8 ok; SAML detail)")
    print("  run-xxx           -> GET2 tenant_name; stays in_progress")
    print("  c1-xxx / c2 / c3  -> failed (critical step)")
    print("  b4-xxx … b7-xxx   -> complete (step 4–7 fail, 8 ok; SAML detail)")
    print("  b8-xxx            -> complete (step 8 fail; no SAML detail)")
    print("  bm-xxx            -> complete (steps 4,5,7 fail; 6+8 ok; all fail texts in Hive error)")
    HTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
