#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHECK="$ROOT/scripts/conventional-commit.sh"

pass() {
  "$CHECK" --subject "$1"
}

fail() {
  if "$CHECK" --subject "$1" >/dev/null 2>&1; then
    echo "expected reject: $1" >&2
    exit 1
  fi
}

pass "feat: start voice-only meetings from the home screen"
pass "fix(call): keep ICE candidates until remote description is set"
pass "feat(call)!: require camera for video rooms"
pass "chore(release): 1.0.0"
pass "docs: add using guide"
pass "ci: enforce conventional commits"
pass "feat: x"
pass "ci(workflows): check PR titles"
pass "perf: debounce mute state broadcasts"
pass "deps: bump okhttp"
pass "revert: undo broken room code parse"
pass "Merge pull request #12 from rconnelly/feat-meetings"
pass "Merge branch 'master' into feat-meetings"
pass "Revert \"feat: start voice-only meetings from the home screen\""

fail "Load nearby meetings and document the app."
fail "Feat: wrong case"
fail "feat:no-space"
fail "feat:"
fail "feat: "
fail "updated meetings"
fail "chore(release) missing colon description"
fail "fix(): empty scope"

echo "conventional-commit checks passed"
