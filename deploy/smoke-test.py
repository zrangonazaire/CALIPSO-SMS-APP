"""Read-only app checks plus login/logout, without creating accounts or sending messages.

Usage: python deploy/smoke-test.py BASE_URL PASSWORD_FILE [ADMIN_USERNAME] [--public-registration]
Uses normal TLS validation. Never displays credentials or auth tokens.
"""
import json
from pathlib import Path
import sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError

base, password_file = sys.argv[1:3]
options = sys.argv[3:]
registration_open = "--public-registration" in options
username = next((option for option in options if not option.startswith("--")), "admin")
base = base.rstrip("/")

def request(path, method="GET", body=None, token=None, headers=None):
    hdrs = {"Accept": "application/json", **(headers or {})}
    if body is not None:
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    req = Request(base + path, data=data, headers=hdrs, method=method)
    try:
        response = urlopen(req, timeout=30)
    except HTTPError as error:
        response = error
    raw = response.read()
    try:
        payload = json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        payload = None
    return response.code, payload, raw, response.headers

status, _, raw, _ = request("/login")
assert status == 200 and b"<app-root" in raw, "SPA route unavailable"
status, config, _, _ = request("/api/v1/auth/configuration")
assert status == 200 and config["publicRegistrationEnabled"] is registration_open, "Unexpected registration setting"
assert request("/api/v1/companies")[0] in (401, 403), "Unauthenticated company access allowed"
for path in ("/actuator/health", "/v3/api-docs", "/swagger-ui/index.html", "/.env", "/application.properties"):
    assert request(path)[0] == 404, "Private route exposed: " + path
for path in ("register", "setup"):
    payload = {} if registration_open else {
        "companyName": "Rejected", "fullName": "Rejected", "username": "rejected",
        "email": "rejected@example.invalid", "password": "rejected-password-123",
    }
    status = request("/api/v1/auth/" + path, "POST", payload)[0]
    assert status == (400 if registration_open else 403), "Unexpected registration validation"
status, _, _, _ = request("/api/v1/auth/login", "OPTIONS", headers={
    "Origin": "https://foreign.example.invalid", "Access-Control-Request-Method": "POST",
})
assert status == 403, "Foreign CORS origin allowed"
password = Path(password_file).read_text(encoding="utf-8").strip()
status, auth, _, _ = request("/api/v1/auth/login", "POST", {"username": username, "password": password})
assert status == 200 and auth.get("token"), "Admin login failed"
token = auth["token"]
try:
    assert auth["user"]["platformAdmin"] is True, "Admin lacks explicit platform access"
    assert request("/api/v1/companies", token=token)[0] == 200, "Admin company access failed"
    status, plans, _, _ = request("/api/v1/subscription/plans", token=token)
    assert status == 200 and len(plans) == 9, "Expected nine subscription plans"
    print("OK: SPA, registration setting, private routes, CORS, administrator login, companies and nine plans.")
    print("No SMS, WhatsApp message or email sent.")
finally:
    request("/api/v1/auth/logout", "POST", {}, token)
