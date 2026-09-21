#!/usr/bin/env python3
"""
Core partner-gateway mock for local Hive testing.

Validates like the real Core contract (Hive does not validate these):
  - X-P26-Internal-Client-Id (set MOCK_CORE_CLIENT_ID, default: test-secret)
  - customer_name required, pattern ^[a-zA-Z0-9-]{3,25}$
  - sso optional; when present all four fields required and non-blank
  - unknown JSON fields rejected
  - poll: first GET in_progress, second GET complete + tenant_name

Usage:
  MOCK_CORE_CLIENT_ID=test-secret python3 scripts/e2e-core-mock.py

Hive .env:
  CORE_BASE_URL=http://localhost:8081
  CORE_INTERNAL_CLIENT_ID=test-secret
"""
from __future__ import annotations

import json
import os
import re
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

CLIENT_ID = os.environ.get("MOCK_CORE_CLIENT_ID", "test-secret")
NAME_PATTERN = re.compile(r"^[a-zA-Z0-9-]{3,25}$")
ALLOWED_BODY_KEYS = {"customer_name", "sso"}
SSO_KEYS = {"metadata_url", "provider_name", "email_attribute", "groups_attribute"}

JOBS: dict[str, dict] = {}


def validation_error(message: str) -> dict:
    return {"code": "VALIDATION_FAILED", "message": message, "details": None}


def job_not_found() -> dict:
    return {"code": "JOB_NOT_FOUND", "message": "No job with that id.", "details": None}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if self.path != "/v1/tenants":
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
        JOBS[job_id] = {"customer_name": customer, "seen_get": False}
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
        job_id = self.path.rsplit("/", 1)[-1]
        job = JOBS.get(job_id)
        if job is None:
            self._json(404, job_not_found())
            return
        if not job["seen_get"]:
            job["seen_get"] = True
            status = "in_progress"
            tenant_name = None
        else:
            status = "complete"
            tenant_name = f"{job['customer_name']}.portal26.ai"
        payload = {
            "job_id": job_id,
            "customer_name": job["customer_name"],
            "status": status,
        }
        if tenant_name:
            payload["tenant_name"] = tenant_name
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
    print(f"Core mock on http://127.0.0.1:8081  client-id={CLIENT_ID!r}")
    HTTPServer(("127.0.0.1", 8081), Handler).serve_forever()
