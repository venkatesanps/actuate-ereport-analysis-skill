# Runnable demo — Actuate → JasperReports migration patterns

A self-contained JasperReports project proving out the two hard Actuate
features on the **embedded library** (no JasperReports Server), the way they'd
run on **Java + Snowflake**.

## What it shows
- **Dynamic layout** — show/hide `Cost`/`Margin` columns, and a `DETAIL` vs
  `SUMMARY` layout variant, all chosen by parameters.
- **Page-level security** — the FINANCE-only columns/section are gated by
  `$P{userRoles}` (visual) AND the confidential data is stripped in Java before
  the report sees it (data-level). Non-FINANCE output has no cost/margin even in
  the HTML source.

The sample rows stand in for a Snowflake result set. Data-level security is
enforced in `RunDemo.applyDataLevelSecurity(...)` — exactly where it belongs in
the embedded setup (your app is the trust boundary, not the report).

## Run
```bash
cd demo
mvn compile exec:java
```
Outputs land in `demo/output/*.html`:
- `1_sales_detail.html`   — SALES, DETAIL, no cost
- `2_finance_detail.html` — FINANCE, DETAIL, cost+margin+profitability section
- `3_sales_summary.html`  — SALES, SUMMARY layout variant

## Files
- `src/main/resources/sales_report.jrxml` — the migrated report (JRXML)
- `src/main/java/com/example/RunDemo.java` — host app: security + fill + export
- `src/main/java/com/example/SalesRow.java` — the data bean (Snowflake row stand-in)

## Swapping in real Snowflake
Replace the bean datasource with a JDBC `Connection`:
```java
params.put("userRoles", currentUser.getRoles());
JasperPrint print = JasperFillManager.fillReport(report, params, snowflakeConn);
```
and move the row filter into the SQL (`WHERE ORG_ID IN ($X{IN, ORG_ID, allowedOrgs})`).
Mind UPPERCASE column names vs case-sensitive `$F{}`, and map NUMBER → BigDecimal.
