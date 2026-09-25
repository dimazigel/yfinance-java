# Releasing

Releases are cut by the **Release** workflow (Actions → Release → Run workflow, on `main`). It
builds and tests, publishes `io.github.dimazigel:yfinance-java:<version>` to GitHub Packages, then
creates the tag (no `v` prefix, e.g. `0.1.0`) and the GitHub release with the jars attached.

Inputs: `bump` (`patch`/`minor`/`major`, applied to the latest tag) or an explicit `version`, plus a
`prerelease` flag. Publishing is idempotent: a re-run after a partial failure skips a version that is
already in the registry and continues from the tag step.

GitHub Packages is the only distribution channel. Publishing to Maven Central was considered and
deliberately not set up; consumers authenticate to GitHub Packages as described in the README.

## Local publishing

```bash
./gradlew publishToMavenLocal                                   # snapshot into ~/.m2
./gradlew publishAllPublicationsToGitHubPackagesRepository -PreleaseVersion=0.1.0 \
    -Pgpr.user=<github-login> -Pgpr.key=<token with write:packages>
```

## Notes

- Releases `0.0.1` and `0.0.2` were published under the previous coordinates `io.ziggy:yfinance-java`
  with package root `io.ziggy.yfinance`. They remain available on GitHub Packages but will not be
  updated; everything from the next release on is `io.github.dimazigel`.
- `publish.yml` publishes to GitHub Packages for releases created by hand in the GitHub UI;
  releases made by the Release workflow are already published and are skipped.
