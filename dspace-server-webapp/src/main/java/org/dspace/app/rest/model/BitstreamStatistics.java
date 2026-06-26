/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal aggregate model used by the bitstream statistics endpoint.
 */
public class BitstreamStatistics {
    private Totals totals = new Totals();
    private Map<String, Breakdown> entityTypeBreakdown = new LinkedHashMap<>();
    private Map<String, Breakdown> itemStatusBreakdown = new LinkedHashMap<>();
    private Map<String, Breakdown> documentTypeBreakdown = new LinkedHashMap<>();

    public BitstreamStatistics() {
    }

    public Totals getTotals() {
        return totals;
    }

    public void setTotals(Totals totals) {
        this.totals = totals;
    }

    public Map<String, Breakdown> getEntityTypeBreakdown() {
        return entityTypeBreakdown;
    }

    public void setEntityTypeBreakdown(Map<String, Breakdown> entityTypeBreakdown) {
        this.entityTypeBreakdown = entityTypeBreakdown;
    }

    public Map<String, Breakdown> getItemStatusBreakdown() {
        return itemStatusBreakdown;
    }

    public void setItemStatusBreakdown(Map<String, Breakdown> itemStatusBreakdown) {
        this.itemStatusBreakdown = itemStatusBreakdown;
    }

    public Map<String, Breakdown> getDocumentTypeBreakdown() {
        return documentTypeBreakdown;
    }

    public void setDocumentTypeBreakdown(Map<String, Breakdown> documentTypeBreakdown) {
        this.documentTypeBreakdown = documentTypeBreakdown;
    }

    public static class Totals {
        private long bitstreams;
        private long pdfPages;

        public long getBitstreams() {
            return bitstreams;
        }

        public void setBitstreams(long bitstreams) {
            this.bitstreams = bitstreams;
        }

        public long getPdfPages() {
            return pdfPages;
        }

        public void setPdfPages(long pdfPages) {
            this.pdfPages = pdfPages;
        }
    }

    public static class Breakdown {
        private long bitstreams;
        private long pdfPages;

        public long getBitstreams() {
            return bitstreams;
        }

        public void setBitstreams(long bitstreams) {
            this.bitstreams = bitstreams;
        }

        public long getPdfPages() {
            return pdfPages;
        }

        public void setPdfPages(long pdfPages) {
            this.pdfPages = pdfPages;
        }
    }
}
