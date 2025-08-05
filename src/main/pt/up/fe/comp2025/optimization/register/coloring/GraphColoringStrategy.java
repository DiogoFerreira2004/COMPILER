package pt.up.fe.comp2025.optimization.register.coloring;

import java.util.Map;
import java.util.Set;

/**
 * Interface for graph coloring algorithms used in register allocation.
 * Follows the Strategy pattern to allow different coloring implementations.
 */
public interface GraphColoringStrategy {

    /**
     * Colors a graph using the minimum number of colors possible.
     *
     * @param graph The interference graph as an adjacency list
     * @return A mapping from node names to color numbers (registers)
     */
    Map<String, Integer> colorGraph(Map<String, Set<String>> graph);

    /**
     * Colors a graph using at most maxColors colors.
     * Throws an exception if not possible.
     *
     * @param graph The interference graph as an adjacency list
     * @param maxColors The maximum number of colors to use
     * @return A mapping from node names to color numbers (registers)
     * @throws RuntimeException if the graph cannot be colored with maxColors
     */
    Map<String, Integer> colorGraphLimited(Map<String, Set<String>> graph, int maxColors);
}