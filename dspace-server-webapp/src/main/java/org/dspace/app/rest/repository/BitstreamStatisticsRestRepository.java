/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.BitstreamStatisticsRest;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.util.UUIDUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component("statistics.bitstreamstatistics")
public class BitstreamStatisticsRestRepository extends DSpaceRestRepository<BitstreamStatisticsRest, String> {

    private static final Logger log = LogManager.getLogger(BitstreamStatisticsRestRepository.class);

    private final BitstreamService bitstreamService;

    private final EPersonService epersonService;

    BitstreamStatisticsRestRepository(BitstreamService bitstreamService, EPersonService epersonService) {
        this.bitstreamService = bitstreamService;
        this.epersonService = epersonService;
    }

    @Override
    public BitstreamStatisticsRest findOne(Context context, String id) {
        throw new RepositoryMethodNotImplementedException("Not implemented", "findOne");
    }

    @Override
    public Page<BitstreamStatisticsRest> findAll(Context context, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "getStatistics")
    public BitstreamStatisticsRest getStatistics(
            @Parameter(value = "submitter") String submitter,
            @Parameter(value = "date") String date) {

        Context context = obtainContext();

        log.info("Starting bitstream statistics calculation. Submitter: {}, Date: {}",
                submitter, date);

        EPerson submitterPerson = resolveSubmitter(context, submitter);

        BitstreamStatisticsRest statisticsRest = new BitstreamStatisticsRest();

        try {
            long methodStartTime = System.currentTimeMillis();

            List<Object[]> bitstreamResults = bitstreamService.findCount(context, submitterPerson);

            for (Object[] result : bitstreamResults) {
                String breakdownType = (String) result[0];
                String category = (String) result[1];
                Long bitstreamCount = result[2] != null
                        ? ((Number) result[2]).longValue()
                        : 0L;
                Long pageCount = result[3] != null
                        ? ((Number) result[3]).longValue()
                        : 0L;

                log.info("Type: {}, Category: {}, Count: {}, Pages: {}",
                        breakdownType, category, bitstreamCount, pageCount);

                if ("TOTAL".equals(breakdownType)) {
                    statisticsRest.getTotals().setBitstreams(bitstreamCount);
                    statisticsRest.getTotals().setPdfPages(pageCount);
                } else if ("ENTITY_TYPE".equals(breakdownType)) {
                    BitstreamStatisticsRest.Breakdown breakdown = new BitstreamStatisticsRest.Breakdown();
                    breakdown.setBitstreams(bitstreamCount);
                    breakdown.setPdfPages(pageCount);
                    statisticsRest.getEntityTypeBreakdown().put(category, breakdown);
                } else if ("ITEM_STATUS".equals(breakdownType)) {
                    BitstreamStatisticsRest.Breakdown breakdown = new BitstreamStatisticsRest.Breakdown();
                    breakdown.setBitstreams(bitstreamCount);
                    breakdown.setPdfPages(pageCount);
                    statisticsRest.getItemStatusBreakdown().put(category, breakdown);
                }
            }

            log.info("Bitstream statistics calculation complete in {}ms",
                    System.currentTimeMillis() - methodStartTime);

        } catch (SQLException e) {
            log.error("SQLException during statistics calculation", e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return statisticsRest;
    }

    private EPerson resolveSubmitter(Context context, String submitter) {
        if (StringUtils.isBlank(submitter)) {
            return null;
        }

        try {
            UUID uuid = UUIDUtils.fromString(submitter);
            if (uuid != null) {
                return epersonService.find(context, uuid);
            }
            return epersonService.findByEmail(context, submitter);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Class<BitstreamStatisticsRest> getDomainClass() {
        return BitstreamStatisticsRest.class;
    }
}
