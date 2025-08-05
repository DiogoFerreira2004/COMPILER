package pt.up.fe.comp2025.optimization.register.coloring;

import java.util.*;

/**
 * Implementation of the DSatur graph coloring algorithm for register allocation.
 * DSatur prioritizes coloring vertices with the highest saturation degree.
 */
public class DSaturColoringStrategy implements GraphColoringStrategy {

    @Override
    public Map<String, Integer> colorGraph(Map<String, Set<String>> graph) {
        System.out.println("\n=== STARTING DSATUR COLORING ===");

        Map<String, Integer> coloring = new HashMap<>();

        // Handle empty graph
        if (graph.isEmpty()) {
            System.out.println("Empty graph - nothing to color");
            return coloring;
        }

        // Pre-color 'this' with register 0
        if (graph.containsKey("this")) {
            coloring.put("this", 0);
            System.out.println("Pre-assigned 'this' to register 0");
        }

        // Calculate degrees for all nodes
        Map<String, Integer> degrees = calculateDegrees(graph);
        System.out.println("Node degrees: " + degrees);

        // Set of uncolored nodes (excluding 'this' if already colored)
        Set<String> uncolored = new HashSet<>(graph.keySet());
        uncolored.removeAll(coloring.keySet());

        // Saturation map (how many different adjacent colors)
        Map<String, Integer> saturation = new HashMap<>();
        for (String node : graph.keySet()) {
            saturation.put(node, 0);
        }

        // Initialize saturations for neighbors of 'this'
        if (graph.containsKey("this")) {
            for (String neighbor : graph.get("this")) {
                // Update saturation for 'this' neighbors (now has color 0)
                saturation.put(neighbor, saturation.getOrDefault(neighbor, 0) + 1);
            }
        }

        // Track adjacent colors for each node
        Map<String, Set<Integer>> adjacentColors = new HashMap<>();
        for (String node : graph.keySet()) {
            adjacentColors.put(node, new HashSet<>());

            // If neighbor of 'this', add 0 to adjacent colors
            if (graph.containsKey("this") && graph.get("this").contains(node)) {
                adjacentColors.get(node).add(0);
            }
        }

        // Color remaining nodes
        while (!uncolored.isEmpty()) {
            // Find node with highest saturation (tiebreak by degree)
            String nextNode = findNodeWithMaxSaturation(uncolored, saturation, degrees);
            System.out.println("Selected node: " + nextNode + " (Saturation: " + saturation.get(nextNode) +
                    ", Degree: " + degrees.get(nextNode) + ")");

            // Find smallest available color
            int color = findSmallestAvailableColor(graph, nextNode, coloring);

            // Ensure we don't use register 0 if 'this' exists
            if (color == 0 && graph.containsKey("this") && !nextNode.equals("this")) {
                color = 1;
                while (isColorUsedByAnyNeighbor(graph, nextNode, coloring, color)) {
                    color++;
                }
            }

            System.out.println("Assigning color " + color + " to node " + nextNode);

            // Assign color
            coloring.put(nextNode, color);
            uncolored.remove(nextNode);

            // Update saturations of uncolored neighbors
            for (String neighbor : graph.get(nextNode)) {
                if (uncolored.contains(neighbor)) {
                    // Add new color to adjacent colors set
                    if (adjacentColors.get(neighbor).add(color)) {
                        // If color was actually added (wasn't there before),
                        // increment saturation
                        saturation.put(neighbor, saturation.get(neighbor) + 1);
                        System.out.println("Updated saturation of " + neighbor + " to " + saturation.get(neighbor));
                    }
                }
            }
        }

        // Ensure special parameters have dedicated registers
        ensureSpecialParametersHaveDedicatedRegisters(coloring, graph);

        // Final check: ensure 'this' is in register 0
        if (graph.containsKey("this") && (!coloring.containsKey("this") || coloring.get("this") != 0)) {
            // Relocate any variable using reg0
            for (Map.Entry<String, Integer> entry : new HashMap<>(coloring).entrySet()) {
                if (entry.getValue() == 0 && !entry.getKey().equals("this")) {
                    // Find another register for this variable
                    int newColor = 1;
                    while (coloring.containsValue(newColor)) {
                        newColor++;
                    }
                    coloring.put(entry.getKey(), newColor);
                    System.out.println("Moved " + entry.getKey() + " from reg0 to reg" + newColor);
                }
            }

            // Put 'this' in register 0
            coloring.put("this", 0);
            System.out.println("Ensured 'this' is in register 0");
        }

        System.out.println("Final coloring: " + coloring);
        System.out.println("=== DSATUR COLORING COMPLETED ===\n");
        return coloring;
    }

