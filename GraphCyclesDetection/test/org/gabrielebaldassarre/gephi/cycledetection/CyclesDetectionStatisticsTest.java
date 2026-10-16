package org.gabrielebaldassarre.gephi.cycledetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.gephi.graph.api.Configuration;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.statistics.spi.Statistics;
import org.gephi.utils.progress.ProgressTicket;
import org.junit.jupiter.api.Test;

class CyclesDetectionStatisticsTest {

    @Test
    void startsSafeAndUiReadsOnlyTheSuppliedStatistics() {
        CyclesDetectionStatistics statistics = new CyclesDetectionStatistics();
        ClyclesDetectionStatisticsUI ui = new ClyclesDetectionStatisticsUI();

        assertEquals(0, statistics.getCycleCount());
        assertTrue(statistics.getCycleLengthDistribution().isEmpty());
        assertNotNull(statistics.getResults());
        assertNotNull(statistics.getReport());
        assertEquals("Not run", statistics.getResults());
        assertEquals("", ui.getValue());
        assertEquals("Not run", ui.getValue(statistics));
        assertEquals("", ui.getValue(new Statistics() {
            @Override
            public void execute(GraphModel graphModel) {
            }

            @Override
            public String getReport() {
                return "";
            }
        }));

        GraphModel graph = graph(true);
        Node a = node(graph, "a");
        Node b = node(graph, "b");
        edge(graph, a, b, true);
        edge(graph, b, a, true);
        statistics.execute(graph);
        assertEquals(1, statistics.getCycleCount());
        assertEquals("Cycle witnesses: 1", ui.getValue(statistics));
        assertEquals("Cycle witnesses: 1", statistics.getResults());

        statistics.execute(graph(true));
        assertEquals("No cycles found", statistics.getResults());
        assertEquals(0, statistics.getCycleCount());
        assertTrue(statistics.getCycleLengthDistribution().isEmpty());
    }

    @Test
    void directedOracleCountsBackEdgeWitnessesAndExposesClosedImmutablePaths() {
        GraphModel empty = graph(true);
        CyclesDetectionStatistics emptyResult = run(empty);
        assertEquals(0, emptyResult.getCycleCount());

        GraphModel dag = graph(true);
        Node a = node(dag, "a");
        Node b = node(dag, "b");
        Node c = node(dag, "c");
        edge(dag, a, b, true);
        edge(dag, a, c, true);
        edge(dag, b, c, true);
        assertEquals(0, run(dag).getCycleCount());

        GraphModel selfLoop = graph(true);
        Node loop = node(selfLoop, "loop");
        edge(selfLoop, loop, loop, true);
        CyclesDetectionStatistics loopResult = run(selfLoop);
        assertEquals(1, loopResult.getCycleCount());
        assertEquals(Map.of(1, 1L), loopResult.getCycleLengthDistribution());
        assertEquals(Arrays.asList(loop, loop), asList(loopResult.cycle(0)));

        GraphModel parallelLoops = graph(true);
        Node same = node(parallelLoops, "same");
        edge(parallelLoops, same, same, true);
        edge(parallelLoops, same, same, true);
        assertEquals(Map.of(1, 2L), run(parallelLoops).getCycleLengthDistribution());

        GraphModel twoCycle = graph(true);
        Node first = node(twoCycle, "first");
        Node second = node(twoCycle, "second");
        edge(twoCycle, first, second, true);
        edge(twoCycle, second, first, true);
        CyclesDetectionStatistics twoCycleResult = run(twoCycle);
        assertEquals(1, twoCycleResult.getCycleCount());
        assertEquals(Map.of(2, 1L), twoCycleResult.getCycleLengthDistribution());
        List<Node> path = asList(twoCycleResult.cycle(0));
        assertEquals(3, path.size());
        assertSameNode(path.get(0), path.get(2));
        assertEquals(2, twoCycleResult.getCycleLengthDistribution().keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class,
                () -> twoCycleResult.getCycleLengthDistribution().put(8, 1L));
        assertThrows(UnsupportedOperationException.class, () -> twoCycleResult.cycle(0).iterator().remove());
        assertThrows(IndexOutOfBoundsException.class, () -> twoCycleResult.cycle(-1));

        GraphModel triangle = graph(true);
        Node x = node(triangle, "x");
        Node y = node(triangle, "y");
        Node z = node(triangle, "z");
        edge(triangle, x, y, true);
        edge(triangle, y, z, true);
        edge(triangle, z, x, true);
        CyclesDetectionStatistics triangleResult = run(triangle);
        assertEquals(1, triangleResult.getCycleCount());
        assertEquals(Map.of(3, 1L), triangleResult.getCycleLengthDistribution());
        assertTrue(triangleResult.getReport().indexOf("<td>3</td>")
                < triangleResult.getReport().indexOf("<td>1</td>"));
    }

