/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.dspace.app.rest.RestResourceController;

/**
 * REST model for community-level bitstream statistics grouped by collection
 * and item status (Approved/Draft/Pending).
 */
public class CommunityBitstreamStatsRest extends BaseObjectRest<String> {
    public static final String NAME = "communitybitstreamstats";
    public static final String PLURAL_NAME = "communitybitstreamstats";
    public static final String CATEGORY = RestModel.STATISTICS;

    private UUID communityId;
    private String communityName;
    private List<CollectionStats> collections = new ArrayList<>();
    private StatusTotals totals = new StatusTotals();

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
    }

    public String getCommunityName() {
        return communityName;
    }

    public void setCommunityName(String communityName) {
        this.communityName = communityName;
    }

    public List<CollectionStats> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionStats> collections) {
        this.collections = collections;
    }

    public StatusTotals getTotals() {
        return totals;
    }

    public void setTotals(StatusTotals totals) {
        this.totals = totals;
    }

    public static class CollectionStats {
        private UUID collectionId;
        private String collectionName;
        private StatusBreakdown approved = new StatusBreakdown();
        private StatusBreakdown draft = new StatusBreakdown();
        private StatusBreakdown pending = new StatusBreakdown();

        public UUID getCollectionId() {
            return collectionId;
        }

        public void setCollectionId(UUID collectionId) {
            this.collectionId = collectionId;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public StatusBreakdown getApproved() {
            return approved;
        }

        public void setApproved(StatusBreakdown approved) {
            this.approved = approved;
        }

        public StatusBreakdown getDraft() {
            return draft;
        }

        public void setDraft(StatusBreakdown draft) {
            this.draft = draft;
        }

        public StatusBreakdown getPending() {
            return pending;
        }

        public void setPending(StatusBreakdown pending) {
            this.pending = pending;
        }
    }

    public static class StatusBreakdown {
        private long bitstreams;
        private long pages;

        public StatusBreakdown() {
        }

        public StatusBreakdown(long bitstreams, long pages) {
            this.bitstreams = bitstreams;
            this.pages = pages;
        }

        public long getBitstreams() {
            return bitstreams;
        }

        public void setBitstreams(long bitstreams) {
            this.bitstreams = bitstreams;
        }

        public long getPages() {
            return pages;
        }

        public void setPages(long pages) {
            this.pages = pages;
        }
    }

    public static class StatusTotals {
        private StatusBreakdown approved = new StatusBreakdown();
        private StatusBreakdown draft = new StatusBreakdown();
        private StatusBreakdown pending = new StatusBreakdown();

        public StatusBreakdown getApproved() {
            return approved;
        }

        public void setApproved(StatusBreakdown approved) {
            this.approved = approved;
        }

        public StatusBreakdown getDraft() {
            return draft;
        }

        public void setDraft(StatusBreakdown draft) {
            this.draft = draft;
        }

        public StatusBreakdown getPending() {
            return pending;
        }

        public void setPending(StatusBreakdown pending) {
            this.pending = pending;
        }
    }

    @JsonIgnore
    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return NAME;
    }

    @Override
    public String getTypePlural() {
        return PLURAL_NAME;
    }

    @JsonIgnore
    @Override
    public Class<?> getController() {
        return RestResourceController.class;
    }
}