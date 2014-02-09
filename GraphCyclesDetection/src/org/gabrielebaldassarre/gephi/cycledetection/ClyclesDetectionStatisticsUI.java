/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.gabrielebaldassarre.gephi.cycledetection;

import javax.swing.JPanel;
import org.gephi.statistics.spi.Statistics;
import org.gephi.statistics.spi.StatisticsUI;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Gabriele Baldassarre
 */
@ServiceProvider(service = StatisticsUI.class)
public class ClyclesDetectionStatisticsUI implements StatisticsUI {
    
    private int position = 1000;
    private String displayName = "Cycles Detection";
    private String shortDescription = "This statistics evaluate the distribution by size of closed cycles in the active graph";

    private CyclesDetectionStatistics sts;
    
    /*
     * No need of setting panes
     */
    @Override
    public JPanel getSettingsPanel() {
        return null;
    }

    @Override
    public void setup(Statistics ststcs) {
    this.sts = (CyclesDetectionStatistics)ststcs;
    }

    @Override
    public void unsetup() { }

    @Override
    public Class<? extends Statistics> getStatisticsClass() {
        return CyclesDetectionStatistics.class;
    }

    /**
     * TODO: define return value from the Statistics
     * @return 
     */
    @Override
    public String getValue() {
       return sts.getResults();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }

    @Override
    public String getCategory() {
        return StatisticsUI.CATEGORY_NETWORK_OVERVIEW;
    }

    @Override
    public int getPosition() {
        return position;
    }
    
}
