/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.dspace.app.util.service.DSpaceObjectUtils;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.discovery.SolrSearchCore;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.discovery.indexobject.IndexableWorkflowItem;
import org.dspace.services.ConfigurationService;
import org.dspace.statistics.service.RepositoryBitstreamStatsPdfExportService;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link RepositoryBitstreamStatsPdfExportService}.
 * <p>
 * Traverses the community tree (mirroring
 * {@code StructBuilder.exportStructure()})
 * and generates a landscape PDF with per-community sections and a grand total.
 */
@Component
public class RepositoryBitstreamStatsPdfExportServiceImpl implements RepositoryBitstreamStatsPdfExportService {

    // PDFBox 3.x requires font instances instead of static fields
    private static final PDType1Font FONT_HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_HELVETICA_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float MARGIN_LEFT = 36f;
    private static final float MARGIN_RIGHT = 36f;
    private static final float MARGIN_TOP = 36f;
    private static final float MARGIN_BOTTOM = 36f;

    private static final float BODY_FONT_SIZE = 9f;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float SECTION_FONT_SIZE = 12f;
    private static final float SUBSECTION_FONT_SIZE = 10f;
    private static final float HEADER_FONT_SIZE = 8f;

    private static final float ROW_HEIGHT = 14f;

    // Column headers for the table
    private static final String[] LEFT_COLUMN_HEADERS = { "Name", "Approved", "Pending" };
    private static final String[] GROUP_HEADERS = { "FILES", "PAGES" };
    private static final String[] SUB_HEADERS = { "Approved", "Draft", "Pending", "Total" };

    private final CommunityService communityService;
    private final BitstreamService bitstreamService;
    private final ConfigurationService configurationService;
    private final DSpaceObjectUtils dSpaceObjectUtils;
    private final SolrSearchCore solrSearchCore;

    RepositoryBitstreamStatsPdfExportServiceImpl(CommunityService communityService, BitstreamService bitstreamService,
            ConfigurationService configurationService, DSpaceObjectUtils dSpaceObjectUtils,
            SolrSearchCore solrSearchCore) {
        this.communityService = communityService;
        this.bitstreamService = bitstreamService;
        this.configurationService = configurationService;
        this.dSpaceObjectUtils = dSpaceObjectUtils;
        this.solrSearchCore = solrSearchCore;
    }

    @Override
    public List<CommunitySection> collectAllStats(Context context) throws SQLException {
        List<CommunitySection> sections = new ArrayList<>();
        List<Community> topCommunities = communityService.findAllTop(context);

        for (Community community : topCommunities) {
            collectCommunityStats(context, community, 0, sections);
        }

        return sections;
    }

    /**
     * Recursively collect stats for a community and its sub-communities.
     * Mirrors the traversal pattern in {@code StructBuilder.exportStructure()} /
     * {@code exportACommunity()}.
     */
    private void collectCommunityStats(Context context, Community community, int depth,
            List<CommunitySection> sections) throws SQLException {

        List<Object[]> rows = bitstreamService.findCommunityBitstreamStats(context, community.getID());

        if (!rows.isEmpty()) {
            List<StatsRow> collectionRows = aggregateRows(context, rows);

            sections.add(new CommunitySection(community.getName(), depth, collectionRows));
        }

        List<Community> subCommunities = community.getSubcommunities();
        for (Community subCommunity : subCommunities) {
            collectCommunityStats(context, subCommunity, depth + 1, sections);
        }
    }

