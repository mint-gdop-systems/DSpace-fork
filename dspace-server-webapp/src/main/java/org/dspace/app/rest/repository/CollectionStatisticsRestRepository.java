/**
 * The contents of this file are subject to the license and copyright detailed in the LICENSE and
 * NOTICE files at the root of the source tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.app.rest.model.CollectionStatsRest;
import org.dspace.content.Collection;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Repository exposing collection-level CRVS statistics for collections the current user has READ
 * access to.
 */
@Component(CollectionStatsRest.CATEGORY + "." + CollectionStatsRest.PLURAL_NAME)
public class CollectionStatisticsRestRepository
        extends DSpaceRestRepository<CollectionStatsRest, String> {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private CollectionStatisticsCacheService cacheService;

    @PreAuthorize("isAuthenticated()")
    @Override
    public Page<CollectionStatsRest> findAll(Context context, Pageable pageable) {
        try {
            List<Collection> collections =
                    collectionService.findAuthorizedOptimized(context, Constants.READ);

            int total = collections.size();
            int offset = Math.toIntExact(pageable.getOffset());
            int pageSize = pageable.getPageSize();

            int fromIndex = Math.min(offset, total);
            int toIndex = Math.min(offset + pageSize, total);
            List<Collection> pagedCollections = collections.subList(fromIndex, toIndex);

            List<CollectionStatsRest> results = new ArrayList<>();
            for (Collection col : pagedCollections) {
                results.add(cacheService.getCollectionStats(context, col));
            }

            return new PageImpl<>(results, pageable, total);
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
