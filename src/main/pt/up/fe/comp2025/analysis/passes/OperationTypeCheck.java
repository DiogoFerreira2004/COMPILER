package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

public class OperationTypeCheck extends AnalysisVisitor {

    private String currentMethod;
    private JmmNode currentMethodNode;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("MULTIPLICATIVE_EXPR", this::visitArithmeticExpr);
        addVisit("ADDITIVE_EXPR", this::visitArithmeticExpr);
        addVisit("RELATIONAL_EXPR", this::visitRelationalExpr);
        addVisit("LogicalAndExpr", this::visitLogicalExpr);
        addVisit("LogicalOrExpr", this::visitLogicalExpr);
        addVisit("NotExpr", this::visitNotExpr);
        addVisit("ParenExpr", this::visitParenExpr);
        addVisit("ParenExpression", this::visitParenExpr);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            currentMethodNode = method;
            System.out.println("DEBUG [OperationTypeCheck]: Entering method: " + currentMethod);
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitArithmeticExpr(JmmNode node, SymbolTable table) {

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.getChildren().size() < 2) {
            return null;
        }

        String operator = node.hasAttribute("operator") ? node.get("operator") :
                (node.hasAttribute("op") ? node.get("op") : "+");

        JmmNode leftOperand = node.getChildren().get(0);
        JmmNode rightOperand = node.getChildren().get(1);

        Type leftType = determineExpressionType(leftOperand, table);
        Type rightType = determineExpressionType(rightOperand, table);

        System.out.println("DEBUG [OperationTypeCheck]: Arithmetic operation '" + operator +
                "' between types: " + leftType.getName() + (leftType.isArray() ? "[]" : "") +
                " and " + rightType.getName() + (rightType.isArray() ? "[]" : ""));

        if (leftType.isArray() || rightType.isArray()) {
            String message = String.format(
                    "Invalid binary operation '%s' with array type. Arrays cannot be used in arithmetic operations.",
                    operator
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
            return null;
        }

        if (!("int".equals(leftType.getName()) && "int".equals(rightType.getName()))) {
            String message = String.format(
                    "Invalid binary operation '%s' between types '%s' and '%s'. Only int %s int is allowed.",
                    operator, leftType.getName(), rightType.getName(), operator
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
        }

        return null;
    }

    private Void visitRelationalExpr(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.getChildren().size() < 2) {
            return null;
        }

        String operator = node.hasAttribute("operator") ? node.get("operator") : "==";

        JmmNode leftOperand = node.getChildren().get(0);
        JmmNode rightOperand = node.getChildren().get(1);

        Type leftType = determineExpressionType(leftOperand, table);
        Type rightType = determineExpressionType(rightOperand, table);

        System.out.println("DEBUG [OperationTypeCheck]: Relational operation '" + operator +
                "' between types: " + leftType.getName() + (leftType.isArray() ? "[]" : "") +
                " and " + rightType.getName() + (rightType.isArray() ? "[]" : ""));

        if ("<".equals(operator) || ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            if (!"int".equals(leftType.getName()) || !"int".equals(rightType.getName())) {
                String message = String.format(
                        "Invalid comparison operation '%s' between types '%s' and '%s'. Only int %s int is allowed.",
                        operator, leftType.getName(), rightType.getName(), operator
                );
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
            }
        } else if ("==".equals(operator) || "!=".equals(operator)) {
            if (!areTypesCompatibleForEquality(leftType, rightType)) {
                String message = String.format(
                        "Invalid equality operation '%s' between types '%s%s' and '%s%s'. Types must be compatible.",
                        operator,
                        leftType.getName(), leftType.isArray() ? "[]" : "",
                        rightType.getName(), rightType.isArray() ? "[]" : ""
                );
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
            }
        }

        return null;
    }

    private Void visitLogicalExpr(JmmNode node, SymbolTable table) {

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.getChildren().size() < 2) {
            return null;
        }

