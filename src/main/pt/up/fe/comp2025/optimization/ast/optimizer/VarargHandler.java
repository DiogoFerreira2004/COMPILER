package pt.up.fe.comp2025.optimization.ast.optimizer;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.ArrayType;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.optimization.ast.util.AstNodeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor responsible for handling varargs parameters in methods.
 * Converts varargs syntax to arrays in the AST.
 * Follows the Visitor pattern for traversing the AST.
 */
public class VarargHandler extends PreorderJmmVisitor<Void, Void> {

    private final SymbolTable symbolTable;
    private final Map<String, List<Integer>> varargMethodsInfo;
    private boolean hasChanged;

    /**
     * Constructs a vararg handler.
     *
     * @param symbolTable The symbol table for the program
     */
    public VarargHandler(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.varargMethodsInfo = new HashMap<>();
        this.hasChanged = false;
    }

    @Override
    protected void buildVisitor() {
        addVisit(Kind.PARAM, this::visitParam);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.DIRECT_METHOD_CALL, this::visitMethodCall);
        addVisit(Kind.MethodCallExpr, this::visitMethodCallExpr);

        setDefaultVisit(this::defaultVisit);
    }

    /**
     * Visits a parameter and identifies if it's a vararg.
     */
    private Void visitParam(JmmNode node, Void unused) {
        // Check if it's a varargs parameter
        boolean isVararg = node.hasAttribute("ellipsis") && "...".equals(node.get("ellipsis"));

        if (isVararg) {
            // Get parent method
            JmmNode methodNode = findParentMethod(node);
            if (methodNode != null && methodNode.hasAttribute("name")) {
                String methodName = methodNode.get("name");
                int paramIndex = findParameterIndex(node, methodNode);

                if (paramIndex >= 0) {
                    // Register method with varargs
                    registerVarargMethod(methodName, paramIndex);

                    // Transform parameter to array
                    transformVarargToArray(node);

                    hasChanged = true;
                }
            }
        }

        return null;
    }

    /**
     * Visits a method declaration and checks its parameters.
     */
    private Void visitMethodDecl(JmmNode node, Void unused) {
        if (node.hasAttribute("name")) {
            String methodName = node.get("name");

            // Visit parameters first
            for (JmmNode child : node.getChildren()) {
                if (Kind.PARAM.check(child)) {
                    visit(child);
                }
            }

            // Visit rest of method
            for (JmmNode child : node.getChildren()) {
                if (!Kind.PARAM.check(child)) {
                    visit(child);
                }
            }

            // Add attribute to mark if method has varargs
            if (varargMethodsInfo.containsKey(methodName)) {
                List<Integer> varargIndices = varargMethodsInfo.get(methodName);
                node.put("hasVarargs", "true");
                node.put("varargIndices", varargIndices.toString());
                hasChanged = true;
            }
        } else {
            defaultVisit(node, unused);
        }

        return null;
    }

    /**
     * Visits a direct method call.
     */
    private Void visitMethodCall(JmmNode node, Void unused) {
        if (node.hasAttribute("name")) {
            String methodName = node.get("name");

            // Check if this method has varargs
            if (varargMethodsInfo.containsKey(methodName)) {
                transformMethodCallWithVarargs(node, methodName);
            } else {
                defaultVisit(node, unused);
            }
        } else {
            defaultVisit(node, unused);
        }

        return null;
    }

    /**
     * Visits a method call in an expression.
     */
    private Void visitMethodCallExpr(JmmNode node, Void unused) {
        if (node.hasAttribute("name")) {
            String methodName = node.get("name");

            // Check if this method has varargs
            if (varargMethodsInfo.containsKey(methodName)) {
                transformMethodCallWithVarargs(node, methodName);
            } else {
                defaultVisit(node, unused);
            }
        } else {
            defaultVisit(node, unused);
        }

        return null;
    }

    /**
     * Transforms a method call with varargs.
     */
    private void transformMethodCallWithVarargs(JmmNode node, String methodName) {
        List<Integer> varargIndices = varargMethodsInfo.get(methodName);
        List<JmmNode> argumentNodes = getArgumentNodes(node);

        // Process each varargs parameter
        for (int varargIndex : varargIndices) {
            int numRegularArgs = varargIndex;
            int numVarargs = argumentNodes.size() - numRegularArgs;

            if (numVarargs > 0) {
                // Create a new array initializer node
                JmmNode arrayInitNode = createArrayInitializer(node, argumentNodes, numRegularArgs, numVarargs);

                // Replace varargs arguments with array
                replaceVarargsWithArray(node, arrayInitNode, numRegularArgs);

                hasChanged = true;
            }
        }

        // Visit arguments
        for (JmmNode child : node.getChildren()) {
            visit(child);
        }
    }

    /**
     * Creates an array initializer node for varargs arguments.
     */
    private JmmNode createArrayInitializer(JmmNode methodCallNode, List<JmmNode> argumentNodes, int startIndex, int count) {
        // Create a new node for the array initializer
        JmmNode arrayInitNode = AstNodeUtils.createNewNode(Kind.ArrayInitializerExpr.getNodeName());

        // Add array elements
        for (int i = startIndex; i < startIndex + count; i++) {
            if (i < argumentNodes.size()) {
                JmmNode argCopy = AstNodeUtils.deepCopyNode(argumentNodes.get(i));
                arrayInitNode.add(argCopy);
            }
        }

        return arrayInitNode;
    }

    /**
     * Replaces varargs arguments with a single array.
     */
    private void replaceVarargsWithArray(JmmNode methodCallNode, JmmNode arrayInitNode, int startIndex) {
        // Remove individual varargs arguments
        int numChildren = methodCallNode.getNumChildren();
        List<JmmNode> childrenToRemove = new ArrayList<>();

        for (int i = startIndex; i < numChildren; i++) {
            childrenToRemove.add(methodCallNode.getChild(i));
        }

        for (JmmNode child : childrenToRemove) {
            AstNodeUtils.removeChild(methodCallNode, child);
        }

        // Add array initializer
        methodCallNode.add(arrayInitNode);
    }

    /**
     * Finds the parent method of a node.
     */
    private JmmNode findParentMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if (Kind.METHOD_DECL.check(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Finds the index of a parameter within a method.
     */
    private int findParameterIndex(JmmNode paramNode, JmmNode methodNode) {
        int index = 0;
        for (JmmNode child : methodNode.getChildren()) {
            if (Kind.PARAM.check(child)) {
                if (child == paramNode) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    /**
     * Registers a method with varargs.
     */
    private void registerVarargMethod(String methodName, int paramIndex) {
        if (!varargMethodsInfo.containsKey(methodName)) {
            varargMethodsInfo.put(methodName, new ArrayList<>());
        }
        varargMethodsInfo.get(methodName).add(paramIndex);
    }

    /**
     * Transforms a vararg parameter to an array.
     */
    private void transformVarargToArray(JmmNode node) {
        // Remove ellipsis attribute
        AstNodeUtils.removeAttribute(node, "ellipsis");

        // Mark type as array
        JmmNode typeNode = node.getChild(0);
        Type paramType = convertType(typeNode);

        // Create new array type
        Type arrayType;
        if (paramType instanceof ArrayType) {
            int dimensions = ((ArrayType) paramType).getDimensions() + 1;
            arrayType = new ArrayType(paramType.getName(), dimensions);
        } else {
            arrayType = new ArrayType(paramType.getName(), 1);
        }

        // Update type
        updateTypeNode(typeNode, arrayType);

        // Add attribute to mark as vararg
        node.put("isVararg", "true");
    }

    /**
     * Gets argument nodes from a method call.
     */
    private List<JmmNode> getArgumentNodes(JmmNode methodCallNode) {
        List<JmmNode> args = new ArrayList<>();

        // First child is the object calling the method in MethodCallExpr
        int startIndex = Kind.MethodCallExpr.check(methodCallNode) ? 1 : 0;

        for (int i = startIndex; i < methodCallNode.getNumChildren(); i++) {
            args.add(methodCallNode.getChild(i));
        }

        return args;
    }

    /**
     * Converts a type node to a Type object.
     */
    private Type convertType(JmmNode typeNode) {
        // Simplified implementation, in practice would use TypeUtils
        boolean isArray = false;
        String typeName = "";

        if (typeNode.hasAttribute("name")) {
            typeName = typeNode.get("name");
        }

        for (JmmNode child : typeNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                isArray = true;
                break;
            }
        }

        return new Type(typeName, isArray);
    }

    /**
     * Updates a type node with a new type.
     */
    private void updateTypeNode(JmmNode typeNode, Type newType) {
        // Update type name
        if (typeNode.hasAttribute("name")) {
            typeNode.put("name", newType.getName());
        }

        // Add ArraySuffix if array
        if (newType.isArray() && newType instanceof ArrayType) {
            int dimensions = ((ArrayType) newType).getDimensions();

            // Remove existing suffixes
            List<JmmNode> toRemove = new ArrayList<>();
            for (JmmNode child : typeNode.getChildren()) {
                if ("ArraySuffix".equals(child.getKind())) {
                    toRemove.add(child);
                }
            }

            for (JmmNode child : toRemove) {
                AstNodeUtils.removeChild(typeNode, child);
            }

            // Add new suffixes
            JmmNode firstSuffix = AstNodeUtils.createNewNode("ArraySuffix");
            typeNode.add(firstSuffix);

            JmmNode currentSuffix = firstSuffix;
            for (int i = 1; i < dimensions; i++) {
                JmmNode nextSuffix = AstNodeUtils.createNewNode("ArraySuffix");
                currentSuffix.add(nextSuffix);
                currentSuffix = nextSuffix;
            }
        }
    }

    /**
     * Default visit method for nodes not specifically handled.
     */
    private Void defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return null;
    }

    /**
     * Checks if the visitor made any changes to the AST.
     */
    public boolean hasChanged() {
        return hasChanged;
    }
}