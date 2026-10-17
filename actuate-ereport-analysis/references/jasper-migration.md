# Actuate e.Report (.rod) → JasperReports Migration

Target-specific reference for migrating Actuate designs to **JasperReports /
JasperReports Server**, with emphasis on the two features that do NOT map 1:1:
**dynamic layout** and **page-level security**.

## Mapping overview

| Actuate concept                     | JasperReports equivalent |
|-------------------------------------|--------------------------|
| `.rod` design                       | `.jrxml` (compiled to `.jasper`) |
| AFC component tree                  | Bands (title/pageHeader/columnHeader/detail/groupHeader/summary…) |
| `AcConnection` + `AcSQLQuerySource` | `<queryString>` + JDBC datasource, or `$P{}`-parameterized SQL |
| `.iob` Information Object            | JRXML `<field>` mapping + app-side SQL (no Domains without Server) |
| Group section / `AcGroup`           | `<group>` with group header/footer bands |
| Summary row / `AcAggregateColumn`   | `<variable calculation="Sum/Count/Average">` |
| Actuate Basic event method          | Reimplement as expression, `<variable>`, scriptlet (JRScriptlet), or data-layer SQL |
| Parameter / `AcSingleInputFilter`   | `<parameter>` + `$P{}` in query / `printWhenExpression` |
| `.rol` library                      | JRXML templates, styles (`.jrtx`), shared subreports |

Workflow: recover the Actuate design per the main SKILL, then author JRXML.
Prefer **Jaspersoft Studio** for visual work; generate JRXML directly for
repeatable/bulk conversion.

---

## 1. Dynamic layout

Actuate does show/hide columns and parameter-driven layouts naturally because
AFC builds the component tree at runtime in Actuate Basic. Jasper's static JRXML
cannot restructure itself at runtime — you pick one of four strategies below.
**Match the strategy to how dynamic the layout really is.**

### A. Conditional elements — `printWhenExpression` (lightest)
Show/hide an element, column cell, or whole band by parameter/field:
```xml
<textField>
  <reportElement x="0" y="0" width="80" height="20">
    <printWhenExpression><![CDATA[$P{showRevenue}]]></printWhenExpression>
  </reportElement>
  <textFieldExpression><![CDATA[$F{revenue}]]></textFieldExpression>
</textField>
```
- Put it on the `<band>` to toggle an entire section/layout variant.
- Hidden elements still occupy their x/width. For true column collapse you must
  also close the gap — see B.
Good for: a handful of optional columns/sections.

### B. Collapsing the gap when columns hide
`printWhenExpression` leaves a hole. To reflow:
- Use a **vertical/horizontal List component** (`jr:list`) or a **Table
  component** whose columns carry `printWhenExpression` — the Table component
  removes hidden columns' width in recent Jasper versions.
- Or set element `positionType="Float"` and `isRemoveLineWhenBlank="true"`, and
  lay cells in a stretching container so neighbors shift left.
- Or use multiple pre-built band variants (see C) — often simpler than fighting
  reflow.

### C. Multiple layout variants selected by parameter
When "different layouts based on parameters" means genuinely different
structures (e.g. summary vs. detail vs. matrix view):
- Build each variant as a **subreport** (`layout_summary.jrxml`,
  `layout_detail.jrxml`, `layout_matrix.jrxml`).
- In the master, pick one at runtime:
```xml
<subreport>
  <subreportExpression><![CDATA[
    $P{viewMode}.equals("summary") ? "layout_summary.jasper"
    : $P{viewMode}.equals("matrix") ? "layout_matrix.jasper"
    : "layout_detail.jasper"
  ]]></subreportExpression>
</subreport>
```
Clean, testable, and each layout stays maintainable. Best default for
Actuate reports that switched whole layouts by parameter.

> Embedded note: a `subreportExpression` returning a path needs a resolvable
> location. Either pass a base dir parameter
> (`$P{SUBREPORT_DIR} + "layout_summary.jasper"`), or pre-compile subreports and
> pass the `net.sf.jasperreports.engine.JasperReport` objects in as parameters so
> there is no filesystem lookup at fill time.

