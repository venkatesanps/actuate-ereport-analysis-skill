# actuate-ereport-analysis

A [Claude Code](https://claude.com/claude-code) **skill** for analyzing,
documenting, and migrating legacy **Actuate e.Report Designer Professional**
reports to **JasperReports**.

Built for a concrete migration: Actuate `.rod` design files →
**embedded JasperReports library** (no JasperReports Server) on a **Java** host
application with **Snowflake** as the data source — preserving two features that
do not map 1:1:

- **Dynamic layout** — show/hide columns and parameter-driven layout variants.
- **Page-level security** — enforced in the host app (there is no Jasper Server).

## What's inside

| File | Purpose |
|------|---------|
| `actuate-ereport-analysis/SKILL.md` | The on-demand workflow: inventory → extract → recover design → document → migrate. |
| `actuate-ereport-analysis/references/file-formats.md` | Actuate artifact types (`.rod/.rox/.roi/.rol/.iob`), the AFC class model, and Actuate Basic. |
| `actuate-ereport-analysis/references/jasper-migration.md` | The migration playbook: dynamic-layout strategies (A–D), page-level security patterns (A–D, embedded), and Snowflake + Java specifics. |

## Scope & authorization

This skill operates only on report artifacts you **own, maintain, or are
authorized to analyze** (your organization's reports, your exports, a client
engagement). It is for understanding and migrating legacy BI reports off an
end-of-life platform — not for defeating licensing or accessing reports you have
no right to.

## Install

Clone into your Claude Code skills directory:

```bash
git clone <this-repo-url> /tmp/actuate-ereport-skill
cp -r /tmp/actuate-ereport-skill/actuate-ereport-analysis ~/.claude/skills/
```

Then in Claude Code, invoke it explicitly:

```
/actuate-ereport-analysis
```

…or just mention Actuate / `.rod` / e.Report / Jasper in a session and it
activates automatically.

## Migration cheat-sheet

**Dynamic layout**
- Few optional columns/sections → `printWhenExpression` (+ Table/List to reflow).
- Distinct layouts by parameter → one subreport per variant, chosen via
  `subreportExpression`.
- Data-driven/unknown column set → DynamicReports or the JasperReports Java API.

**Page-level security (embedded — the app is the trust boundary)**
- **Data-level filtering is mandatory**: parameterized `WHERE ... IN ($X{IN,...})`
  or an app-scoped datasource; optionally push to Snowflake row-access
  policies / secure views.
- **Role-gated sections** via plain `$P{userRoles}` params your app passes +
  `printWhenExpression` + page breaks (visual only — always paired with data
  filtering).

**Snowflake + Java**
- Pooled `SnowflakeDriver` connection into `JasperFillManager.fillReport(...)`.
- Rewrite Actuate per-row fetches as set-based SQL (JOINs/CTEs/windows).
- UPPERCASE columns vs. case-sensitive `$F{}`; NUMBER → `BigDecimal`;
  flatten VARIANT with `LATERAL FLATTEN`.
