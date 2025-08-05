package pt.up.fe.comp2025.optimization.register.analysis;

import java.util.*;

/**
 * Builds an interference graph for register allocation.
 * Identifies when variables cannot share registers due to overlapping lifetimes.
 */
public class InterferenceGraphBuilder {

    /**
     * Builds the interference graph from live ranges.
     * Two variables interfere if their live ranges overlap.
     *
     * @param liveRanges Map of variables to their live instruction points
     * @return Interference graph as an adjacency map
     */
    public Map<String, Set<String>> build(Map<String, Set<Integer>> liveRanges) {
        System.out.println("\n=== BUILDING INTERFERENCE GRAPH ===");

        // Initialize interference graph
        Map<String, Set<String>> interferenceGraph = new HashMap<>();

        // Initialize adjacency sets for each variable
        for (String var : liveRanges.keySet()) {
            interferenceGraph.put(var, new HashSet<>());
        }

        // Enhanced interference detection
        detectInterferences(liveRanges, interferenceGraph);

        // Add special handling for method parameters and fields
        handleSpecialCases(interferenceGraph, liveRanges);

        // Validate graph for correctness
        validateInterferenceGraph(interferenceGraph, liveRanges);

        System.out.println("Final interference graph: " + interferenceGraph);
        System.out.println("=== INTERFERENCE GRAPH BUILT ===\n");
        return interferenceGraph;
    }

    /**
     * Core interference detection algorithm.
     * Carefully analyzes live ranges to detect overlaps.
     */
    private void detectInterferences(Map<String, Set<Integer>> liveRanges,
                                     Map<String, Set<String>> interferenceGraph) {
        List<String> varList = new ArrayList<>(liveRanges.keySet());

        for (int i = 0; i < varList.size(); i++) {
            String var1 = varList.get(i);
            Set<Integer> range1 = liveRanges.get(var1);

            // Skip variables with empty live ranges
            if (range1.isEmpty()) {
                System.out.println("Variable " + var1 + " has empty live range - likely unused");
                continue;
            }

            // Find minimum and maximum instruction points for range1
            int var1Start = Collections.min(range1);
            int var1End = Collections.max(range1);

            for (int j = i + 1; j < varList.size(); j++) {
                String var2 = varList.get(j);
                Set<Integer> range2 = liveRanges.get(var2);

                // Skip variables with empty live ranges
                if (range2.isEmpty()) {
                    System.out.println("Variable " + var2 + " has empty live range - likely unused");
                    continue;
                }

                // Check for interference (any overlap in live ranges)
                boolean interfere = rangesOverlap(range1, range2);

                // Special handling for temporaries
                if (var1.startsWith("tmp") && var2.startsWith("tmp")) {
                    // Only interfere if they overlap exactly
                    interfere = rangesHaveCommonPoints(range1, range2);
                }

                // Add interference edge if detected
                if (interfere) {
                    // Add edge in the undirected graph
                    interferenceGraph.get(var1).add(var2);
                    interferenceGraph.get(var2).add(var1);
                    System.out.println("Interference detected: " + var1 + " <-> " + var2);
                } else {
                    System.out.println("No interference between: " + var1 + " and " + var2);
                }
            }
        }
    }

