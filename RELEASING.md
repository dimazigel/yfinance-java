# Releasing

Releases are cut by the **Release** workflow (Actions → Release → Run workflow, on `main`). It
builds and tests, publishes `io.github.dimazigel:yfinance-java:<version>` to GitHub Packages, then
creates the tag (`v` + version, e.g. `v1.0.0`; the package version itself has no prefix) and the GitHub
release with the jars attached.

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

- `1.0.0` is the first release. The versions published while the library was taking shape
  (`0.0.1`–`2.0.0`, the first two under the old `io.ziggy` coordinates) were withdrawn on 2026-10-02
  — releases, tags and packages — and numbering restarted.
- Tags carry a `v` prefix for a reason: the repository has immutable releases enabled, so a tag name
  a release once used can never be created again, even after the release is deleted. The withdrawn
  releases used the bare numbers `0.0.1`–`2.0.0`, which are therefore gone for good as tag names.
- `publish.yml` publishes to GitHub Packages for releases created by hand in the GitHub UI;
  releases made by the Release workflow are already published and are skipped.
