#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
missing=0
found_build=0
found_migrations=0
found_runtime_config=0

printf '%s\n' 'Numou real-repository integration preflight'
printf 'Root: %s\n\n' "$ROOT"

for descriptor in pom.xml build.gradle build.gradle.kts settings.gradle settings.gradle.kts; do
  if [[ -f "$ROOT/$descriptor" ]]; then
    printf 'FOUND build descriptor: %s\n' "$descriptor"
    found_build=1
  fi
done
if [[ "$found_build" -eq 0 ]]; then
  printf '%s\n' 'MISSING: Maven/Gradle build descriptor (pom.xml or Gradle build file)'
  missing=1
fi

if [[ -d "$ROOT/src/main/resources/db/migration" || -d "$ROOT/src/main/resources/db/migrations" || -d "$ROOT/db/migration" || -d "$ROOT/db/migrations" ]]; then
  printf '%s\n' 'FOUND a conventional migration directory'
  found_migrations=1
else
  printf '%s\n' 'MISSING: canonical application migration directory (review the actual repository layout if nonstandard)'
  missing=1
fi

for config in application.properties application.yaml application.yml src/main/resources/application.properties src/main/resources/application.yaml src/main/resources/application.yml; do
  if [[ -f "$ROOT/$config" ]]; then
    printf 'FOUND runtime configuration: %s\n' "$config"
    found_runtime_config=1
  fi
done
if [[ "$found_runtime_config" -eq 0 ]]; then
  printf '%s\n' 'MISSING: application runtime configuration'
  missing=1
fi

if [[ ! -d "$ROOT/src/main/java" || ! -d "$ROOT/src/test/java" ]]; then
  printf '%s\n' 'MISSING: conventional Java main/test source roots'
  missing=1
else
  printf '%s\n' 'FOUND Java main/test source roots'
fi

if [[ "$missing" -ne 0 ]]; then
  printf '\n%s\n' 'BLOCKED: this bundle is not sufficient to validate integration against the real Numou repository.'
  printf '%s\n' 'Run this preflight from the root of the complete repository, then execute its supported build and PostgreSQL integration suite.'
  exit 2
fi

printf '\n%s\n' 'PREFLIGHT PASSED: expected repository entry points were found.'
printf '%s\n' 'This is a presence check only; it does not compile Java, validate migrations, or prove runtime behavior.'
