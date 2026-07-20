/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.dspace.core.Context;

/**
 * Service interface for exporting repository-wide bitstream statistics as a
 * PDF.
 */
public interface RepositoryBitstreamStatsPdfExportService {

    /**
     * Represents the stats for a single collection within a community section.
     */
    class StatsRow {
        private final String name;
        private long approvedBitstreams;
        private long approvedPages;
        private long draftBitstreams;
        private long draftPages;
        private long pendingBitstreams;
        private long pendingPages;
        private long approvedItemCount;
        private long pendingItemCount;

        public StatsRow(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public long getApprovedBitstreams() {
            return approvedBitstreams;
        }

        public void setApprovedBitstreams(long approvedBitstreams) {
            this.approvedBitstreams = approvedBitstreams;
        }

        public long getApprovedPages() {
            return approvedPages;
        }

        public void setApprovedPages(long approvedPages) {
            this.approvedPages = approvedPages;
        }

        public long getDraftBitstreams() {
            return draftBitstreams;
        }

        public void setDraftBitstreams(long draftBitstreams) {
            this.draftBitstreams = draftBitstreams;
        }

        public long getDraftPages() {
            return draftPages;
        }

        public void setDraftPages(long draftPages) {
            this.draftPages = draftPages;
        }

        public long getPendingBitstreams() {
            return pendingBitstreams;
        }

        public void setPendingBitstreams(long pendingBitstreams) {
            this.pendingBitstreams = pendingBitstreams;
        }

        public long getPendingPages() {
            return pendingPages;
        }

        public void setPendingPages(long pendingPages) {
            this.pendingPages = pendingPages;
        }

        public long getFilesApproved() {
            return approvedBitstreams;
        }

        public long getFilesDraft() {
            return draftBitstreams;
        }

        public long getFilesPending() {
            return pendingBitstreams;
        }

        public long getFilesTotal() {
            return getFilesApproved() + getFilesDraft() + getFilesPending();
        }

        public long getPagesApproved() {
            return approvedPages;
        }

        public long getPagesDraft() {
            return draftPages;
        }

        public long getPagesPending() {
            return pendingPages;
        }

        public long getPagesTotal() {
            return approvedPages + draftPages + pendingPages;
        }

        public long getApprovedItemCount() {
            return approvedItemCount;
        }

        public void setApprovedItemCount(long approvedItemCount) {
            this.approvedItemCount = approvedItemCount;
        }

        public long getPendingItemCount() {
            return pendingItemCount;
        }

        public void setPendingItemCount(long pendingItemCount) {
            this.pendingItemCount = pendingItemCount;
        }
    }

    /**
     * Represents a section in the PDF for a community or sub-community.
     */
    class CommunitySection {
        private final String communityName;
        private final int depthLevel;
        private final List<StatsRow> collectionRows;

        public CommunitySection(String communityName, int depthLevel, List<StatsRow> collectionRows) {
            this.communityName = communityName;
            this.depthLevel = depthLevel;
            this.collectionRows = collectionRows;
        }

        public String getCommunityName() {
            return communityName;
        }

        public int getDepthLevel() {
            return depthLevel;
        }

        public List<StatsRow> getCollectionRows() {
            return collectionRows;
        }
    }

    /**
     * Traverse the entire community hierarchy and collect bitstream statistics.
     *
     * @param context the DSpace context
     * @return ordered list of community sections, depth-first
     * @throws SQLException if database error
     */
    List<CommunitySection> collectAllStats(Context context) throws SQLException;

    /**
     * Generate a PDF document from the given community sections.
     *
     * @param sections the community sections to include
     * @return the PDF bytes
     * @throws IOException if PDF generation fails
     */
    byte[] generatePdf(List<CommunitySection> sections) throws IOException;
}
