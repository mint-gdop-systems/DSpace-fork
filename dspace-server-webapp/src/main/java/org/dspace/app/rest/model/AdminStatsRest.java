package org.dspace.app.rest.model;

import org.dspace.app.rest.RestResourceController;
import java.util.List;
import java.util.Map;

/**
 * REST model for Admin Statistics.
 */
public class AdminStatsRest extends BaseObjectRest<String> {
    public static final String NAME = "adminstats";
    public static final String PLURAL_NAME = "adminstats";
    public static final String CATEGORY = RestModel.STATISTICS;

    private int totalPageCount;
    private List<Map<String, Object>> collectionsStats;

    public AdminStatsRest() {
    }

    public int getTotalPageCount() {
        return totalPageCount;
    }

    public void setTotalPageCount(int totalPageCount) {
        this.totalPageCount = totalPageCount;
    }

    public List<Map<String, Object>> getCollectionsStats() {
        return collectionsStats;
    }

    public void setCollectionsStats(List<Map<String, Object>> collectionsStats) {
        this.collectionsStats = collectionsStats;
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
