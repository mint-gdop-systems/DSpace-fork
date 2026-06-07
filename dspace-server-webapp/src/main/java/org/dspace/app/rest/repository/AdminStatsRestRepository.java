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
            stats.setTotalPageCount(totalPageCount);

            Map<String, Integer> collCounts = AdminStatsHelper.getCollectionPageCounts(context);
            List<Map<String, Object>> collectionsStats = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : collCounts.entrySet()) {
                Map<String, Object> statMap = new HashMap<>();
                statMap.put("collectionId", entry.getKey());
                statMap.put("pageCount", entry.getValue());
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
