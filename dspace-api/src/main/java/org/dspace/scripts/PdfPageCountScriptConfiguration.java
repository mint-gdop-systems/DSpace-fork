/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * Configuration for the {@link PdfPageCountScript}.
 */
public class PdfPageCountScriptConfiguration<T extends PdfPageCountScript>
        extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        Options options = new Options();
        options.addOption("v", "verbose", false,
                "Print each bitstream ID and its page count to the log");
        options.addOption("b", "batch-size", true, "Number of bitstreams to process per batch (default: 1000)");
        return options;
    }
}