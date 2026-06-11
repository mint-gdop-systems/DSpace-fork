/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.BitstreamStatistics;
import org.dspace.authorize.AuthorizeException;
import org.dspace.app.rest.model.BitstreamStatisticsRest;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.PdfPageCountService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component("statistics.bitstreamstatistics")
public class BitstreamStatisticsRestRepository extends DSpaceRestRepository<BitstreamStatisticsRest, String> {

    private static final Logger log = LogManager.getLogger(BitstreamStatisticsRestRepository.class);

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private EPersonService epersonService;

    @Autowired
    private PdfPageCountService pdfPageCountService;

    @Autowired
    private ItemService itemService;

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

        long methodStartTime = System.currentTimeMillis();

        EPerson submitterPerson = resolveSubmitter(context, submitter);
        LocalDate filterDate = parseDate(date);

        BitstreamStatistics statistics = new BitstreamStatistics();

        // Metrics tracking
        long totalDbQueryTime = 0;
        long totalPdfCountTime = 0;
        long totalFindItemsTime = 0;

        int totalBatches = 0;
        int totalBitstreamsProcessed = 0;
        int skippedBitstreams = 0;
        int pdfCount = 0;
        int imageCount = 0;

        try {
            final int batchSize = 1000;

            for (int offset = 0;; offset += batchSize) {

                totalBatches++;

                long batchStartTime = System.currentTimeMillis();
                long batchPdfCountTime = 0;
                long batchFindItemsTime = 0;

                // Database query timing
                long dbQueryStart = System.currentTimeMillis();

                Iterator<Bitstream> bitstreamIterator = bitstreamService.findAll(
                        context,
                        batchSize,
                        offset,
                        submitterPerson,
                        filterDate);

                long dbQueryTime = System.currentTimeMillis() - dbQueryStart;
                totalDbQueryTime += dbQueryTime;

                if (!bitstreamIterator.hasNext()) {
                    log.debug(
                            "Batch {}: No more bitstreams found. DB query time: {}ms",
                            totalBatches,
                            dbQueryTime);
                    break;
                }

                int batchBitstreamCount = 0;

                while (bitstreamIterator.hasNext()) {

                    Bitstream bitstream = bitstreamIterator.next();

                    batchBitstreamCount++;
                    totalBitstreamsProcessed++;

                    // Find live items timing
                    long findItemsStart = System.currentTimeMillis();
                    List<Item> liveItems = findLiveItems(bitstream);
                    long findItemsTime = System.currentTimeMillis() - findItemsStart;
                    batchFindItemsTime += findItemsTime;
                    totalFindItemsTime += findItemsTime;

                    if (liveItems.isEmpty()) {
                        skippedBitstreams++;
                        continue;
                    }

                    // PDF counting timing
                    long pdfCountStart = System.currentTimeMillis();
                    long pdfPages = countPages(context, bitstream);
                    long pdfCountTime = System.currentTimeMillis() - pdfCountStart;
                    batchPdfCountTime += pdfCountTime;
                    totalPdfCountTime += pdfCountTime;

                    if (pdfPages > 0) {
                        String mimeType = bitstream.getFormat(context).getMIMEType();
                        if ("application/pdf".equalsIgnoreCase(mimeType)) {
                            pdfCount++;
                        } else {
                            imageCount++;
                        }
                    }

                    statistics.getTotals().setBitstreams(
                            statistics.getTotals().getBitstreams() + 1);
                    statistics.getTotals().setPdfPages(
                            statistics.getTotals().getPdfPages() + pdfPages);

                    Set<Item> uniqueItems = new LinkedHashSet<>(liveItems);

                    for (Item item : uniqueItems) {

                        String entityType = StringUtils.defaultIfBlank(
                                itemService.getEntityTypeLabel(item),
                                "unknown");

                        incrementBreakdown(
                                statistics.getEntityTypeBreakdown(),
                                entityType,
                                1L,
                                pdfPages);

                        String itemStatus = item.isWithdrawn()
                                ? "withdrawn"
                                : item.isArchived()
                                        ? "Approved"
                                        : "Pending";

                        incrementBreakdown(
                                statistics.getItemStatusBreakdown(),
                                itemStatus,
                                1L,
                                pdfPages);
                    }
                }

                long batchTime = System.currentTimeMillis() - batchStartTime;

                log.info(
                        "Batch {} complete: {} bitstreams processed in {}ms "
                                + "(DB query: {}ms, PDF counting: {}ms, Find items: {}ms)",
                        totalBatches,
                        batchBitstreamCount,
                        batchTime,
                        dbQueryTime,
                        batchPdfCountTime,
                        batchFindItemsTime);
            }

        } catch (SQLException e) {
            log.error("SQLException during statistics calculation", e);
            throw new RuntimeException(e.getMessage(), e);
        }