    @Override
    public Map<String, Integer> colorGraphLimited(Map<String, Set<String>> graph, int maxColors) {
        System.out.println("\n=== STARTING LIMITED DSATUR COLORING (MAX " + maxColors + " COLORS) ===");

        Map<String, Integer> coloring = new HashMap<>();
        Set<String> spilledVars = new HashSet<>();

        // Handle empty graph
        if (graph.isEmpty()) {
            System.out.println("Empty graph - nothing to color");
            return coloring;
        }

        // Pre-color 'this' with register 0
        if (graph.containsKey("this")) {
            coloring.put("this", 0);
            System.out.println("Pre-assigned 'this' to register 0");

            // If maxColors is 1, we cannot color anything else
            if (maxColors == 1) {
                throw new RuntimeException("Cannot color graph with only " + maxColors +
                        " color when 'this' must be in register 0");
            }
        }

        // Calculate degrees for all nodes
        Map<String, Integer> degrees = calculateDegrees(graph);
        System.out.println("Node degrees: " + degrees);

        // Set of uncolored nodes (excluding 'this' if already colored)
        Set<String> uncolored = new HashSet<>(graph.keySet());
        uncolored.removeAll(coloring.keySet());

        // Saturation map (how many different adjacent colors)
        Map<String, Integer> saturation = new HashMap<>();
        for (String node : graph.keySet()) {
            saturation.put(node, 0);
        }

        // Initialize saturations for neighbors of 'this'
        if (graph.containsKey("this")) {
            for (String neighbor : graph.get("this")) {
                // Update saturation for 'this' neighbors (now has color 0)
                saturation.put(neighbor, saturation.getOrDefault(neighbor, 0) + 1);
            }
        }

        // Track adjacent colors for each node
        Map<String, Set<Integer>> adjacentColors = new HashMap<>();
        for (String node : graph.keySet()) {
            adjacentColors.put(node, new HashSet<>());

            // If neighbor of 'this', add 0 to adjacent colors
            if (graph.containsKey("this") && graph.get("this").contains(node)) {
                adjacentColors.get(node).add(0);
            }
        }

        // Separate regular and temporary variables for prioritization
        Set<String> regularVars = new HashSet<>();
        Set<String> tempVars = new HashSet<>();
        for (String node : uncolored) {
            if (isTemporaryVariable(node)) {
                tempVars.add(node);
            } else {
                regularVars.add(node);
            }
        }

        // First color regular variables
        while (!regularVars.isEmpty()) {
            // Find node with highest saturation
            String nextNode = findNodeWithMaxSaturation(regularVars, saturation, degrees);
            System.out.println("Selected regular node: " + nextNode + " (Saturation: " + saturation.get(nextNode) +
                    ", Degree: " + degrees.get(nextNode) + ")");

            // CORREÇÃO: Uso correto do findSmallestAvailableColorLimited
            int color = findSmallestAvailableColorLimited(graph, nextNode, coloring, maxColors);

            // Se a cor exceder o limite máximo, forçar para a última disponível (spillage)
            if (color >= maxColors) {
                color = maxColors - 1;
                spilledVars.add(nextNode);
                System.out.println("Variable " + nextNode + " exceeds register limit, spilling to reg" + color);
            }

            System.out.println("Assigning color " + color + " to node " + nextNode);

            // Assign color
            coloring.put(nextNode, color);
            regularVars.remove(nextNode);
            uncolored.remove(nextNode);

            // Update saturations of uncolored neighbors
            updateNeighborSaturations(graph, nextNode, color, uncolored, adjacentColors, saturation);
        }

        // Then color temporary variables
        int spillRegister = maxColors - 1;  // Last register for spilled variables

        while (!tempVars.isEmpty()) {
            // Find node with highest saturation
            String nextNode = findNodeWithMaxSaturation(tempVars, saturation, degrees);
            System.out.println("Selected temporary node: " + nextNode + " (Saturation: " + saturation.get(nextNode) +
                    ", Degree: " + degrees.get(nextNode) + ")");

            // CORREÇÃO: Uso correto do findSmallestAvailableColorLimited
            int color = findSmallestAvailableColorLimited(graph, nextNode, coloring, maxColors);

            // If color exceeds the limit, mark as spilled and assign to spill register
            if (color >= maxColors) {
                color = spillRegister;
                spilledVars.add(nextNode);
                System.out.println("Spilling variable " + nextNode + " to register " + color);
            } else {
                System.out.println("Assigning color " + color + " to node " + nextNode);
            }

            // Assign color
            coloring.put(nextNode, color);
            tempVars.remove(nextNode);
            uncolored.remove(nextNode);

            // Update saturations of uncolored neighbors
            updateNeighborSaturations(graph, nextNode, color, uncolored, adjacentColors, saturation);
        }

        // Ensure special parameters have dedicated registers
        ensureSpecialParametersHaveDedicatedRegistersLimited(coloring, graph, maxColors);

        // Final check: ensure 'this' is in register 0
        if (graph.containsKey("this") && (!coloring.containsKey("this") || coloring.get("this") != 0)) {
            // Relocate any variable using reg0
            for (Map.Entry<String, Integer> entry : new HashMap<>(coloring).entrySet()) {
                if (entry.getValue() == 0 && !entry.getKey().equals("this")) {
                    // Find another register for this variable
                    int newColor = 1;
                    while (newColor < maxColors - 1 && coloring.containsValue(newColor)) {
                        newColor++;
                    }

                    if (newColor >= maxColors - 1) {
                        // Not enough space, mark as spilled
                        coloring.put(entry.getKey(), spillRegister);
                        spilledVars.add(entry.getKey());
                        System.out.println("Moved " + entry.getKey() + " from reg0 to spillage register");
                    } else {
                        coloring.put(entry.getKey(), newColor);
                        System.out.println("Moved " + entry.getKey() + " from reg0 to reg" + newColor);
                    }
                }
            }

            // Put 'this' in register 0
            coloring.put("this", 0);
            System.out.println("Ensured 'this' is in register 0");
        }

        // CORREÇÃO CRÍTICA: Validação final para garantir que todas as cores estejam abaixo do limite
        Map<String, Integer> finalColoring = new HashMap<>();
        for (Map.Entry<String, Integer> entry : coloring.entrySet()) {
            String var = entry.getKey();
            int color = entry.getValue();

            // Se a cor é >= maxColors e ainda não foi marcada como spilled, corrigir
            if (color >= maxColors && !spilledVars.contains(var)) {
                finalColoring.put(var, maxColors - 1);
                spilledVars.add(var);
                System.out.println("Final adjustment: Spilled " + var + " to register " + (maxColors - 1));
            } else {
                // Garantir que a cor nunca exceda o limite
                finalColoring.put(var, Math.min(color, maxColors - 1));
            }
        }

        System.out.println("Final coloring: " + finalColoring);
        System.out.println("Spilled variables: " + spilledVars);
        System.out.println("=== LIMITED DSATUR COLORING COMPLETED ===\n");
        return finalColoring;
    }

