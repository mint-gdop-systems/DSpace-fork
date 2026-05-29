/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.dspace.app.rest.model.BitstreamStatistics;
import org.dspace.app.rest.model.BitstreamStatisticsRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.stereotype.Component;

@Component
public class BitstreamStatisticsConverter implements DSpaceConverter<BitstreamStatistics, BitstreamStatisticsRest> {

    @Override
    public BitstreamStatisticsRest convert(BitstreamStatistics modelObject, Projection projection) {
        BitstreamStatisticsRest rest = new BitstreamStatisticsRest();
        rest.setProjection(projection);
        rest.setTotals(new BitstreamStatisticsRest.Totals(
                modelObject.getTotals().getBitstreams(),
                modelObject.getTotals().getPdfPages()));
        rest.setEntityTypeBreakdown(toRestBreakdown(modelObject.getEntityTypeBreakdown()));
        rest.setItemStatusBreakdown(toRestBreakdown(modelObject.getItemStatusBreakdown()));
        return rest;
    }

    private Map<String, BitstreamStatisticsRest.Breakdown> toRestBreakdown(
            Map<String, BitstreamStatistics.Breakdown> source) {
        Map<String, BitstreamStatisticsRest.Breakdown> target = new LinkedHashMap<>();
        for (Map.Entry<String, BitstreamStatistics.Breakdown> entry : source.entrySet()) {
            target.put(entry.getKey(), new BitstreamStatisticsRest.Breakdown(
                    entry.getValue().getBitstreams(), entry.getValue().getPdfPages()));
        }
        return target;
    }

    @Override
    public Class<BitstreamStatistics> getModelClass() {
        return BitstreamStatistics.class;
    }
}
