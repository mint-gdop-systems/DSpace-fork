package org.dspace.app.rest.model;

import org.dspace.app.rest.RestResourceController;

import java.util.HashMap;

/**
 * REST model for per-collection CRVS statistics.
 */
public class CollectionStatsRest extends BaseObjectRest<String> {
    public static final String NAME = "collectionstats";
    public static final String PLURAL_NAME = "collectionstats";
    public static final String CATEGORY = RestModel.STATISTICS;

    private String collectionId;
    private String collectionName;
    private String entityType;

    private CaseFileStats caseFileStats;
    private CirculationEventStats circulationEventStats;

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public CaseFileStats getCaseFileStats() {
        return caseFileStats;
    }

    public void setCaseFileStats(CaseFileStats caseFileStats) {
        this.caseFileStats = caseFileStats;
    }

    public CirculationEventStats getCirculationEventStats() {
        return circulationEventStats;
    }

    public void setCirculationEventStats(CirculationEventStats circulationEventStats) {
        this.circulationEventStats = circulationEventStats;
    }

    public static class CaseFileStats {
        private int totalRegisteredCaseFiles;
        private java.util.Map<String, Integer> distributionByCaseType = new HashMap<>();
        private java.util.Map<String, Integer> distributionByCaseStatus = new HashMap<>();

        public int getTotalRegisteredCaseFiles() {
            return totalRegisteredCaseFiles;
        }

        public void setTotalRegisteredCaseFiles(int totalRegisteredCaseFiles) {
            this.totalRegisteredCaseFiles = totalRegisteredCaseFiles;
        }

        public java.util.Map<String, Integer> getDistributionByCaseType() {
            return distributionByCaseType;
        }

        public void setDistributionByCaseType(java.util.Map<String, Integer> distributionByCaseType) {
            this.distributionByCaseType = distributionByCaseType;
        }

        public java.util.Map<String, Integer> getDistributionByCaseStatus() {
            return distributionByCaseStatus;
        }

        public void setDistributionByCaseStatus(java.util.Map<String, Integer> distributionByCaseStatus) {
            this.distributionByCaseStatus = distributionByCaseStatus;
        }
    }

    public static class CirculationEventStats {
        private int totalCirculationEvents;
        private java.util.Map<String, Integer> distributionByEventStatus = new HashMap<>();

        public int getTotalCirculationEvents() {
            return totalCirculationEvents;
        }

        public void setTotalCirculationEvents(int totalCirculationEvents) {
            this.totalCirculationEvents = totalCirculationEvents;
        }

        public java.util.Map<String, Integer> getDistributionByEventStatus() {
            return distributionByEventStatus;
        }

        public void setDistributionByEventStatus(java.util.Map<String, Integer> distributionByEventStatus) {
            this.distributionByEventStatus = distributionByEventStatus;
        }
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class<RestResourceController> getController() {
        return RestResourceController.class;
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getTypePlural() {
        return PLURAL_NAME;
    }
}