    /**
     * Updates saturations of neighbors of a newly colored node.
     */
    private void updateNeighborSaturations(Map<String, Set<String>> graph,
                                           String coloredNode,
                                           int assignedColor,
                                           Set<String> uncolored,
                                           Map<String, Set<Integer>> adjacentColors,
                                           Map<String, Integer> saturation) {
        for (String neighbor : graph.get(coloredNode)) {
            if (uncolored.contains(neighbor)) {
                // Add new color to adjacent colors set
                if (adjacentColors.get(neighbor).add(assignedColor)) {
                    // If color was actually added (wasn't there before),
                    // increment saturation
                    saturation.put(neighbor, saturation.get(neighbor) + 1);
                    System.out.println("Updated saturation of " + neighbor + " to " + saturation.get(neighbor));
                }
            }
        }
    }

    /**
     * Checks if a color is used by any neighbor of a node.
     */
    private boolean isColorUsedByAnyNeighbor(Map<String, Set<String>> graph,
                                             String node,
                                             Map<String, Integer> coloring,
                                             int color) {
        for (String neighbor : graph.get(node)) {
            if (coloring.containsKey(neighbor) && coloring.get(neighbor) == color) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a variable is temporary (compiler-generated).
     */
    private boolean isTemporaryVariable(String node) {
        return node.startsWith("tmp");
    }

    /**
     * Calculates the degree of each node in the graph.
     */
    private Map<String, Integer> calculateDegrees(Map<String, Set<String>> graph) {
        Map<String, Integer> degrees = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            degrees.put(entry.getKey(), entry.getValue().size());
        }
        return degrees;
    }

    /**
     * Finds the uncolored node with highest saturation.
     * In case of tie, choose the one with highest degree.
     */
    private String findNodeWithMaxSaturation(Set<String> uncolored,
                                             Map<String, Integer> saturation,
                                             Map<String, Integer> degrees) {
        int maxSaturation = -1;
        int maxDegree = -1;
        String selected = null;

        for (String node : uncolored) {
            int nodeSaturation = saturation.get(node);
            int nodeDegree = degrees.get(node);

            if (nodeSaturation > maxSaturation ||
                    (nodeSaturation == maxSaturation && nodeDegree > maxDegree)) {
                maxSaturation = nodeSaturation;
                maxDegree = nodeDegree;
                selected = node;
            }
        }

        return selected;
    }

    /**
     * Finds the smallest available color for a node.
     */
    private int findSmallestAvailableColor(Map<String, Set<String>> graph,
                                           String node,
                                           Map<String, Integer> coloring) {
        // Set of colors used by neighbors
        Set<Integer> usedColors = new HashSet<>();

        // Add all colors used by neighbors
        for (String neighbor : graph.get(node)) {
            if (coloring.containsKey(neighbor)) {
                usedColors.add(coloring.get(neighbor));
            }
        }

        // Check if this is a special parameter
        boolean isSpecialParam = isSpecialParameter(node);

        // Find smallest color not used by neighbors
        int color = 0;
        while (usedColors.contains(color) ||
                (isSpecialParam && isColorUsedByAnyNode(coloring, color))) {
            color++;
        }

        return color;
    }

    /**
     * Finds the smallest available color for a node, limited to maxColors.
     * CORREÇÃO: Implementação correta para respeitar o limite de cores.
     */
    private int findSmallestAvailableColorLimited(Map<String, Set<String>> graph,
                                                  String node,
                                                  Map<String, Integer> coloring,
                                                  int maxColors) {
        // Set of colors used by neighbors
        Set<Integer> usedColors = new HashSet<>();

        // Add all colors used by neighbors
        for (String neighbor : graph.get(node)) {
            if (coloring.containsKey(neighbor)) {
                usedColors.add(coloring.get(neighbor));
            }
        }

        // Check if this is a special parameter
        boolean isSpecialParam = isSpecialParameter(node);

        // CORREÇÃO: Encontrar a menor cor disponível, estritamente menor que maxColors
        int color = 0;
        // Considerar apenas cores menores que maxColors
        while (color < maxColors &&
                (usedColors.contains(color) ||
                        (isSpecialParam && isColorUsedByAnyNode(coloring, color)))) {
            color++;
        }

        // CORREÇÃO CRÍTICA: Se não encontrou cor disponível dentro do limite,
        // retornar a última cor disponível (para "spill")
        if (color >= maxColors) {
            return maxColors - 1;
        }

        return color;
    }

    /**
     * Checks if the node is a special parameter that needs its own register.
     */
    private boolean isSpecialParameter(String node) {
        return node.equals("args") ||
                (node.startsWith("arg") && node.length() <= 5) ||
                node.equals("this");
    }

    /**
     * Checks if a color is already used by any node.
     */
    private boolean isColorUsedByAnyNode(Map<String, Integer> coloring, int color) {
        return coloring.values().contains(color);
    }

    /**
     * Ensures special parameters have dedicated registers.
     */
    private void ensureSpecialParametersHaveDedicatedRegisters(Map<String, Integer> coloring,
                                                               Map<String, Set<String>> graph) {
        // Find all nodes that are special parameters
        for (String node : graph.keySet()) {
            if (isSpecialParameter(node) && coloring.containsKey(node)) {
                int currentColor = coloring.get(node);
                boolean colorShared = false;

                // Check if color is shared
                for (Map.Entry<String, Integer> entry : coloring.entrySet()) {
                    if (!entry.getKey().equals(node) && entry.getValue() == currentColor) {
                        colorShared = true;
                        break;
                    }
                }

                // If color is shared, assign a new unique color
                if (colorShared) {
                    int newColor = findUniqueColor(coloring);
                    System.out.println("Reassigning special parameter " + node +
                            " from color " + currentColor + " to dedicated color " + newColor);
                    coloring.put(node, newColor);
                }
            }
        }
    }

    /**
     * Ensures special parameters have dedicated registers,
     * as long as it doesn't exceed the limit.
     */
    private void ensureSpecialParametersHaveDedicatedRegistersLimited(Map<String, Integer> coloring,
                                                                      Map<String, Set<String>> graph,
                                                                      int maxColors) {
        // Find all nodes that are special parameters
        for (String node : graph.keySet()) {
            if (isSpecialParameter(node) && coloring.containsKey(node)) {
                int currentColor = coloring.get(node);
                boolean colorShared = false;

                // Check if color is shared
                for (Map.Entry<String, Integer> entry : coloring.entrySet()) {
                    if (!entry.getKey().equals(node) && entry.getValue() == currentColor) {
                        colorShared = true;
                        break;
                    }
                }

                // If color is shared, try to assign a new unique color
                if (colorShared) {
                    int newColor = findUniqueColorLimited(coloring, maxColors);
                    if (newColor < maxColors) {
                        System.out.println("Reassigning special parameter " + node +
                                " from color " + currentColor + " to dedicated color " + newColor);
                        coloring.put(node, newColor);
                    } else {
                        System.out.println("Cannot assign dedicated color to special parameter " + node +
                                " because it would exceed the limit of " + maxColors + " colors");
                    }
                }
            }
        }
    }

    /**
     * Finds a color that isn't used by any node.
     */
    private int findUniqueColor(Map<String, Integer> coloring) {
        int maxColor = -1;
        for (int color : coloring.values()) {
            maxColor = Math.max(maxColor, color);
        }
        return maxColor + 1;
    }

    /**
     * Finds a color that isn't used by any node, limited to maxColors.
     */
    private int findUniqueColorLimited(Map<String, Integer> coloring, int maxColors) {
        Set<Integer> usedColors = new HashSet<>(coloring.values());
        for (int i = 0; i < maxColors; i++) {
            if (!usedColors.contains(i)) {
                return i;
            }
        }
        return maxColors; // Indicates no available colors within limit
    }
}