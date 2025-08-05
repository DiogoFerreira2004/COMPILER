package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ReturnTypeCheck extends AnalysisVisitor {

    private String currentMethod;
    private JmmNode currentMethodNode;
    private List<Symbol> currentMethodParameters;
    private Map<String, Type> returnTypeCache = new HashMap<>();

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("RETURN_STMT", this::visitReturnStmt);
        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            currentMethodNode = method;
            currentMethodParameters = extractParametersFromNode(method);
            String methodSignature = buildMethodSignature(currentMethod, currentMethodParameters);
            Type returnType = extractReturnTypeFromNode(method);
            returnTypeCache.put(methodSignature, returnType);

            System.out.println("DEBUG [ReturnTypeCheck]: Entering method: " + currentMethod +
                    " with " + currentMethodParameters.size() + " parameters - signature: " + methodSignature +
                    " - return type: " + (returnType != null ? returnType.getName() : "null") +
                    (returnType != null && returnType.isArray() ? "[]" : ""));
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private String buildMethodSignature(String methodName, List<Symbol> parameters) {
        StringBuilder sb = new StringBuilder(methodName);
        sb.append('(');

        if (parameters != null && !parameters.isEmpty()) {
            for (int i = 0; i < parameters.size(); i++) {
                Symbol param = parameters.get(i);
                sb.append(param.getType().getName());
                if (param.getType().isArray()) {
                    sb.append("[]");
                }

                if (i < parameters.size() - 1) {
                    sb.append(',');
                }
            }
        }

        sb.append(')');
        return sb.toString();
    }

    private List<Symbol> extractParametersFromNode(JmmNode methodNode) {
        List<Symbol> parameters = new ArrayList<>();

        JmmNode paramListNode = null;
        for (JmmNode child : methodNode.getChildren()) {
            if ("ParamList".equals(child.getKind())) {
                paramListNode = child;
                break;
            }
        }

        if (paramListNode == null) {
            return parameters;
        }

        for (JmmNode paramNode : paramListNode.getChildren()) {
            if ("PARAM".equals(paramNode.getKind())) {
                Symbol param = extractParameterSymbol(paramNode);
                if (param != null) {
                    parameters.add(param);
                }
            }
        }

        return parameters;
    }

    private Symbol extractParameterSymbol(JmmNode paramNode) {

        String paramName = null;
        if (paramNode.hasAttribute("name")) {
            paramName = paramNode.get("name");
        } else if (paramNode.hasAttribute("ID")) {
            paramName = paramNode.get("ID");
        }

        if (paramName == null) {
            return null;
        }

        Type paramType = null;
        for (JmmNode child : paramNode.getChildren()) {
            if ("TYPE".equals(child.getKind())) {
                paramType = TypeUtils.convertType(child);
                break;
            }
        }

        if (paramType == null) {
            paramType = new Type("int", false);
        }

        return new Symbol(paramType, paramName);
    }

    private Type extractReturnTypeFromNode(JmmNode methodNode) {

        JmmNode functionTypeNode = null;
        for (JmmNode child : methodNode.getChildren()) {
            if ("FUNCTION_TYPE".equals(child.getKind())) {
                functionTypeNode = child;
                break;
            }
        }

        if (functionTypeNode == null) {
            return new Type("void", false);
        }

        return TypeUtils.convertType(functionTypeNode);
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        System.out.println("DEBUG [ReturnTypeCheck]: Checking return statement at line " + returnStmt.getLine());

        if (currentMethod == null || currentMethodNode == null) {
            return null;
        }

        String methodSignature = buildMethodSignature(currentMethod, currentMethodParameters);
        Type expectedType = returnTypeCache.get(methodSignature);

        if (expectedType == null) {
            expectedType = extractReturnTypeFromNode(currentMethodNode);
        }

        System.out.println("DEBUG [ReturnTypeCheck]: Method '" + methodSignature +
                "' declared return type: " + expectedType.getName() +
                (expectedType.isArray() ? "[]" : ""));

        if (returnStmt.getChildren().isEmpty()) {
            if (!"void".equals(expectedType.getName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        "Empty return in non-void method '" + methodSignature + "'",
                        null
                ));
            }
            return null;
        }

        JmmNode returnExpr = returnStmt.getChildren().get(0);
        System.out.println("DEBUG [ReturnTypeCheck]: Return expression kind: " + returnExpr.getKind());

        Type actualType = determineExpressionType(returnExpr, table);

        System.out.println("DEBUG [ReturnTypeCheck]: Return expression type determined as: " +
                actualType.getName() + (actualType.isArray() ? "[]" : ""));

        if (!areTypesCompatible(expectedType, actualType)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    "Return type mismatch in method '" + currentMethod + "': expected '" +
                            expectedType.getName() + (expectedType.isArray() ? "[]" : "") +
                            "' but found '" + actualType.getName() + (actualType.isArray() ? "[]" : "") + "'",
                    null
            ));
        }

        return null;
    }

    private Type determineExpressionType(JmmNode expr, SymbolTable table) {

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        try {
            Type type = typeUtils.getExprType(expr);
            if (type != null) {
                System.out.println("DEBUG [ReturnTypeCheck]: TypeUtils determined type: " +
                        type.getName() + (type.isArray() ? "[]" : ""));
                return type;
            }
        } catch (Exception e) {
            System.out.println("WARNING: " + e.getMessage());
        }

        String kind = expr.getKind();

        if ("NewIntArrayExpr".equals(kind) || kind.contains("NewArrayExpr")) {
            return new Type("int", true);
        }

        if ("INTEGER_LITERAL".equals(kind) || "IntLiteral".equals(kind)) {
            return new Type("int", false);
        }

        if ("TrueLiteral".equals(kind) || "FalseLiteral".equals(kind) ||
                "TrueLiteralExpr".equals(kind) || "FalseLiteralExpr".equals(kind)) {
            return new Type("boolean", false);
        }

        if ("VAR_REF_EXPR".equals(kind) && expr.hasAttribute("name")) {
            String varName = expr.get("name");

            for (Symbol param : currentMethodParameters) {
                if (param.getName().equals(varName)) {
                    System.out.println("DEBUG: Found parameter: " + varName +
                            " with type: " + param.getType().getName() +
                            (param.getType().isArray() ? "[]" : ""));
                    return param.getType();
                }
            }

            Type varType = findVariableType(varName, table);
            if (varType != null) {
                return varType;
            }
        }

        if ("ADDITIVE_EXPR".equals(kind) || "MULTIPLICATIVE_EXPR".equals(kind)) {
            return new Type("int", false);
        }

        if ("LOGICAL_EXPR".equals(kind) || "RELATIONAL_EXPR".equals(kind) ||
                "LogicalAndExpr".equals(kind) || "LogicalOrExpr".equals(kind)) {
            return new Type("boolean", false);
        }

        if ("ParenExpr".equals(kind) && !expr.getChildren().isEmpty()) {
            return determineExpressionType(expr.getChildren().get(0), table);
        }

        String methodSignature = buildMethodSignature(currentMethod, currentMethodParameters);
        Type expectedType = returnTypeCache.get(methodSignature);
        if (expectedType != null) {
            return expectedType;
        }

        return new Type("int", false);
    }

    private Type findVariableType(String varName, SymbolTable table) {

        for (Symbol param : table.getParameters(currentMethod)) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        for (Symbol local : table.getLocalVariables(currentMethod)) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        return null;
    }

    private boolean areTypesCompatible(Type expectedType, Type actualType) {

        if (expectedType.getName().equals(actualType.getName()) &&
                expectedType.isArray() == actualType.isArray()) {
            return true;
        }

        if (isPrimitiveType(expectedType.getName()) || isPrimitiveType(actualType.getName())) {
            return expectedType.getName().equals(actualType.getName()) &&
                    expectedType.isArray() == actualType.isArray();
        }

        return false;
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