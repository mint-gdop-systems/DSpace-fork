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
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.PdfPageCountService;
import org.dspace.core.Context;
import org.dspace.utils.DSpace;

/**
 * Script to batch-process all PDF bitstreams, calculate page count,
 * and store the result in {@code crvs.document.pages} metadata.
 */
public class PdfPageCountScript extends DSpaceRunnable<PdfPageCountScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(PdfPageCountScript.class);
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int CACHE_LIMIT = 100;
    private static final String METADATA_SCHEMA = "crvs";
    private static final String METADATA_ELEMENT = "document";
    private static final String METADATA_QUALIFIER = "pages";

    private BitstreamService bitstreamService;
    private PdfPageCountService pdfPageCountService;
    private boolean verbose;
    private int batchSize;

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

        batchSize = DEFAULT_BATCH_SIZE;
        if (commandLine.hasOption('b')) {
            String rawValue = commandLine.getOptionValue('b');
            try {
                batchSize = Integer.parseInt(rawValue.trim());
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid batch size '" + rawValue + "': must be an integer");
            }
            if (batchSize <= 0) {
                throw new ParseException("Invalid batch size '" + rawValue + "': must be greater than 0");
            }
        }
    }

    @Override
    public void internalRun() throws Exception {
        Context context = new Context();
        int processed = 0;
        int errors = 0;

        try {
            context.turnOffAuthorisationSystem();

            for (int offset = 0;; offset += batchSize) {

                Iterator<Bitstream> batch = bitstreamService.findAll(
                        context, batchSize, offset, null, null, false);

                if (!batch.hasNext()) {
                    break;
                }

                while (batch.hasNext()) {
                    Bitstream bitstream = batch.next();

                    try {
                        long pages = pdfPageCountService.getNumberOfPdfPages(context, bitstream);

                        bitstreamService.setMetadataSingleValue(
                                context, bitstream,
                                METADATA_SCHEMA, METADATA_ELEMENT, METADATA_QUALIFIER,
                                null, String.valueOf(pages));

                        bitstreamService.update(context, bitstream);
                        processed++;

                        context.uncacheEntity(bitstream);

                        if (verbose) {
                            handler.logInfo("Bitstream " + bitstream.getID()
                                    + " -> " + pages + " pages");
                        }

                    } catch (Exception e) {
                        errors++;
                        context.uncacheEntity(bitstream);
                        log.error("Error processing bitstream {}: {}",
                                bitstream.getID(), e.getMessage());
                        handler.logError("Failed on bitstream " + bitstream.getID()
                                + ": " + e.getMessage());
                    }
                }

                // Commit after each batch to avoid memory build-up
                context.commit();

                if (processed % CACHE_LIMIT == 0) {
                    context.complete();
                    context = new Context();
                    context.turnOffAuthorisationSystem();
                }

                handler.logInfo("Committed batch at offset: " + offset + ". Processed so far: " + processed + "\n");
            }

        } catch (SQLException e) {
            log.error("Fatal SQL error during script execution", e);
            throw e;
        } finally {
            context.restoreAuthSystemState();
            context.complete();
        }

        handler.logInfo("Done. Processed: " + processed + ", Errors: " + errors);
    }
}