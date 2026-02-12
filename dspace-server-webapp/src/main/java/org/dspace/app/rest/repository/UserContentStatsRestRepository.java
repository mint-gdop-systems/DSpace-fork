package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dspace.app.rest.model.UserContentStatsRest;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.dspace.xmlworkflow.storedcomponents.service.ClaimedTaskService;
import org.dspace.xmlworkflow.storedcomponents.service.PoolTaskService;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.ClaimedTask;
import org.dspace.xmlworkflow.storedcomponents.PoolTask;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;

import org.dspace.statistics.ObjectCount;
import org.dspace.statistics.service.SolrLoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component(UserContentStatsRest.CATEGORY + "." + UserContentStatsRest.PLURAL_NAME)
public class UserContentStatsRestRepository extends DSpaceRestRepository<UserContentStatsRest, UUID> {

    @Autowired
    private ItemService itemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private XmlWorkflowItemService xmlWorkflowItemService;

    @Autowired
    private EPersonService ePersonService;

    @Autowired
    private org.dspace.authorize.service.AuthorizeService authorizeService;

    @Autowired
    private ClaimedTaskService claimedTaskService;

    @Autowired
    private PoolTaskService poolTaskService;

    @Autowired
    private SolrLoggerService solrLoggerService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public UserContentStatsRest findOne(Context context, UUID id) {
        UserContentStatsRest stats = new UserContentStatsRest();
        stats.setId(id.toString());

        try {
            UUID epersonUuid = id;
            EPerson eperson = ePersonService.find(context, epersonUuid);

            if (eperson == null) {
                return null;
            }

            // Check authorization: User can only see their own stats unless they are admin
            EPerson currentUser = context.getCurrentUser();
            if (currentUser == null) {
                throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
            }

            if (!currentUser.getID().equals(epersonUuid) && !authorizeService.isAdmin(context)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You are not authorized to view these statistics");
            }

            // ==========================================
            // 1. My Submissions (Grouped)
            // ==========================================
            UserContentStatsRest.MySubmissionStats mySubmission = stats.getMySubmission();

            // A. Workspace
            List<WorkspaceItem> wsItems = workspaceItemService.findByEPerson(context, eperson);
            int workspaceCount = wsItems.size();
            int rejectedCount = 0;

            for (WorkspaceItem wsi : wsItems) {
                List<MetadataValue> provenance = itemService.getMetadata(wsi.getItem(), "dc", "description",
                        "provenance", Item.ANY);
                for (MetadataValue mv : provenance) {
                    if (mv.getValue() != null && mv.getValue().contains("Rejected by")) {
                        rejectedCount++;
                        break;
                    }
                }
            }
            mySubmission.getWorkspace().setTotal(workspaceCount);
            mySubmission.getWorkspace().setRejected(rejectedCount);

            // B. Workflow
            int pageSize = Integer.MAX_VALUE;
            List<XmlWorkflowItem> wfItems = xmlWorkflowItemService.findBySubmitter(context, eperson, 0, pageSize);
            int workflowCount = wfItems.size();

            mySubmission.getWorkflow().setTotal(workflowCount);

            for (XmlWorkflowItem wfi : wfItems) {
                String stage = "unknown";
                List<ClaimedTask> claimedTasks = claimedTaskService.find(context, wfi);
                if (!claimedTasks.isEmpty()) {
                    stage = claimedTasks.get(0).getStepID();
                } else {
                    List<PoolTask> poolTasks = poolTaskService.find(context, wfi);
                    if (!poolTasks.isEmpty()) {
                        stage = poolTasks.get(0).getStepID();
                    }
                }
                mySubmission.getWorkflow().addCount(stage,
                        mySubmission.getWorkflow().getCounts().getOrDefault(stage, 0) + 1);
            }

            // C. Archived & Withdrawn
            int archivedCount = 0;
            int withdrawnCount = 0;

            java.util.Iterator<Item> itemIterator = itemService.findBySubmitter(context, eperson, true);
            while (itemIterator.hasNext()) {
                Item item = itemIterator.next();
                if (item.isArchived()) {
                    archivedCount++;
                } else if (item.isWithdrawn()) {
                    withdrawnCount++;
                }
            }
            mySubmission.setArchived(archivedCount);
            mySubmission.setWithdrawn(withdrawnCount);

            // ==========================================
            // 2. My Actions (Grouped by Step)
            // ==========================================
            // Use Discovery Search to find items with provenance containing user's email
            // Then parse provenance metadata to extract actions

            Map<String, Map<String, Integer>> myActions = stats.getMyActions();
            UUID userUuid = eperson.getID();
            String userEmail = eperson.getEmail();

            // Query Solr Statistics to find items the user has acted on
            // This is more reliable than Discovery if provenance is not indexed
            try {
                String query = "statistics_type:workflow AND actor:" + userUuid.toString();
                ObjectCount[] itemIds = solrLoggerService.queryFacetField(query, null, "id", 1000, false, null, 1);

                for (ObjectCount oc : itemIds) {
                    String itemUuidString = oc.getValue();
                    if (itemUuidString == null) {
                        continue;
                    }

                    UUID itemUuid;
                    try {
                        itemUuid = UUID.fromString(itemUuidString);
                    } catch (IllegalArgumentException e) {
                        continue; // Not a valid UUID
                    }

                    Item item = itemService.find(context, itemUuid);
                    if (item != null) {
                        List<MetadataValue> provList = itemService.getMetadata(item, "dc", "description", "provenance",
                                Item.ANY);

                        // Regex to parse: Step: [stepId] - action:[actionId] [Approved|Rejected] ... by
                        // [User] ...
                        // Example: Step: reviewStep - action:scoreReviewAction Approved for entry into
                        // archive by John Doe (john@doe.com) on ...
                        Pattern pattern = Pattern.compile(
                                "Step:\\s*([^\\s]+)\\s*-\\s*action:[^\\s]+\\s+(Approved|Rejected)[^\\n]*\\s+by\\s+(.+?)(?:,|\\son\\s)",
                                Pattern.CASE_INSENSITIVE);

                        // Track Latest Action Per Step for this item
                        Map<String, ActionInfo> latestActionPerStep = new HashMap<>();

                        for (MetadataValue mv : provList) {
                            String val = mv.getValue();
                            if (val != null) {
                                Matcher m = pattern.matcher(val);
                                if (m.find()) {
                                    String stepId = m.group(1);
                                    String outcome = m.group(2); // Approved or Rejected
                                    String actor = m.group(3); // The user string
                                    latestActionPerStep.put(stepId, new ActionInfo(outcome, actor));
                                }
                            }
                        }

                        // Aggregate if the latest action was by this user
                        for (Map.Entry<String, ActionInfo> entry : latestActionPerStep.entrySet()) {
                            String step = entry.getKey();
                            ActionInfo info = entry.getValue();

                            // Strict check for email matching to avoid substring overlaps (e.g., "editor"
                            // matching "finaleditor")
                            boolean isUserActor = false;
                            if (info.actor != null) {
                                if (info.actor.equals(userEmail)) {
                                    isUserActor = true;
                                } else if (info.actor.contains("(" + userEmail + ")")) {
                                    isUserActor = true;
                                }
                            }

                            if (isUserActor) {
                                myActions.putIfAbsent(step, new HashMap<>());
                                Map<String, Integer> stepActions = myActions.get(step);

                                String outcomeKey = info.outcome;
                                if (outcomeKey.equalsIgnoreCase("approved"))
                                    outcomeKey = "Approved";
                                if (outcomeKey.equalsIgnoreCase("rejected"))
                                    outcomeKey = "Rejected";

                                stepActions.put(outcomeKey, stepActions.getOrDefault(outcomeKey, 0) + 1);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                org.apache.logging.log4j.LogManager.getLogger(this.getClass())
                        .error("Error querying Solr Statistics for user actions", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return stats;
    }

    private static class ActionInfo {
        String outcome;
        String actor;

        public ActionInfo(String outcome, String actor) {
            this.outcome = outcome;
            this.actor = actor;
        }
    }

    @Override
    public Page<UserContentStatsRest> findAll(Context context, Pageable pageable) {
        return null;
    }

    @Override
    public Class<UserContentStatsRest> getDomainClass() {
        return UserContentStatsRest.class;
    }
}