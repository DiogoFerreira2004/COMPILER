package pt.up.fe.comp2025.optimization.ast.util;

import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for manipulating AST nodes.
 * Centralizes common operations that were duplicated across visitors.
 */
public class AstNodeUtils {

    /**
     * Removes an attribute from a node safely.
     * Uses reflection to invoke removeAttribute if available.
     *
     * @param node The node to modify
     * @param attribute The attribute to remove
     */
    public static void removeAttribute(JmmNode node, String attribute) {
        if (node.hasAttribute(attribute)) {
            try {
                if (node.getClass().getMethod("removeAttribute", String.class) != null) {
                    node.getClass().getMethod("removeAttribute", String.class).invoke(node, attribute);
                }
            } catch (Exception e) {
                // Se não conseguir remover usando reflexão, apenas ignoramos silenciosamente
                System.out.println("DEBUG: Could not remove attribute " + attribute + ": " + e.getMessage());
            }
        }
    }

    /**
     * Remove all attributes except the ones specified
     */
    public static void removeAllAttributes(JmmNode node, String... exceptAttributes) {
        List<String> exceptions = Arrays.asList(exceptAttributes);
        Set<String> attributesToRemove = new HashSet<>();

        // Fase 1: Coletar atributos a serem removidos para evitar ConcurrentModificationException
        for (String attribute : node.getAttributes()) {
            if (!exceptions.contains(attribute)) {
                attributesToRemove.add(attribute);
            }
        }

        // Fase 2: Remover cada atributo individualmente
        for (String attribute : attributesToRemove) {
            removeAttribute(node, attribute);
        }
    }
    /**
     * Removes a node from its parent completely
     */
    public static void removeNode(JmmNode node) {
        JmmNode parent = node.getParent();
        if (parent != null) {
            // Remove from parent's children
            removeChild(parent, node);
        }
    }

    /**
     * Checks if two nodes represent the same variable or value
     */
    public static boolean nodesEqual(JmmNode node1, JmmNode node2) {
        if (!node1.getKind().equals(node2.getKind())) {
            return false;
        }

        if ("VAR_REF_EXPR".equals(node1.getKind())) {
            return node1.hasAttribute("name") &&
                    node2.hasAttribute("name") &&
                    node1.get("name").equals(node2.get("name"));
        }

        if ("IntLiteral".equals(node1.getKind()) || "INTEGER_LITERAL".equals(node1.getKind())) {
            return node1.hasAttribute("value") &&
                    node2.hasAttribute("value") &&
                    node1.get("value").equals(node2.get("value"));
        }

        return false;
    }

    /**
     * Clones a node without its parent
     */
    public static JmmNode cloneNode(JmmNode node) {
        JmmNode clone = createNewNode(node.getKind());

        // Copy attributes
        for (String attr : node.getAttributes()) {
            clone.put(attr, node.get(attr));
        }

        // Clone children
        for (JmmNode child : node.getChildren()) {
            clone.add(cloneNode(child));
        }

        return clone;
    }