        long totalMethodTime = System.currentTimeMillis() - methodStartTime;

        double dbPercent = totalMethodTime > 0 ? (totalDbQueryTime * 100.0 / totalMethodTime) : 0;
        double pdfPercent = totalMethodTime > 0 ? (totalPdfCountTime * 100.0 / totalMethodTime) : 0;
        double findItemsPercent = totalMethodTime > 0 ? (totalFindItemsTime * 100.0 / totalMethodTime) : 0;

        log.info("Bitstream statistics calculation complete in {}ms", totalMethodTime);
        log.info(
                "Summary - Total batches: {}, Total bitstreams processed: {}, "
                        + "Skipped: {}, PDFs: {}, Images: {}",
                totalBatches, totalBitstreamsProcessed, skippedBitstreams, pdfCount, imageCount);
        log.info(
                "Timing breakdown - Total DB query time: {}ms ({}%), "
                        + "Total PDF count time: {}ms ({}%), "
                        + "Total find items time: {}ms ({}%)",
                totalDbQueryTime, String.format("%.1f", dbPercent),
                totalPdfCountTime, String.format("%.1f", pdfPercent),
                totalFindItemsTime, String.format("%.1f", findItemsPercent));

        if (totalBitstreamsProcessed > 0) {
            log.info(
                    "Average time per bitstream: {}ms",
                    String.format("%.2f", totalMethodTime * 1.0 / totalBitstreamsProcessed));
        }

        return converter.toRest(statistics, utils.obtainProjection());
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

    private LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new DSpaceBadRequestException("Invalid date format: " + value + ". Use ISO-8601.");
        }
    }

    private long countPages(Context context, Bitstream bitstream) {
        long startTime = System.currentTimeMillis();
        try {
            String mimeType = bitstream.getFormat(context).getMIMEType();

            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                // Read pre-computed page count from crvs.document.pages metadata
                List<MetadataValue> values = bitstreamService.getMetadata(
                        bitstream, "crvs", "document", "pages", Item.ANY);
                if (!values.isEmpty()) {
                    try {
                        long pages = Long.parseLong(values.get(0).getValue());
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration > 100) {
                            log.warn("Slow metadata read for bitstream {}: {}ms",
                                    bitstream.getID(), duration);
                        }
                        return pages;
                    } catch (NumberFormatException e) {
                        log.warn("Invalid crvs.document.pages value for bitstream {}: {}",
                                bitstream.getID(), values.get(0).getValue());
                        return 0L;
                    }
                }
                // No metadata yet — page count script hasn't run for this bitstream
                log.debug("No crvs.document.pages metadata for PDF bitstream {}",
                        bitstream.getID());
                return 0L;
            }

            if (isImageMimeType(mimeType)) {
                return 1L;
            }

            return 0L;

        } catch (SQLException e) {
            log.error("Error reading page count metadata for bitstream {}: {}",
                    bitstream.getID(), e.getMessage());
            return 0L;
        }
    }

    private boolean isImageMimeType(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/"));
    }

    private List<Item> findLiveItems(Bitstream bitstream) throws SQLException {
        long startTime = System.currentTimeMillis();
        List<Item> liveItems = new ArrayList<>();
        for (Bundle bundle : bitstream.getBundles()) {
            for (Item item : bundle.getItems()) {
                if (item != null && item.getID() != null) {
                    liveItems.add(item);
                }
            }
        }
        long duration = System.currentTimeMillis() - startTime;
        if (duration > 50) {
            log.debug("Slow findLiveItems for bitstream {}: {}ms, found {} items",
                    bitstream.getID(), duration, liveItems.size());
        }
        return liveItems;
    }

    private void incrementBreakdown(Map<String, BitstreamStatistics.Breakdown> breakdown,
            String key, long bitstreams, long pdfPages) {
        String normalizedKey = StringUtils.defaultIfBlank(key, "unknown");
        BitstreamStatistics.Breakdown entry = breakdown.get(normalizedKey);
        if (entry == null) {
            entry = new BitstreamStatistics.Breakdown();
            breakdown.put(normalizedKey, entry);
        }
        entry.setBitstreams(entry.getBitstreams() + bitstreams);
        entry.setPdfPages(entry.getPdfPages() + pdfPages);
    }

    @Override
    public Class<BitstreamStatisticsRest> getDomainClass() {
        return BitstreamStatisticsRest.class;
    }
}