    @Test
    void undirectedOracleSkipsOnlyTheExactParentEdgeAndKeepsParallelIdentity() {
        GraphModel oneEdge = graph(false);
        Node a = node(oneEdge, "a");
        Node b = node(oneEdge, "b");
        edge(oneEdge, a, b, false);
        assertEquals(0, run(oneEdge).getCycleCount());

        GraphModel parallelTwo = graph(false);
        Node p = node(parallelTwo, "p");
        Node q = node(parallelTwo, "q");
        edge(parallelTwo, p, q, false);
        edge(parallelTwo, p, q, false);
        CyclesDetectionStatistics two = run(parallelTwo);
        assertEquals(1, two.getCycleCount());
        assertEquals(Map.of(2, 1L), two.getCycleLengthDistribution());

        GraphModel parallelThree = graph(false);
        Node u = node(parallelThree, "u");
        Node v = node(parallelThree, "v");
        Edge first = edge(parallelThree, u, v, false);
        edge(parallelThree, u, v, false);
        edge(parallelThree, u, v, false);
        CyclesDetectionStatistics three = run(parallelThree);
        assertEquals(2, three.getCycleCount());
        assertEquals(Map.of(2, 2L), three.getCycleLengthDistribution());
        assertEquals(asList(three.cycle(0)), asList(three.cycle(1)));
        assertTrue(parallelThree.getGraph().contains(first));

        GraphModel noReturn = graph(true);
        Node from = node(noReturn, "from");
        Node to = node(noReturn, "to");
        edge(noReturn, from, to, true);
        edge(noReturn, from, to, true);
        assertEquals(0, run(noReturn).getCycleCount());

        GraphModel triangleAndLoop = graph(false);
        Node a1 = node(triangleAndLoop, "a");
        Node b1 = node(triangleAndLoop, "b");
        Node c1 = node(triangleAndLoop, "c");
        Node d1 = node(triangleAndLoop, "d");
        edge(triangleAndLoop, a1, b1, false);
        edge(triangleAndLoop, b1, c1, false);
        edge(triangleAndLoop, c1, a1, false);
        edge(triangleAndLoop, d1, d1, false);
        CyclesDetectionStatistics result = run(triangleAndLoop);
        assertEquals(2, result.getCycleCount());
        assertEquals(Map.of(1, 1L, 3, 1L), result.getCycleLengthDistribution());
    }

