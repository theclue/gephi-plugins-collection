package org.gabrielebaldassarre.gephi.cycledetection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.EdgeIterable;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeIterable;
import org.gephi.statistics.spi.Statistics;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;

/**
 * Counts DFS back-edge witnesses. These are not necessarily all simple cycles.
 *
 * @author Gabrio
 */
public class CyclesDetectionStatistics implements Statistics, LongTask {

    private final Object lifecycleLock = new Object();
    private volatile boolean cancelRequested;
    private volatile Snapshot snapshot = Snapshot.empty(Status.NOT_RUN, "Unknown");
    private ProgressTicket progressTicket;

    @Override
    public void execute(GraphModel graphModel) {
        synchronized (lifecycleLock) {
            if (snapshot.status == Status.RUNNING) {
                throw new IllegalStateException("Cycle detection is already running");
            }
            cancelRequested = false;
            snapshot = Snapshot.empty(Status.RUNNING, "Unknown");
        }

        try {
            Snapshot result = compute(graphModel);
            synchronized (lifecycleLock) {
                snapshot = cancelRequested
                        ? Snapshot.empty(Status.CANCELLED, result.graphType)
                        : result;
            }
        } catch (CancelledRun ex) {
            synchronized (lifecycleLock) {
                snapshot = Snapshot.empty(Status.CANCELLED, ex.graphType);
            }
        } catch (UnsupportedGraph ex) {
            synchronized (lifecycleLock) {
                snapshot = Snapshot.empty(cancelRequested ? Status.CANCELLED : Status.UNSUPPORTED,
                        ex.graphType);
            }
        } catch (RuntimeException | Error ex) {
            synchronized (lifecycleLock) {
                snapshot = Snapshot.empty(Status.FAILED, "Unknown");
            }
            throw ex;
        }
    }

