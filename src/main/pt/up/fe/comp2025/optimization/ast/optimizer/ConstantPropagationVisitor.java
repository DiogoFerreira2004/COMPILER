package pt.up.fe.comp2025.optimization.ast.optimizer;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.optimization.ast.util.AstNodeUtils;
import pt.up.fe.comp2025.optimization.ast.util.ModifiedVariablesFinderUtil;

import java.util.*;

/**
 * Visitor that propagates constant values through the AST.
 * Identifies variables with constant values and replaces occurrences
 * with their literal values when safe to do so.
 */
public class ConstantPropagationVisitor extends PreorderJmmVisitor<Map<String, Object>, Boolean> {

    private static final String TYPE_INTEGER = "int";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final int MAX_RECURSION_DEPTH = 50;

    private final SymbolTable symbolTable;
    private boolean hasChanged;
    private final Set<JmmNode> visitedNodes;
    private String currentMethod;
    private final Map<String, Map<String, Object>> methodConstants;
    private int recursionDepth;
    private Set<String> loopModifiedVariables;
    private Set<String> multiAssignedVariables;
    private Map<String, Set<String>> methodMultiAssignedVars;
    private Set<String> loopConditionVariables = new HashSet<>();

    public ConstantPropagationVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.hasChanged = false;
        this.visitedNodes = new HashSet<>();
        this.currentMethod = null;
        this.methodConstants = new HashMap<>();
        this.recursionDepth = 0;
        this.loopModifiedVariables = new HashSet<>();
        this.multiAssignedVariables = new HashSet<>();
        this.methodMultiAssignedVars = new HashMap<>();
    }

    @Override
    protected void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("ASSIGN_STMT", this::visitAssignStmt);
        addVisit("AssignStatement", this::visitAssignStmt);
        addVisit("IfStmt", this::visitIfStmt);
        addVisit("WhileStmt", this::visitWhileStmt);
        addVisit("BlockStmt", this::visitBlockStmt);
        addVisit("RETURN_STMT", this::visitReturnStmt);
        addVisit("RELATIONAL_EXPR", this::visitExpressionNode);
        addVisit("MULTIPLICATIVE_EXPR", this::visitExpressionNode);
        addVisit("ADDITIVE_EXPR", this::visitExpressionNode);
        addVisit("BINARY_EXPR", this::visitBinaryExpr);
        addVisit("VAR_REF_EXPR", this::visitVarRefExpr);
        addVisit("IdentifierReference", this::visitVarRefExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitExpressionNode(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        boolean changed = false;

        try {
            Set<JmmNode> savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            for (int i = 0; i < node.getNumChildren(); i++) {
                JmmNode child = node.getChild(i);
                boolean childChanged = visit(child, constants);
                changed |= childChanged;
            }

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);
        } finally {
            recursionDepth--;
        }

        if (changed) {
            this.hasChanged = true;
        }

        return changed;
    }

    private Boolean visitMethodDecl(JmmNode node, Map<String, Object> constants) {
        String methodName = node.hasAttribute("name") ? node.get("name") : "unknown";
        this.currentMethod = methodName;

        methodConstants.putIfAbsent(methodName, new HashMap<>());
        visitedNodes.clear();

        Map<String, Object> methodConstMap = methodConstants.get(methodName);
        boolean methodChanged = false;

        for (JmmNode child : node.getChildren()) {
            if (visit(child, methodConstMap))
                methodChanged = true;
        }

        this.currentMethod = null;
        return methodChanged;
    }

    private Boolean visitAssignStmt(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            if (node.getNumChildren() < 2) {
                return defaultVisit(node, constants);
            }

            JmmNode lhs = node.getChild(0);
            JmmNode rhs = node.getChild(1);

            boolean rhsChanged = visit(rhs, constants);

            if (!isSimpleVariable(lhs)) {
                boolean lhsChanged = visit(lhs, constants);
                return rhsChanged || lhsChanged;
            }

            String varName = lhs.get("name");

            if (isLiteral(rhs)) {
                Object constValue = getLiteralValue(rhs);
                constants.put(varName, constValue);
                return rhsChanged;
            }
            else if (isVariableReference(rhs) && hasConstantValue(rhs, constants)) {
                String rhsVarName = rhs.get("name");
                Object constValue = constants.get(rhsVarName);
                constants.put(varName, constValue);
                return rhsChanged;
            }
            else {
                if (constants.containsKey(varName)) {
                    constants.remove(varName);
                }
                return rhsChanged;
            }
        } finally {
            recursionDepth--;
        }
    }

    private Boolean visitIfStmt(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;

            if (node.getNumChildren() < 2) {
                return defaultVisit(node, constants);
            }

            JmmNode condition = node.getChild(0);
            JmmNode thenBranch = node.getChild(1);
            JmmNode elseBranch = node.getNumChildren() > 2 ? node.getChild(2) : null;

            Set<String> modifiedVars = findModifiedVariables(node);

            for (String var : modifiedVars) {
                if (constants.containsKey(var)) {
                    constants.remove(var);
                }
                registerMultiAssignedVariable(var);
            }

            Set<JmmNode> savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            Map<String, Object> conditionConstants = new HashMap<>(constants);
            for (String var : modifiedVars) {
                conditionConstants.remove(var);
            }
            boolean conditionChanged = visit(condition, conditionConstants);
            changed |= conditionChanged;

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);

            Map<String, Object> thenConstants = deepCopyConstants(constants);
            Map<String, Object> elseConstants = deepCopyConstants(constants);

            extractConstantsFromCondition(condition, thenConstants, elseConstants);

            savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            changed |= visit(thenBranch, thenConstants);

            if (elseBranch != null) {
                visitedNodes.clear();
                changed |= visit(elseBranch, elseConstants);
            }

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);

            Set<String> allVars = new HashSet<>();
            allVars.addAll(thenConstants.keySet());
            if (elseBranch != null) {
                allVars.addAll(elseConstants.keySet());
            }

            for (String var : allVars) {
                boolean inconsistentValue = false;

                if (elseBranch != null) {
                    if (thenConstants.containsKey(var) && elseConstants.containsKey(var)) {
                        Object thenValue = thenConstants.get(var);
                        Object elseValue = elseConstants.get(var);

                        if (!Objects.equals(thenValue, elseValue)) {
                            inconsistentValue = true;
                        }
                    } else if (thenConstants.containsKey(var) || elseConstants.containsKey(var)) {
                        inconsistentValue = true;
                    }
                }

                if (inconsistentValue || modifiedVars.contains(var)) {
                    constants.remove(var);
                    registerMultiAssignedVariable(var);
                }
            }

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private boolean isVariableNode(JmmNode node) {
        return "VAR_REF_EXPR".equals(node.getKind()) ||
                node.getKind().contains("Identifier") ||
                (node.hasAttribute("name") && !node.hasAttribute("value"));
    }

    private Set<String> extractVariablesFromCondition(JmmNode conditionNode) {
        Set<String> variables = new HashSet<>();
        extractVariablesRecursive(conditionNode, variables);
        return variables;
    }

    private void extractVariablesRecursive(JmmNode node, Set<String> variables) {
        if (isVariableNode(node) && node.hasAttribute("name")) {
            variables.add(node.get("name"));
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            extractVariablesRecursive(node.getChild(i), variables);
        }
    }

    private Boolean visitBlockStmt(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;
            Set<JmmNode> savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            for (JmmNode child : node.getChildren()) {
                changed |= visit(child, constants);
            }

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private Boolean visitReturnStmt(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;
            Set<JmmNode> savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            for (JmmNode child : node.getChildren()) {
                if ("VAR_REF_EXPR".equals(child.getKind()) && child.hasAttribute("name")) {
                    String varName = child.get("name");

                    if (isVariableInIfElse(varName)) {
                        constants.remove(varName);
                        registerMultiAssignedVariable(varName);

                        if (child.hasAttribute("optimized") && "true".equals(child.get("optimized"))) {
                            String origVar = child.get("name");

                            Set<String> attrsToRemove = new HashSet<>(child.getAttributes());
                            attrsToRemove.remove("name");
                            attrsToRemove.remove("line");
                            attrsToRemove.remove("column");

                            for (String attr : attrsToRemove) {
                                AstNodeUtils.removeAttribute(child, attr);
                            }

                            child.put("name", origVar);
                            changed = true;
                        }
                    }
                }

                boolean childChanged = visit(child, constants);
                changed |= childChanged;
            }

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private boolean isVariableInIfElse(String varName) {
        if (multiAssignedVariables.contains(varName)) {
            return true;
        }

        if (currentMethod == null) {
            return false;
        }

        JmmNode methodNode = findMethodNode(currentMethod);
        if (methodNode == null) {
            return false;
        }

        int assignmentBlocks = 0;

        for (int i = 0; i < methodNode.getNumChildren(); i++) {
            JmmNode child = methodNode.getChild(i);

            if ("IfStmt".equals(child.getKind())) {
                if (child.getNumChildren() > 1) {
                    JmmNode thenBlock = child.getChild(1);
                    if (containsAssignmentTo(thenBlock, varName)) {
                        assignmentBlocks++;
                    }
                }

                if (child.getNumChildren() > 2) {
                    JmmNode elseBlock = child.getChild(2);
                    if (containsAssignmentTo(elseBlock, varName)) {
                        assignmentBlocks++;
                    }
                }
            }

            if (("ASSIGN_STMT".equals(child.getKind()) || "AssignStatement".equals(child.getKind())) &&
                    isAssignmentTo(child, varName)) {
                assignmentBlocks++;
            }
        }

        if (assignmentBlocks > 1) {
            registerMultiAssignedVariable(varName);
            return true;
        }

        return false;
    }

    private JmmNode findMethodNode(String methodName) {
        for (JmmNode node : visitedNodes) {
            JmmNode methodNode = findClosestMethodNode(node);
            if (methodNode != null && methodNode.hasAttribute("name") &&
                    methodNode.get("name").equals(methodName)) {
                return methodNode;
            }
        }

        return null;
    }

    private JmmNode findClosestMethodNode(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("METHOD_DECL".equals(current.getKind())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean containsAssignmentTo(JmmNode node, String varName) {
        if (isAssignmentTo(node, varName)) {
            return true;
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            if (containsAssignmentTo(node.getChild(i), varName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAssignmentTo(JmmNode node, String varName) {
        if (("ASSIGN_STMT".equals(node.getKind()) || "AssignStatement".equals(node.getKind())) &&
                node.getNumChildren() > 0) {

            JmmNode lhs = node.getChild(0);
            if (("IdentifierLValue".equals(lhs.getKind()) || lhs.getKind().contains("Identifier")) &&
                    lhs.hasAttribute("name") && lhs.get("name").equals(varName)) {
                return true;
            }
        }

        return false;
    }

    public Set<String> getLoopConditionVariables() {
        return Collections.unmodifiableSet(loopConditionVariables);
    }

    private Boolean visitWhileStmt(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;

            if (node.getNumChildren() < 2) {
                return defaultVisit(node, constants);
            }

            JmmNode condition = node.getChild(0);
            JmmNode body = node.getChild(1);

            Set<String> conditionVariables = extractConditionVariables(condition);

            Set<String> modifiedVars = findModifiedVariables(body);

            markLoopConditionVariables(condition);

            Map<String, Object> conditionConstants = new HashMap<>(constants);

            boolean conditionChanged = visit(condition, conditionConstants);
            changed |= conditionChanged;

            for (String var : conditionVariables) {
                registerLoopConditionVariable(var);
            }

            for (String var : modifiedVars) {
                constants.remove(var);

                if (conditionVariables.contains(var)) {
                    registerLoopModifiedVariable(var);
                }
            }

            Map<String, Object> bodyConstants = deepCopyConstants(constants);
            for (String var : modifiedVars) {
                bodyConstants.remove(var);
            }

            boolean bodyChanged = visit(body, bodyConstants);
            changed |= bodyChanged;

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private Set<String> extractConditionVariables(JmmNode conditionNode) {
        Set<String> variables = new HashSet<>();
        collectVariables(conditionNode, variables);
        return variables;
    }

    private void collectVariables(JmmNode node, Set<String> variables) {
        if ("VAR_REF_EXPR".equals(node.getKind()) && node.hasAttribute("name")) {
            variables.add(node.get("name"));
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            collectVariables(node.getChild(i), variables);
        }
    }

    private void markLoopConditionVariables(JmmNode node) {
        if ("VAR_REF_EXPR".equals(node.getKind()) && node.hasAttribute("name")) {
            node.put("loopCondition", "true");
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            markLoopConditionVariables(node.getChild(i));
        }
    }

    private void registerLoopConditionVariable(String varName) {
        loopConditionVariables.add(varName);

        if (symbolTable.putObject("loopConditionVariables", loopConditionVariables) == null) {
            Map<String, Set<String>> methodMap = new HashMap<>();
            if (currentMethod != null) {
                methodMap.put(currentMethod, new HashSet<>(Arrays.asList(varName)));
            }
            symbolTable.putObject("methodLoopConditionVars", methodMap);
        }
    }

    private boolean isLoopVariable(String varName) {
        if (loopModifiedVariables.contains(varName)) {
            return true;
        }

        if (loopConditionVariables.contains(varName)) {
            return true;
        }

        return false;
    }

    private Boolean visitVarRefExpr(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node)) {
            return false;
        }
        visitedNodes.add(node);

        if (node.hasAttribute("name")) {
            String varName = node.get("name");

            boolean isMultiAssigned = isMultiAssignedVariable(varName);

            boolean shouldPreserve = shouldPreserveVariable(varName) || isMultiAssigned;

            if (shouldPreserve) {
                if (loopModifiedVariables.contains(varName)) {
                    node.put("debug_loopModified", "true");
                }
                if (loopConditionVariables.contains(varName)) {
                    node.put("debug_loopCondition", "true");
                }

                if (constants.containsKey(varName)) {
                    node.put("constValue", constants.get(varName).toString());
                }
                return false;
            }

            if (constants.containsKey(varName)) {
                Object constantValue = constants.get(varName);

                if (replaceWithLiteral(node, constantValue)) {
                    hasChanged = true;

                    node.put("originalVar", varName);
                    return true;
                }
            }
        }

        return false;
    }

    private Boolean visitBinaryExpr(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;

            for (int i = 0; i < node.getNumChildren(); i++) {
                Boolean childChanged = visit(node.getChild(i), constants);
                changed |= childChanged;
            }

            boolean isInsideLoop = isInsideLoopBody(node);

            for (int i = 0; i < node.getNumChildren(); i++) {
                JmmNode childNode = node.getChild(i);

                if (childNode.getKind().equals("VAR_REF_EXPR") && childNode.hasAttribute("name")) {
                    String varName = childNode.get("name");

                    boolean isLoopVar = shouldPreserveVariable(varName);

                    if (!isLoopVar && constants.containsKey(varName)) {
                        Object constantValue = constants.get(varName);
                        replaceWithLiteral(childNode, constantValue);
                        changed = true;
                    }
                }
            }

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private boolean isInsideLoopBody(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if (current.getKind().equals("WhileStmt") ||
                    current.getKind().equals("WhileStatement")) {
                JmmNode parent = current.getParent();
                if (parent != null && parent.getNumChildren() > 1) {
                    return current != parent.getChild(0);
                }
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean shouldPreserveVariable(String varName) {
        return loopModifiedVariables.contains(varName) &&
                loopConditionVariables.contains(varName);
    }

    private Boolean defaultVisit(JmmNode node, Map<String, Object> constants) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return false;
        }
        visitedNodes.add(node);
        recursionDepth++;

        try {
            boolean changed = false;
            Set<JmmNode> savedVisited = new HashSet<>(visitedNodes);
            visitedNodes.clear();

            for (JmmNode child : node.getChildren()) {
                changed |= visit(child, constants);
            }

            visitedNodes.clear();
            visitedNodes.addAll(savedVisited);

            if (changed) {
                this.hasChanged = true;
            }

            return changed;
        } finally {
            recursionDepth--;
        }
    }

    private Set<String> findModifiedVariables(JmmNode node) {
        return ModifiedVariablesFinderUtil.findModifiedVariables(node);
    }

    private void extractConstantsFromCondition(JmmNode condition,
                                               Map<String, Object> thenConstants,
                                               Map<String, Object> elseConstants) {
        if (!isEqualityComparison(condition)) {
            return;
        }

        JmmNode left = condition.getChild(0);
        JmmNode right = condition.getChild(1);
        String operator = getOperator(condition);

        if ("==".equals(operator) || "EQ".equals(operator)) {
            if (isVariableReference(left) && isLiteral(right)) {
                String varName = left.get("name");
                Object literalValue = getLiteralValue(right);
                thenConstants.put(varName, literalValue);
            }
            else if (isLiteral(left) && isVariableReference(right)) {
                String varName = right.get("name");
                Object literalValue = getLiteralValue(left);
                thenConstants.put(varName, literalValue);
            }
        }
        else if ("!=".equals(operator) || "NOT_EQ".equals(operator) || "NE".equals(operator)) {
            if (isVariableReference(left) && isLiteral(right)) {
                String varName = left.get("name");
                Object literalValue = getLiteralValue(right);
                elseConstants.put(varName, literalValue);
            }
            else if (isLiteral(left) && isVariableReference(right)) {
                String varName = right.get("name");
                Object literalValue = getLiteralValue(left);
                elseConstants.put(varName, literalValue);
            }
        }
    }

    private boolean isEqualityComparison(JmmNode node) {
        if (node.getNumChildren() < 2) {
            return false;
        }

        String kind = node.getKind();
        if (!"RELATIONAL_EXPR".equals(kind) &&
                !"EQUALITY_EXPR".equals(kind) &&
                !kind.contains("Relation")) {
            return false;
        }

        String operator = getOperator(node);
        return "==".equals(operator) || "!=".equals(operator) ||
                "EQ".equals(operator) || "NOT_EQ".equals(operator) ||
                "NE".equals(operator);
    }

    private String getOperator(JmmNode node) {
        if (node.hasAttribute("operator")) {
            return node.get("operator");
        } else if (node.hasAttribute("op")) {
            return node.get("op");
        }
        return "";
    }

    private boolean isSimpleVariable(JmmNode node) {
        return ("IdentifierLValue".equals(node.getKind()) ||
                "VAR_REF_EXPR".equals(node.getKind()) ||
                "IdentifierReference".equals(node.getKind())) &&
                node.hasAttribute("name");
    }

    private boolean isVariableReference(JmmNode node) {
        return isSimpleVariable(node);
    }

    private boolean isLiteral(JmmNode node) {
        String kind = node.getKind();

        if ("IntLiteral".equals(kind) || "INTEGER_LITERAL".equals(kind) ||
                "TrueLiteral".equals(kind) || "FalseLiteral".equals(kind) ||
                "TrueLiteralExpr".equals(kind) || "FalseLiteralExpr".equals(kind)) {
            return true;
        }

        if (node.hasAttribute("kind")) {
            String nodeKind = node.get("kind");
            return "IntLiteral".equals(nodeKind) || "INTEGER_LITERAL".equals(nodeKind) ||
                    "TrueLiteral".equals(nodeKind) || "FalseLiteral".equals(nodeKind) ||
                    "TrueLiteralExpr".equals(nodeKind) || "FalseLiteralExpr".equals(nodeKind);
        }

        return false;
    }

    private Object getLiteralValue(JmmNode node) {
        String kind = node.getKind();

        if ("IntLiteral".equals(kind) || "INTEGER_LITERAL".equals(kind) ||
                (node.hasAttribute("kind") &&
                        ("IntLiteral".equals(node.get("kind")) ||
                                "INTEGER_LITERAL".equals(node.get("kind"))))) {

            if (node.hasAttribute("value")) {
                try {
                    return Integer.parseInt(node.get("value"));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }

        if ("TrueLiteral".equals(kind) || "TrueLiteralExpr".equals(kind) ||
                (node.hasAttribute("kind") &&
                        ("TrueLiteral".equals(node.get("kind")) ||
                                "TrueLiteralExpr".equals(node.get("kind"))))) {

            return Boolean.TRUE;
        }

        if ("FalseLiteral".equals(kind) || "FalseLiteralExpr".equals(kind) ||
                (node.hasAttribute("kind") &&
                        ("FalseLiteral".equals(node.get("kind")) ||
                                "FalseLiteralExpr".equals(node.get("kind"))))) {

            return Boolean.FALSE;
        }

        throw new IllegalArgumentException("Node is not a recognized literal: " + node.getKind());
    }

    private boolean hasConstantValue(JmmNode node, Map<String, Object> constants) {
        if (!isVariableReference(node)) {
            return false;
        }

        String varName = node.get("name");
        return constants.containsKey(varName);
    }

    private boolean replaceWithLiteral(JmmNode node, Object value) {
        try {
            int line = node.hasAttribute("line") ? Integer.parseInt(node.get("line")) : -1;
            int column = node.hasAttribute("column") ? Integer.parseInt(node.get("column")) : -1;
            String varName = node.hasAttribute("name") ? node.get("name") : "<unknown>";

            AstNodeUtils.removeAllChildren(node);

            Set<String> attrsToRemove = new HashSet<>(node.getAttributes());
            attrsToRemove.remove("line");
            attrsToRemove.remove("column");

            for (String attr : attrsToRemove) {
                AstNodeUtils.removeAttribute(node, attr);
            }

            if (value instanceof Integer) {
                node.put("kind", "IntLiteral");
                node.put("value", value.toString());
                node.put("type", "int");
            } else if (value instanceof Boolean) {
                boolean boolValue = (Boolean) value;
                node.put("kind", boolValue ? "TrueLiteral" : "FalseLiteral");
                node.put("value", value.toString());
                node.put("type", "boolean");
            } else {
                return false;
            }

            node.put("optimized", "true");
            node.put("optimizedBy", "ConstantPropagation");
            node.put("originalVar", varName);

            if (line > 0) node.put("line", Integer.toString(line));
            if (column > 0) node.put("column", Integer.toString(column));

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, Object> deepCopyConstants(Map<String, Object> constants) {
        return new HashMap<>(constants);
    }

    private void registerLoopModifiedVariable(String varName) {
        loopModifiedVariables.add(varName);
        multiAssignedVariables.add(varName);

        if (currentMethod != null) {
            methodMultiAssignedVars.computeIfAbsent(currentMethod, k -> new HashSet<>())
                    .add(varName);
        }
    }

    private void registerMultiAssignedVariable(String varName) {
        multiAssignedVariables.add(varName);

        if (currentMethod != null) {
            methodMultiAssignedVars.computeIfAbsent(currentMethod, k -> new HashSet<>())
                    .add(varName);
        }

        if (symbolTable.putObject("multiAssignedVars", multiAssignedVariables) == null) {
            Map<String, Set<String>> methodMap = new HashMap<>();
            methodMap.put(currentMethod, new HashSet<>(Arrays.asList(varName)));
            symbolTable.putObject("methodMultiAssignedVars", methodMap);
        }
    }

    private boolean isMultiAssignedVariable(String varName) {
        if (multiAssignedVariables.contains(varName)) {
            return true;
        }

        if (currentMethod != null) {
            Set<String> methodVars = methodMultiAssignedVars.get(currentMethod);
            if (methodVars != null && methodVars.contains(varName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isLoopModifiedVariable(String varName) {
        return loopModifiedVariables.contains(varName);
    }

    public Set<String> getLoopModifiedVariables() {
        return loopModifiedVariables;
    }

    private boolean isInLoopContext(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("WhileStmt".equals(current.getKind()) || current.getKind().contains("While")) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isUsedInRelationalExpression(JmmNode node) {
        JmmNode parent = node.getParent();
        if (parent != null) {
            String parentKind = parent.getKind();
            return "RELATIONAL_EXPR".equals(parentKind) ||
                    parentKind.contains("Relational") ||
                    parentKind.contains("RELATION");
        }
        return false;
    }

    public boolean hasChanged() {
        boolean changed = this.hasChanged;
        this.hasChanged = false;
        return changed;
    }
}