### D. Fully runtime-dynamic structure — DynamicReports / Jasper API
When the column *set* is data-driven (unknown at design time, like Actuate
building columns from a result set), static JRXML can't express it. Build the
report programmatically:
- **DynamicReports** (`dynamicreports.org`) — a Java DSL over JasperReports that
  adds/removes columns, groups, and bands at runtime. The closest analog to
  Actuate's AFC "build the tree in code" model.
- Or the raw **JasperReports Java API** (`DesignJasperReport`,
  `JRDesignColumn`…) to emit JRXML/`.jasper` on the fly.
Use this only when A–C genuinely can't express the variability — it moves layout
back into Java code (much like the original Actuate Basic).

### Conditional formatting
Actuate conditional styles → Jasper `<conditionalStyle>`:
```xml
<style name="rowStyle">
  <conditionalStyle>
    <conditionExpression><![CDATA[$F{status}.equals("OVERDUE")]]></conditionExpression>
    <style forecolor="#CC0000" isBold="true"/>
  </conditionalStyle>
</style>
```

**Decision guide**
- Few optional columns/sections → **A** (+ **B** if gaps matter).
- A fixed set of distinct layouts by parameter → **C** (subreports).
- Column set itself is data-driven/unknown → **D** (DynamicReports).

---

## 2. Page-level security  (embedded JasperReports library — NO Server)

This is the biggest conceptual gap. Actuate (iServer/Encyclopedia) can secure
**individual pages/sections of a single report instance (ROI)** so different
users opening the same report see only authorized pages.

> **This deployment uses the embedded JasperReports library, not JasperReports
> Server.** That means **none** of the Server security machinery exists:
> - No built-in `$P{LoggedInUsername}` / `$P{LoggedInUserRoles}` parameters.
> - No Domains, no `securityDefinition.xml`, no OLAP access grants.
> - No resource-level permissions or repository ACLs.
>
> **All security must be enforced by YOUR host application** (the Java/app that
> calls `JasperFillManager`). The report engine trusts whatever your app passes
> in. So: authenticate the user in the app, resolve their roles there, and
> (a) filter the data before/as it reaches the report, and (b) pass role flags
> as ordinary report parameters for layout gating.

The report is **not** a trust boundary — your app is. Patterns below reflect
that.

### Pattern A — App resolves identity, passes roles as ordinary parameters
Declare plain parameters and populate them from your app's auth context, then
gate bands/sections with `printWhenExpression`:
```xml
<parameter name="userRoles" class="java.util.Collection"/>
<parameter name="userId"    class="java.lang.String"/>
...
<band height="120">
  <printWhenExpression><![CDATA[
    $P{userRoles}.contains("FINANCE")
  ]]></printWhenExpression>
  <!-- finance-only page/section content -->
</band>
```
Host side:
```java
Map<String,Object> params = new HashMap<>();
params.put("userRoles", currentUser.getRoles());   // from YOUR auth layer
params.put("userId",    currentUser.getId());
JasperPrint print = JasperFillManager.fillReport(jasper, params, connection);
```
- Force a section onto its own page with `<break type="Page"/>` so "section
  security" reads like "page security."
- **Visual hiding only.** The data for a hidden section is still fetched and
  still present in the `JasperPrint`/export source unless you also filter it
  (Pattern B). Never rely on `printWhenExpression` alone for confidential data.

### Pattern B — Data-level filtering in the app/SQL — REQUIRED for real confidentiality
Since there are no Domains, enforce row/column security yourself so unauthorized
data never enters the report:
- **Parameterized WHERE** using the app-passed user/roles:
  ```xml
  <parameter name="allowedOrgs" class="java.util.Collection"/>
  <queryString><![CDATA[
    SELECT ... FROM sales
    WHERE org_id IN ($X{IN, org_id, allowedOrgs})
  ]]></queryString>
  ```
  (`$X{IN, col, allowedOrgs}` expands a collection safely.)