    private Snapshot compute(GraphModel graphModel) {
        checkCancelled();
        Graph graph = graphModel.getGraphVisible();
        graph.readLock();
        boolean progressStarted = false;
        try {
            boolean directed = graphModel.isDirected();
            boolean undirected = graphModel.isUndirected();
            String graphType = directed == undirected
                    ? "Mixed/unsupported"
                    : directed ? "Directed" : "Undirected";
            if (directed == undirected) {
                throw new UnsupportedGraph(graphType);
            }

            int nodeCount = graph.getNodeCount();
            Progress.start(progressTicket, nodeCount);
            progressStarted = true;
            Progress.setDisplayName(progressTicket, "Visiting nodes...");

            List<Node> nodes = new ArrayList<Node>(nodeCount);
            NodeIterable nodeIterable = graph.getNodes();
            Iterator<Node> nodeIterator = nodeIterable.iterator();
            boolean nodesExhausted = false;
            try {
                while (nodeIterator.hasNext()) {
                    checkCancelled(graphType);
                    nodes.add(nodeIterator.next());
                }
                nodesExhausted = true;
            } finally {
                if (!nodesExhausted) {
                    nodeIterable.doBreak();
                }
            }

            IdentityHashMap<Node, Boolean> discovered = new IdentityHashMap<Node, Boolean>(nodeCount);
            IdentityHashMap<Node, Boolean> active = new IdentityHashMap<Node, Boolean>(nodeCount);
            IdentityHashMap<Node, Boolean> finished = new IdentityHashMap<Node, Boolean>(nodeCount);
            IdentityHashMap<Node, Node> parent = new IdentityHashMap<Node, Node>(nodeCount);
            IdentityHashMap<Node, Edge> parentEdge = new IdentityHashMap<Node, Edge>(nodeCount);
            IdentityHashMap<Node, Integer> depth = new IdentityHashMap<Node, Integer>(nodeCount);
            IdentityHashMap<Edge, Boolean> closureEdges = new IdentityHashMap<Edge, Boolean>();
            List<CycleDescriptor> cycles = new ArrayList<CycleDescriptor>();
            Map<Integer, Long> distribution = new HashMap<Integer, Long>();

            Deque<Frame> frames = new ArrayDeque<Frame>();
            try {
                for (Node root : nodes) {
                    checkCancelled(graphType);
                    if (discovered.containsKey(root)) {
                        continue;
                    }
                    discovered.put(root, Boolean.TRUE);
                    active.put(root, Boolean.TRUE);
                    depth.put(root, 0);
                    Progress.progress(progressTicket);
                    checkCancelled(graphType);

                    EdgeIterable rootEdges = graph.getEdges(root);
                    Frame rootFrame = new Frame(root, rootEdges);
                    frames.push(rootFrame);
                    rootFrame.edges = rootEdges.iterator();
                    while (!frames.isEmpty()) {
                        checkCancelled(graphType);
                        Frame frame = frames.peek();
                        if (!frame.edges.hasNext()) {
                            frame.exhausted = true;
                            frames.pop();
                            active.remove(frame.node);
                            finished.put(frame.node, Boolean.TRUE);
                            continue;
                        }

                        Edge edge = frame.edges.next();
                        checkCancelled(graphType);
                        if (edge.isDirected() != directed) {
                            throw new UnsupportedGraph(graphType);
                        }

                        Node neighbor;
                        if (directed) {
                            if (edge.getSource() != frame.node) {
                                continue;
                            }
                            neighbor = edge.getTarget();
                        } else if (edge.getSource() == frame.node) {
                            neighbor = edge.getTarget();
                        } else if (edge.getTarget() == frame.node) {
                            neighbor = edge.getSource();
                        } else {
                            throw new UnsupportedGraph(graphType);
                        }

                        if (!discovered.containsKey(neighbor)) {
                            discovered.put(neighbor, Boolean.TRUE);
                            active.put(neighbor, Boolean.TRUE);
                            parent.put(neighbor, frame.node);
                            parentEdge.put(neighbor, edge);
                            depth.put(neighbor, depth.get(frame.node) + 1);
                            Progress.progress(progressTicket);
                            checkCancelled(graphType);

                            EdgeIterable neighborEdges = graph.getEdges(neighbor);
                            Frame neighborFrame = new Frame(neighbor, neighborEdges);
                            frames.push(neighborFrame);
                            neighborFrame.edges = neighborEdges.iterator();
                        } else if (active.containsKey(neighbor)) {
                            if (!directed && parentEdge.get(frame.node) == edge) {
                                continue;
                            }
                            if (closureEdges.put(edge, Boolean.TRUE) != null) {
                                continue;
                            }
                            int length = depth.get(frame.node) - depth.get(neighbor) + 1;
                            cycles.add(new CycleDescriptor(frame.node, neighbor, length));
                            Long count = distribution.get(length);
                            distribution.put(length, count == null ? 1L : count + 1L);
                        } else if (finished.containsKey(neighbor)) {
                            continue;
                        }
                    }
                }

                checkCancelled(graphType);
                return Snapshot.completed(graphType, cycles, parent, distribution);
            } finally {
                for (Frame frame : frames) {
                    if (!frame.exhausted) {
                        frame.iterable.doBreak();
                    }
                }
            }
        } finally {
            try {
                if (progressStarted) {
                    Progress.finish(progressTicket);
                }
            } finally {
                graph.readUnlock();
            }
        }
    }

    private void checkCancelled() {
        if (cancelRequested) {
            throw new CancelledRun("Unknown");
        }
    }

    private void checkCancelled(String graphType) {
        if (cancelRequested) {
            throw new CancelledRun(graphType);
        }
    }

    @Override
    public String getReport() {
        Snapshot current = snapshot;
        StringBuilder report = new StringBuilder("<html><body><h1>Cycles Detection</h1>");
        report.append("<p>Graph type: ").append(current.graphType).append("</p>");
        report.append("<p>").append(summary(current)).append("</p>");
        if (current.status == Status.COMPLETED && !current.distribution.isEmpty()) {
            report.append("<table><thead><tr><th>Witness length</th><th>Count</th></tr></thead><tbody>");
            for (Map.Entry<Integer, Long> entry : current.distribution.entrySet()) {
                report.append("<tr><td>").append(entry.getKey()).append("</td><td>")
                        .append(entry.getValue()).append("</td></tr>");
            }
            report.append("</tbody></table>");
        }
        report.append("<p>Counts DFS back-edge witnesses; it does not enumerate all simple cycles or cliques.</p>")
                .append("</body></html>");
        return report.toString();
    }

