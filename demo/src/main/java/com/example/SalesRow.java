package com.example;

import java.math.BigDecimal;

/**
 * One row of report data. Stands in for a row returned from Snowflake.
 *
 * In the real embedded setup this bean is populated from a Snowflake JDBC
 * ResultSet (or you pass the Connection straight into JasperFillManager).
 * Here we use beans so the demo is self-contained and needs no credentials —
 * and so we can demonstrate DATA-LEVEL security by nulling out cost/margin in
 * Java before the data ever reaches the report.
 */
public class SalesRow {
    private String region;
    private String product;
    private Integer units;
    private BigDecimal revenue;
    private BigDecimal cost;    // confidential: only populated for FINANCE
    private BigDecimal margin;  // confidential: only populated for FINANCE

    public SalesRow(String region, String product, Integer units,
                    BigDecimal revenue, BigDecimal cost, BigDecimal margin) {
        this.region = region;
        this.product = product;
        this.units = units;
        this.revenue = revenue;
        this.cost = cost;
        this.margin = margin;
    }

    public String getRegion()      { return region; }
    public String getProduct()     { return product; }
    public Integer getUnits()      { return units; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getCost()    { return cost; }
    public BigDecimal getMargin()  { return margin; }
}
