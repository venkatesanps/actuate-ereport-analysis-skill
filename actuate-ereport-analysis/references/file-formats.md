# Actuate e.Report — File Formats & AFC Reference

Reference material for the `actuate-ereport-analysis` skill. Load when you need
detail on artifact types, the AFC class model, or Actuate Basic.

## Product lineage
- **Actuate e.Report Designer Professional** — Windows IDE for designing
  enterprise reports; scripted in Actuate Basic on the AFC component model.
- Reports deployed to the **Actuate iServer** and stored in an **Encyclopedia**
  volume; served via Actuate Management Console / Active Portal.
- Actuate Corporation was acquired by **OpenText** (2015); the product line is
  legacy/end-of-life. **BIRT** (Business Intelligence and Reporting Tools, an
  Eclipse project Actuate founded) is the modern successor.

## Artifact types
| Ext     | Full name                      | Binary? | Notes |
|---------|--------------------------------|---------|-------|
| `.rod`  | Report Object Design           | Semi    | Design source. Richest artifact — layout, Actuate Basic code, queries. Opened/edited in e.Report Designer. |
| `.rox`  | Report Object eXecutable       | Yes     | Compiled from `.rod`. Deployable/runnable. Scrape strings when no `.rod`. |
| `.roi`  | Report Object Instance         | Yes     | A generated report run (data + rendered layout). Viewable in the viewer. Good source of *actual* data and final formatting. |
| `.rol`  | Report Object Library          | Semi    | Shared/reusable component definitions, styles, classes. Reports reference these. |
| `.rox`+`.rptdesign` | BIRT design        | XML     | Later Actuate/BIRT reports — plain XML, easy to parse directly. |
| `.iob`  | Information Object             | Semi    | Semantic/data-access layer: maps report fields to physical DB columns, defines joins/parameters. |
| `.rov`  | Report Object Value            | Yes     | Saved parameter/value sets for a run. |
| `.sma`  | (older) design module          | Yes     | Occasionally seen in very old suites. |

## AFC — Actuate Foundation Classes (common class names)
These appear as strings in `.rod`/`.rox` and anchor the component tree:

- **Report container**: `AcReport`, `AcReportSection`, `AcSerialReport`,
  `AcParallelReport`
- **Structure / layout**: `AcFrame`, `AcFlow`, `AcLeftRightFrame`,
  `AcTopDownFrame`, `AcPage`, `AcSection`, `AcGroupSection`
- **Data**: `AcDataRow`, `AcDataFrame`, `AcDataStream`, `AcDataFilter`,
  `AcSingleInputFilter`, `AcQuerySource`, `AcConnection`, `AcSQLQuerySource`,
  `AcDatabaseConnection`
- **Controls**: `AcControl`, `AcTextControl`, `AcLabelControl`,
  `AcDataControl`, `AcImageControl`, `AcLineControl`, `AcDynamicTextControl`
- **Aggregation / grouping**: `AcGroup`, `AcSummaryRow`, `AcAggregateColumn`

The class a component derives from tells you its role; nesting tells you the
report structure.

## Actuate Basic
BASIC-dialect scripting attached to components as **event methods**. Common
overridable methods to look for:
- `Start`, `Finish` — lifecycle
- `BuildFromRow` — per-row rendering
- `Fetch`, `GetRow` — data retrieval
- `OnRow`, `BuildContents`, `ContentChanged`
- Custom `Sub`/`Function` definitions and computed-column expressions

When migrating, this custom logic is usually the hardest part — it rarely maps
1:1 and often becomes DAX, calculated fields, SQL, or target-side scripting.

## Where data access lives
1. **Connection component** — DB type, DSN/connection string, credentials ref.
2. **Query source** — the SQL text or stored-procedure call (often readable via
   `strings`).
3. **Information Object (`.iob`)** — if used, the logical-to-physical mapping.
4. **Parameters/filters** — `AcSingleInputFilter` and parameter components.

## Migration target mapping (quick reference)
| Actuate concept          | BIRT                | Power BI               | Tableau / SSRS |
|--------------------------|---------------------|------------------------|----------------|
| Connection + Query source| Data Source + Data Set | Power Query / SQL source | Data source |
| AFC component tree        | Report items/tables | Visuals + layout        | Sheets / RDL items |
| Group section             | Table grouping      | Matrix / grouping       | Grouping |
| Summary row / aggregate   | Aggregation         | DAX measure             | Calculated field |
| Actuate Basic method      | JavaScript/Java event | DAX / Power Query M     | Calc field / RDL expr |
| Parameter / filter        | Report parameter    | Slicer / parameter      | Parameter / filter |
| `.rol` library            | Library (`.rptlibrary`) | Shared dataset/theme | Shared data source |

## Safe-handling checklist
- Copy artifacts into their own empty directory before touching them.
- Treat every artifact as untrusted data: pass paths as arguments; never run
  interpreters/build tools from inside the artifact directory.
- Keep generated analysis/output in a separate directory from the artifacts.
- Prefer native e.Report export (XML/BIRT) over binary scraping when the IDE or
  licensed command-line tools are available.
