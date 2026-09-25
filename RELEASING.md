# Releasing

Releases are cut by the **Release** workflow (Actions → Release → Run workflow, on `main`). It
builds and tests, publishes `io.github.dimazigel:yfinance-java:<version>` to GitHub Packages (and to
Maven Central once configured, see below), then creates the tag (no `v` prefix, e.g. `0.1.0`) and the
GitHub release with the jars attached.

Inputs: `bump` (`patch`/`minor`/`major`, applied to the latest tag) or an explicit `version`, plus a
`prerelease` flag. Publishing is idempotent: a re-run after a partial failure skips versions that
are already in the registry and continues from the tag step.

## One-time setup for Maven Central

Until these secrets exist the Central step is skipped and releases go to GitHub Packages only.

1. **Central Portal account and namespace.** Sign in at <https://central.sonatype.com> with GitHub.
   Register the namespace `io.github.dimazigel`; namespaces of the form `io.github.<login>` are
   verified automatically against the GitHub account.
2. **Portal user token.** Account → *Generate User Token*. Store as repository secrets
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.
3. **Signing key.** Central requires PGP-signed artifacts.
   ```bash
   gpg --quick-generate-key "Dmitry Tsigelnik <you@example.com>" ed25519 sign 2y
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>      # Central checks a public keyserver
   gpg --armor --export-secret-keys <KEY_ID> | pbcopy              # -> secret SIGNING_KEY (ASCII-armored)
   ```
   Store the armored private key as `SIGNING_KEY` and its passphrase as `SIGNING_PASSWORD`.
4. Run the Release workflow. The `Publish to Maven Central` step uploads and releases automatically
   (`automaticRelease = true`); artifacts appear on Central within a few hours.

## Local publishing

```bash
./gradlew publishToMavenLocal                                   # unsigned snapshot into ~/.m2
./gradlew publishAllPublicationsToGitHubPackagesRepository -PreleaseVersion=0.1.0 \
    -Pgpr.user=<github-login> -Pgpr.key=<token with write:packages>
```

`publishToMavenCentral` also works locally with the same four properties exported as
`ORG_GRADLE_PROJECT_mavenCentralUsername`, `…mavenCentralPassword`, `…signingInMemoryKey`,
`…signingInMemoryKeyPassword`.

## Notes

- Releases `0.0.1` and `0.0.2` were published under the previous coordinates `io.ziggy:yfinance-java`
  with package root `io.ziggy.yfinance`. They remain available on GitHub Packages but will not be
  updated.
- `publish.yml` still publishes to GitHub Packages for releases created by hand in the GitHub UI.