        String operatorSymbol = node.getKind().equals("LogicalAndExpr") ? "&&" : "||";

        JmmNode leftOperand = node.getChildren().get(0);
        JmmNode rightOperand = node.getChildren().get(1);

        Type leftType = determineExpressionType(leftOperand, table);
        Type rightType = determineExpressionType(rightOperand, table);

        System.out.println("DEBUG [OperationTypeCheck]: Logical operation '" + operatorSymbol +
                "' between types: " + leftType.getName() + " and " + rightType.getName());

        if (!"boolean".equals(leftType.getName()) || !"boolean".equals(rightType.getName())) {
            String message = String.format(
                    "Invalid logical operation '%s' between types '%s' and '%s'. Only boolean %s boolean is allowed.",
                    operatorSymbol, leftType.getName(), rightType.getName(), operatorSymbol
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
        }

        return null;
    }

    private Void visitNotExpr(JmmNode node, SymbolTable table) {

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.getChildren().isEmpty()) {
            return null;
        }

        JmmNode operand = node.getChildren().get(0);

        Type operandType = determineExpressionType(operand, table);

        System.out.println("DEBUG [OperationTypeCheck]: Not operation on type: " +
                operandType.getName());

        if (!"boolean".equals(operandType.getName())) {
            String message = String.format(
                    "Invalid unary operation '!' on type '%s'. Operand must be boolean.",
                    operandType.getName()
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
        }

        return null;
    }

    private Void visitParenExpr(JmmNode node, SymbolTable table) {

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }

    private Type determineExpressionType(JmmNode expr, SymbolTable table) {

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        try {
            Type type = typeUtils.getExprType(expr);
            if (type != null) {
                return type;
            }
        } catch (Exception e) {
            System.out.println("DEBUG [OperationTypeCheck]: TypeUtils error: " + e.getMessage());
        }

        String kind = expr.getKind();

        if ("NewIntArrayExpr".equals(kind) || kind.contains("NewArrayExpr")) {
            return new Type("int", true);
        }

        if ("NewObjectArrayExpr".equals(kind)) {
            String typeName = expr.hasAttribute("type") ? expr.get("type") : "Object";
            return new Type(typeName, true);
        }

        if ("INTEGER_LITERAL".equals(kind) || "IntLiteral".equals(kind) ||
                kind.contains("Number") || kind.equals("IntLiteral")) {
            return new Type("int", false);
        }

        if ("TrueLiteral".equals(kind) || "FalseLiteral".equals(kind) ||
                "TrueLiteralExpr".equals(kind) || "FalseLiteralExpr".equals(kind) ||
                kind.contains("Boolean") || kind.equals("BooleanLiteral")) {
            return new Type("boolean", false);
        }

        if (kind.contains("String") || kind.equals("StringLiteral")) {
            return new Type("String", false);
        }

        if ("VAR_REF_EXPR".equals(kind) || "IdentifierReference".equals(kind)) {
            if (expr.hasAttribute("name")) {
                String varName = expr.get("name");
                return findVariableType(varName, expr, table);
            }
        }

        if ("THIS".equals(kind) && table != null) {
            return new Type(table.getClassName(), false);
        }

        if (kind.contains("MethodCall") && expr.hasAttribute("name")) {
            String methodName = expr.get("name");
            Type returnType = table.getReturnType(methodName);
            if (returnType != null) {
                return returnType;
            }

            if (expr.hasAttribute("returnType")) {
                boolean isArray = expr.hasAttribute("isArrayReturn") &&
                        "true".equals(expr.get("isArrayReturn"));
                return new Type(expr.get("returnType"), isArray);
            }
        }

        if (kind.contains("ADDITIVE") || kind.contains("MULTIPLICATIVE") ||
                kind.contains("Additive") || kind.contains("Multiplicative")) {
            return new Type("int", false);
        }

        if (kind.contains("LOGICAL") || kind.contains("RELATIONAL") ||
                kind.contains("Logical") || kind.contains("Relational") ||
                kind.contains("Not")) {
            return new Type("boolean", false);
        }

        if (kind.contains("ArrayAccess")) {
            if (!expr.getChildren().isEmpty()) {
                JmmNode arrayExpr = expr.getChildren().get(0);
                Type arrayType = determineExpressionType(arrayExpr, table);
                if (arrayType.isArray()) {
                    return new Type(arrayType.getName(), false);
                }
            }
            return new Type("int", false);
        }

        if (kind.contains("NewObject")) {
            if (expr.hasAttribute("type")) {
                return new Type(expr.get("type"), false);
            } else if (expr.hasAttribute("name")) {
                return new Type(expr.get("name"), false);
            }
        }

        if (kind.contains("ArrayLength") || kind.contains("Length")) {
            return new Type("int", false);
        }

        if ("ParenExpr".equals(kind) || "ParenExpression".equals(kind)) {
            if (!expr.getChildren().isEmpty()) {
                return determineExpressionType(expr.getChildren().get(0), table);
            }
        }

        if (isInBooleanContext(expr)) {
            return new Type("boolean", false);
        }

        return new Type("int", false);
    }

