#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

printf '%s\n' '== Numou Phase 1 static contract checks =='
for check in "$ROOT"/scripts/check-*.sh; do
  [[ -f "$check" ]] || continue
  printf '\n-- %s --\n' "$(basename "$check")"
  bash "$check"
done

printf '\n%s\n' '-- Shell syntax checks --'
while IFS= read -r script; do
  bash -n "$script"
done < <(find "$ROOT/scripts" -maxdepth 1 -type f -name '*.sh' -print | sort)
printf '%s\n' 'PASS: all shell scripts parse successfully'
printf '\n%s\n' 'NOTE: these checks validate source contracts and shell syntax; they do not compile Java or execute PostgreSQL integration tests.'
