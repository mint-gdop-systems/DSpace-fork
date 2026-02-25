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

    private HouseStats houseStats;
    private VitalEventStats vitalEventStats;

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

    public HouseStats getHouseStats() {
        return houseStats;
    }

    public void setHouseStats(HouseStats houseStats) {
        this.houseStats = houseStats;
    }

    public VitalEventStats getVitalEventStats() {
        return vitalEventStats;
    }

    public void setVitalEventStats(VitalEventStats vitalEventStats) {
        this.vitalEventStats = vitalEventStats;
    }

    public static class HouseStats {
        private int totalRegisteredHouses;
        private java.util.Map<String, Integer> distributionByHouseType = new HashMap<>();
        private double averageFamilySizePerHouse;
        private long totalRegisteredCitizens;

        public int getTotalRegisteredHouses() {
            return totalRegisteredHouses;
        }

        public void setTotalRegisteredHouses(int totalRegisteredHouses) {
            this.totalRegisteredHouses = totalRegisteredHouses;
        }

        public java.util.Map<String, Integer> getDistributionByHouseType() {
            return distributionByHouseType;
        }

        public void setDistributionByHouseType(java.util.Map<String, Integer> distributionByHouseType) {
            this.distributionByHouseType = distributionByHouseType;
        }

        public double getAverageFamilySizePerHouse() {
            return averageFamilySizePerHouse;
        }

        public void setAverageFamilySizePerHouse(double averageFamilySizePerHouse) {
            this.averageFamilySizePerHouse = averageFamilySizePerHouse;
        }

        public long getTotalRegisteredCitizens() {
            return totalRegisteredCitizens;
        }

        public void setTotalRegisteredCitizens(long totalRegisteredCitizens) {
            this.totalRegisteredCitizens = totalRegisteredCitizens;
        }
    }

    public static class VitalEventStats {
        private int totalVitalEvents;
        private int birthRecords;
        private int deathRecords;
        private int marriageRecords;

        public int getTotalVitalEvents() {
            return totalVitalEvents;
        }

        public void setTotalVitalEvents(int totalVitalEvents) {
            this.totalVitalEvents = totalVitalEvents;
        }

        public int getBirthRecords() {
            return birthRecords;
        }

        public void setBirthRecords(int birthRecords) {
            this.birthRecords = birthRecords;
        }

        public int getDeathRecords() {
            return deathRecords;
        }

        public void setDeathRecords(int deathRecords) {
            this.deathRecords = deathRecords;
        }

        public int getMarriageRecords() {
            return marriageRecords;
        }

        public void setMarriageRecords(int marriageRecords) {
            this.marriageRecords = marriageRecords;
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
