/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 * Utility service to count pages in PDF bitstreams.
 */
public interface PdfPageCountService {

    long getNumberOfPdfPages(Context context, Bitstream bitstream)
            throws IOException, SQLException, AuthorizeException;
}
