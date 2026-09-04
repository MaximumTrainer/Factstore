#!/usr/bin/env bash
#
# Records one gate outcome against the release trail (#150).
#
#   attest.sh <type> <step-outcome> [details]
#
# <step-outcome> is a GitHub Actions step outcome — success | failure | cancelled | skipped.
# A failing gate is recorded as a FAILED attestation rather than being skipped: the point of
# a fact store is that the evidence exists whether or not the news is good.
#
# Expects FACTSTORE_URL, TRAIL_ID and RELEASE_ID in the environment, and optionally
# FACTSTORE_TOKEN when the target instance enforces authentication.
set -euo pipefail

TYPE="${1:?attestation type is required}"
OUTCOME="${2:?step outcome is required}"
DETAILS="${3:-}"

case "$OUTCOME" in
  success) STATUS=PASSED ;;
  failure) STATUS=FAILED ;;
  # A gate that never ran has not passed, and recording it as PENDING keeps the trail
  # honest: the assert will report it as missing rather than as a pass.
  *)       STATUS=PENDING ;;
esac

BUILD_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"

PAYLOAD=$(jq -n \
  --arg type "$TYPE" \
  --arg status "$STATUS" \
  --arg name "$TYPE" \
  --arg details "${DETAILS:+$DETAILS — }step outcome: $OUTCOME" \
  --arg evidenceUrl "$BUILD_URL" \
  --arg sha "${GITHUB_SHA:-}" \
  --arg branch "${GITHUB_REF_NAME:-}" \
  '{
    type: $type,
    name: $name,
    status: $status,
    details: $details,
    evidenceUrl: $evidenceUrl,
    gitCommitSha: $sha,
    gitBranch: $branch
  }')

AUTH=()
if [ -n "${FACTSTORE_TOKEN:-}" ]; then
  AUTH=(-H "Authorization: Bearer ${FACTSTORE_TOKEN}")
fi

curl -sf -X POST "${FACTSTORE_URL}/api/v1/trails/${TRAIL_ID}/attestations" \
  -H 'Content-Type: application/json' \
  "${AUTH[@]}" \
  -d "$PAYLOAD" > /dev/null

echo "Attested ${TYPE}=${STATUS} against trail ${TRAIL_ID}"
