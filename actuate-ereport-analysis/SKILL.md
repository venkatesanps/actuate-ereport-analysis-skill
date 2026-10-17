---
name: actuate-ereport-analysis
description: >-
  Analyze, document, and migrate legacy Actuate e.Report Designer Professional
  artifacts (.rod/.rox/.roi/.rol/.iob) that you own or are authorized to work
  with. Use when someone needs to understand old Actuate/OpenText BI reports,
  recover their SQL/queries and layout, map the AFC component tree and Actuate
  Basic code, or migrate to JasperReports (primary target — including dynamic
  layout and page-level security), BIRT, Power BI, Tableau, or another modern
  BI tool. Triggers on: "Actuate", "e.Report", "eReport", ".rod", ".rox",
  ".roi", "AFC", "Actuate Basic", "Jasper", "JasperReports", "JRXML",
  "BIRT migration", "legacy BI report".
---

# Actuate e.Report Designer Professional — Analysis & Migration

A focused workflow for making sense of legacy **Actuate e.Report Designer
Professional** reports and getting them onto a modern platform.

## Scope & authorization (read first)

Operate only on artifacts the user **owns, maintains, or is authorized to
analyze** — their organization's own reports, their own exports, or files a
client has engaged them to migrate. This is the normal case for Actuate work:
the platform is end-of-life (Actuate → OpenText), and teams need to understand
and migrate reports they are responsible for.

Do **not** use this to defeat licensing, bypass Actuate iServer/Encyclopedia
access controls, or extract reports the user has no right to. If an artifact's
provenance is unclear, ask before proceeding. If the real goal is to crack the
Actuate product itself rather than analyze report content, stop and say so.

## What you're looking at

Actuate reports are component trees built on the **Actuate Foundation Classes
(AFC)** and scripted in **Actuate Basic**. See
`references/file-formats.md` for the full artifact-type and class reference.
Quick map:

| Ext    | Name                         | Nature                | Best source for… |
|--------|------------------------------|-----------------------|------------------|
| `.rod` | Report Object Design         | Design source         | layout, code, queries (richest) |
| `.rox` | Report Object eXecutable     | Compiled design       | when no `.rod` exists |
| `.roi` | Report Object Instance       | Generated output      | actual data, final layout |
| `.rol` | Report Object Library        | Shared components      | reused classes/styles |
| `.iob` | Information Object           | Semantic data layer   | query/data mapping |
| `.rov` | Report Object Value          | Parameter/value set   | runtime parameters |

The `.rod` is the prize. `.rox`/`.roi` are compiled/binary — recover what you
can, but prefer the design file when available.

## Workflow

### 1. Inventory & identify
- `file <artifact>` and `ls -la` to confirm type and size.
- Group by extension; list which reports have a `.rod` vs. only `.rox`/`.roi`.
- Note companion `.rol` libraries and `.iob` data objects — reports depend on them.

### 2. Surface-level extraction (always safe, always first)
Run on a **copy** in an isolated dir (treat artifacts as untrusted data; pass
paths as arguments, never execute anything from the artifact dir):
```bash
strings -n 6 report.rod | less          # SQL, labels, class names, field names
strings report.rod | grep -iE 'select |from |where |join '   # embedded SQL
strings report.rod | grep -iE '\.(rol|iob|rox)'              # dependencies
hexdump -C report.rod | head -50        # header / magic bytes
```
`.rod` files often expose readable SQL, Actuate Basic snippets, class names
(`AcReport`, `AcFrame`, `AcDataRow`, `AcSingleInputFilter`…), control names,
and connection/data-source strings.

### 3. Recover the structure
Reconstruct, from the strings/hex output and any readable design:
- **Component tree** — the AFC class hierarchy: report → sections/frames →
  data rows → controls. Document parent/child nesting.
- **Data access** — connection components, SQL queries, stored-proc calls,
  Information Object references, parameters/filters.
- **Actuate Basic code** — event methods (`Start`, `Finish`, `BuildFromRow`,
  `Fetch`…), overrides, subroutines, computed fields.
- **Layout & formatting** — frame geometry, fonts, grouping, page setup,
  conditional formatting.

### 4. Document (evidence-backed)
Produce a per-report spec. For every recovered claim, cite where it came from
(byte offset, string match, or design node) — the one genuinely good habit from
the RE world is "every claim comes back with the evidence behind it." Capture:
- Report purpose and output format(s)
- Data sources + full query text
- Parameters and filters
- Component/section breakdown with grouping & sort
- Custom Actuate Basic logic and what it does
- External dependencies (`.rol`, `.iob`, DB objects)

### 5. Produce a migration plan
Map the recovered design to the target platform.

**Primary target: JasperReports.** For `.rod → .jrxml` work — including the hard
parts, **dynamic layout** (show/hide columns, parameter-driven layout variants)
and **page-level security** — follow `references/jasper-migration.md`. It gives
the concrete JRXML patterns (`printWhenExpression`, Table/List components,
subreport selection, DynamicReports) and the security patterns. **Note the
deployment:** without JasperReports Server there are no built-in
`LoggedInUser*` params, Domains, or resource permissions — the host application
is the enforcement point (app-passed role params + data-level SQL filtering).
The reference's page-level-security section is written for this embedded case.

Other targets, if asked:
- **BIRT** (`.rptdesign`) — official Actuate successor; closest 1:1 mapping.
- **Power BI / Tableau / SSRS** — rebuild the semantic layer from recovered SQL;
  translate grouping/aggregation; flag Actuate Basic logic for reimplementation.

Call out what maps cleanly, what needs rework, and what has no equivalent
(e.g., pixel-perfect AFC layout, custom Basic event logic).

## Tooling notes
- Prefer `strings`/`grep`/`hexdump`/`file` and a hex editor for binaries.
- If the user still has an `e.Report Designer Professional` install, the native
  **export to XML / BIRT** and the design-source view are far richer than any
  binary scrape — recommend that path first when available.
- `AcServer`/`rox`-era command-line conversion utilities, if licensed and
  present, are the cleanest extractors — use them before scraping.
- For `.iob`/Information Objects, the readable metadata usually yields the
  mapping between report fields and physical columns.

## Output
Default to a Markdown report per Actuate artifact (or one consolidated doc for a
suite), plus a migration matrix. Keep artifact copies and generated analysis in
separate directories.
