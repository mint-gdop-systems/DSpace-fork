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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
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
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.PdfPageCountService;
import org.dspace.content.MetadataValue;
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
    public BitstreamStatisticsRest getStatistics(@Parameter(value = "submitter") String submitter,
            @Parameter(value = "date") String date) {
        Context context = obtainContext();

        EPerson submitterPerson = resolveSubmitter(context, submitter);
        LocalDate filterDate = parseDate(date);

        BitstreamStatistics statistics = new BitstreamStatistics();

        try {
            List<Bitstream> bitstreams = bitstreamService.findAll(context);
            for (Bitstream bitstream : bitstreams) {
                if (bitstream.isDeleted()) {
                    continue;
                }
                if (!matchesSubmitterFilter(context, bitstream, submitterPerson)) {
                    continue;
                }
                if (!matchesAccessionDate(bitstream, filterDate)) {
                    continue;
                }

                List<Item> liveItems = findLiveItems(bitstream);
                if (liveItems.isEmpty()) {
                    continue;
                }

                long pdfPages = countPages(context, bitstream);

                statistics.getTotals().setBitstreams(statistics.getTotals().getBitstreams() + 1);
                statistics.getTotals().setPdfPages(statistics.getTotals().getPdfPages() + pdfPages);

                Set<Item> uniqueItems = new LinkedHashSet<>(liveItems);
                for (Item item : uniqueItems) {
                    String entityType = StringUtils.defaultIfBlank(itemService.getEntityTypeLabel(item), "unknown");
                    incrementBreakdown(statistics.getEntityTypeBreakdown(), entityType, 1L, pdfPages);

                    String itemStatus = item.isWithdrawn() ? "withdrawn"
                            : item.isArchived() ? "archived" : "unarchived";
                    incrementBreakdown(statistics.getItemStatusBreakdown(), itemStatus, 1L, pdfPages);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
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

    private boolean matchesSubmitterFilter(Context context, Bitstream bitstream, EPerson submitterPerson) {
        if (submitterPerson == null) {
            return true;
        }

        try {
            for (Bundle bundle : bitstream.getBundles()) {
                for (Item item : bundle.getItems()) {
                    if (submitterPerson.equals(item.getSubmitter())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean matchesAccessionDate(Bitstream bitstream, LocalDate filterDate) {
        if (filterDate == null) {
            return true;
        }

        try {
            for (Bundle bundle : bitstream.getBundles()) {
                for (Item item : bundle.getItems()) {
                    if (itemMatchesAccessionDate(item, filterDate)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean itemMatchesAccessionDate(Item item, LocalDate filterDate) {
        List<MetadataValue> metadataValues = itemService.getMetadata(item, "dc", "date", "accessioned", Item.ANY);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue == null || StringUtils.isBlank(metadataValue.getValue())) {
                continue;
            }
            LocalDate valueDate = parseMetadataDate(metadataValue.getValue());
            if (filterDate.equals(valueDate)) {
                return true;
            }
        }
        return false;
    }

    private LocalDate parseMetadataDate(String value) {
        try {
            return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignored2) {
                try {
                    return LocalDateTime.parse(value).toLocalDate();
                } catch (DateTimeParseException e) {
                    throw new DSpaceBadRequestException("Invalid date format: " + value + ". Use ISO-8601.");
                }
            }
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
        try {
            String mimeType = bitstream.getFormat(context).getMIMEType();
            if ("application/pdf".equalsIgnoreCase(mimeType)) {
                return pdfPageCountService.getNumberOfPdfPages(context, bitstream);
            }
            if (isImageMimeType(mimeType)) {
                return 1L;
            }
            return 0L;
        } catch (IOException | SQLException | AuthorizeException e) {
            // For corrupted PDFs and other unreadable assets, count nothing.
            return 0L;
        }
    }

    private boolean isImageMimeType(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/")
                || "application/postscript".equalsIgnoreCase(mimeType));
    }

    private List<Item> findLiveItems(Bitstream bitstream) throws SQLException {
        List<Item> liveItems = new ArrayList<>();
        for (Bundle bundle : bitstream.getBundles()) {
            for (Item item : bundle.getItems()) {
                if (item != null && item.getID() != null) {
                    liveItems.add(item);
                }
            }
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
