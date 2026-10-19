#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
factory = (root / 'construction-adapter/src/main/java/tech/kayys/numou/construction/adapter/postgres/boq/DefaultPgBoqRevisionOperationsFactory.java').read_text()
port = (root / 'construction-spi/src/main/java/tech/kayys/numou/construction/spi/boq/BoqRevisionAuthorization.java').read_text()
domain = (root / 'construction-domain/src/main/java/tech/kayys/numou/construction/domain/boq/BoqRevisionSnapshot.java').read_text()
actor_port = (root / 'construction-spi/src/main/java/tech/kayys/numou/construction/spi/boq/BoqRevisionActorContext.java').read_text()

def method_body(name, next_name):
    start = factory.index('public Uni<BoqRevisionSnapshot> ' + name + '(')
    end = factory.index('public Uni<BoqRevisionSnapshot> ' + next_name + '(', start) if next_name else factory.index('private Uni<Void> authorize(', start)
    return factory[start:end]

cases = [
    ('captureDraft', 'submitForReview', 'CAPTURE_DRAFT'),
    ('submitForReview', 'approve', 'SUBMIT_FOR_REVIEW'),
    ('approve', None, 'APPROVE'),
]
for name, next_name, action in cases:
    body = method_body(name, next_name)
    auth = body.find('BoqRevisionAuthorization.Action.' + action)
    lock = body.find('live.loadForUpdate(tenantId, boqId)')
    if auth < 0 or lock < 0 or auth > lock:
        raise SystemExit(f'FAIL: {name} must authorize {action} before loading/locking the BOQ')
    print(f'PASS: {name} authorizes {action} before BOQ access')

if 'Objects.requireNonNull(authorization, "authorization")' not in factory:
    raise SystemExit('FAIL: authorization dependency must be required (no implicit allow-all)')
if 'Uni<Void> authorize(String tenantId, UUID actorId, UUID boqId, Action action)' not in port:
    raise SystemExit('FAIL: authorization port must receive tenant, actor, BOQ, and action')
if 'actorId.equals(submittedBy)' not in domain or 'cannot approve the same BOQ revision' not in domain:
    raise SystemExit('FAIL: domain self-approval invariant missing')
if 'if (actorId.equals(before.submittedBy()))' not in factory:
    raise SystemExit('FAIL: workflow self-approval guard missing')
print('PASS: authorization is mandatory and receives tenant/actor/resource/action')
print('PASS: self-approval is rejected in domain and workflow')

commands = root / 'construction-application/src/main/java/tech/kayys/numou/construction/application/boq/command'
handlers = root / 'construction-application/src/main/java/tech/kayys/numou/construction/application/boq/handler'
for name in ['CaptureBoqRevisionCommand.java', 'SubmitBoqRevisionForReviewCommand.java', 'ApproveBoqRevisionCommand.java']:
    source = (commands / name).read_text()
    if 'actorId' in source:
        raise SystemExit(f'FAIL: {name} must not accept caller-supplied actor identity')
for name in ['CaptureBoqRevisionHandler.java', 'SubmitBoqRevisionForReviewHandler.java', 'ApproveBoqRevisionHandler.java']:
    source = (handlers / name).read_text()
    if 'actorContext.requireActorId()' not in source or 'command.actorId()' in source:
        raise SystemExit(f'FAIL: {name} must resolve actor from trusted context')
if 'UUID requireActorId()' not in actor_port or 'fail closed' not in actor_port:
    raise SystemExit('FAIL: trusted actor context contract missing')
print('PASS: command payloads cannot choose actor identity; handlers use trusted actor context')
print('NOTE: static contract checks do not replace Java integration tests or verify transaction rollback at runtime')
PY
