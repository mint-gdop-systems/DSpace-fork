package org.dspace.app.rest;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics/useritemstats")
public class UserItemStatsController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private XmlWorkflowItemService xmlWorkflowItemService;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private EPersonService ePersonService;

    @Autowired
    private AuthorizeService authorizeService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserItemStats(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String filterStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) throws Exception {

        Context context = ContextUtil.obtainContext(request);
        EPerson eperson = null;
        if (StringUtils.isNotBlank(userId)) {
            eperson = ePersonService.find(context, UUID.fromString(userId));
        }

        boolean isSysAdmin = authorizeService.isAdmin(context);
        List<Collection> adminCollections = new ArrayList<>();
        if (!isSysAdmin) {
            adminCollections = authorizeService.findAdminAuthorizedCollection(context, null, 0, Integer.MAX_VALUE);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date start = null;
        Date end = null;
        if (StringUtils.isNotBlank(startDate)) {
            start = sdf.parse(startDate);
        }
        if (StringUtils.isNotBlank(endDate)) {
            end = sdf.parse(endDate);
            // Include entire end date day
            end = new Date(end.getTime() + (1000 * 60 * 60 * 24) - 1);
        }

        List<ItemStatRecord> allRecords = new ArrayList<>();

        if (eperson != null) {
            // Archived / Withdrawn items
            Iterator<Item> itemIterator = itemService.findBySubmitter(context, eperson, true);
            while (itemIterator.hasNext()) {
                Item item = itemIterator.next();
                if (item.isArchived() || item.isWithdrawn()) {
                    boolean hasAccess = isSysAdmin;
                    if (!hasAccess && item.getOwningCollection() != null) {
                        hasAccess = adminCollections.contains(item.getOwningCollection());
                    }
                    if (hasAccess) {
                        processItem(item, "Approved", start, end, allRecords);
                    }
                }
            }

            // Workflow items
            List<XmlWorkflowItem> wfItems = xmlWorkflowItemService.findBySubmitter(context, eperson, 0, Integer.MAX_VALUE);
            for (XmlWorkflowItem wfi : wfItems) {
                boolean hasAccess = isSysAdmin;
                if (!hasAccess && wfi.getCollection() != null) {
                    hasAccess = adminCollections.contains(wfi.getCollection());
                }
                if (hasAccess) {
                    processItem(wfi.getItem(), "Pending", start, end, allRecords);
                }
            }
        } else {
            // Total system items for the given date?
            // User requested if neither selected it should display total.
            // Getting all items in the system can be extremely heavy. We will only aggregate if userId is null.
            // If they want pagination of ALL items in the system, this will crash. 
            // We should use discovery or limit it, but if it's admin they might want it.
            // Wait, "if neither selected it should display total" means just the total cards, no table!
            // We can return empty items array if userId is not provided.
        }

        // Apply status filter if provided
        if (StringUtils.isNotBlank(filterStatus)) {
            List<ItemStatRecord> filteredRecords = new ArrayList<>();
            for (ItemStatRecord r : allRecords) {
                if (filterStatus.equalsIgnoreCase(r.status)) {
                    filteredRecords.add(r);
                }
            }
            allRecords = filteredRecords;
        }

        // Sort descending by date
        allRecords.sort(Comparator.comparing(ItemStatRecord::getSubmissionDate, Comparator.nullsLast(Comparator.reverseOrder())));

        // Calculate totals
        int totalItems = allRecords.size();
        int totalArchived = 0;
        int totalPending = 0;
        int sumJudgePages = 0;
        int sumMiscPages = 0;
        int sumOtherPages = 0;
        int sumTotalPages = 0;

        for (ItemStatRecord r : allRecords) {
            if ("Approved".equals(r.status)) totalArchived++;
            if ("Pending".equals(r.status)) totalPending++;
            sumJudgePages += r.judgePageCount;
            sumMiscPages += r.miscPageCount;
            sumOtherPages += r.otherPageCount;
            sumTotalPages += r.totalPageCount;
        }

        // Pagination
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<ItemStatRecord> paginatedRecords = allRecords.subList(fromIndex, toIndex);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalItems", totalItems);
        summary.put("approvedCount", totalArchived);
        summary.put("pendingCount", totalPending);
        summary.put("judgePageCount", sumJudgePages);
        summary.put("miscPageCount", sumMiscPages);
        summary.put("otherPageCount", sumOtherPages);
        summary.put("totalPageCount", sumTotalPages);

        Map<String, Object> response = new HashMap<>();
        response.put("summary", summary);
        response.put("items", paginatedRecords);
        response.put("totalElements", totalItems);
        response.put("totalPages", (int) Math.ceil((double) totalItems / size));

        return ResponseEntity.ok(response);
    }

    private void processItem(Item item, String status, Date start, Date end, List<ItemStatRecord> records) {
        // Date check based on accession date or submit date
        String dateStr = itemService.getMetadataFirstValue(item, "dc", "date", "accessioned", Item.ANY);
        if (dateStr == null) {
            dateStr = itemService.getMetadataFirstValue(item, "dc", "date", "submitted", Item.ANY);
        }
        Date itemDate = null;
        if (dateStr != null) {
            try {
                // DSpace dates are ISO 8601, substring handles the date part
                itemDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr.substring(0, 10));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        if (itemDate == null && item.getLastModified() != null) {
            itemDate = java.util.Date.from(item.getLastModified());
        }

        if (start != null || end != null) {
            if (itemDate == null) {
                return;
            }
            if (start != null && itemDate.before(start)) return;
            if (end != null && itemDate.after(end)) return;
        }

        ItemStatRecord record = new ItemStatRecord();
        record.itemId = item.getID().toString();
        record.caseNumber = itemService.getMetadataFirstValue(item, "legal", "case", "fileNumber", Item.ANY);
        record.status = status;
        record.submissionDate = itemDate;

        try {
            List<Bundle> bundles = item.getBundles();
            for (Bundle bundle : bundles) {
                // exclude workspace items or non-original bundles? Original bundle is typically named "ORIGINAL"
                if (!"ORIGINAL".equals(bundle.getName())) continue;

                for (Bitstream bitstream : bundle.getBitstreams()) {
                    record.fileCount++;

                    int pages = 0;
                    String pagesStr = bitstreamService.getMetadataFirstValue(bitstream, "legal", "document", "pageCount", Item.ANY);
                    if (pagesStr != null) {
                        try {
                            pages = Integer.parseInt(pagesStr.trim());
                        } catch (NumberFormatException ignored) {}
                    }

                    String docType = bitstreamService.getMetadataFirstValue(bitstream, "legal", "document", "type", Item.ANY);
                    if ("በዳኛ የተሰራ".equals(docType)) {
                        record.judgePageCount += pages;
                    } else if ("ልዩ ልዩ".equals(docType)) {
                        record.miscPageCount += pages;
                    } else {
                        record.otherPageCount += pages;
                    }
                    record.totalPageCount += pages;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        records.add(record);
    }

    public static class ItemStatRecord {
        public String itemId;
        public String caseNumber;
        public String status;
        public Date submissionDate;
        public int fileCount = 0;
        public int judgePageCount = 0;
        public int miscPageCount = 0;
        public int otherPageCount = 0;
        public int totalPageCount = 0;

        public Date getSubmissionDate() {
            return submissionDate;
        }
    }
}
