/**
 * The contents of this file are subject to the license and copyright detailed in the LICENSE and
 * NOTICE files at the root of the source tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.app.rest.model.CollectionStatsRest;
import org.dspace.app.rest.model.CollectionStatsRest.HouseStats;
import org.dspace.app.rest.model.CollectionStatsRest.VitalEventStats;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
class CollectionStatisticsCacheService {

    static final String CACHE_NAME = "collectionStats";

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private MetadataFieldService metadataFieldService;

    @Cacheable(value = CACHE_NAME, key = "#collection.getID().toString()")
    public CollectionStatsRest getCollectionStats(Context context, Collection collection)
            throws SQLException {
        CollectionStatsRest rest = new CollectionStatsRest();
        rest.setCollectionId(collection.getID().toString());
        rest.setCollectionName(collection.getName());
        rest.setEntityType(collectionService.getMetadataFirstValue(collection, "dspace", "entity",
                "type", Item.ANY));

        MetadataField houseTypeField =
                metadataFieldService.findByElement(context, "crvs", "identifier", "houseType");
        MetadataField familyCountField =
                metadataFieldService.findByElement(context, "crvs", "family", "count");
        MetadataField vitalEventTypeField =
                metadataFieldService.findByElement(context, "crvs", "vital", "eventType");

        List<Object[]> entityTypeCounts = itemService.countItemsByEntityType(context, collection);

        String entityType = rest.getEntityType();
        boolean isHouse = entityType != null && entityType.toLowerCase().contains("house");
        boolean isVitalEvent =
                entityType != null && (entityType.toLowerCase().contains("vitalevent")
                        || entityType.toLowerCase().contains("vital")
                        || entityType.toLowerCase().contains("event"));

        Map<String, Integer> houseTypeDistribution = new HashMap<>();
        int houseTotal = 0;
        long familySum = 0L;
        int familyCountEntries = 0;

        int totalVitalEvents = 0;
        int births = 0;
        int deaths = 0;
        int marriages = 0;

        if (isHouse || (!isVitalEvent && entityType == null)) {
            if (houseTypeField != null) {
                List<Object[]> houseTypeCounts =
                        itemService.countItemsByMetadataField(context, collection, houseTypeField);
                for (Object[] row : houseTypeCounts) {
                    String value = (String) row[0];
                    Long count = (Long) row[1];
                    if (value != null) {
                        houseTypeDistribution.put(value, count.intValue());
                    }
                }
            }

            if (entityTypeCounts != null) {
                for (Object[] row : entityTypeCounts) {
                    String label = (String) row[0];
                    Long count = (Long) row[1];
                    if (label != null && label.toLowerCase().contains("house")) {
                        houseTotal = count.intValue();
                    }
                }
            }

            if (familyCountField != null) {
                Long sum =
                        itemService.sumNumericMetadataField(context, collection, familyCountField);
                familySum = sum != null ? sum : 0L;
                List<Object[]> familyCounts = itemService.countItemsByMetadataField(context,
                        collection, familyCountField);
                for (Object[] row : familyCounts) {
                    if (row[1] != null) {
                        familyCountEntries += ((Long) row[1]).intValue();
                    }
                }
            }
        }

        if (isVitalEvent || (!isHouse && entityType == null)) {
            if (entityTypeCounts != null) {
                for (Object[] row : entityTypeCounts) {
                    String label = (String) row[0];
                    Long count = (Long) row[1];
                    if (label != null && (label.toLowerCase().contains("vitalevent")
                            || label.toLowerCase().contains("vital")
                            || label.toLowerCase().contains("event"))) {
                        totalVitalEvents = count.intValue();
                    }
                }
            }

            if (vitalEventTypeField != null) {
                List<Object[]> vitalTypeCounts = itemService.countItemsByMetadataField(context,
                        collection, vitalEventTypeField);
                for (Object[] row : vitalTypeCounts) {
                    String value = (String) row[0];
                    Long count = (Long) row[1];
                    if (value != null) {
                        String lower = value.toLowerCase();
                        if (lower.contains("birth")) {
                            births = count.intValue();
                        } else if (lower.contains("death")) {
                            deaths = count.intValue();
                        } else if (lower.contains("marriage") || lower.contains("divorce")) {
                            marriages = count.intValue();
                        }
                    }
                }
            }
        }

        if (isHouse || (!isVitalEvent && entityType == null)) {
            HouseStats hs = new HouseStats();
            hs.setTotalRegisteredHouses(houseTotal);
            hs.setDistributionByHouseType(houseTypeDistribution);
            hs.setTotalRegisteredCitizens(familySum);
            double avg = 0.0;
            if (familyCountEntries > 0) {
                avg = (double) familySum / (double) familyCountEntries;
            }
            hs.setAverageFamilySizePerHouse(avg);
            rest.setHouseStats(hs);
        }

        if (isVitalEvent || (!isHouse && entityType == null)) {
            VitalEventStats ves = new VitalEventStats();
            ves.setTotalVitalEvents(totalVitalEvents);
            ves.setBirthRecords(births);
            ves.setDeathRecords(deaths);
            ves.setMarriageRecords(marriages);
            rest.setVitalEventStats(ves);
        }

        return rest;
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAllCache() {}
}
