/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.audit;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.mail.MessagingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.audit.factory.AuditServiceFactory;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Class to store all received events in the audit system, if auditing is
 * enabled.
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 * @author Stefano Maffei (stefano.maffei at 4science.com)
 */

public class AuditConsumer implements Consumer {

    private static final Logger log = LogManager.getLogger(AuditConsumer.class);

    private AuditService auditService;
    private ConfigurationService configurationService;
    private List<Integer> meaningfulEvents;

    public void initialize() throws Exception {
        auditService = AuditServiceFactory.getInstance().getAuditService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        meaningfulEvents = List.of(Event.MODIFY_METADATA, Event.CREATE, Event.DELETE,
                Event.REMOVE);
    }

    /**
     * Consume a content event
     * 
     * @param ctx   DSpace context
     * @param event Content event
     */
    @Override
    public void consume(Context ctx, Event event) throws Exception {
        boolean enabled = configurationService.getBooleanProperty("audit.enabled", false);
        boolean meaningful = isEventMeaningful(event);

        if (enabled && meaningful) {
            auditService.store(ctx, event);

            if (event.getEventType() == Event.DELETE && configurationService.getBooleanProperty(
                    "send.deletion.email",
                    true)) {
                sendDeletionNotification(ctx, event);
            }
        }
    }

    /**
     * Sends a deletion notification email to the administrator.
     *
     * @param ctx   DSpace context
     * @param event Content event
     */
    private void sendDeletionNotification(Context ctx, Event event) {
        String adminEmail = configurationService.getProperty("mail.admin");
        if (adminEmail == null) {
            log.warn("Cannot send deletion notification: mail.admin property is missing.");
            return;
        }

        try {
            Email email = Email.getEmail(I18nUtil.getEmailFilename(ctx.getCurrentLocale(),
                    "deletion_notification_template"));
            email.addRecipient(adminEmail);

            // For DELETE events, the deleted object is usually the SUBJECT of the event.
            // objectID/Type might be null/Unknown in those cases.
            UUID id = event.getObjectID();
            if (id == null) {
                id = event.getSubjectID();
            }

            String type = event.getObjectTypeAsString();
            if ("(Unknown)".equals(type)) {
                type = event.getSubjectTypeAsString();
            }

            email.setSubject("DSpace Object Deletion: " + type + " - " + id);

            EPerson currentUser = ctx.getCurrentUser();
            String deleter = (currentUser != null) ? currentUser.getEmail() : "Anonymous";

            // Build metadata string
            StringBuilder metadataBlock = new StringBuilder();
            List<String> metadata = event.getMetadataValues();

            if (metadata != null && !metadata.isEmpty()) {
                for (String md : metadata) {
                    metadataBlock.append(md).append("\n");
                }
            } else {
                metadataBlock.append("No metadata available");
            }

            email.addArgument(type);
            email.addArgument(id != null ? id.toString() : "unknown");
            email.addArgument(deleter);
            email.addArgument(metadataBlock.toString());

            email.send();
            log.info("Deletion notification sent to {}", adminEmail);
        } catch (MessagingException | IOException e) {
            log.error("Failed to send deletion notification email", e);
        }
    }

    /**
     * Checks if the given event is meaningful for audit purposes.
     * An event is considered meaningful if its type is present in the
     * meaningfulEvents list,
     * or if it has a non-null related object ID.
     * Some events, may not be in the meaningfulEvents list, eighter because they
     * contain
     * duplicated information or because they are not relevant for auditing.
     *
     * @param event the event to check
     * @return true if the event is meaningful, false otherwise
     */
    private boolean isEventMeaningful(Event event) {
        if (meaningfulEvents.contains(event.getEventType())) {
            return true;
        }
        UUID relatedObjectId = event.getObjectID();
        return relatedObjectId != null;
    }

    @Override
    public void end(Context ctx) throws Exception {
        // no-op
    }

    @Override
    public void finish(Context ctx) throws Exception {
        // No-op
    }

}
