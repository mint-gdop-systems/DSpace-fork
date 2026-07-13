package org.dspace.app.bulkaccesscontrol;

import org.dspace.core.Context;
import org.dspace.content.Collection;
import org.dspace.content.service.CollectionService;
import org.dspace.eperson.Group;
import org.apache.commons.cli.ParseException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Constants;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.content.factory.ContentServiceFactory;

import java.util.List;

public class ReplaceAddWithWriteForWorkflowGroups extends DSpaceRunnable {

    private CollectionService collectionService;
    private AuthorizeService authorizeService;

    @Override
    public void setup() throws ParseException {
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    }

    @Override
    public void internalRun() throws Exception {
        Context context = new Context();
        try {
            List<Collection> allCollections = collectionService.findAll(context);

            for (Collection collection : allCollections) {
                for (int step = 1; step <= 3; step++) {
                    Group workflowGroup = collectionService.getWorkflowGroup(context, collection, step);
                    if (workflowGroup != null) {
                        // Remove ALL policies for this workflow group on the collection
                        authorizeService.removeGroupPolicies(context, collection, workflowGroup);
                        // Add WRITE permission for this workflow group
                        authorizeService.addPolicy(context, collection, Constants.WRITE, workflowGroup);
                        handler.logInfo("Replaced ADD with WRITE for workflow group " + step +
                                " in collection: " + collection.getID());
                    }
                }

                Group submitters = collection.getSubmitters();
                if (submitters != null) {
                    // Remove ALL policies for submitters group on this collection
                    authorizeService.removeGroupPolicies(context, collection, submitters);
                    // Restore the correct ADD permission for submitters
                    authorizeService.addPolicy(context, collection, Constants.ADD, submitters);
                    handler.logInfo("Restored ADD permission for submitters in collection: " + collection.getID());
                }
            }
            context.complete();
        } catch (Exception e) {
            context.abort();
            throw e;
        }
    }

    @Override
    public ScriptConfiguration getScriptConfiguration() {
        return new ReplaceAddWithWriteForWorkflowGroupsScriptConfiguration();
    }
}