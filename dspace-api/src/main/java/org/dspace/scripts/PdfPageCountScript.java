/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import java.sql.SQLException;
import java.util.Iterator;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.PdfPageCountService;
import org.dspace.core.Context;
import org.dspace.utils.DSpace;
import org.dspace.content.Item;
import java.util.List;

/**
 * Script to batch-process all PDF bitstreams, calculate page count,
 * and store the result in {@code crvs.document.pages} metadata.
 */
public class PdfPageCountScript extends DSpaceRunnable<PdfPageCountScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(PdfPageCountScript.class);
    private static final int BATCH_SIZE = 100;
    private static final String METADATA_SCHEMA = "crvs";
    private static final String METADATA_ELEMENT = "document";
    private static final String METADATA_QUALIFIER = "pages";

    private BitstreamService bitstreamService;
    private PdfPageCountService pdfPageCountService;
    private boolean verbose;

    @Override
    public PdfPageCountScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager()
                .getServiceByName("pdf-page-count", PdfPageCountScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        verbose = commandLine.hasOption('v');
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        pdfPageCountService = ContentServiceFactory.getInstance().getPdfPageCountService();

        if (bitstreamService == null) {
            throw new IllegalStateException(
                    "bitstreamService could not be found. Check your Spring configuration.");
        }

        if (pdfPageCountService == null) {
            throw new IllegalStateException(
                    "PdfPageCountService could not be found. Check your Spring configuration.");
        }
    }

    @Override
    public void internalRun() throws Exception {
        Context context = new Context();
        int processed = 0;
        int errors = 0;
        int skipped = 0;

        try {
            context.turnOffAuthorisationSystem();

            for (int offset = 0;; offset += BATCH_SIZE) {

                Iterator<Bitstream> batch = bitstreamService.findAll(
                        context, BATCH_SIZE, offset, null, null);

                if (!batch.hasNext()) {
                    break;
                }

                while (batch.hasNext()) {
                    Bitstream bitstream = batch.next();

                    try {
                        String mime = bitstream.getFormat(context).getMIMEType();

                        if (!"application/pdf".equalsIgnoreCase(mime)) {
                            skipped++;
                            continue;
                        }

                        List<MetadataValue> existing = bitstreamService.getMetadata(
                                bitstream, METADATA_SCHEMA, METADATA_ELEMENT, METADATA_QUALIFIER, Item.ANY);
                        if (!existing.isEmpty()) {
                            skipped++;
                            continue;
                        }

                        long pages = pdfPageCountService.getNumberOfPdfPages(context, bitstream);

                        bitstreamService.setMetadataSingleValue(
                                context, bitstream,
                                METADATA_SCHEMA, METADATA_ELEMENT, METADATA_QUALIFIER,
                                null, String.valueOf(pages));

                        bitstreamService.update(context, bitstream);
                        processed++;

                        if (verbose) {
                            handler.logInfo("Bitstream " + bitstream.getID()
                                    + " -> " + pages + " pages");
                        }

                    } catch (Exception e) {
                        errors++;
                        log.error("Error processing bitstream {}: {}",
                                bitstream.getID(), e.getMessage());
                        handler.logError("Failed on bitstream " + bitstream.getID()
                                + ": " + e.getMessage());
                    }
                }

                // Flush writes to DB, then clear Hibernate session cache
                // This releases all loaded Bitstream/Item/Bundle objects from memory
                context.commit();
                context.uncacheEntities();
                log.info("Committed and cleared cache at offset {}. Processed so far: {}",
                        offset, processed);
            }

        } catch (SQLException e) {
            log.error("Fatal SQL error during script execution", e);
            throw e;
        } finally {
            context.restoreAuthSystemState();
            context.complete();
        }

        handler.logInfo("Done. Processed: " + processed
                + ", Skipped (non-PDF): " + skipped
                + ", Errors: " + errors);
    }
}