    @Test
    void analyzesVisibleGraphWithoutMutatingUnderlyingGraphAndHandlesUnsupportedGraphs() {
        GraphModel graph = graph(true);
        Node a = node(graph, "a");
        Node b = node(graph, "b");
        Node c = node(graph, "c");
        Edge ab = edge(graph, a, b, true);
        Edge bc = edge(graph, b, c, true);
        Edge ca = edge(graph, c, a, true);
        Graph all = graph.getGraph();
        int nodeCount = all.getNodeCount();
        int edgeCount = all.getEdgeCount();
        var cutView = graph.createView(n -> true, e -> e != ca);
        graph.setVisibleView(cutView);
        CyclesDetectionStatistics visibleResult = run(graph);
        assertEquals(0, visibleResult.getCycleCount());
        assertTrue(all.contains(ab));
        assertTrue(all.contains(bc));
        assertTrue(all.contains(ca));
        assertEquals(nodeCount, all.getNodeCount());
        assertEquals(edgeCount, all.getEdgeCount());

        GraphModel mixed = graph(true);
        Node m = node(mixed, "m");
        Node n = node(mixed, "n");
        Node r = node(mixed, "r");
        Node s = node(mixed, "s");
        edge(mixed, m, n, true);
        edge(mixed, r, s, false);
        CyclesDetectionStatistics unsupported = run(mixed);
        assertEquals(0, unsupported.getCycleCount());
        assertEquals("Unsupported graph type", unsupported.getResults());
        assertTrue(unsupported.getReport().contains("Unsupported graph type"));

        CyclesDetectionStatistics failed = new CyclesDetectionStatistics();
        assertThrows(NullPointerException.class, () -> failed.execute(null));
        assertEquals("Analysis failed", failed.getResults());
        assertEquals(0, failed.getCycleCount());

        graph.destroyView(cutView);
    }

    @Test
    void deepTraversalIsIterativeAndDoesNotOverflow() {
        final int length = 50_000;
        GraphModel chain = graph(true);
        Node previous = node(chain, 0);
        for (int i = 1; i < length; i++) {
            Node next = node(chain, "chain-" + i);
            edge(chain, previous, next, true);
            previous = next;
        }
        assertEquals(0, run(chain).getCycleCount());

        GraphModel closed = graph(true);
        Node[] nodes = new Node[length];
        for (int i = 0; i < length; i++) {
            nodes[i] = node(closed, "closed-" + i);
        }
        for (int i = 0; i < length; i++) {
            edge(closed, nodes[i], nodes[(i + 1) % length], true);
        }
        CyclesDetectionStatistics result = run(closed);
        assertEquals(1, result.getCycleCount());
        assertEquals(Map.of(length, 1L), result.getCycleLengthDistribution());
        assertEquals(length + 1, asList(result.cycle(0)).size());
    }