    private Type findVariableType(String varName, JmmNode context, SymbolTable table) {

        for (Symbol param : table.getParameters(currentMethod)) {
            if (param.getName().equals(varName)) {
                System.out.println("DEBUG [OperationTypeCheck]: Found type for " +
                        varName + " in parameter table: " + param.getType().getName() +
                        (param.getType().isArray() ? "[]" : ""));
                return param.getType();
            }
        }

        for (Symbol local : table.getLocalVariables(currentMethod)) {
            if (local.getName().equals(varName)) {
                System.out.println("DEBUG [OperationTypeCheck]: Found type for " +
                        varName + " in local variable table: " + local.getType().getName() +
                        (local.getType().isArray() ? "[]" : ""));
                return local.getType();
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                System.out.println("DEBUG [OperationTypeCheck]: Found type for " +
                        varName + " in field table: " + field.getType().getName() +
                        (field.getType().isArray() ? "[]" : ""));
                return field.getType();
            }
        }

        if (isInBooleanContext(context)) {
            System.out.println("DEBUG [OperationTypeCheck]: Variable " + varName +
                    " is in a boolean context, inferring boolean type");
            return new Type("boolean", false);
        }

        System.out.println("DEBUG [OperationTypeCheck]: Could not determine type for " +
                varName + ", defaulting to int");
        return new Type("int", false);
    }

    private boolean isInBooleanContext(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            String kind = current.getKind();

            if (kind.contains("LogicalAnd") || kind.contains("LogicalOr") ||
                    kind.contains("Not") || kind.contains("LOGICAL") ||
                    kind.contains("RELATIONAL") || kind.contains("Relational") ||
                    kind.contains("IfStmt") || kind.contains("WhileStmt")) {
                return true;
            }

            if (("IfStmt".equals(kind) || "WhileStmt".equals(kind)) &&
                    !current.getChildren().isEmpty() &&
                    current.getChildren().get(0) == node) {
                return true;
            }

            current = current.getParent();
        }
        return false;
    }

    private boolean areTypesCompatibleForEquality(Type type1, Type type2) {

        if (type1.getName().equals(type2.getName()) && type1.isArray() == type2.isArray()) {
            return true;
        }

        if (type1.isArray() != type2.isArray()) {
            return false;
        }

        if (isPrimitiveType(type1.getName()) || isPrimitiveType(type2.getName())) {
            return type1.getName().equals(type2.getName());
        }

        return type1.getName().equals(type2.getName());
    }

    private boolean isPrimitiveType(String typeName) {
        return "int".equals(typeName) ||
                "boolean".equals(typeName) ||
                "void".equals(typeName);
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }
}