package org.dspace.app.rest.repository;

import org.dspace.app.rest.model.AdminStatsRest;
import org.dspace.core.Context;
import org.dspace.core.AdminStatsHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(AdminStatsRest.CATEGORY + "." + AdminStatsRest.PLURAL_NAME)
public class AdminStatsRestRepository extends DSpaceRestRepository<AdminStatsRest, String> {

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminStatsRest findOne(Context context, String id) {
        if (!"all".equals(id)) {
            return null;
        }

        AdminStatsRest stats = new AdminStatsRest();
        stats.setId("all");

        try {
            int totalPageCount = AdminStatsHelper.getTotalPageCount(context);
            int totalWorkflowPageCount = AdminStatsHelper.getTotalWorkflowPageCount(context);
            stats.setTotalPageCount(totalPageCount + totalWorkflowPageCount);

            int totalWorkflowCount = AdminStatsHelper.getTotalWorkflowCount(context);
            stats.setTotalWorkflowCount(totalWorkflowCount);

            Map<String, Integer> collCounts = AdminStatsHelper.getCollectionPageCounts(context);
            Map<String, Integer> workflowCounts = AdminStatsHelper.getCollectionWorkflowCounts(context);
            Map<String, Integer> collWorkflowPageCounts = AdminStatsHelper.getCollectionWorkflowPageCounts(context);

            java.util.Set<String> allCollIds = new java.util.HashSet<>();
            allCollIds.addAll(collCounts.keySet());
            allCollIds.addAll(workflowCounts.keySet());
            allCollIds.addAll(collWorkflowPageCounts.keySet());

            List<Map<String, Object>> collectionsStats = new ArrayList<>();
            for (String collId : allCollIds) {
                Map<String, Object> statMap = new HashMap<>();
                statMap.put("collectionId", collId);
                int combinedPageCount = collCounts.getOrDefault(collId, 0) + collWorkflowPageCounts.getOrDefault(collId, 0);
                statMap.put("pageCount", combinedPageCount);
                statMap.put("workflowCount", workflowCounts.getOrDefault(collId, 0));
                collectionsStats.add(statMap);
            }
            stats.setCollectionsStats(collectionsStats);

        } catch (SQLException e) {
            throw new RuntimeException("Database error calculating admin stats", e);
        }

        return stats;
    }

    @Override
    public Page<AdminStatsRest> findAll(Context context, Pageable pageable) {
        return null; // Finding all is not supported
    }

    @Override
    public Class<AdminStatsRest> getDomainClass() {
        return AdminStatsRest.class;
    }
}
