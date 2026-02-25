package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dspace.app.rest.model.CollectionStatsRest;
import org.dspace.app.rest.model.CollectionStatsRest.HouseStats;
import org.dspace.app.rest.model.CollectionStatsRest.VitalEventStats;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.EntityType;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Repository exposing collection-level CRVS statistics for collections the
 * current
 * user has READ access to.
 */
@Component(CollectionStatsRest.CATEGORY + "." + CollectionStatsRest.PLURAL_NAME)
public class CollectionStatisticsRestRepository extends DSpaceRestRepository<CollectionStatsRest, String> {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ItemService itemService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public Page<CollectionStatsRest> findAll(Context context, Pageable pageable) {
        try {
            List<Collection> collections = collectionService.findAuthorizedOptimized(context, Constants.READ);

            List<CollectionStatsRest> results = new ArrayList<>();

            for (Collection col : collections) {
                CollectionStatsRest rest = new CollectionStatsRest();
                rest.setCollectionId(col.getID().toString());
                rest.setCollectionName(col.getName());
                rest.setEntityType(collectionService.getMetadataFirstValue(col, "dspace", "entity", "type", Item.ANY));

                // House aggregation helpers
                int houseTotal = 0;
                Map<String, Integer> houseTypeDistribution = new HashMap<>();
                long familySum = 0L;
                int familyCountEntries = 0;

                // VitalEvent aggregation helpers
                int totalVitalEvents = 0;
                int births = 0;
                int deaths = 0;
                int marriages = 0;

                Iterator<Item> items = itemService.findAllByCollection(context, col);
                while (items.hasNext()) {
                    Item item = items.next();

                    String etLabel = itemService.getEntityTypeLabel(item);
                    if (etLabel == null) {
                        EntityType et = itemService.getEntityType(context, item);
                        etLabel = et != null ? et.getLabel() : "";
                    }

                    String etLower = etLabel != null ? etLabel.toLowerCase() : "";
                    if (etLower.contains("house")) {
                        houseTotal++;

                        // Distribution by House Type (crvs.identifier.houseType)
                        List<MetadataValue> ht = itemService.getMetadata(item, "crvs", "identifier", "houseType",
                                Item.ANY, true);
                        if (ht != null && !ht.isEmpty() && ht.get(0).getValue() != null) {
                            String htv = ht.get(0).getValue();
                            houseTypeDistribution.put(htv, houseTypeDistribution.getOrDefault(htv, 0) + 1);
                        }

                        // Family size and total citizens (crvs.family.count)
                        List<MetadataValue> fam = itemService.getMetadata(item, "crvs", "family", "count",
                                Item.ANY, true);
                        if (fam != null && !fam.isEmpty() && fam.get(0).getValue() != null) {
                            String fv = fam.get(0).getValue();
                            try {
                                long val = Long.parseLong(fv.trim());
                                familySum += val;
                                familyCountEntries++;
                            } catch (NumberFormatException e) {

                                // ignore unparsable values
                            }
                        }
                    } else if (etLower.contains("vitalevent") || etLower.contains("vital")
                            || etLower.contains("event")) {

                        totalVitalEvents++;

                        // Determine sub-type by looking for specific CRVS metadata elements
                        boolean foundType = false;
                        if (!itemService.getMetadata(item, "crvs", "birth", Item.ANY, Item.ANY, true).isEmpty()) {
                            births++;
                            foundType = true;
                        } else if (!itemService.getMetadata(item, "crvs", "death", Item.ANY, Item.ANY, true)
                                .isEmpty()) {
                            deaths++;
                            foundType = true;
                        } else if (!itemService.getMetadata(item, "crvs", "marriage", Item.ANY, Item.ANY, true)
                                .isEmpty()
                                || !itemService.getMetadata(item, "crvs", "divorce", Item.ANY, Item.ANY, true)
                                        .isEmpty()) {
                            marriages++;
                            foundType = true;
                        }

                        if (!foundType) {
                            // Fallback to searching type metadata if specific elements are missing
                            String eventType = null;
                            String[][] candidates = {
                                    { "crvs", "event", "type" },
                                    { "crvs", "type", null },
                                    { "crvs", "identifier", "eventType" },
                                    { "dc", "type", null }
                            };

                            for (String[] c : candidates) {
                                List<MetadataValue> ev = itemService.getMetadata(item, c[0], c[1], c[2], Item.ANY,
                                        true);
                                if (ev != null && !ev.isEmpty() && ev.get(0).getValue() != null) {
                                    eventType = ev.get(0).getValue().toLowerCase();
                                    break;
                                }
                            }

                            if (eventType != null) {
                                if (eventType.contains("birth")) {
                                    births++;
                                } else if (eventType.contains("death") || eventType.contains("died")) {
                                    deaths++;
                                } else if (eventType.contains("marriage") || eventType.contains("married")) {
                                    marriages++;
                                }
                            }
                        }
                    } else {
                        // Optional: Log labels that don't match for debugging
                        // item: " + item.getID());
                    }
                }

                // Populate final DTOs based on entity type
                String entityType = rest.getEntityType();

                if (entityType != null && entityType.toLowerCase().contains("house")) {
                    HouseStats hs = new HouseStats();
                    hs.setTotalRegisteredHouses(houseTotal);
                    hs.setDistributionByHouseType(houseTypeDistribution);
                    hs.setTotalRegisteredCitizens(familySum);
                    double avg = 0.0;
                    if (familyCountEntries > 0) {
                        // Using familyCountEntries as denominator for a more accurate average of known
                        // data
                        avg = (double) familySum / (double) familyCountEntries;
                    }
                    hs.setAverageFamilySizePerHouse(avg);
                    rest.setHouseStats(hs);
                } else if (entityType != null && (entityType.toLowerCase().contains("vitalevent")
                        || entityType.toLowerCase().contains("vital") || entityType.toLowerCase().contains("event"))) {
                    VitalEventStats ves = new VitalEventStats();
                    ves.setTotalVitalEvents(totalVitalEvents);
                    ves.setBirthRecords(births);
                    ves.setDeathRecords(deaths);
                    ves.setMarriageRecords(marriages);
                    rest.setVitalEventStats(ves);
                } else {
                    // If no matching entity type, we include both to maintain backwards
                    // compatibility or show zeros
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

                    VitalEventStats ves = new VitalEventStats();
                    ves.setTotalVitalEvents(totalVitalEvents);
                    ves.setBirthRecords(births);
                    ves.setDeathRecords(deaths);
                    ves.setMarriageRecords(marriages);
                    rest.setVitalEventStats(ves);
                }

                results.add(rest);
            }

            int total = results.size();
            int offset = Math.toIntExact(pageable.getOffset());
            int pageSize = pageable.getPageSize();

            // Simple pagination in-memory
            int fromIndex = Math.min(offset, total);
            int toIndex = Math.min(offset + pageSize, total);
            List<CollectionStatsRest> pageList = results.subList(fromIndex, toIndex);

            return new PageImpl<>(pageList, pageable, total);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public CollectionStatsRest findOne(Context context, String id) {
        return null;
    }

    @Override
    public Class<CollectionStatsRest> getDomainClass() {
        return CollectionStatsRest.class;
    }
}
