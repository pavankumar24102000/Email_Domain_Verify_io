# Email Verifier

Selenium job that reads emails from `Domain.xlsx`, looks up each one on verifymail.io, and writes the status back into the **same file** (in a `Results` sheet). Runs on GitHub Actions. Results are displayed on a GitHub Pages site.

## Repo layout

```
.
├── Domain.xlsx                          # Input AND output (overwritten in place)
├── index.html                           # GitHub Pages site
├── pom.xml                              # Maven build
├── src/main/java/org/example/Main.java  # Selenium code
└── .github/workflows/verify.yml         # CI workflow
```

## How it works

1. Push `Domain.xlsx` (column A = emails, row 1 = header).
2. GitHub Actions checks out the repo, builds the jar, runs Selenium headless.
3. The workflow commits the updated `Domain.xlsx` (now containing a `Results` sheet) back to the repo.
4. GitHub Pages serves `index.html`, which fetches `Domain.xlsx` and renders a searchable table + summary cards.

## Triggering

- Push any change to `Domain.xlsx`, `src/**`, or `pom.xml`
- Or run manually: **Actions tab → "Run Email Verifier" → "Run workflow"**
- Or wait for the daily cron (06:00 UTC) — remove the `schedule:` block if you don't want this.

## Local run

```bash
mvn clean package
java -jar target/email-verifier.jar
```

(You'll need Chrome installed locally. Selenium Manager auto-resolves the driver.)