    /**
     * Check if two ranges have any overlap.
     */
    private boolean rangesOverlap(Set<Integer> range1, Set<Integer> range2) {
        // Check for common points first (faster)
        if (rangesHaveCommonPoints(range1, range2)) {
            return true;
        }

        // Then check for range overlap
        int min1 = Collections.min(range1);
        int max1 = Collections.max(range1);
        int min2 = Collections.min(range2);
        int max2 = Collections.max(range2);

        // Check if one range contains endpoints of the other
        return (min1 <= min2 && min2 <= max1) ||
                (min1 <= max2 && max2 <= max1) ||
                (min2 <= min1 && min1 <= max2) ||
                (min2 <= max1 && max1 <= max2);
    }
    /**
     * Enhanced method to check if two ranges have common instruction points.
     * Much more efficient than checking point by point.
     */
    private boolean rangesHaveCommonPoints(Set<Integer> range1, Set<Integer> range2) {
        // If one set is significantly smaller, iterate through it
        if (range1.size() < range2.size()) {
            for (Integer point : range1) {
                if (range2.contains(point)) {
                    return true;
                }
            }
        } else {
            for (Integer point : range2) {
                if (range1.contains(point)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check for definition-use interferences.
     * This helps catch cases where variables interact in complex control flow.
     */
    private boolean checkDefUseInterference(String var1, String var2,
                                            Set<Integer> range1, Set<Integer> range2) {
        // This is a heuristic check for variables that might have
        // overlapping lifetimes due to definition-use relationships

        // If both variables are temporaries, they likely don't interfere
        // unless their live ranges explicitly overlap
        if (isTemporary(var1) && isTemporary(var2)) {
            return false;
        }

        // If both are non-temporary variables that are used in nearby
        // instructions, we need to be more conservative
        if (!isTemporary(var1) && !isTemporary(var2)) {
            // Check if their ranges are close together
            int minRange1 = Collections.min(range1);
            int maxRange1 = Collections.max(range1);
            int minRange2 = Collections.min(range2);
            int maxRange2 = Collections.max(range2);

            // If the ranges are very close, consider them interfering
            // This helps with variables used across branches
            if (Math.abs(minRange1 - minRange2) <= 2 ||
                    Math.abs(maxRange1 - maxRange2) <= 2) {
                return true;
            }

            // If both variables' ranges span multiple instructions, they likely
            // have interactions across different parts of the program
            if (maxRange1 - minRange1 > 5 && maxRange2 - minRange2 > 5) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a variable name suggests it's a temporary.
     */
    private boolean isTemporary(String varName) {
        return varName.startsWith("tmp") || varName.matches("t\\d+");
    }

    /**
     * Handle special cases for parameters, fields, and other variables
     * that need particular treatment.
     */
    private void handleSpecialCases(Map<String, Set<String>> interferenceGraph,
                                    Map<String, Set<Integer>> liveRanges) {
        // Ensure 'this' parameter has special treatment (interferes with everything)
        if (interferenceGraph.containsKey("this")) {
            for (String var : interferenceGraph.keySet()) {
                if (!var.equals("this") && !var.equals("return")) {
                    interferenceGraph.get("this").add(var);
                    interferenceGraph.get(var).add("this");
                }
            }
        }

        // Parameters should generally interfere with more variables as they're
        // alive from method start
        for (String var : interferenceGraph.keySet()) {
            if (isMethodParameter(var)) {
                for (String otherVar : interferenceGraph.keySet()) {
                    if (!otherVar.equals(var) && !otherVar.equals("return") && !isTemporary(otherVar)) {
                        interferenceGraph.get(var).add(otherVar);
                        interferenceGraph.get(otherVar).add(var);
                    }
                }
            }
        }

        // Ensure any variables used in the same context (like in if-else branches)
        // are marked as interfering
        for (String var1 : interferenceGraph.keySet()) {
            if (isTemporary(var1)) continue;

            for (String var2 : interferenceGraph.keySet()) {
                if (var1.equals(var2) || isTemporary(var2)) continue;

                // If both are non-temporary variables and have similar live ranges,
                // they likely interact and shouldn't share registers
                if (haveSimilarLiveRanges(liveRanges.get(var1), liveRanges.get(var2))) {
                    interferenceGraph.get(var1).add(var2);
                    interferenceGraph.get(var2).add(var1);
                }
            }
        }
    }

    /**
     * Checks if a variable is likely a method parameter based on its name or
     * characteristics of its live range.
     */
    private boolean isMethodParameter(String varName) {
        return varName.equals("this") ||
                varName.equals("args") ||
                varName.startsWith("arg") ||
                varName.startsWith("param");
    }

    /**
     * Check if two live ranges have similar characteristics.
     * This helps catch variables used in similar contexts.
     */
    private boolean haveSimilarLiveRanges(Set<Integer> range1, Set<Integer> range2) {
        if (range1.isEmpty() || range2.isEmpty()) return false;

        int min1 = Collections.min(range1);
        int max1 = Collections.max(range1);
        int min2 = Collections.min(range2);
        int max2 = Collections.max(range2);

        // Check if the ranges have similar spans
        double span1 = max1 - min1;
        double span2 = max2 - min2;

        // If one range is much larger than the other, they're likely different
        if (span1 > 2 * span2 || span2 > 2 * span1) {
            return false;
        }

        // If ranges significantly overlap in their span, they likely interfere
        int overlapStart = Math.max(min1, min2);
        int overlapEnd = Math.min(max1, max2);

        if (overlapEnd >= overlapStart) {
            double overlapSize = overlapEnd - overlapStart;
            if (overlapSize / span1 > 0.5 || overlapSize / span2 > 0.5) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate the interference graph for correctness and completeness.
     * Makes sure the graph is symmetrical and consistent.
     */
    private void validateInterferenceGraph(Map<String, Set<String>> graph,
                                           Map<String, Set<Integer>> liveRanges) {
        // Check for symmetry in the graph (if A interferes with B, B must interfere with A)
        for (String node : graph.keySet()) {
            for (String neighbor : graph.get(node)) {
                if (!graph.containsKey(neighbor) || !graph.get(neighbor).contains(node)) {
                    System.out.println("WARNING: Asymmetric interference detected: " +
                            node + " -> " + neighbor);
                    graph.get(neighbor).add(node);
                }
            }
        }

        // Special validation for pairs that must interfere based on their usage patterns
        for (String var1 : graph.keySet()) {
            for (String var2 : graph.keySet()) {
                if (var1.equals(var2)) continue;

                // If both variables are primary (non-temporary) and have substantial
                // live ranges, they likely should interfere
                if (!isTemporary(var1) && !isTemporary(var2) &&
                        !liveRanges.get(var1).isEmpty() && !liveRanges.get(var2).isEmpty()) {

                    // Check basic conditions suggesting interference
                    if (mustInterfere(var1, var2, liveRanges.get(var1), liveRanges.get(var2)) &&
                            !graph.get(var1).contains(var2)) {

                        System.out.println("Adding missing interference: " + var1 + " <-> " + var2);
                        graph.get(var1).add(var2);
                        graph.get(var2).add(var1);
                    }
                }
            }
        }
    }

    /**
     * Determines if two variables must interfere based on heuristic rules.
     * This catches cases that the basic liveness analysis might miss.
     */
    private boolean mustInterfere(String var1, String var2, Set<Integer> range1, Set<Integer> range2) {
        // Variables with completely disjoint live ranges clearly don't interfere
        if (Collections.max(range1) < Collections.min(range2) ||
                Collections.max(range2) < Collections.min(range1)) {
            return false;
        }

        // If variables are both primary variables (not temporaries),
        // they likely interact across the function
        if (!isTemporary(var1) && !isTemporary(var2)) {
            // Check if their ranges have substantial overlap
            int overlapStart = Math.max(Collections.min(range1), Collections.min(range2));
            int overlapEnd = Math.min(Collections.max(range1), Collections.max(range2));

            if (overlapEnd >= overlapStart) {
                // There is range overlap
                return true;
            }

            // If the ranges are very close, be conservative and mark as interfering
            int gap = Math.min(
                    Math.abs(Collections.max(range1) - Collections.min(range2)),
                    Math.abs(Collections.max(range2) - Collections.min(range1))
            );

            return gap <= 3; // If within 3 instructions, consider interfering
        }

        return false;
    }

    /**
     * Generates a visual representation of the interference graph.
     */
    public String visualizeGraph(Map<String, Set<String>> graph) {
        StringBuilder sb = new StringBuilder();

        List<String> sortedNodes = new ArrayList<>(graph.keySet());
        Collections.sort(sortedNodes);

        for (String node : sortedNodes) {
            sb.append(node).append(" -> ");

            List<String> neighbors = new ArrayList<>(graph.get(node));
            Collections.sort(neighbors);

            if (neighbors.isEmpty()) {
                sb.append("(none)");
            } else {
                for (int i = 0; i < neighbors.size(); i++) {
                    sb.append(neighbors.get(i));
                    if (i < neighbors.size() - 1) {
                        sb.append(", ");
                    }
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}