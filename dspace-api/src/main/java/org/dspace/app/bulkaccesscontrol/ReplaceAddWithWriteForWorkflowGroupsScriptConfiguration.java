package org.dspace.app.bulkaccesscontrol;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

public class ReplaceAddWithWriteForWorkflowGroupsScriptConfiguration extends ScriptConfiguration {

    private Class dspaceRunnableClass;

    @Override
    public Class getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            // Add any command-line options here if needed
            // For now, we'll keep it empty since this script doesn't require parameters
            super.options = options;
        }
        return options;
    }

    @Override
    public String getName() {
        return "replace-add-with-write";
    }

    @Override
    public String getDescription() {
        return "Replace ADD permissions with WRITE permissions for workflow groups";
    }
}