/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight case-file duplicate checks for submission forms.
 */
@RestController
@RequestMapping("/api/dars/document")
public class CaseFileDuplicateRestController implements InitializingBean {

    private static final String FILE_NUMBER_FIELD = "dars.document.number";

    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;

    @Autowired
    private MetadataFieldService metadataFieldService;

    @Autowired
    private MetadataValueService metadataValueService;

    // @Autowired
    // private WorkspaceItemService workspaceItemService;

    @Autowired
    private XmlWorkflowItemService workflowItemService;

    @Autowired
    private ItemService itemService;

    @Override
    public void afterPropertiesSet() {
        discoverableEndpointsService.register(
                this, Arrays.asList(Link.of("/api/dars/document", "dars-document")));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/exists")
    public Map<String, Object> fileNumberExists(
            HttpServletRequest request,
            @RequestParam("fileNumber") String fileNumber) throws SQLException {

        String normalizedFileNumber = StringUtils.trimToEmpty(fileNumber);
        if (StringUtils.isBlank(normalizedFileNumber)) {
            throw new DSpaceBadRequestException("The fileNumber query parameter is required");
        }

        Context context = ContextUtil.obtainContext(request);
        MetadataField fileNumberField = metadataFieldService.findByString(context, FILE_NUMBER_FIELD, '.');
        if (fileNumberField == null) {
            throw new DSpaceBadRequestException("Metadata field is not registered: " + FILE_NUMBER_FIELD);
        }

        Iterator<MetadataValue> values = metadataValueService.findByFieldAndValue(
                context, fileNumberField, normalizedFileNumber);

        Map<String, Object> response = new HashMap<>();
        response.put("exists", false);

        while (values.hasNext()) {
            MetadataValue value = values.next();
            DSpaceObject dso = value.getDSpaceObject();
            if (dso == null || dso.getType() != Constants.ITEM) {
                continue;
            }

            Item item = (Item) org.hibernate.Hibernate.unproxy(dso);
            if (item == null) {
                continue;
            }
            if (item.isArchived()) {
                response.put("exists", true);
                return response;
            }

            // WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, item);
            // if (workspaceItem != null) {
            //     response.put("exists", true);
            //     return response;
            // }

            XmlWorkflowItem workflowItem = workflowItemService.findByItem(context, item);
            if (workflowItem != null) {
                response.put("exists", true);
                return response;
            }
        }

        return response;
    }
}
