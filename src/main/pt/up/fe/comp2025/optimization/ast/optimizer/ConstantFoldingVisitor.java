package pt.up.fe.comp2025.optimization.ast.optimizer;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.optimization.ast.util.AstNodeUtils;

import java.util.HashSet;
import java.util.Set;

import static pt.up.fe.comp2025.optimization.ast.util.AstNodeUtils.isIntegerValue;

/**
 * Visitor responsible for constant folding optimization.
 * Identifies expressions that can be evaluated at compile time.
 */
public class ConstantFoldingVisitor extends PreorderJmmVisitor<Void, Void> {

    private final SymbolTable symbolTable;
    private boolean hasChanged;
    private final Set<JmmNode> visitedNodes;
    private Set<String> loopModifiedVariables;
    private int recursionDepth;
    private static final int MAX_RECURSION_DEPTH = 50;
    private String currentMethod;
    private Set<String> loopConditionVariables = new HashSet<>();

    public ConstantFoldingVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.hasChanged = false;
        this.visitedNodes = new HashSet<>();
        this.loopModifiedVariables = new HashSet<>();
        this.recursionDepth = 0;
    }

    @Override
    protected void buildVisitor() {
        addVisit("BINARY_EXPR", this::visitBinaryExpr);
        addVisit("MULTIPLICATIVE_EXPR", this::visitBinaryExpr);
        addVisit("ADDITIVE_EXPR", this::visitBinaryExpr);
        addVisit("RELATIONAL_EXPR", this::visitBinaryExpr);
        addVisit("EQUALITY_EXPR", this::visitBinaryExpr);
        addVisit("LogicalAndExpr", this::visitBinaryExpr);
        addVisit("LogicalOrExpr", this::visitBinaryExpr);
        addVisit("LOGICAL_AND_EXPR", this::visitBinaryExpr);
        addVisit("LOGICAL_OR_EXPR", this::visitBinaryExpr);

        addVisit("UNARY_EXPR", this::visitUnaryExpr);
        addVisit("SignExpr", this::visitUnaryExpr);
        addVisit("NotExpr", this::visitUnaryExpr);

        addVisit("IfStmt", this::visitIfStmt);
        addVisit("WhileStmt", this::visitWhileStmt);

        setDefaultVisit(this::defaultVisit);
    }


    public void setCurrentMethod(String methodName) {
        this.currentMethod = methodName;
    }
    /**
     * Visits a binary expression for constant folding.
     */
    private Void visitBinaryExpr(JmmNode node, Void unused) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return null;
        }

        visitedNodes.add(node);
        recursionDepth++;

        try {
            // Visit children first (crucial for bottom-up approach)
            for (JmmNode child : node.getChildren()) {
                visit(child);
            }

            if (node.getNumChildren() < 2) {
                return null;
            }

            JmmNode left = node.getChild(0);
            JmmNode right = node.getChild(1);
            String operator = getOperator(node);

            // Skip folding if any operand is related to loop variables
            if (containsLoopVariable(left) || containsLoopVariable(right)) {
                return null;
            }

            boolean localChange = false;

            // Tratar expressões com parênteses - extrair valores dos parênteses quando possível
            Integer leftValue = getIntegerValue(left);
            Integer rightValue = getIntegerValue(right);

            // Se ambos operandos têm valores inteiros efetivos (diretos ou dentro de parênteses)
            if (leftValue != null && rightValue != null) {
                // Expressões relacionais
                if (isRelationalOperator(operator)) {
                    Boolean result = evaluateRelationalExpression(leftValue, rightValue, operator);
                    if (result != null) {
                        transformToBooleanLiteral(node, result);
                        hasChanged = true;
                        localChange = true;
                    }
                }
                // Expressões aritméticas
                else {
                    Integer result = null;
                    switch (operator) {
                        case "+", "PLUS": result = leftValue + rightValue; break;
                        case "-", "MINUS": result = leftValue - rightValue; break;
                        case "*", "STAR": result = leftValue * rightValue; break;
                        case "/", "DIV":
                            if (rightValue != 0) result = leftValue / rightValue;
                            break;
                        case "%", "MOD":
                            if (rightValue != 0) result = leftValue % rightValue;
                            break;
                    }

                    if (result != null) {
                        transformToIntegerLiteral(node, result);
                        hasChanged = true;
                        localChange = true;
                    }
                }
            }
            // Se não conseguirmos extrair valores inteiros, verificar se são literais booleanos
            else if (isBooleanLiteral(left) && isBooleanLiteral(right)) {
                foldBooleanBinaryExpr(node, left, right);
                localChange = hasChanged;
            }
            // Verificar literais inteiros regulares (sem parênteses)
            else if (isIntegerLiteral(left) && isIntegerLiteral(right)) {
                foldIntegerBinaryExpr(node, left, right);
                localChange = hasChanged;
            }
            // Quando nenhuma das opções acima funciona, tentar otimizações parciais
            else {
                boolean previousChanged = hasChanged;
                optimizePartialExpression(node, left, right);
                localChange = hasChanged && !previousChanged;
            }

            // Se o nó foi alterado, remova-o dos nós visitados para permitir revisita
            if (localChange) {
                visitedNodes.remove(node);
            }

        } finally {
            recursionDepth--;
        }

        return null;
    }

    /**
     * Visits a while statement for optimizing constant conditions.
     */
    private Void visitWhileStmt(JmmNode node, Void unused) {
        if (visitedNodes.contains(node)) {
            return null;
        }

        visitedNodes.add(node);

        // Visit condition and find all variables modified in loop body
        if (node.getNumChildren() > 0) {
            JmmNode condition = node.getChild(0);

            // Identificar TODAS as variáveis na condição do loop
            Set<String> conditionVariables = extractConditionVariables(condition);

            // Marcar todas as variáveis da condição
            markLoopConditionVariables(condition);

            // Para cada variável da condição, preservá-la
            for (String var : conditionVariables) {
                // Registrar como variável especial
                registerLoopConditionVariable(var);
            }

            // Visit body first to identify loop variables
            if (node.getNumChildren() > 1) {
                JmmNode body = node.getChild(1);

                // Coletar variáveis modificadas no corpo
                Set<String> bodyModifiedVars = new HashSet<>();
                collectLoopModifiedVariables(body, bodyModifiedVars);

                // Para variáveis modificadas que também estão na condição
                for (String var : bodyModifiedVars) {
                    if (conditionVariables.contains(var)) {
                        // Registrar como variável de loop completa
                        loopModifiedVariables.add(var);
                    }
                }

                visit(body);
            }

            // Then process condition, now knowing which variables are modified in the loop
            visit(condition);

            // If condition is a constant boolean, optimize
            if (isBooleanLiteral(condition)) {
                boolean value = isTrueLiteral(condition);

                if (!value) {
                    // Condition always false - remove the loop entirely
                    AstNodeUtils.removeNode(node);
                    hasChanged = true;
                }
            }
        }

        return null;
    }

    /**
     * Extrai todas as variáveis usadas em uma condição de loop
     */
    private Set<String> extractConditionVariables(JmmNode conditionNode) {
        Set<String> variables = new HashSet<>();
        collectVariables(conditionNode, variables);
        return variables;
    }

    /**
     * Coleta recursivamente todas as referências a variáveis em uma expressão
     */
    private void collectVariables(JmmNode node, Set<String> variables) {
        // Identificar referências a variáveis
        if ("VAR_REF_EXPR".equals(node.getKind()) && node.hasAttribute("name")) {
            variables.add(node.get("name"));
        }

        // Processar recursivamente os filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            collectVariables(node.getChild(i), variables);
        }
    }

    /**
     * Marca recursivamente nós da condição com um atributo para identificá-los
     */
    private void markLoopConditionVariables(JmmNode node) {
        // Marcar nós de referência a variáveis
        if ("VAR_REF_EXPR".equals(node.getKind()) && node.hasAttribute("name")) {
            node.put("loopCondition", "true");
        }

        // Processar recursivamente os filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            markLoopConditionVariables(node.getChild(i));
        }
    }

    /**
     * Registra uma variável como parte da condição do loop
     */
    private void registerLoopConditionVariable(String varName) {
        loopConditionVariables.add(varName);
    }

    /**
     * Collects variables that are modified within a loop.
     */
    private void collectLoopModifiedVariables(JmmNode node, Set<String> modifiedVars) {
        // Find assignments in the loop body
        if (node.getKind().equals("ASSIGN_STMT") || node.getKind().equals("AssignStatement")) {
            if (node.getNumChildren() > 0) {
                JmmNode lhs = node.getChild(0);
                if ((lhs.getKind().equals("IdentifierLValue") ||
                        lhs.getKind().contains("Identifier")) &&
                        lhs.hasAttribute("name")) {
                    String varName = lhs.get("name");
                    modifiedVars.add(varName);
                }
            }
        }

        // Recursively check all children
        for (int i = 0; i < node.getNumChildren(); i++) {
            collectLoopModifiedVariables(node.getChild(i), modifiedVars);
        }
    }
    /**
     * Checks if a node or its children contain references to loop variables.
     */
    private boolean containsLoopVariable(JmmNode node) {
        // Verificar se este nó é uma variável que está na lista de variáveis de loop
        if (isVariableNode(node) && node.hasAttribute("name")) {
            String varName = node.get("name");

            // Verificar se é variável de loop ou parte da condição
            if (isLoopVariable(varName) || loopConditionVariables.contains(varName)) {
                return true;
            }
        }

        // Check children recursively
        for (JmmNode child : node.getChildren()) {
            if (containsLoopVariable(child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica se uma variável é modificada em um loop ou faz parte de uma condição.
     */
    private boolean isLoopVariable(String varName) {
        if (varName == null) {
            return false;
        }

        // Verificar no conjunto de variáveis de loop ou de condição
        return loopModifiedVariables.contains(varName) || loopConditionVariables.contains(varName);
    }

    /**
     * Verifica se uma variável é usada como contador em loops no método.
     */
    private boolean isCounterInLoops(JmmNode methodNode, String varName) {
        // Buscar todos os loops no método
        for (JmmNode child : methodNode.getChildren()) {
            if ("WhileStmt".equals(child.getKind())) {
                // Verificar se o loop usa essa variável na condição
                if (child.getNumChildren() > 0) {
                    JmmNode condition = child.getChild(0);
                    if (containsVariable(condition, varName)) {
                        // Verificar se também é modificada no corpo do loop
                        if (child.getNumChildren() > 1) {
                            JmmNode body = child.getChild(1);
                            if (containsAssignmentTo(body, varName)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Verifica se um nó ou seus filhos contêm referência a uma variável específica.
     */
    private boolean containsVariable(JmmNode node, String varName) {
        if ("VAR_REF_EXPR".equals(node.getKind()) &&
                node.hasAttribute("name") &&
                node.get("name").equals(varName)) {
            return true;
        }

        for (JmmNode child : node.getChildren()) {
            if (containsVariable(child, varName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Encontra o nó de método que contém o nó atual.
     */
    private JmmNode findEnclosingMethodNode(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("METHOD_DECL".equals(current.getKind())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Obtém um valor de um nó, verificando todos os possíveis nomes de atributos.
     */
    private String getValue(JmmNode node) {
        if (node.hasAttribute("value")) return node.get("value");
        if (node.hasAttribute("val")) return node.get("val");
        return null;
    }
    /**
     * Verifica se um nó ou sua subárvore contém uma atribuição à variável especificada.
     */
    private boolean containsAssignmentTo(JmmNode node, String varName) {
        // Verificar se este nó é uma atribuição à variável
        if (("ASSIGN_STMT".equals(node.getKind()) || "AssignStatement".equals(node.getKind())) &&
                node.getNumChildren() > 0) {

            JmmNode lhs = node.getChild(0);
            if (("IdentifierLValue".equals(lhs.getKind()) || lhs.getKind().contains("Identifier")) &&
                    lhs.hasAttribute("name") && lhs.get("name").equals(varName)) {
                return true;
            }
        }

        // Verificar recursivamente em todos os filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (containsAssignmentTo(node.getChild(i), varName)) {
                return true;
            }
        }

        return false;
    }
    /**
     * Transforma um nó em uma expressão de negação unária.
     */
    private void transformToUnaryMinus(JmmNode node, JmmNode operand) {
        try {
            // Preservar posição para diagnóstico
            String line = node.hasAttribute("line") ? node.get("line") : null;
            String column = node.hasAttribute("column") ? node.get("column") : null;

            // Limpar filhos e atributos
            AstNodeUtils.removeAllChildren(node);
            Set<String> currentAttrs = new HashSet<>(node.getAttributes());
            for (String attr : currentAttrs) {
                if (!attr.equals("line") && !attr.equals("column")) {
                    AstNodeUtils.removeAttribute(node, attr);
                }
            }

            // Configurar como expressão unária
            node.put("kind", "SignExpr");
            node.put("operator", "-");
            node.put("optimized", "true");
            node.add(operand);

            // Restaurar posição
            if (line != null && !node.hasAttribute("line")) node.put("line", line);
            if (column != null && !node.hasAttribute("column")) node.put("column", column);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Evaluates a relational expression with constant values.
     */
    private Boolean evaluateRelationalExpression(int leftValue, int rightValue, String operator) {
        switch (operator) {
            case "<", "LT": return leftValue < rightValue;
            case ">", "BT", "GT": return leftValue > rightValue;
            case "<=", "LOE", "LE": return leftValue <= rightValue;
            case ">=", "BOE", "GE": return leftValue >= rightValue;
            case "==", "EQ": return leftValue == rightValue;
            case "!=", "NOT_EQ", "NE": return leftValue != rightValue;
            default: return null;
        }
    }

    /**
     * Checks if an operator is relational.
     */
    private boolean isRelationalOperator(String operator) {
        return operator.equals("<") || operator.equals(">") ||
                operator.equals("<=") || operator.equals(">=") ||
                operator.equals("==") || operator.equals("!=") ||
                operator.equals("LT") || operator.equals("GT") ||
                operator.equals("LE") || operator.equals("GE") ||
                operator.equals("EQ") || operator.equals("NE");
    }

    /**
     * Folds an integer binary expression with constant operands.
     */
    private void foldIntegerBinaryExpr(JmmNode node, JmmNode left, JmmNode right) {
        int leftValue = Integer.parseInt(left.get("value"));
        int rightValue = Integer.parseInt(right.get("value"));
        String operator = getOperator(node);

        Integer result = null;
        Boolean boolResult = null;

        switch (operator) {
            case "+", "PLUS": result = leftValue + rightValue; break;
            case "-", "MINUS": result = leftValue - rightValue; break;
            case "*", "STAR": result = leftValue * rightValue; break;
            case "/", "DIV":
                if (rightValue != 0) result = leftValue / rightValue;
                break;
            case "%", "MOD":
                if (rightValue != 0) result = leftValue % rightValue;
                break;
            case "<", "LT": boolResult = leftValue < rightValue; break;
            case ">", "BT", "GT": boolResult = leftValue > rightValue; break;
            case "<=", "LOE", "LE": boolResult = leftValue <= rightValue; break;
            case ">=", "BOE", "GE": boolResult = leftValue >= rightValue; break;
            case "==", "EQ": boolResult = leftValue == rightValue; break;
            case "!=", "NOT_EQ", "NE": boolResult = leftValue != rightValue; break;
        }

        if (result != null) {
            transformToIntegerLiteral(node, result);
            hasChanged = true;
        } else if (boolResult != null) {
            transformToBooleanLiteral(node, boolResult);
            hasChanged = true;
        }
    }

    /**
     * Folds a boolean binary expression with constant operands.
     */
    private void foldBooleanBinaryExpr(JmmNode node, JmmNode left, JmmNode right) {
        boolean leftValue = isTrueLiteral(left);
        boolean rightValue = isTrueLiteral(right);
        String operator = getOperator(node);

        Boolean result = null;

        switch (operator) {
            case "&&", "AND": result = leftValue && rightValue; break;
            case "||", "OR": result = leftValue || rightValue; break;
            case "==", "EQ": result = leftValue == rightValue; break;
            case "!=", "NOT_EQ", "NE": result = leftValue != rightValue; break;
        }

        if (result != null) {
            transformToBooleanLiteral(node, result);
            hasChanged = true;
        }
    }

    /**
     * Visits a unary expression for constant folding.
     */
    private Void visitUnaryExpr(JmmNode node, Void unused) {
        if (visitedNodes.contains(node)) {
            return null;
        }

        visitedNodes.add(node);

        // Visit operand
        if (node.getNumChildren() > 0) {
            visit(node.getChild(0));
        }

        if (node.getNumChildren() > 0) {
            JmmNode operand = node.getChild(0);
            String operator = getOperator(node);

            // Skip if operand is a loop variable
            if (isVariableNode(operand) && isLoopVariable(getVariableName(operand))) {
                return null;
            }

            if (isIntegerLiteral(operand)) {
                int value = Integer.parseInt(operand.get("value"));

                if (operator.equals("-") || operator.equals("MINUS")) {
                    transformToIntegerLiteral(node, -value);
                    hasChanged = true;
                }
            } else if (isBooleanLiteral(operand)) {
                boolean value = isTrueLiteral(operand);

                if (operator.equals("!") || operator.equals("NOT")) {
                    transformToBooleanLiteral(node, !value);
                    hasChanged = true;
                }
            }
        }

        return null;
    }

    /**
     * Visits an if statement for optimizing constant conditions.
     */
    private Void visitIfStmt(JmmNode node, Void unused) {
        if (visitedNodes.contains(node)) {
            return null;
        }

        visitedNodes.add(node);

        // Visit condition
        if (node.getNumChildren() > 0) {
            JmmNode condition = node.getChild(0);
            visit(condition);

            // If the condition is a constant boolean literal, simplify
            if (isBooleanLiteral(condition)) {
                boolean value = isTrueLiteral(condition);

                if (value) {
                    // Condition always true - replace with then branch
                    if (node.getNumChildren() > 1) {
                        JmmNode thenBranch = node.getChild(1);
                        AstNodeUtils.replaceNode(node, thenBranch);
                        hasChanged = true;
                    }
                } else {
                    // Condition always false - replace with else branch or remove
                    if (node.getNumChildren() > 2) {
                        JmmNode elseBranch = node.getChild(2);
                        AstNodeUtils.replaceNode(node, elseBranch);
                    } else {
                        // No else branch, remove if entirely
                        AstNodeUtils.removeNode(node);
                    }
                    hasChanged = true;
                }

                return null;
            }
        }

        // Visit rest of children
        for (JmmNode child : node.getChildren()) {
            visit(child);
        }

        return null;
    }




    /**
     * Default visitor for unmatched node types.
     */
    private Void defaultVisit(JmmNode node, Void unused) {
        if (visitedNodes.contains(node) || recursionDepth > MAX_RECURSION_DEPTH) {
            return null;
        }

        for (JmmNode child : node.getChildren()) {
            visit(child);
        }
        return null;
    }

    /**
     * Applies partial optimizations to expressions when full folding isn't possible.
     */
    private void optimizePartialExpression(JmmNode node, JmmNode left, JmmNode right) {
        String operator = getOperator(node);

        // Verificar se operandos estão relacionados a variáveis de loop
        boolean leftIsLoopVar = (isVariableNode(left) && isLoopVariable(getVariableName(left)));
        boolean rightIsLoopVar = (isVariableNode(right) && isLoopVariable(getVariableName(right)));
        boolean hasLoopVar = leftIsLoopVar || rightIsLoopVar;

        // Caso especial: expressões relacionais que envolvem variáveis de loop
        if (isRelationalOperator(operator) && hasLoopVar) {
            // Se a variável do loop está à esquerda e uma constante à direita
            if (leftIsLoopVar && isIntegerValue(right)) {
                // Preservar a variável de loop, mas permitir que o lado direito seja uma constante
                return;
            }

            // Se a variável do loop está à direita e uma constante à esquerda
            if (rightIsLoopVar && isIntegerValue(left)) {
                // Preservar a variável de loop, mas permitir que o lado esquerdo seja uma constante
                return;
            }

            // Se ambos operandos são variáveis de loop, não otimizar
            if (leftIsLoopVar && rightIsLoopVar) {
                return;
            }
        }

        // Caso especial: operações aritméticas envolvendo variáveis de loop
        if (("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                "/".equals(operator) || "%".equals(operator)) && hasLoopVar) {

            // Se a variável de loop está à esquerda e uma constante à direita
            // por exemplo: i + 1 ou i * 3
            if (leftIsLoopVar && isIntegerValue(right)) {
                // Preservar a expressão, mas garantir que o valor constante seja reconhecido
                if ("+".equals(operator) && "1".equals(getValue(right))) {
                    // Caso i + 1: não otimizar para preservar a estrutura do incremento
                    return;
                }
                return;
            }

            // Se a constante está à esquerda e a variável de loop à direita
            // por exemplo: 1 + i ou 3 * i
            if (rightIsLoopVar && isIntegerValue(left)) {
                return;
            }
        }

        // Identidades algebricas para multiplicação
        if ("*".equals(operator) || "STAR".equals(operator)) {
            // Qualquer número multiplicado por zero é zero
            if (isZero(left) || isZero(right)) {
                transformToIntegerLiteral(node, 0);
                hasChanged = true;
                return;
            }

            // Qualquer número multiplicado por um é o próprio número
            if (isOne(left)) {
                AstNodeUtils.replaceNode(node, right);
                hasChanged = true;
                return;
            }
            if (isOne(right)) {
                AstNodeUtils.replaceNode(node, left);
                hasChanged = true;
                return;
            }
        }

        // Identidades algebricas para adição
        if ("+".equals(operator) || "PLUS".equals(operator)) {
            // Adição com zero
            if (isZero(left)) {
                AstNodeUtils.replaceNode(node, right);
                hasChanged = true;
                return;
            }
            if (isZero(right)) {
                AstNodeUtils.replaceNode(node, left);
                hasChanged = true;
                return;
            }
        }

        // Identidades para subtração
        if ("-".equals(operator) || "MINUS".equals(operator)) {
            // Subtração por zero
            if (isZero(right)) {
                AstNodeUtils.replaceNode(node, left);
                hasChanged = true;
                return;
            }
            // Zero menos algum número
            if (isZero(left)) {
                // Transformar em negação unária
                transformToUnaryMinus(node, right);
                hasChanged = true;
                return;
            }
        }

        // Identidades para AND lógico
        if ("&&".equals(operator) || "AND".equals(operator)) {
            // true && x = x
            if (isTrue(left)) {
                AstNodeUtils.replaceNode(node, right);
                hasChanged = true;
                return;
            }
            // x && true = x
            if (isTrue(right)) {
                AstNodeUtils.replaceNode(node, left);
                hasChanged = true;
                return;
            }
            // false && x = false ou x && false = false
            if (isFalse(left) || isFalse(right)) {
                transformToBooleanLiteral(node, false);
                hasChanged = true;
                return;
            }
        }

        // Identidades para OR lógico
        if ("||".equals(operator) || "OR".equals(operator)) {
            // false || x = x
            if (isFalse(left)) {
                AstNodeUtils.replaceNode(node, right);
                hasChanged = true;
                return;
            }
            // x || false = x
            if (isFalse(right)) {
                AstNodeUtils.replaceNode(node, left);
                hasChanged = true;
                return;
            }
            // true || x = true ou x || true = true
            if (isTrue(left) || isTrue(right)) {
                transformToBooleanLiteral(node, true);
                hasChanged = true;
                return;
            }
        }
    }
    /**
     * Transforms a node into an integer literal.
     */
    private void transformToIntegerLiteral(JmmNode node, int value) {
        try {
            // Preserve position attributes
            String line = node.hasAttribute("line") ? node.get("line") : null;
            String column = node.hasAttribute("column") ? node.get("column") : null;

            // Remove existing children
            AstNodeUtils.removeAllChildren(node);

            // Remove all attributes except position
            Set<String> currentAttrs = new HashSet<>(node.getAttributes());
            for (String attr : currentAttrs) {
                if (!attr.equals("line") && !attr.equals("column")) {
                    AstNodeUtils.removeAttribute(node, attr);
                }
            }

            // Configure node as IntLiteral
            node.put("kind", "IntLiteral");
            node.put("value", String.valueOf(value));
            node.put("optimized", "true");
            node.put("originalKind", "ADDITIVE_EXPR");

            // Restore position info
            if (line != null && !node.hasAttribute("line")) node.put("line", line);
            if (column != null && !node.hasAttribute("column")) node.put("column", column);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Transforms a node into a boolean literal.
     */
    private void transformToBooleanLiteral(JmmNode node, boolean value) {
        try {
            // Preserve position attributes
            String line = node.hasAttribute("line") ? node.get("line") : null;
            String column = node.hasAttribute("column") ? node.get("column") : null;

            // Remove existing children and attributes
            AstNodeUtils.removeAllChildren(node);
            Set<String> currentAttrs = new HashSet<>(node.getAttributes());
            for (String attr : currentAttrs) {
                if (!attr.equals("line") && !attr.equals("column")) {
                    AstNodeUtils.removeAttribute(node, attr);
                }
            }

            // Set as TrueLiteral or FalseLiteral
            node.put("kind", value ? "TrueLiteral" : "FalseLiteral");
            node.put("value", value ? "true" : "false");
            node.put("optimized", "true");

            // Restore position info
            if (line != null && !node.hasAttribute("line")) node.put("line", line);
            if (column != null && !node.hasAttribute("column")) node.put("column", column);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a node is an integer literal.
     */
    private boolean isIntegerLiteral(JmmNode node) {
        String kind = node.getKind();

        if (("IntLiteral".equals(kind) || "INTEGER_LITERAL".equals(kind)) &&
                node.hasAttribute("value")) {
            return true;
        }

        if (node.hasAttribute("kind") &&
                ("IntLiteral".equals(node.get("kind")) || "INTEGER_LITERAL".equals(node.get("kind"))) &&
                node.hasAttribute("value")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a node is a boolean literal.
     */
    private boolean isBooleanLiteral(JmmNode node) {
        String kind = node.getKind();

        if ("TrueLiteral".equals(kind) || "FalseLiteral".equals(kind) ||
                "TrueLiteralExpr".equals(kind) || "FalseLiteralExpr".equals(kind)) {
            return true;
        }

        if (node.hasAttribute("kind") &&
                ("TrueLiteral".equals(node.get("kind")) || "FalseLiteral".equals(node.get("kind")) ||
                        "TrueLiteralExpr".equals(node.get("kind")) || "FalseLiteralExpr".equals(node.get("kind")))) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a node is a true literal.
     */
    private boolean isTrueLiteral(JmmNode node) {
        String kind = node.getKind();
        return "TrueLiteral".equals(kind) || "TrueLiteralExpr".equals(kind) ||
                (node.hasAttribute("kind") &&
                        ("TrueLiteral".equals(node.get("kind")) || "TrueLiteralExpr".equals(node.get("kind"))));
    }

    /**
     * Checks if a node represents a zero value.
     */
    private boolean isZero(JmmNode node) {
        return isIntegerLiteral(node) && "0".equals(node.get("value"));
    }

    /**
     * Checks if a node represents a one value.
     */
    private boolean isOne(JmmNode node) {
        return isIntegerLiteral(node) && "1".equals(node.get("value"));
    }

    /**
     * Checks if a node represents a true value.
     */
    private boolean isTrue(JmmNode node) {
        return isBooleanLiteral(node) && isTrueLiteral(node);
    }

    /**
     * Checks if a node represents a false value.
     */
    private boolean isFalse(JmmNode node) {
        return isBooleanLiteral(node) && !isTrueLiteral(node);
    }

    /**
     * Gets the operator from a node.
     */
    private String getOperator(JmmNode node) {
        if (node.hasAttribute("operator")) {
            return node.get("operator");
        } else if (node.hasAttribute("op")) {
            return node.get("op");
        } else {
            // Infer operator from kind
            String kind = node.getKind();
            if (kind.contains("MULTIPLICATIVE")) return "*";
            if (kind.contains("ADDITIVE")) return "+";
            if (kind.contains("RELATIONAL")) return "<";
            if (kind.contains("EQUALITY")) return "==";
            if (kind.contains("LOGICAL_AND") || kind.contains("LogicalAnd")) return "&&";
            if (kind.contains("LOGICAL_OR") || kind.contains("LogicalOr")) return "||";
        }
        return "+"; // Default
    }

    /**
     * Verifica se um nó representa uma variável.
     */
    private boolean isVariableNode(JmmNode node) {
        return "VAR_REF_EXPR".equals(node.getKind()) ||
                node.getKind().contains("Identifier") ||
                (node.hasAttribute("name") && !node.hasAttribute("value"));
    }

    /**
     * Extrai os nomes de variáveis usadas em uma expressão de condição.
     */
    private Set<String> extractVariablesFromCondition(JmmNode conditionNode) {
        Set<String> variables = new HashSet<>();
        extractVariablesRecursive(conditionNode, variables);
        return variables;
    }

    /**
     * Auxilia a extração de variáveis recursivamente de um nó da AST.
     */
    private void extractVariablesRecursive(JmmNode node, Set<String> variables) {
        // Verificar se o nó atual é uma referência de variável
        if (isVariableNode(node) && node.hasAttribute("name")) {
            variables.add(node.get("name"));
        }

        // Processar recursivamente todos os filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            extractVariablesRecursive(node.getChild(i), variables);
        }
    }
    /**
     * Gets the variable name from a node.
     */
    private String getVariableName(JmmNode node) {
        if (node.hasAttribute("name")) {
            return node.get("name");
        }
        return "";
    }

    /**
     * Sets loop-modified variables from another source.
     */
    public void setLoopModifiedVariables(Set<String> variables) {
        this.loopModifiedVariables = new HashSet<>(variables);
    }

    /**
     * Configura o conjunto de variáveis identificadas como parte da condição do loop.
     * Este método permite receber essa informação de outros visitors.
     *
     * @param variables Conjunto de nomes de variáveis usadas em condições de loop
     */
    public void setLoopConditionVariables(Set<String> variables) {
        this.loopConditionVariables = new HashSet<>(variables);
    }
    /**
     * Returns whether the visitor made any optimizations.
     */
    public boolean hasChanged() {
        boolean changed = this.hasChanged;
        visitedNodes.clear();
        return changed;
    }

    /**
     * Verifica se um nó é uma expressão parentizada.
     */
    private boolean isParenExpr(JmmNode node) {
        return "ParenExpr".equals(node.getKind());
    }

    /**
     * Obtém o valor inteiro de um nó, considerando expressões parentizadas.
     * Retorna null se o nó não for um literal inteiro ou uma expressão parentizada com literal.
     */
    private Integer getIntegerValue(JmmNode node) {
        // Caso seja um literal inteiro direto
        if (isIntegerLiteral(node) && node.hasAttribute("value")) {
            return Integer.parseInt(node.get("value"));
        }

        // Caso seja uma expressão parentizada
        if (isParenExpr(node) && node.getNumChildren() == 1) {
            JmmNode child = node.getChild(0);
            if (isIntegerLiteral(child) && child.hasAttribute("value")) {
                return Integer.parseInt(child.get("value"));
            }
        }

        return null;
    }

    /**
     * Verifica se um nó é um literal inteiro ou contém um literal inteiro dentro de parênteses.
     */
    private boolean isEffectiveIntegerLiteral(JmmNode node) {
        return isIntegerLiteral(node) ||
               (isParenExpr(node) && node.getNumChildren() == 1 && isIntegerLiteral(node.getChild(0)));
    }
}