# Security Policy

## Supported versions

Only the latest release on the `1.x` line (currently `1.1.0`) is supported. Fixes ship as a new
patch or minor release; there is no backport line for older versions.

## Reporting a vulnerability

Please use [GitHub private vulnerability reporting](https://github.com/dimazigel/yfinance-java/security/advisories/new)
rather than opening a public issue.

Include:

- The library version you're using.
- A minimal reproduction (code + the Yahoo endpoint/symbol involved, if relevant).
- The impact you believe it has.

You should expect a first response within 7 days.

## Scope

This library is a client for Yahoo Finance's unofficial endpoints. It:

- Talks only to `query1`/`query2.finance.yahoo.com` and `fc.yahoo.com` — no other network
  destination.
- Stores the auth cookie and crumb in memory only (`auth/CrumbStore`); nothing is written to disk.
- Has no server component, credential store, or user data of its own.

Reports about vulnerabilities in Yahoo Finance's own services, or in Yahoo's infrastructure, are
out of scope for this repository — please report those to Yahoo directly.
