> **Demonstration only. Not for use.**
>
> This repository is an early-stage test of
> [dew-agent-factory](https://github.com/MrCYlmz/dew-agent-factory): the code was written
> by the agent team that factory generates. It is published only to show that.
>
> - **No security checks** have been done on this code: no security review, dependency
>   audit or penetration test.
> - It is not maintained, not supported and not ready for any real use.
> - All sample data, names and figures are invented.
>
> The team-lead workspace that ran these stories is at
> [delivery-quotation-archive-project](https://github.com/MrCYlmz/delivery-quotation-archive-project).

    # Delivery Quotation Archive

A private archive of past deliveries, so a new quote can be based on comparable ones instead of a search through old email. Single user, runs locally. Requirements live in [SPEC.md](SPEC.md); the interfaces between modules are in [CONTRACTS.md](CONTRACTS.md).

## Run it

```bash
mvn clean package
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
```

Then open <http://localhost:8080>. One jar carries the backend, the parser and the built frontend.

Needs JDK 21+ and Maven. Node is **not** required — the build downloads its own.

## Configuration

Nothing is hard-coded; every value below is an environment variable with a default, which is what keeps all three packaging options in §10.4 open.

| Variable | Default | |
|---|---|---|
| `DEW_PORT` | `8080` | HTTP port |
| `DEW_DB_PATH` | `./data/deliveries.db` | SQLite file; created on first run |
| `DEW_STORAGE_DIR` | `./data/uploads` | Uploaded PDFs and Excels |
| `DEW_MAX_FILE_SIZE` | `50MB` | Per file |
| `DEW_MAX_REQUEST_SIZE` | `200MB` | Whole request — several PDFs at once |
| `DEW_BUSY_TIMEOUT_MS` | `5000` | SQLite lock wait |
| `DEW_OPEN_BROWSER` | `false` | Open a browser on startup. On in the packaged Windows app |
| `DEW_DB_POOL_SIZE` | — | Connection pool size |

```bash
DEW_PORT=9000 DEW_DB_PATH=/srv/dew/deliveries.db DEW_STORAGE_DIR=/srv/dew/files \
  java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
```

**`./data/` holds the only irreplaceable thing here.** It is gitignored. Back it up by copying the directory.

## Building the Windows app

Alice runs Windows; development happens on Linux. jpackage **cannot cross-compile**, so the
Windows build runs on a Windows CI runner: `.github/workflows/windows-exe.yml`.

Trigger it from the Actions tab (**Windows executable** → *Run workflow*), or push a tag:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

A tag also attaches the results to a GitHub release. It produces two things:

| Artifact | |
|---|---|
| `.exe` | Installer. **Per-user, so no administrator rights are needed.** Start Menu entry. |
| `-portable.zip` | No installation at all — unzip and run the launcher. |

Both exist deliberately. SPEC.md §10.4 flags an open question: whether Alice is even allowed
to install software on her laptop. The zip is the answer if she is not, and it costs one
extra build step rather than a re-plan.

The version must be numeric `major.minor.patch` with a major of 1 or more — jpackage's
Windows packaging rejects `0.x` and anything non-numeric, so the workflow validates it up
front instead of failing deep inside WiX.

**The packaged app stores data in `%LOCALAPPDATA%\DewDeliveryArchive`**, not next to the
executable. The launcher activates the `windows` Spring profile
(`backend/src/main/resources/application-windows.yml`) to do this. It is not cosmetic: the
development default is a relative `./data/`, and an installed app inheriting that would try
to write inside its own install directory, which Windows refuses — SQLite then cannot create
the database and the app dies at startup.

Running it: a console window opens, the app **opens your browser by itself**, and the console
reports the address in case it doesn't. Closing the console stops the app.

The browser launch is off everywhere except the `windows` profile — `DEW_OPEN_BROWSER=true`
turns it on when running the jar by hand. It is deliberately best-effort: the address is
always logged, so a machine with no reachable browser leaves you a URL to copy rather than a
failed startup.

## Layout

```
parser/     Packing-list reader. Plain Java + Apache POI, no Spring. SPEC.md §8
backend/    Spring Boot, SQLite, Flyway, REST. SPEC.md §6, §7
frontend/   Vue 3 + TypeScript, three screens. SPEC.md §9
samples/    Acceptance fixtures. SPEC.md §12
```

`parser` and `backend` are Maven modules; `frontend` is built by `frontend-maven-plugin` into the backend's static resources during `mvn package`.

## Developing

```bash
# Backend only, skipping the npm work
mvn -Dskip.frontend=true -pl backend spring-boot:run

# Frontend with hot reload (proxies /api to localhost:8080)
cd frontend && npm install && npm run dev

# Tests
mvn test                          # everything
mvn -pl parser test               # parser, including the §12 acceptance test
mvn -Dskip.frontend=true -pl backend test
cd frontend && npm run typecheck
```

CI (`.github/workflows/ci.yml`) runs `mvn -B clean verify` on every pull request into `main` and every push to `main`: parser and backend tests, and the frontend typecheck, lint and tests through the Maven build.

## The one test that matters most

The parser is checked against the factory's own arithmetic. Both fixtures in `samples/` must yield **20 items · 6 pallets · 7.42 m³ · 1580 kg gross** — the figures on the sheet's own `TOTAL` row, which the parser is forbidden from reading (it holds live `SUM()` formulas; including it doubles every figure).

If a parser change breaks those numbers, the change is wrong.

## Things that will bite you

Each of these cost real debugging time and is guarded by a test:

- **The packing list is not the first sheet.** Factory files carry an invoice first. Never default to sheet 0 — see SPEC.md §8.1.
- **The header is three rows.** The pallet column is only distinguishable on the *unit* row; matching the top label alone silently sums the wrong column. §8.2.
- **`Total G.W.` and `G.W./package` both contain "g.w."** A loose match picks the per-package column and produces a plausible *wrong* total rather than an error.
- **SQLite enforces foreign keys per connection.** Without `foreign_keys=on` on the JDBC url, every `ON DELETE CASCADE` is silently inert.
- **Uploaded filenames carry U+00A0** non-breaking spaces. Nothing is derived from a filename; files are stored under generated names with the original kept in the database.
- **SQLite's `ALTER TABLE` is limited.** Some migrations need create-copy-drop-rename, not a straight alter.

## Not built, on purpose

No quote generation, no login, no multi-currency, no price prediction, no PDF text extraction, no note search. Each was considered and excluded — SPEC.md §3 and §13 record why, so the reasoning doesn't get re-litigated.
