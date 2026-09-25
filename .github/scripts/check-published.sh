#!/usr/bin/env bash
# Reports whether io.ziggy:yfinance-java:<version> already exists in this repository's GitHub
# Packages Maven registry, so a workflow can skip publishing instead of failing on a duplicate.
#
# Usage: check-published.sh <version>     (needs GITHUB_TOKEN and GITHUB_REPOSITORY)
# Writes `published=true|false` to $GITHUB_OUTPUT. Exits non-zero on any answer other than a
# definite yes (HTTP 200) or no (HTTP 404), so an auth or outage problem is never mistaken for
# "not published".
set -euo pipefail

version="$1"
url="https://maven.pkg.github.com/${GITHUB_REPOSITORY}/io/ziggy/yfinance-java/${version}/yfinance-java-${version}.pom"

status=$(curl -sS -o /dev/null -w '%{http_code}' -I \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" "$url")

case "$status" in
  200)
    echo "::notice::io.ziggy:yfinance-java:${version} is already published; skipping publish."
    echo "published=true" >> "${GITHUB_OUTPUT:-/dev/stdout}"
    ;;
  404)
    echo "io.ziggy:yfinance-java:${version} is not published yet."
    echo "published=false" >> "${GITHUB_OUTPUT:-/dev/stdout}"
    ;;
  *)
    echo "::error::Unexpected HTTP ${status} from ${url}; cannot tell whether ${version} is published."
    exit 1
    ;;
esac