    @Override
    public boolean cancel() {
        synchronized (lifecycleLock) {
            if (snapshot.status != Status.RUNNING) {
                return false;
            }
            cancelRequested = true;
            return true;
        }
    }

    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
        this.progressTicket = progressTicket;
    }

    public String getResults() {
        return summary(snapshot);
    }

    public long getCycleCount() {
        return snapshot.cycles.size();
    }

    public Map<Integer, Long> getCycleLengthDistribution() {
        return snapshot.distribution;
    }

    /**
     * Returns one witness as nodes in edge order, with the closing node repeated.
     * Parallel edges may therefore have identical node sequences.
     *
     * @param index witness index
     * @return an immutable node sequence
     */
    public Iterable<Node> cycle(int index) {
        Snapshot current = snapshot;
        if (index < 0 || index >= current.cycles.size()) {
            throw new IndexOutOfBoundsException();
        }
        CycleDescriptor cycle = current.cycles.get(index);
        List<Node> nodes = new ArrayList<Node>(cycle.length + 1);
        Node node = cycle.from;
        while (node != cycle.target) {
            nodes.add(node);
            node = current.parent.get(node);
            if (node == null) {
                throw new IllegalStateException("Incomplete DFS predecessor chain");
            }
        }
        nodes.add(cycle.target);
        Collections.reverse(nodes);
        nodes.add(cycle.target);
        return Collections.unmodifiableList(nodes);
    }

    private static String summary(Snapshot snapshot) {
        switch (snapshot.status) {
            case NOT_RUN:
                return "Not run";
            case RUNNING:
                return "Running";
            case COMPLETED:
                return snapshot.cycles.isEmpty()
                        ? "No cycles found"
                        : "Cycle witnesses: " + snapshot.cycles.size();
            case CANCELLED:
                return "Cancelled; partial results discarded";
            case UNSUPPORTED:
                return "Unsupported graph type";
            case FAILED:
                return "Analysis failed";
            default:
                throw new AssertionError(snapshot.status);
        }
    }

    private enum Status {
        NOT_RUN, RUNNING, COMPLETED, CANCELLED, UNSUPPORTED, FAILED
    }

    private static final class Frame {

        private final Node node;
        private final EdgeIterable iterable;
        private Iterator<Edge> edges;
        private boolean exhausted;

        private Frame(Node node, EdgeIterable iterable) {
            this.node = node;
            this.iterable = iterable;
        }
    }

    private static final class CycleDescriptor {

        private final Node from;
        private final Node target;
        private final int length;

        private CycleDescriptor(Node from, Node target, int length) {
            this.from = from;
            this.target = target;
            this.length = length;
        }
    }

    private static final class Snapshot {

        private final Status status;
        private final String graphType;
        private final List<CycleDescriptor> cycles;
        private final Map<Node, Node> parent;
        private final Map<Integer, Long> distribution;

        private Snapshot(Status status, String graphType, List<CycleDescriptor> cycles,
                Map<Node, Node> parent, Map<Integer, Long> distribution) {
            this.status = status;
            this.graphType = graphType;
            this.cycles = cycles;
            this.parent = parent;
            this.distribution = distribution;
        }

        private static Snapshot empty(Status status, String graphType) {
            return new Snapshot(status, graphType, Collections.<CycleDescriptor>emptyList(),
                    Collections.<Node, Node>emptyMap(), Collections.<Integer, Long>emptyMap());
        }

        private static Snapshot completed(String graphType, List<CycleDescriptor> cycles,
                IdentityHashMap<Node, Node> parent, Map<Integer, Long> distribution) {
            IdentityHashMap<Node, Node> parentCopy = new IdentityHashMap<Node, Node>(parent);
            TreeMap<Integer, Long> distributionCopy = new TreeMap<Integer, Long>(distribution);
            return new Snapshot(Status.COMPLETED, graphType,
                    Collections.unmodifiableList(new ArrayList<CycleDescriptor>(cycles)),
                    Collections.unmodifiableMap(parentCopy),
                    Collections.unmodifiableMap(distributionCopy));
        }
    }

    private static final class CancelledRun extends RuntimeException {

        private final String graphType;

        private CancelledRun(String graphType) {
            this.graphType = graphType;
        }
    }

    private static final class UnsupportedGraph extends RuntimeException {

        private final String graphType;

        private UnsupportedGraph(String graphType) {
            this.graphType = graphType;
        }
    }
}