    @Test
    void cancellationDiscardsPartialResultsRejectsConcurrentRunsAndAllowsReuse() throws Exception {
        GraphModel graph = graph(true);
        Node a = node(graph, "a");
        Node b = node(graph, "b");
        Node c = node(graph, "c");
        Node d = node(graph, "d");
        Node e = node(graph, "e");
        Node f = node(graph, "f");
        edge(graph, a, b, true);
        edge(graph, b, c, true);
        edge(graph, c, a, true);
        // This second component starts after the cycle witness has been found. At its
        // first child, the parent's edge iterator is active and still has another edge.
        edge(graph, d, e, true);
        edge(graph, d, f, true);

        CountDownLatch reachedAfterCycle = new CountDownLatch(1);
        CountDownLatch continueRun = new CountDownLatch(1);
        AtomicInteger visited = new AtomicInteger();
        CyclesDetectionStatistics statistics = new CyclesDetectionStatistics();
        statistics.setProgressTicket(new TestProgressTicket() {
            @Override
            public void progress() {
                // a,b,c complete the first component; d is the second root; e is
                // discovered while d's DFS frame and edge iterable remain active.
                if (visited.incrementAndGet() == 5) {
                    reachedAfterCycle.countDown();
                    await(continueRun);
                }
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = executor.submit(() -> statistics.execute(graph));
            assertTrue(reachedAfterCycle.await(5, TimeUnit.SECONDS), "execution reached coordinated cancellation point");
            assertEquals("Running", statistics.getResults());
            assertThrows(IllegalStateException.class, () -> statistics.execute(graph));
            assertTrue(statistics.cancel());
            continueRun.countDown();
            run.get(5, TimeUnit.SECONDS);
        } finally {
            continueRun.countDown();
            executor.shutdownNow();
        }

        assertEquals("Cancelled; partial results discarded", statistics.getResults());
        assertEquals(0, statistics.getCycleCount());
        assertTrue(statistics.getCycleLengthDistribution().isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> statistics.cycle(0));
        assertWriteLockCanBeAcquired(graph.getGraph());

        statistics.setProgressTicket(null);
        statistics.execute(graph);
        assertEquals(1, statistics.getCycleCount());
        assertFalse(statistics.cancel());
    }

    @Test
    void releasesOnlyItsOwnReadLockAndPropagatesFailuresAfterUnlocking() {
        GraphModel graph = graph(true);
        Node a = node(graph, "a");
        Node b = node(graph, "b");
        edge(graph, a, b, true);
        Graph visible = graph.getGraphVisible();
        visible.readLock();
        try {
            run(graph);
            assertEquals(1, visible.getLock().getReadHoldCount(), "the caller's read lock remains held");
        } finally {
            visible.readUnlock();
        }
        assertWriteLockCanBeAcquired(visible);

        CyclesDetectionStatistics failure = new CyclesDetectionStatistics();
        AtomicInteger progressCalls = new AtomicInteger();
        failure.setProgressTicket(new TestProgressTicket() {
            @Override
            public void progress() {
                if (progressCalls.incrementAndGet() == 2) {
                    throw new IllegalArgumentException("injected progress failure with active DFS frame");
                }
            }
        });
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> failure.execute(graph));
        assertEquals("injected progress failure with active DFS frame", thrown.getMessage());
        assertEquals("Analysis failed", failure.getResults());
        assertEquals(0, failure.getCycleCount());
        assertWriteLockCanBeAcquired(visible);
    }

    private static GraphModel graph(boolean directed) {
        Configuration configuration = Configuration.builder()
                .enableParallelEdgesSameType(true)
                .build();
        return GraphModel.Factory.newInstance(configuration);
    }

    private static Node node(GraphModel model, Object id) {
        Node node = model.factory().newNode(String.valueOf(id));
        model.getGraph().addNode(node);
        return node;
    }

    private static Edge edge(GraphModel model, Node source, Node target, boolean directed) {
        Edge edge = model.factory().newEdge(source, target, directed);
        model.getGraph().addEdge(edge);
        return edge;
    }

    private static CyclesDetectionStatistics run(GraphModel model) {
        CyclesDetectionStatistics statistics = new CyclesDetectionStatistics();
        statistics.execute(model);
        return statistics;
    }

    private static List<Node> asList(Iterable<Node> nodes) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            result.add(node);
        }
        return result;
    }

    private static void assertSameNode(Node expected, Node actual) {
        assertTrue(expected == actual, "cycle path closes with the identical node");
    }

    private static void assertWriteLockCanBeAcquired(Graph graph) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> acquired = executor.submit(() -> {
                graph.writeLock();
                try {
                    return true;
                } finally {
                    graph.writeUnlock();
                }
            });
            assertTrue(acquired.get(5, TimeUnit.SECONDS));
        } catch (Exception ex) {
            throw new AssertionError("another thread could not acquire the graph write lock", ex);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for coordinated test continuation");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while coordinating test", ex);
        }
    }

    private static class TestProgressTicket implements ProgressTicket {
        @Override public void finish() { }
        @Override public void finish(String message) { }
        @Override public void progress() { }
        @Override public void progress(int percentage) { }
        @Override public void progress(String message) { }
        @Override public void progress(String message, int percentage) { }
        @Override public String getDisplayName() { return "test"; }
        @Override public void setDisplayName(String displayName) { }
        @Override public void start() { }
        @Override public void start(int workunits) { }
        @Override public void switchToDeterminate(int workunits) { }
        @Override public void switchToIndeterminate() { }
    }
}