    /**
     * Removes a child from a node safely.
     * Uses reflection to invoke removeChild if available.
     *
     * @param node The parent node
     * @param child The child node to remove
     */
    public static void removeChild(JmmNode node, JmmNode child) {
        try {
            if (node.getClass().getMethod("removeChild", JmmNode.class) != null) {
                node.getClass().getMethod("removeChild", JmmNode.class).invoke(node, child);
                return;
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Could not use direct removeChild, trying alternative");
        }

        // Tente uma abordagem baseada em lista
        try {
            List<JmmNode> children = new ArrayList<>();
            for (int i = 0; i < node.getNumChildren(); i++) {
                if (node.getChild(i) != child) {
                    children.add(node.getChild(i));
                }
            }

            // Remova todos os filhos
            for (int i = node.getNumChildren() - 1; i >= 0; i--) {
                JmmNode existingChild = node.getChild(i);
                try {
                    if (node.getClass().getMethod("remove", JmmNode.class) != null) {
                        node.getClass().getMethod("remove", JmmNode.class).invoke(node, existingChild);
                    }
                } catch (Exception ex) {
                    // Ignore
                }
            }

            // Adicione de volta todos exceto o filho a ser removido
            for (JmmNode remainingChild : children) {
                node.add(remainingChild);
            }
        } catch (Exception e) {
            System.out.println("WARNING: All removal methods failed: " + e.getMessage());
        }
    }

    /**
     * Removes all children from a node safely.
     *
     * @param node The node to modify
     */
    public static void removeAllChildren(JmmNode node) {
        // Evitar ConcurrentModificationException copiando a lista de filhos primeiro
        List<JmmNode> children = new ArrayList<>();
        for (int i = 0; i < node.getNumChildren(); i++) {
            children.add(node.getChild(i));
        }

        // Remover cada filho individualmente
        for (JmmNode child : children) {
            removeChild(node, child);
        }
    }

    /**
     * Creates a new node with the specified kind.
     * Generic implementation that uses reflection.
     *
     * @param kind The kind of node to create
     * @return The new node
     */
    public static JmmNode createNewNode(String kind) {
        try {
            // Implementação genérica que pode ser substituída pelo método específico
            Class<?> nodeClass = Class.forName("pt.up.fe.comp.jmm.ast.JmmNodeImpl");
            return (JmmNode) nodeClass.getConstructor(String.class).newInstance(kind);
        } catch (Exception e) {
            System.out.println("DEBUG: Could not create new node via JmmNodeImpl: " + e.getMessage());
            // Tentativa alternativa usando uma abordagem diferente
            try {
                // Algumas implementações podem ter factory methods
                Class<?> factoryClass = Class.forName("pt.up.fe.comp.jmm.ast.JmmNodeFactory");
                if (factoryClass.getMethod("createNode", String.class) != null) {
                    return (JmmNode) factoryClass.getMethod("createNode", String.class)
                            .invoke(null, kind);
                }
            } catch (Exception e2) {
                // Ignorar silenciosamente esta segunda exceção
            }

            throw new RuntimeException("Could not create new node: " + e.getMessage());
        }
    }

    /**
     * Creates a deep copy of a node.
     *
     * @param original The node to copy
     * @return A deep copy of the node
     */
    public static JmmNode deepCopyNode(JmmNode original) {
        JmmNode copy = createNewNode(original.getKind());

        // Copy attributes
        for (String attr : original.getAttributes()) {
            copy.put(attr, original.get(attr));
        }

        // Copy children recursively
        for (JmmNode child : original.getChildren()) {
            copy.add(deepCopyNode(child));
        }

        return copy;
    }

    /**
     * Replaces a node with another.
     *
     * @param node The node to replace
     * @param replacement The replacement node
     */
    public static void replaceNode(JmmNode node, JmmNode replacement) {
        JmmNode parent = node.getParent();
        if (parent == null) {
            System.out.println("WARNING: Cannot replace node without parent");
            return;
        }

        // Find index of node in parent
        int index = -1;
        for (int i = 0; i < parent.getNumChildren(); i++) {
            if (parent.getChild(i) == node) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            // Remove original node
            removeChild(parent, node);

            // Insert replacement at same position
            try {
                // Tentativa 1: Usar método add com índice
                if (parent.getClass().getMethod("add", int.class, JmmNode.class) != null) {
                    parent.getClass().getMethod("add", int.class, JmmNode.class)
                            .invoke(parent, index, replacement);
                    return;
                }
            } catch (Exception e) {
                System.out.println("DEBUG: Could not use indexed add, trying alternative method");
            }

            try {
                // Tentativa 2: Usar método addChild (comum em algumas implementações AST)
                if (parent.getClass().getMethod("addChild", int.class, JmmNode.class) != null) {
                    parent.getClass().getMethod("addChild", int.class, JmmNode.class)
                            .invoke(parent, index, replacement);
                    return;
                }
            } catch (Exception e) {
                System.out.println("DEBUG: Could not use addChild, falling back to default add");
            }

            // Fallback to normal add
            parent.add(replacement);
            System.out.println("WARNING: Could not replace node at exact position, added at end instead");
        }
    }

    /**
     * Safely checks if a node has a specific kind.
     */
    public static boolean hasKind(JmmNode node, String kind) {
        return node.getKind().equals(kind) ||
                (node.hasAttribute("kind") && node.get("kind").equals(kind));
    }

    /**
     * Gets a value from a node, checking all possible attribute names.
     */
    public static String getValue(JmmNode node) {
        if (node.hasAttribute("value")) return node.get("value");
        if (node.hasAttribute("val")) return node.get("val");
        return null;
    }

    /**
     * Checks if a node (of any kind) represents a literal integer value.
     */
    public static boolean isIntegerValue(JmmNode node) {
        if (hasKind(node, "IntLiteral") || hasKind(node, "INTEGER_LITERAL")) {
            return node.hasAttribute("value") || node.hasAttribute("val");
        }
        return false;
    }
}