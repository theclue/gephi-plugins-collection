package org.gabrielebaldassarre.gephi.cycledetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.statistics.plugin.ChartUtils;
import org.gephi.statistics.spi.Statistics;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 *
 * @author Gabrio
 */
public class CyclesDetectionStatistics implements Statistics, LongTask {

    private String report = "";
    private boolean cancel = false;
    private ProgressTicket progressTicket;
    private GraphModel gm;

    private List<Node> marked;          // marked[v] = has vertex v been marked?
    private Map<Node, Node> edgeTo;     // edgeTo[v] = previous vertex on path to v
    private List<Node> onStack;         // onStack[v] = is vertex on the stack?
    private List<Stack<Node>> cycle;    // directed cycle (or null if no such cycle)

    private List<Node> neigh;

    @Override
    public void execute(GraphModel graphModel, AttributeModel attributeModel) {

        gm = graphModel;

        Graph graph = graphModel.getGraphVisible();

        marked = new ArrayList<Node>(graph.getNodeCount());
        onStack = new ArrayList<Node>(graph.getNodeCount());
        edgeTo = new HashMap<Node, Node>(graph.getNodeCount());

        cycle = new ArrayList<Stack<Node>>();

        graph.readLock();

        try {
            // Init the progress tick to the number of nodes to be visited
            Progress.start(progressTicket, graph.getNodeCount());
            Progress.setDisplayName(progressTicket, "Visiting nodes...");

            for (Node v : graph.getNodes()) {
                if (!marked.contains(v)) {
                    // Exit loop if cancel is pressed
                    if (cancel) break;
                    dfs(graph, v);
                }
            }
            graph.readUnlockAll();
        } catch (Exception e) {
            e.printStackTrace();
            //Unlock graph
            graph.readUnlockAll();
        }
        Progress.finish(progressTicket);
    }

    @Override
    public String getReport() {
        
        //distribution of values
        Map<Integer, Integer> dist = new HashMap<Integer, Integer>();
        for (int i = 0; i < cycle.size(); i++) {
            Integer d = cycle.get(i).size();
            if (dist.containsKey(d)) {
                Integer v = dist.get(d);
                dist.put(d, v + 1);
            } else {
                dist.put(d, 1);
            }
        }
        
        //Distribution series
        XYSeries dSeries = ChartUtils.createXYSeries(dist, "Cycle Size");
        
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(dSeries);

        JFreeChart chart = ChartFactory.createScatterPlot(
                "Cycle Size Distribution",
                "Cycle Size",
                "Count",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                false,
                false);
        chart.removeLegend();
        chart.getXYPlot().getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        chart.getXYPlot().getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        
        ChartUtils.decorateChart(chart);
        //ChartUtils.scaleChart(chart, dSeries, true);
        String imageFile = ChartUtils.renderChart(chart, "closedcycles.png");
        
        String report = "<HTML> <BODY> <h1>Cycles Detection </h1> "
                + "<hr> <br />"
                + "<h2> Parameters: </h2>"
                + "Graph Type: " + (gm.isDirected() ? "Directed" : gm.isUndirected() ? "Undirected" : "Mixed") + "<br>"
                + "<br />" + (hasCycle() ? "<h2> Cycles found: " + cycle.size() : "No cycles found") + "</h2>"
                + imageFile
                + "<br />" + "<h2> Algorithm: </h2>"
                + "Depth First Order - Robert Sedgewick and Kevin Wayne<br />"
                + "</BODY> </HTML>";

        return report;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
        this.progressTicket = progressTicket;
    }

    public String getResults() {
        return null;
    }

    private void dfs(Graph graph, Node node) {
        
        // A new node has been visited
        Progress.progress(progressTicket);
        marked.add(node);
        onStack.add(node);

        neigh = new ArrayList<Node>();
        
        // Break the recursion if cancel is pressed
        if (cancel) return;

        // For directed graphs, take only target nodes
        if (gm.isDirected()) {
            for (Edge e : graph.getEdges(node)) {
                if (e.getSource().equals(node) && !e.getSource().equals(e.getTarget())) {
                    neigh.add(e.getTarget());
                }
            }
        }

        for (Node w : (gm.isDirected() ? neigh : graph.getNeighbors(node))) {

            if (!marked.contains(w)) {
                edgeTo.put(w, node);
                dfs(graph, w);
            } // trace back directed cycle
            else if (onStack.contains(w)) {
                Stack<Node> oneCycle = new Stack<Node>();
                for (Node x = node; !x.equals(w); x = edgeTo.get(x)) {
                    oneCycle.push(x);
                }
                oneCycle.push(w);
                oneCycle.push(node);
                // Add to the list of cycles
                cycle.add(oneCycle);
            }
        }
        onStack.remove(node);
    }

    private boolean hasCycle() {
        return cycle != null;
    }
    /**
     * Return the index<em>th></em>  cycle
     * 
     * @param index get the cycle at the given index
     * @return the index-th cycle as an Iterable list of Nodes
     */
    public Iterable<Node> cycle(int index) {
        if(index >= cycle.size() || index < 0) throw new IndexOutOfBoundsException();
        return cycle.get(index);
    }

}
