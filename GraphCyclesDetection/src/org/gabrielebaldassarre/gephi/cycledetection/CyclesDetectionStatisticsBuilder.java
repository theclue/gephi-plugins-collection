/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.gabrielebaldassarre.gephi.cycledetection;

import org.gephi.statistics.spi.Statistics;
import org.gephi.statistics.spi.StatisticsBuilder;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Gabriele Baldassarre
 */
@ServiceProvider(service = StatisticsBuilder.class)
public class CyclesDetectionStatisticsBuilder implements StatisticsBuilder {
    
    private final String name = "Cycles Detection";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Statistics getStatistics() {
        return new CyclesDetectionStatistics();
    }

    @Override
    public Class<? extends Statistics> getStatisticsClass() {
        return CyclesDetectionStatistics.class;
    }
    
}