- **Or filter in the app**: build the `JRDataSource` / result set already scoped
  to the user, or wrap your `Connection`/`JRDataSource` so it can only return
  authorized rows. This keeps enforcement entirely in trusted Java.
- **Or `<filterExpression>`** on the dataset as a last-resort in-report filter —
  but prefer SQL/app filtering; `filterExpression` still pulls rows into memory.
- Pair with Pattern A so the layout hides now-empty sections cleanly.
- This is the only approach that guarantees a user cannot obtain hidden data via
  export or source inspection.

### Pattern C — Per-user generation / bursting (app-driven)
If Actuate's page-level security was really "each recipient gets their own
pages," replicate it in your app with a loop — no Server scheduler needed:
```java
for (User u : recipients) {
    Map<String,Object> p = baseParams(u);           // user-scoped params
    JasperPrint jp = JasperFillManager.fillReport(jasper, p, conn);
    JasperExportManager.exportReportToPdfFile(jp, outDir + "/" + u.getId() + ".pdf");
}
```
Each output contains only that user's authorized data/pages. Combine with
Pattern B so each run is already data-scoped.

### Pattern D — Separate report files gated by the app
If Actuate's secured pages were effectively independent deliverables, split them
into separate `.jrxml`/`.jasper` files and let your **app decide which report a
user may run** (your existing authorization checks before calling
`JasperFillManager`). Equivalent to Server resource permissions, enforced in your
code.

### Recommended combination (embedded)
1. **Pattern B (data-level, in SQL or app)** — mandatory; guarantees no leakage.
2. **Pattern A (role-gated sections + page breaks)** — reproduces the
   page/section visibility experience, driven by params your app passes.
3. **Pattern C** if delivery was per-recipient; **Pattern D** if pages were
   really separate reports.
Your host app is the enforcement point throughout — the `.jrxml` only *reflects*
decisions the app has already made and the data it was already handed.

Document, per Actuate secured page: which role(s) saw it, what data it exposed,
which Jasper pattern now enforces it, and **where in the app** the check lives.

---

## 3. Snowflake + Java specifics (this stack)

Embedded JasperReports, Java host app, **Snowflake** as the source. Snowflake is
a columnar analytical warehouse billed by warehouse time — this changes both the
SQL you port and how you enforce security.

### Connection
- Driver: `net.snowflake.client.jdbc.SnowflakeDriver`
  (`net.snowflake:snowflake-jdbc`).
- URL: `jdbc:snowflake://<account>.snowflakecomputing.com/?db=DB&schema=SCH&warehouse=WH&role=ROLE`.
- **Pool connections** (HikariCP). Keep the warehouse small/auto-suspend; don't
  open a connection per row.
- Pass the `Connection` into `JasperFillManager.fillReport(jasper, params, conn)`
  as in the patterns above.

### SQL porting from Actuate → Snowflake
- **Kill per-row queries.** Actuate Basic / AFC often fetched row-by-row or ran
  a sub-query per detail row. On Snowflake that is slow and costly — rewrite as
  **set-based SQL with JOINs / CTEs / window functions** and let one query feed
  the report. This is usually the single biggest performance win.
- **Stored procedures:** Actuate proc calls may not port 1:1. Prefer plain SQL /
  views; use Snowflake scripting procs only when real procedural logic is needed.
- **No per-report cursors:** fetch once; group/aggregate in SQL or Jasper
  `<variable>`s, not in a scriptlet loop.
- Use **result caching** and parameter binding so repeated runs are cheap.

