package com.example;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/**
 * Runs the migrated JasperReports report under several parameter/role scenarios
 * to prove out the Actuate-equivalent behaviour:
 *   - dynamic layout (show/hide columns, DETAIL vs SUMMARY variant)
 *   - page-level security (role-gated sections + data-level stripping)
 *
 * No database needed: the sample rows stand in for a Snowflake result set. The
 * key security point — data-level filtering — is done HERE in Java, which is
 * exactly where it must live in the embedded (no JasperReports Server) setup.
 */
public class RunDemo {

    /** The full, trusted result set (as if just fetched from Snowflake). */
    static List<SalesRow> fullDataset() {
        return new ArrayList<>(List.of(
            row("West",  "Widget A", 120, "14400.00", "9000.00"),
            row("West",  "Widget B",  80, "12000.00", "7200.00"),
            row("East",  "Widget A", 150, "18000.00", "11000.00"),
            row("East",  "Gadget X",  60, "15000.00", "9500.00"),
            row("North", "Gadget X",  40, "10000.00", "6400.00"),
            row("South", "Widget B",  95, "14250.00", "8100.00")
        ));
    }

    static SalesRow row(String region, String product, int units, String revenue, String cost) {
        BigDecimal rev = new BigDecimal(revenue);
        BigDecimal cst = new BigDecimal(cost);
        return new SalesRow(region, product, units, rev, cst, rev.subtract(cst));
    }

    /**
     * DATA-LEVEL SECURITY (Pattern B). If the user is not FINANCE, the
     * confidential cost/margin values never reach the report at all — not just
     * hidden visually. This is what guarantees no leakage via the export source.
     */
    static List<SalesRow> applyDataLevelSecurity(List<SalesRow> rows, Collection<String> roles) {
        boolean finance = roles != null && roles.contains("FINANCE");
        if (finance) return rows;
        List<SalesRow> scrubbed = new ArrayList<>();
        for (SalesRow r : rows) {
            scrubbed.add(new SalesRow(r.getRegion(), r.getProduct(), r.getUnits(),
                    r.getRevenue(), null, null)); // cost & margin stripped
        }
        return scrubbed;
    }

    record Scenario(String file, String title, String viewMode,
                    boolean showCost, List<String> roles) {}

    public static void main(String[] args) throws Exception {
        // Compile the .jrxml once (in production you'd pre-compile to .jasper).
        JasperReport report;
        try (InputStream in = RunDemo.class.getResourceAsStream("/sales_report.jrxml")) {
            report = JasperCompileManager.compileReport(in);
        }

        List<Scenario> scenarios = List.of(
            new Scenario("1_sales_detail",   "Sales Report",  "DETAIL",  false, List.of("SALES")),
            new Scenario("2_finance_detail", "Sales Report",  "DETAIL",  true,  List.of("SALES","FINANCE")),
            new Scenario("3_sales_summary",  "Sales Summary", "SUMMARY", false, List.of("SALES"))
        );

        Path outDir = Paths.get("output");
        Files.createDirectories(outDir);

        for (Scenario s : scenarios) {
            // 1. app enforces data-level security BEFORE the report sees the data
            List<SalesRow> data = applyDataLevelSecurity(fullDataset(), s.roles());

            // 2. app passes identity/roles + layout params into the report
            Map<String, Object> params = new HashMap<>();
            params.put("reportTitle", s.title());
            params.put("viewMode",    s.viewMode());
            params.put("showCost",    s.showCost());
            params.put("userRoles",   s.roles());

            // 3. fill
            JasperPrint print = JasperFillManager.fillReport(
                    report, params, new JRBeanCollectionDataSource(data));

            // 4. export to HTML
            Path out = outDir.resolve(s.file() + ".html");
            HtmlExporter exporter = new HtmlExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleHtmlExporterOutput(out.toFile()));
            exporter.exportReport();

            System.out.printf("Rendered %-22s roles=%-16s view=%-8s cost=%s -> %s%n",
                    s.file(), s.roles(), s.viewMode(), s.showCost(), out);
        }
        System.out.println("\nDone. Open the HTML files in demo/output/.");
    }
}
