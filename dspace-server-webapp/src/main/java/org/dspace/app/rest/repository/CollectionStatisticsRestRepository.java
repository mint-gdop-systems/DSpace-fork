package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dspace.app.rest.model.CollectionStatsRest;
import org.dspace.app.rest.model.CollectionStatsRest.CaseFileStats;
import org.dspace.app.rest.model.CollectionStatsRest.CirculationEventStats;
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

                // CaseFile aggregation helpers
                int caseFileTotal = 0;
                Map<String, Integer> caseTypeDistribution = new HashMap<>();
                Map<String, Integer> caseStatusDistribution = new HashMap<>();

                // CirculationEvent aggregation helpers
                int circulationEventTotal = 0;
                Map<String, Integer> eventStatusDistribution = new HashMap<>();

                Iterator<Item> items = itemService.findAllByCollection(context, col);
                while (items.hasNext()) {
                    Item item = items.next();

                    String etLabel = itemService.getEntityTypeLabel(item);
                    if (etLabel == null) {
                        EntityType et = itemService.getEntityType(context, item);
                        etLabel = et != null ? et.getLabel() : "";
                    }

                    String etLower = etLabel != null ? etLabel.toLowerCase() : "";
                    if (etLower.contains("casefile") || etLower.contains("case")) {
                        caseFileTotal++;

                        // Distribution by Case Type (legal.case.type)
                        List<MetadataValue> ct = itemService.getMetadata(item, "legal", "case", "type",
                                Item.ANY, true);
                        if (ct != null && !ct.isEmpty() && ct.get(0).getValue() != null) {
                            String ctv = ct.get(0).getValue();
                            caseTypeDistribution.put(ctv, caseTypeDistribution.getOrDefault(ctv, 0) + 1);
                        }

                        // Distribution by Case Status (legal.case.status)
                        List<MetadataValue> cs = itemService.getMetadata(item, "legal", "case", "status",
                                Item.ANY, true);
                        if (cs != null && !cs.isEmpty() && cs.get(0).getValue() != null) {
                            String csv = cs.get(0).getValue();
                            caseStatusDistribution.put(csv, caseStatusDistribution.getOrDefault(csv, 0) + 1);
                        }
                    } else if (etLower.contains("circulationevent") || etLower.contains("circulation")) {
                        circulationEventTotal++;

                        // Distribution by Event Status (legal.event.status)
                        List<MetadataValue> es = itemService.getMetadata(item, "legal", "event", "status",
                                Item.ANY, true);
                        if (es != null && !es.isEmpty() && es.get(0).getValue() != null) {
                            String esv = es.get(0).getValue();
                            eventStatusDistribution.put(esv, eventStatusDistribution.getOrDefault(esv, 0) + 1);
                        }
                    } else {
                        // Optional: Log labels that don't match for debugging
                        // item: " + item.getID());
                    }
                }

                // Populate final DTOs based on entity type
                String entityType = rest.getEntityType();

                if (entityType != null && (entityType.toLowerCase().contains("casefile") || entityType.toLowerCase().contains("case"))) {
                    CaseFileStats cfs = new CaseFileStats();
                    cfs.setTotalRegisteredCaseFiles(caseFileTotal);
                    cfs.setDistributionByCaseType(caseTypeDistribution);
                    cfs.setDistributionByCaseStatus(caseStatusDistribution);
                    rest.setCaseFileStats(cfs);
                } else if (entityType != null && (entityType.toLowerCase().contains("circulationevent") || entityType.toLowerCase().contains("circulation"))) {
                    CirculationEventStats ces = new CirculationEventStats();
                    ces.setTotalCirculationEvents(circulationEventTotal);
                    ces.setDistributionByEventStatus(eventStatusDistribution);
                    rest.setCirculationEventStats(ces);
                } else {
                    // If no matching entity type, we include both to maintain backwards
                    // compatibility or show zeros
                    CaseFileStats cfs = new CaseFileStats();
                    cfs.setTotalRegisteredCaseFiles(caseFileTotal);
                    cfs.setDistributionByCaseType(caseTypeDistribution);
                    cfs.setDistributionByCaseStatus(caseStatusDistribution);
                    rest.setCaseFileStats(cfs);

                    CirculationEventStats ces = new CirculationEventStats();
                    ces.setTotalCirculationEvents(circulationEventTotal);
                    ces.setDistributionByEventStatus(eventStatusDistribution);
                    rest.setCirculationEventStats(ces);
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
