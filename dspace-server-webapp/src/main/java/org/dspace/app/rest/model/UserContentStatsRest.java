/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import org.dspace.app.rest.RestResourceController;

/**
 * REST model for user content statistics.
 */
public class UserContentStatsRest extends BaseObjectRest<String> {
    public static final String NAME = "usercontentstats";
    public static final String PLURAL_NAME = "usercontentstats";
    public static final String CATEGORY = RestModel.STATISTICS;

    private MySubmissionStats mySubmission;
    private java.util.Map<String, java.util.Map<String, Integer>> myActions;
    private java.util.Map<String, java.util.Map<String, Integer>> myActionsPageCounts;

    public UserContentStatsRest() {
        this.mySubmission = new MySubmissionStats();
        this.myActions = new java.util.HashMap<>();
        this.myActionsPageCounts = new java.util.HashMap<>();
    }

    public MySubmissionStats getMySubmission() {
        return mySubmission;
    }

    public void setMySubmission(MySubmissionStats mySubmission) {
        this.mySubmission = mySubmission;
    }

    public java.util.Map<String, java.util.Map<String, Integer>> getMyActions() {
        return myActions;
    }

    public void setMyActions(java.util.Map<String, java.util.Map<String, Integer>> myActions) {
        this.myActions = myActions;
    }

    public java.util.Map<String, java.util.Map<String, Integer>> getMyActionsPageCounts() {
        return myActionsPageCounts;
    }

    public void setMyActionsPageCounts(java.util.Map<String, java.util.Map<String, Integer>> myActionsPageCounts) {
        this.myActionsPageCounts = myActionsPageCounts;
    }

    public static class MySubmissionStats {
        private WorkspaceStats workspace;
        private WorkflowStats workflow;
        private int archived;
        private int withdrawn;
        private int pageCount;

        public MySubmissionStats() {
            this.workspace = new WorkspaceStats();
            this.workflow = new WorkflowStats();
        }

        public WorkspaceStats getWorkspace() {
            return workspace;
        }

        public void setWorkspace(WorkspaceStats workspace) {
            this.workspace = workspace;
        }

        public WorkflowStats getWorkflow() {
            return workflow;
        }

        public void setWorkflow(WorkflowStats workflow) {
            this.workflow = workflow;
        }

        public int getArchived() {
            return archived;
        }

        public void setArchived(int archived) {
            this.archived = archived;
        }

        public int getWithdrawn() {
            return withdrawn;
        }

        public void setWithdrawn(int withdrawn) {
            this.withdrawn = withdrawn;
        }

        public int getPageCount() {
            return pageCount;
        }

        public void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }
    }

    public static class WorkspaceStats {
        private int total;
        private int rejected;

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getRejected() {
            return rejected;
        }

        public void setRejected(int rejected) {
            this.rejected = rejected;
        }
    }

    public static class WorkflowStats {
        private java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        public int getTotal() {
            return counts.getOrDefault("total", 0);
        }

        public void setTotal(int total) {
            counts.put("total", total);
        }

        @com.fasterxml.jackson.annotation.JsonAnyGetter
        public java.util.Map<String, Integer> getCounts() {
            return counts;
        }

        public void addCount(String stage, int count) {
            counts.put(stage, count);
        }
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class<RestResourceController> getController() {
        return RestResourceController.class;
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getTypePlural() {
        return PLURAL_NAME;
    }
}