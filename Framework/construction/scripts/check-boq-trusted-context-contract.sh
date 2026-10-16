#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
spi_base = root / 'construction-spi/src/main/java/tech/kayys/numou/construction/spi'
app_base = root / 'construction-application/src/main/java/tech/kayys/numou/construction/application'
tenant_port = (spi_base / 'boq/BoqRevisionTenantContext.java').read_text()
workflow = (spi_base / 'boq/BoqRevisionWorkflow.java').read_text()
if 'String requireTenantId()' not in tenant_port or 'fail closed' not in tenant_port:
    raise SystemExit('FAIL: trusted tenant context port must require tenant identity and fail closed')
for operation in ['captureDraft', 'submitForReview', 'approve']:
    start = workflow.index('CompletionStage<BoqRevisionSnapshot> ' + operation + '(')
    body = workflow[start:workflow.index(');', start) + 2]
    if 'String tenantId' not in body:
        raise SystemExit(f'FAIL: workflow {operation} must carry tenantId explicitly')
    print(f'PASS: workflow {operation} carries explicit tenantId')
handlers = app_base / 'boq/handler'
for name, operation in [
    ('CaptureBoqRevisionHandler.java', 'captureDraft'),
    ('SubmitBoqRevisionForReviewHandler.java', 'submitForReview'),
    ('ApproveBoqRevisionHandler.java', 'approve'),
]:
    source = (handlers / name).read_text()
    if 'BoqRevisionTenantContext tenantContext' not in source or 'tenantContext.requireTenantId()' not in source:
        raise SystemExit(f'FAIL: {name} must require trusted tenant context')
    if 'requireTenantId(), command.boqId()' not in source:
        raise SystemExit(f'FAIL: {name} must pass tenant before BOQ id')
    if 'command.tenantId()' in source:
        raise SystemExit(f'FAIL: {name} must not trust command tenantId')
    print(f'PASS: {operation} resolves tenant from trusted context')
for name in ['CaptureBoqRevisionCommand.java', 'SubmitBoqRevisionForReviewCommand.java', 'ApproveBoqRevisionCommand.java']:
    source = (app_base / 'boq/command' / name).read_text()
    if 'tenantId' in source:
        raise SystemExit(f'FAIL: {name} must not accept caller-supplied tenantId')
print('PASS: command payloads do not select tenant identity')
print('NOTE: static checks do not replace runtime authorization and transaction integration tests')
PY