    private List<StatsRow> aggregateRows(Context context, List<Object[]> rows)
            throws SQLException {
        Map<UUID, StatsRow> colMap = new LinkedHashMap<>();

        for (Object[] row : rows) {
            UUID colId = (UUID) row[0];
            String colName = (String) row[1];
            String status = (String) row[2];
            long bitstreamCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            long pageCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            StatsRow cs = colMap.computeIfAbsent(colId, k -> {
                return new StatsRow(colName);
            });

            if ("Approved".equals(status)) {
                cs.setApprovedBitstreams(bitstreamCount);
                cs.setApprovedPages(pageCount);
            } else if ("Draft".equals(status)) {
                cs.setDraftBitstreams(bitstreamCount);
                cs.setDraftPages(pageCount);
            } else if ("Pending".equals(status)) {
                cs.setPendingBitstreams(bitstreamCount);
                cs.setPendingPages(pageCount);
            }
        }

        for (Map.Entry<UUID, StatsRow> entry : colMap.entrySet()) {
            DSpaceObject dso = dSpaceObjectUtils.findDSpaceObject(context, entry.getKey());
            if (dso == null) {
                continue;
            }
            try {
                long approved = countApprovedItems(context, dso);
                long pending = countPendingItems(context, dso);
                entry.getValue().setApprovedItemCount(approved);
                entry.getValue().setPendingItemCount(pending);
            } catch (Exception e) {
                throw new SQLException("Discovery search failed for " + entry.getKey(), e);
            }
        }

        return new ArrayList<>(colMap.values());
    }

    private long countApprovedItems(Context context, DSpaceObject dso) {
        try {
            SolrClient solr = solrSearchCore.getSolr();
            SolrQuery query = new SolrQuery("*:*");

            String locationFilter;
            if (dso instanceof Collection) {
                locationFilter = "location.coll:" + dso.getID().toString();
            } else if (dso instanceof Community) {
                locationFilter = "location.comm:" + dso.getID().toString();
            } else {
                return 0;
            }

            query.addFilterQuery(locationFilter);
            query.addFilterQuery("search.resourcetype:" + IndexableItem.TYPE);
            query.addFilterQuery("NOT(discoverable:false)");
            query.addFilterQuery("withdrawn:false");
            query.addFilterQuery("archived:true");
            query.setRows(0);
            QueryResponse response = solr.query(query, solrSearchCore.REQUEST_METHOD);
            return response.getResults().getNumFound();
        } catch (Exception e) {
            return 0;
        }
    }

