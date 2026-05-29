/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.hateoas;

import org.dspace.app.rest.model.BitstreamStatisticsRest;
import org.dspace.app.rest.model.hateoas.annotations.RelNameDSpaceResource;

@RelNameDSpaceResource(BitstreamStatisticsRest.NAME)
public class BitstreamStatisticsResource extends HALResource<BitstreamStatisticsRest> {
    public BitstreamStatisticsResource(BitstreamStatisticsRest content) {
        super(content);
    }
}