### Identifier casing (common Jasper pitfall)
- Snowflake returns **UPPERCASE** column names unless the query quotes them.
  Jasper `$F{...}` field names are **case-sensitive** and must match the returned
  label exactly. Either:
  - name fields `$F{REVENUE}` (match Snowflake's uppercase), or
  - alias in SQL: `SELECT revenue AS "revenue"` to control the case.
- Be consistent across all `.jrxml` to avoid "field not found" at fill time.

### Type mapping (`<field class="...">`)
| Snowflake type                 | `<field>` class            | Notes |
|--------------------------------|----------------------------|-------|
| NUMBER / DECIMAL               | `java.math.BigDecimal`     | precision-safe; avoid double |
| INT / BIGINT                   | `java.lang.Long`           | |
| FLOAT / DOUBLE                 | `java.lang.Double`         | |
| VARCHAR / STRING               | `java.lang.String`         | |
| BOOLEAN                        | `java.lang.Boolean`        | |
| DATE                           | `java.sql.Date`            | |
| TIMESTAMP_NTZ/LTZ/TZ           | `java.sql.Timestamp`       | mind session timezone |
| VARIANT / OBJECT / ARRAY       | `java.lang.String`         | `PARSE_JSON`/`TO_VARCHAR`, or `LATERAL FLATTEN` into rows first |

Flatten semi-structured columns in SQL (`LATERAL FLATTEN`) rather than parsing
VARIANT inside the report.

### Page-level / data-level security on Snowflake (two routes)
With no Jasper Server, enforce Pattern B (mandatory data-level) one of two ways:

1. **App-side parameter filtering (simplest, recommended default).**
   Service-account connection; the Java app resolves the user's roles/scope and
   binds them:
   ```xml
   <parameter name="allowedOrgs" class="java.util.Collection"/>
   <queryString><![CDATA[
     SELECT ... FROM REPORTING.SALES
     WHERE ORG_ID IN ($X{IN, ORG_ID, allowedOrgs})
   ]]></queryString>
   ```
   Enforcement lives in trusted Java + the WHERE clause. Easy to test, no
   Snowflake object changes.

2. **Push security into Snowflake (defense-in-depth).** Use Snowflake's own
   **row access policies** and/or **secure views** so the warehouse enforces
   visibility even if the app's WHERE is wrong:
   - Per-user DB role: connect/`USE ROLE` so Snowflake RBAC + row access policies
     apply to that role. Cleanest, but needs a Snowflake role per report persona
     and per-user credentials or key-pair auth.
   - Shared service account + **session context**: set a session variable or use
     an app-set context, and write the row access policy against it:
     ```sql
     -- once per fill, before running the report query:
     ALTER SESSION SET QUERY_TAG = 'report';
     -- bind the acting user into a session variable the policy reads
     ```
     then a row access policy keyed on that variable (or on `CURRENT_ROLE()` /
     mapping table) filters rows centrally. Report against a **secure view**, not
     the base table, so the definition can't be bypassed.
   Route 2 is stronger (the warehouse is the boundary) but heavier to set up —
   adopt it for genuinely confidential data; Route 1 is fine for most migrations.

Document, per secured Actuate page, which route enforces it and the exact
role→data rule.

---

## Deliverables for a Jasper migration
Per `.rod`, produce:
1. Recovered design spec (from the main SKILL workflow).
2. `.jrxml` (or DynamicReports Java) implementing layout + dynamic behavior.
3. A **security mapping table**: each Actuate secured page/section → chosen
   Jasper pattern(s) → the roles/data rules that enforce it.
4. Datasource/parameter config and any subreport/template/`.jrtx` files.
5. Notes on Actuate Basic logic that became variables/scriptlets/SQL.

## Gotchas
- Actuate pixel-perfect AFC frames rarely map exactly — expect band/geometry
  rework.
- `printWhenExpression` returns `Boolean`; null-guard parameters.
- Hidden ≠ secure: visual hiding (Pattern A) without data filtering (Pattern B)
  can leak via export/source — always combine for confidential data.
- Dynamic/unknown column counts need the Table/List component or DynamicReports —
  don't try to force static JRXML to do it.
- Snowflake returns UPPERCASE columns; keep `$F{}` names in sync or alias in SQL.
- Map NUMBER → `BigDecimal` (not double) to avoid precision loss in totals.
- Rewrite Actuate per-row fetches as set-based Snowflake SQL — don't port the
  cursor pattern.
- Test every parameter combination that selected a different Actuate layout.