    private long countPendingItems(Context context, DSpaceObject dso) {
        try {
            SolrClient solr = solrSearchCore.getSolr();
            SolrQuery query = new SolrQuery("*:*");

            String locationFilter;
            if (dso instanceof Collection) {
                locationFilter = "location.coll:" + dso.getID().toString();
            } else if (dso instanceof Community) {
                locationFilter = "location.comm:" + dso.getID().toString();
            } else {
                return 0;
            }

            query.addFilterQuery(locationFilter);
            query.addFilterQuery("search.resourcetype:" + IndexableWorkflowItem.TYPE);
            query.setRows(0);
            QueryResponse response = solr.query(query, solrSearchCore.REQUEST_METHOD);
            return response.getResults().getNumFound();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public byte[] generatePdf(List<CommunitySection> sections) throws IOException {
        // Landscape A4: width=842, height=595
        PDRectangle landscape = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

        try (PDDocument document = new PDDocument()) {
            float usableWidth = landscape.getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            float[] colWidths = computeColumnWidths(usableWidth);

            PdfRenderer renderer = new PdfRenderer(document, landscape, colWidths, configurationService);

            // Cover page
            renderer.renderCoverPage();

            // Community sections
            for (CommunitySection section : sections) {
                renderer.renderSection(section);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Compute column widths. The "Name" column gets more space; numeric columns
     * share the rest equally.
     */
    private float[] computeColumnWidths(float usableWidth) {
        float nameWidth = usableWidth * 0.22f;
        float remainingWidth = usableWidth - nameWidth;
        float numericColWidth = remainingWidth / 10f;

        float[] widths = new float[11];
        widths[0] = nameWidth;
        for (int i = 1; i < 11; i++) {
            widths[i] = numericColWidth;
        }
        return widths;
    }

    private static final class CoverLine {
        final String text;
        final PDFont font;
        final float size;
        final float gapAfter;

        CoverLine(String text, PDFont font, float size, float gapAfter) {
            this.text = text;
            this.font = font;
            this.size = size;
            this.gapAfter = gapAfter;
        }
    }

    /**
     * Inner class that manages PDF page state and renders content.
     */
    private static class PdfRenderer {
        private final PDDocument document;
        private final PDRectangle landscape;
        private final float[] colWidths;
        private PDPage currentPage;
        private PDPageContentStream cs;
        private float currentY;
        private final ConfigurationService configurationService;

        private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

        private String fmt(long value) {
            return NUMBER_FORMAT.format(value);
        }

        PdfRenderer(PDDocument document, PDRectangle landscape, float[] colWidths,
                ConfigurationService configurationService) {
            this.document = document;
            this.landscape = landscape;
            this.colWidths = colWidths;
            this.configurationService = configurationService;

        }

        private void startNewPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            currentPage = new PDPage(landscape);
            document.addPage(currentPage);
            cs = new PDPageContentStream(document, currentPage);
            currentY = landscape.getHeight() - MARGIN_TOP;
        }

        private void ensureSpace(float needed) throws IOException {
            if (currentY - needed < MARGIN_BOTTOM) {
                startNewPage();
            }
        }

        void renderCoverPage()
                throws IOException {
            startNewPage();

            String orgName = configurationService.getProperty("dspace.name", "Repository");

            String dateStr = "Generated: " + java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Addis_Ababa"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            List<CoverLine> lines = new ArrayList<>();
            lines.add(new CoverLine(orgName, FONT_HELVETICA_BOLD, SUBSECTION_FONT_SIZE + 2, 24f));
            lines.add(new CoverLine("Repository Archive Statistics", FONT_HELVETICA_BOLD, TITLE_FONT_SIZE, 24f));
            lines.add(new CoverLine(dateStr, FONT_HELVETICA, SUBSECTION_FONT_SIZE, 6f));

            // Approximate line height as 1.2x font size (standard leading multiplier) and
            // sum
            // the whole block so it can be centered as a unit rather than centering each
            // line
            // independently against a fixed offset.
            float blockHeight = 0f;
            for (CoverLine line : lines) {
                blockHeight += line.size * 1.2f + line.gapAfter;
            }

            float y = (landscape.getHeight() + blockHeight) / 2f;

            for (CoverLine line : lines) {
                float textWidth = line.font.getStringWidth(line.text) / 1000 * line.size;
                float x = (landscape.getWidth() - textWidth) / 2;

                cs.beginText();
                cs.setFont(line.font, line.size);
                cs.newLineAtOffset(x, y);
                cs.showText(line.text);
                cs.endText();

                y -= (line.size * 1.2f + line.gapAfter);
            }

            cs.close();
            cs = null;
        }

        void renderSection(CommunitySection section) throws IOException {
            startNewPage();

            String heading = section.getCommunityName();

            cs.beginText();
            cs.setFont(FONT_HELVETICA_BOLD, SECTION_FONT_SIZE);
            cs.newLineAtOffset(MARGIN_LEFT, currentY);
            cs.showText(heading);
            cs.endText();
            currentY -= SECTION_FONT_SIZE + 6;

            renderTableHeader();

            List<StatsRow> rows = section.getCollectionRows();

            for (StatsRow row : rows) {
                ensureSpace(ROW_HEIGHT + 2);
                renderRow(row);
            }
            renderTotalRow(rows);

            cs.close();
            cs = null;
        }

        private void renderTableHeader() throws IOException {
            float headerHeight = ROW_HEIGHT * 2;
            ensureSpace(headerHeight + 4);

            float tableWidth = landscape.getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            float top = currentY;

            cs.setNonStrokingColor(220f / 255f, 220f / 255f, 220f / 255f);
            cs.addRect(MARGIN_LEFT, top - headerHeight, tableWidth, headerHeight);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);

            // Name / Approved(items) / Pending(items): span both header rows
            float x = MARGIN_LEFT;
            for (int i = 0; i < LEFT_COLUMN_HEADERS.length; i++) {
                drawCenteredHeaderBlock(LEFT_COLUMN_HEADERS[i].split("\n"), x, top, headerHeight);
                x += colWidths[i];
            }

            // FILES / PAGES group labels, each centered over their 4 columns, top row only
            float filesGroupWidth = colWidths[3] + colWidths[4] + colWidths[5] + colWidths[6];
            float pagesX = x + filesGroupWidth;
            float pagesGroupWidth = colWidths[7] + colWidths[8] + colWidths[9] + colWidths[10];
            drawGroupLabel(GROUP_HEADERS[0], x, filesGroupWidth, top);
            drawGroupLabel(GROUP_HEADERS[1], pagesX, pagesGroupWidth, top);

            // Approved/Draft/Pending/Total sub-headers, bottom row only, under each group
            float subX = x;
            for (int g = 0; g < 2; g++) {
                for (int i = 0; i < SUB_HEADERS.length; i++) {
                    int colIndex = 3 + g * 4 + i;
                    drawText(SUB_HEADERS[i], subX + 2, top - ROW_HEIGHT - HEADER_FONT_SIZE - 2,
                            FONT_HELVETICA_BOLD, HEADER_FONT_SIZE);
                    subX += colWidths[colIndex];
                }
            }

            cs.setStrokingColor(0f, 0f, 0f);
            cs.setLineWidth(0.5f);
            // divider between group row and sub-header row (only under Files/Pages)
            cs.moveTo(x, top - ROW_HEIGHT);
            cs.lineTo(landscape.getWidth() - MARGIN_RIGHT, top - ROW_HEIGHT);
            cs.stroke();
            // vertical dividers: left block | FILES | PAGES
            cs.moveTo(x, top);
            cs.lineTo(x, top - headerHeight);
            cs.stroke();
            cs.moveTo(pagesX, top);
            cs.lineTo(pagesX, top - headerHeight);
            cs.stroke();

            currentY = top - headerHeight;

            cs.moveTo(MARGIN_LEFT, currentY);
            cs.lineTo(landscape.getWidth() - MARGIN_RIGHT, currentY);
            cs.stroke();
            currentY -= 2;
        }

        private void drawText(String text, float x, float y, PDFont font, float size) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(text);
            cs.endText();
        }

        private void drawGroupLabel(String text, float groupX, float groupWidth, float top) throws IOException {
            float textWidth = FONT_HELVETICA_BOLD.getStringWidth(text) / 1000 * HEADER_FONT_SIZE;
            float x = groupX + (groupWidth - textWidth) / 2f;
            float y = top - (ROW_HEIGHT / 2f) - (HEADER_FONT_SIZE / 2f) + 2;
            drawText(text, x, y, FONT_HELVETICA_BOLD, HEADER_FONT_SIZE);
        }

        private void drawCenteredHeaderBlock(String[] lines, float colX, float top, float blockHeight)
                throws IOException {
            float lineHeight = HEADER_FONT_SIZE + 2;
            float totalTextHeight = lines.length * lineHeight;
            float startY = top - (blockHeight - totalTextHeight) / 2f - HEADER_FONT_SIZE;
            cs.beginText();
            cs.setFont(FONT_HELVETICA_BOLD, HEADER_FONT_SIZE);
            cs.newLineAtOffset(colX + 2, startY);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    cs.newLineAtOffset(0, -lineHeight);
                }
                cs.showText(lines[i]);
            }
            cs.endText();
        }

        private void renderRow(StatsRow row) throws IOException {
            ensureSpace(ROW_HEIGHT + 2);

            float x = MARGIN_LEFT;
            String[] values = {
                    truncate(row.getName(), colWidths[0]),
                    fmt(row.getApprovedItemCount()),
                    fmt(row.getPendingItemCount()),
                    fmt(row.getFilesApproved()),
                    fmt(row.getFilesDraft()),
                    fmt(row.getFilesPending()),
                    fmt(row.getFilesTotal()),
                    fmt(row.getPagesApproved()),
                    fmt(row.getPagesDraft()),
                    fmt(row.getPagesPending()),
                    fmt(row.getPagesTotal())
            };

            for (int i = 0; i < values.length; i++) {
                cs.beginText();
                cs.setFont(FONT_HELVETICA, BODY_FONT_SIZE);
                cs.newLineAtOffset(x + 2, currentY - BODY_FONT_SIZE);
                cs.showText(values[i]);
                cs.endText();
                x += colWidths[i];
            }

            currentY -= ROW_HEIGHT;

            cs.setStrokingColor(200f / 255f, 200f / 255f, 200f / 255f);
            cs.setLineWidth(0.25f);
            cs.moveTo(MARGIN_LEFT, currentY);
            cs.lineTo(landscape.getWidth() - MARGIN_RIGHT, currentY);
            cs.stroke();
            currentY -= 2;
        }

        private void renderTotalRow(List<StatsRow> rows) throws IOException {
            ensureSpace(ROW_HEIGHT + 4);

            long approvedItems = 0, pendingItems = 0;
            long filesApproved = 0, filesDraft = 0, filesPending = 0, filesTotal = 0;
            long pagesApproved = 0, pagesDraft = 0, pagesPending = 0, pagesTotal = 0;

            for (StatsRow row : rows) {
                approvedItems += row.getApprovedItemCount();
                pendingItems += row.getPendingItemCount();
                filesApproved += row.getFilesApproved();
                filesDraft += row.getFilesDraft();
                filesPending += row.getFilesPending();
                filesTotal += row.getFilesTotal();
                pagesApproved += row.getPagesApproved();
                pagesDraft += row.getPagesDraft();
                pagesPending += row.getPagesPending();
                pagesTotal += row.getPagesTotal();
            }

            cs.setNonStrokingColor(245f / 255f, 245f / 255f, 245f / 255f);
            cs.addRect(MARGIN_LEFT, currentY - ROW_HEIGHT, landscape.getWidth() - MARGIN_LEFT - MARGIN_RIGHT,
                    ROW_HEIGHT);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);

            float x = MARGIN_LEFT;
            String[] values = {
                    "Total", fmt(approvedItems), fmt(pendingItems),
                    fmt(filesApproved), fmt(filesDraft),
                    fmt(filesPending), fmt(filesTotal),
                    fmt(pagesApproved), fmt(pagesDraft),
                    fmt(pagesPending), fmt(pagesTotal)
            };

            for (int i = 0; i < values.length; i++) {
                drawText(values[i], x + 2, currentY - BODY_FONT_SIZE, FONT_HELVETICA_BOLD, BODY_FONT_SIZE);
                x += colWidths[i];
            }

            currentY -= ROW_HEIGHT;
            cs.setStrokingColor(0f, 0f, 0f);
            cs.setLineWidth(0.75f);
            cs.moveTo(MARGIN_LEFT, currentY);
            cs.lineTo(landscape.getWidth() - MARGIN_RIGHT, currentY);
            cs.stroke();
            currentY -= 2;
        }

        private String truncate(String text, float maxWidth) {
            if (text == null) {
                return "";
            }
            try {
                float textWidth = FONT_HELVETICA.getStringWidth(text) / 1000 * BODY_FONT_SIZE;
                if (textWidth <= maxWidth - 4) {
                    return text;
                }
                while (text.length() > 1) {
                    text = text.substring(0, text.length() - 1);
                    textWidth = FONT_HELVETICA.getStringWidth(text + "...") / 1000 * BODY_FONT_SIZE;
                    if (textWidth <= maxWidth - 4) {
                        return text + "...";
                    }
                }
            } catch (IOException e) {
                // Fall through — return truncated text without width check
            }
            return "...";
        }
    }
}
