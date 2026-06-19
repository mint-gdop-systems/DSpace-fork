/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.PdfPageCountService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default implementation for PDF page counting.
 */
@Service
public class PdfPageCountServiceImpl implements PdfPageCountService {

    @Autowired
    private BitstreamService bitstreamService;

    @Override
    public long getNumberOfPdfPages(Context context, Bitstream bitstream)
            throws IOException, SQLException, AuthorizeException {
        try (InputStream inputStream = bitstreamService.retrieve(context, bitstream);
                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            return document.getNumberOfPages();
        }
    }
}