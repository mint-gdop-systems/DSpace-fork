/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.CommunityBitstreamStatsRest;
import org.dspace.app.rest.model.CommunityBitstreamStatsRest.CollectionStats;
import org.dspace.app.rest.model.CommunityBitstreamStatsRest.StatusBreakdown;
import org.dspace.app.rest.model.CommunityBitstreamStatsRest.StatusTotals;
import org.dspace.content.Community;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.statistics.service.RepositoryBitstreamStatsPdfExportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component(CommunityBitstreamStatsRest.CATEGORY + "." + CommunityBitstreamStatsRest.PLURAL_NAME)
public class CommunityBitstreamStatsRestRepository extends DSpaceRestRepository<CommunityBitstreamStatsRest, String> {

    private static final Logger log = LogManager.getLogger(CommunityBitstreamStatsRestRepository.class);

    private final CommunityService communityService;

    private final BitstreamService bitstreamService;

    private final RepositoryBitstreamStatsPdfExportService repositoryBitstreamStatsPdfExportService;

    CommunityBitstreamStatsRestRepository(BitstreamService bitstreamService, CommunityService communityService,
            RepositoryBitstreamStatsPdfExportService repositoryBitstreamStatsPdfExportService) {
        this.bitstreamService = bitstreamService;
        this.communityService = communityService;
        this.repositoryBitstreamStatsPdfExportService = repositoryBitstreamStatsPdfExportService;
    }

    @Override
    public CommunityBitstreamStatsRest findOne(Context context, String id) {
        throw new RepositoryMethodNotImplementedException("Not implemented", "findOne");
    }

    @Override
    public Page<CommunityBitstreamStatsRest> findAll(Context context, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "byCommunity")
    public CommunityBitstreamStatsRest byCommunity(
            @Parameter(value = "communityId", required = true) String communityId) {
        Context context = obtainContext();

        if (StringUtils.isBlank(communityId)) {
            throw new IllegalArgumentException("communityId parameter is required");
        }

        UUID commUuid = UUID.fromString(communityId);

        try {
            Community community = communityService.find(context, commUuid);
            if (community == null) {
                throw new IllegalArgumentException("Community not found: " + communityId);
            }

            List<Object[]> rows = bitstreamService.findCommunityBitstreamStats(context, commUuid);

            CommunityBitstreamStatsRest result = new CommunityBitstreamStatsRest();
            result.setCommunityId(commUuid);
            result.setCommunityName(community.getName());

            Map<UUID, CollectionStats> colMap = new LinkedHashMap<>();
            StatusTotals totals = new StatusTotals();

            for (Object[] row : rows) {
                UUID colId = (UUID) row[0];
                String colName = (String) row[1];
                String status = (String) row[2];
                long bitstreamCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                long pageCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;

                CollectionStats cs = colMap.get(colId);
                if (cs == null) {
                    cs = new CollectionStats();
                    cs.setCollectionId(colId);
                    cs.setCollectionName(colName);
                    colMap.put(colId, cs);
                }

                StatusBreakdown breakdown = new StatusBreakdown(bitstreamCount, pageCount);

                if ("Approved".equals(status)) {
                    cs.setApproved(breakdown);
                } else if ("Draft".equals(status)) {
                    cs.setDraft(breakdown);
                } else if ("Pending".equals(status)) {
                    cs.setPending(breakdown);
                }
            }

            for (CollectionStats cs : colMap.values()) {
                accumulateTotals(totals.getApproved(), cs.getApproved());
                accumulateTotals(totals.getDraft(), cs.getDraft());
                accumulateTotals(totals.getPending(), cs.getPending());
            }

            result.setCollections(new ArrayList<>(colMap.values()));
            result.setTotals(totals);

            return result;
        } catch (SQLException e) {
            log.error("SQLException during community bitstream stats calculation", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void accumulateTotals(StatusBreakdown total, StatusBreakdown increment) {
        if (increment != null) {
            total.setBitstreams(total.getBitstreams() + increment.getBitstreams());
            total.setPages(total.getPages() + increment.getPages());
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @SearchRestMethod(name = "exportAll")
    public void exportAll(
            @Parameter(value = "format", required = false) String format) throws IOException {

        if (StringUtils.isBlank(format)) {
            format = "pdf";
        }

        if (!"pdf".equalsIgnoreCase(format)) {
            HttpServletResponse response = obtainServletResponse();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Unsupported export format: " + format + ". Only 'pdf' is supported.");
            return;
        }

        Context context = obtainContext();
        try {
            List<RepositoryBitstreamStatsPdfExportService.CommunitySection> sections = repositoryBitstreamStatsPdfExportService
                    .collectAllStats(context);

            byte[] pdfBytes = repositoryBitstreamStatsPdfExportService.generatePdf(sections);

            HttpServletResponse response = obtainServletResponse();
            String filename = "repository-bitstream-stats-"
                    + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();

            try (OutputStream out = response.getOutputStream()) {
                out.write(pdfBytes);
                out.flush();
            }
        } catch (SQLException e) {
            log.error("SQLException during repository bitstream stats export", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private HttpServletResponse obtainServletResponse() {
        return requestService.getCurrentRequest().getHttpServletResponse();
    }

    @Override
    public Class<CommunityBitstreamStatsRest> getDomainClass() {
        return CommunityBitstreamStatsRest.class;
    }
}