package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class MethodCallCheck extends AnalysisVisitor {

    private String currentMethod;
    private Map<JmmNode, Type> methodCallTypes = new HashMap<>();
    private Map<JmmNode, Type> originalObjectTypes = new HashMap<>();

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("MethodCallExpr", this::visitMethodCallExpr);
        addVisit("DIRECT_METHOD_CALL", this::visitDirectMethodCall);

        setDefaultVisit(this::visitDefault);
    }

    public Map<JmmNode, Type> getMethodCallTypes() {
        return methodCallTypes;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [MethodCallCheck]: Entering method: " + currentMethod);
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitMethodCallExpr(JmmNode node, SymbolTable table) {
        System.out.println("DEBUG [MethodCallCheck]: Checking method call expression at line " + node.getLine());

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.getChildren().isEmpty()) {
            return null;
        }

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        JmmNode objectExpr = node.getChildren().get(0);
        System.out.println("DEBUG [MethodCallCheck]: Object expression kind: " + objectExpr.getKind());

        Type objectType = null;
        try {
            objectType = typeUtils.getExprType(objectExpr);
            System.out.println("DEBUG [MethodCallCheck]: Object type: " +
                    (objectType != null ? objectType.getName() : "null"));
        } catch (Exception e) {
            System.out.println("DEBUG [MethodCallCheck]: Error getting object type: " + e.getMessage());
        }

        if (objectType == null || isPrimitiveType(objectType)) {
            if ("MethodCallExpr".equals(objectExpr.getKind())) {
                System.out.println("DEBUG: Analyzing method call: " +
                        (objectExpr.hasAttribute("name") ? objectExpr.get("name") : "unknown"));

                Type originalType = originalObjectTypes.get(objectExpr);

                if (originalType != null) {
                    System.out.println("DEBUG: Method call object type: " + originalType.getName());
                    objectType = originalType;

                    if (isThisExpression(objectExpr.getChildren().get(0))) {
                        System.out.println("DEBUG: Chained call on 'this' detected");
                        objectType = new Type(table.getClassName(), false);
                    }
                } else if ("ThisExpression".equals(objectExpr.getChildren().get(0).getKind())) {
                    objectType = new Type(table.getClassName(), false);
                    System.out.println("DEBUG: Using class type for chained call: " + objectType.getName());
                }
            } else if (isThisExpression(objectExpr)) {
                objectType = new Type(table.getClassName(), false);
                System.out.println("DEBUG: Using class type for 'this' call: " + objectType.getName());
            }
        }

        String methodName = null;
        if (node.hasAttribute("name")) {
            methodName = node.get("name");
            System.out.println("DEBUG [MethodCallCheck]: Found method name on node: " + methodName);
        } else {
            for (JmmNode child : node.getChildren()) {
                if ("MethodCall".equals(child.getKind()) && child.hasAttribute("name")) {
                    methodName = child.get("name");
                    break;
                }
            }
        }

        if (methodName == null) {
            System.out.println("DEBUG [MethodCallCheck]: Could not determine method name");
            return null;
        }

        System.out.println("DEBUG [MethodCallCheck]: Method name: " + methodName);

        Type returnType = null;

        if (objectType != null && objectType.getName().equals(((JmmSymbolTable) table).getClassName())) {
            boolean methodExists = false;

            for (String classMethod : ((JmmSymbolTable) table).getMethods()) {
                if (classMethod.equals(methodName)) {
                    methodExists = true;
                    returnType = ((JmmSymbolTable) table).getReturnType(methodName);
                    System.out.println("DEBUG [MethodCallCheck]: Found method " + methodName +
                            " with return type: " +
                            (returnType != null ? returnType.getName() : "null"));

                    checkMethodArguments(node, methodName, table);
                    break;
                }
            }

            if (!methodExists && ((JmmSymbolTable) table).getSuper() != null) {
                System.out.println("DEBUG [MethodCallCheck]: Method may be in superclass, assuming valid call");
                returnType = guessReturnTypeFromContext(node, table);

                if (returnType == null && isThisExpression(objectExpr)) {
                    returnType = objectType; // Mantém o tipo do objeto para a próxima chamada na cadeia
                    System.out.println("DEBUG [MethodCallCheck]: Assuming method returns same object type for chaining");
                }
            } else if (!methodExists) {
                String message = String.format("Undeclared method: '%s'", methodName);
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
                System.out.println("DEBUG [MethodCallCheck]: Reported undeclared method");
                return null;
            }
        } else if (objectType != null) {
            if (isExternalType(objectType, (JmmSymbolTable)table)) {
                System.out.println("DEBUG [MethodCallCheck]: Call on external type " +
                        objectType.getName() + ", assuming method exists");
                returnType = guessReturnTypeFromContext(node, table);

                if (returnType == null && "MethodCallExpr".equals(objectExpr.getKind())) {
                    Type originalType = originalObjectTypes.get(objectExpr);
                    if (originalType != null) {
                        returnType = originalType;
                        System.out.println("DEBUG [MethodCallCheck]: Using original object type for chained call");
                    }
                }

                if (returnType == null && objectType.getName().equals(((JmmSymbolTable)table).getClassName())) {
                    returnType = objectType;
                    System.out.println("DEBUG [MethodCallCheck]: Assuming method returns same class for chaining support");
                }
            } else {
                String message = String.format("Unknown type: '%s'", objectType.getName());
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
                System.out.println("DEBUG [MethodCallCheck]: Reported unknown type");
                return null;
            }
        } else {
            String message = "Cannot determine object type for method call";
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [MethodCallCheck]: Reported undetermined object type");
            return null;
        }

        if (returnType != null) {
            System.out.println("DEBUG [MethodCallCheck]: Storing return type for method call: " +
                    returnType.getName() + (returnType.isArray() ? "[]" : ""));
            methodCallTypes.put(node, returnType);

            node.put("returnType", returnType.getName());
            node.put("isArrayReturn", String.valueOf(returnType.isArray()));

            originalObjectTypes.put(node, objectType);
        }

        return null;
    }

    private boolean isThisExpression(JmmNode node) {
        return node != null && "ThisExpression".equals(node.getKind());
    }

    private boolean isPrimitiveType(Type type) {
        if (type == null) return false;
        String name = type.getName();
        return "int".equals(name) || "boolean".equals(name) || "void".equals(name);
    }

    private Void visitDirectMethodCall(JmmNode node, SymbolTable table) {
        System.out.println("DEBUG [MethodCallCheck]: Checking direct method call at line " + node.getLine());

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }

        if (node.hasAttribute("name")) {
            String methodName = node.get("name");
            System.out.println("DEBUG [MethodCallCheck]: Direct method call to: " + methodName);

            JmmSymbolTable jmmTable = (JmmSymbolTable) table;
            boolean methodExists = jmmTable.getMethods().contains(methodName);

            if (!methodExists && jmmTable.getSuper() == null) {
                String message = String.format("Undeclared method: '%s'", methodName);
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
                System.out.println("DEBUG [MethodCallCheck]: Reported undeclared direct method");
            } else {
                checkMethodArguments(node, methodName, table);

                Type returnType = jmmTable.getReturnType(methodName);
                if (returnType != null) {
                    methodCallTypes.put(node, returnType);
                    node.put("returnType", returnType.getName());
                    node.put("isArrayReturn", String.valueOf(returnType.isArray()));

                    originalObjectTypes.put(node, new Type(jmmTable.getClassName(), false));
                }
            }
        }

        return null;
    }

    private boolean isExternalType(Type type, JmmSymbolTable table) {
        String typeName = type.getName();

        if (isImportedType(typeName, table)) {
            return true;
        }

        if (table.getSuper() != null && table.getSuper().equals(typeName)) {
            return true;
        }

        if (typeName.equals(table.getClassName()) &&
                table.getSuper() != null &&
                isImportedType(table.getSuper(), table)) {
            System.out.println("DEBUG [MethodCallCheck]: " + typeName + " is a subclass of imported class " +
                    table.getSuper() + ", assuming methods are in parent");
            return true;
        }

        return false;
    }

    private boolean isImportedType(String typeName, JmmSymbolTable table) {

        if (table.getImports().contains(typeName)) {
            return true;
        }

        for (String importName : table.getImports()) {
            if (importName.endsWith("." + typeName)) {
                return true;
            }

            if (importName.equals(typeName)) {
                return true;
            }
        }

        return false;
    }

    private void checkMethodArguments(JmmNode methodNode, String methodName, SymbolTable table) {
        System.out.println("DEBUG [MethodCallCheck]: Checking arguments for method: " + methodName);

        List<Symbol> formalParams = table.getParameters(methodName);
        System.out.println("DEBUG [MethodCallCheck]: Method has " + formalParams.size() + " parameters");

        List<JmmNode> argumentList = methodNode.getChildren();
        List<JmmNode> actualArgs = null;

        for (JmmNode child : argumentList) {
            if ("ArgumentList".equals(child.getKind())) {
                actualArgs = child.getChildren();
                break;
            }
        }

        if (actualArgs == null) {
            return;
        }

        System.out.println("DEBUG [MethodCallCheck]: Call has " + actualArgs.size() + " arguments");

        boolean hasVarargs = !formalParams.isEmpty() &&
                formalParams.get(formalParams.size() - 1).getType().isArray();
        System.out.println("DEBUG [MethodCallCheck]: Method has varargs: " + hasVarargs);

        if (!hasVarargs && actualArgs.size() != formalParams.size()) {
            String message = String.format(
                    "Method '%s' expects %d arguments but got %d",
                    methodName, formalParams.size(), actualArgs.size()
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    methodNode.getLine(),
                    methodNode.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [MethodCallCheck]: Reported argument count mismatch");
            return;
        }

        if (hasVarargs && actualArgs.size() < formalParams.size() - 1) {
            String message = String.format(
                    "Method '%s' requires at least %d arguments but got %d",
                    methodName, formalParams.size() - 1, actualArgs.size()
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    methodNode.getLine(),
                    methodNode.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [MethodCallCheck]: Reported too few arguments for varargs method");
            return;
        }

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        int regularParamCount = hasVarargs ? formalParams.size() - 1 : formalParams.size();

        for (int i = 0; i < regularParamCount && i < actualArgs.size(); i++) {
            Type paramType = formalParams.get(i).getType();
            Type argType;

            try {
                argType = typeUtils.getExprType(actualArgs.get(i));
                System.out.println("DEBUG [MethodCallCheck]: Param " + i + " type: " + paramType.getName() +
                        (paramType.isArray() ? "[]" : "") +
                        ", Arg type: " + argType.getName() +
                        (argType.isArray() ? "[]" : ""));

                if (!isTypeCompatible(paramType, argType, table)) {
                    String message = String.format(
                            "Incompatible type for argument %d in call to '%s': expected '%s%s' but got '%s%s'",
                            i + 1, methodName,
                            paramType.getName(), paramType.isArray() ? "[]" : "",
                            argType.getName(), argType.isArray() ? "[]" : ""
                    );
                    Report report = Report.newError(
                            Stage.SEMANTIC,
                            actualArgs.get(i).getLine(),
                            actualArgs.get(i).getColumn(),
                            message,
                            null
                    );
                    addReport(report);
                    System.out.println("DEBUG [MethodCallCheck]: Reported argument type mismatch");
                }
            } catch (Exception e) {
                System.out.println("DEBUG [MethodCallCheck]: Error getting argument type: " + e.getMessage());
            }
        }

        if (hasVarargs && regularParamCount < formalParams.size()) {
            Type varargParamType = formalParams.get(regularParamCount).getType();
            Type varargElemType = new Type(varargParamType.getName(), false);

            System.out.println("DEBUG [MethodCallCheck]: Vararg element type: " + varargElemType.getName());

            if (actualArgs.size() == regularParamCount + 1) {
                try {
                    Type argType = typeUtils.getExprType(actualArgs.get(regularParamCount));
                    System.out.println("DEBUG [MethodCallCheck]: Vararg arg type: " + argType.getName() +
                            (argType.isArray() ? "[]" : ""));

                    if (argType.isArray()) {
                        if (!argType.getName().equals(varargElemType.getName())) {
                            String message = String.format(
                                    "Incompatible array type for vararg parameter: expected '%s[]' but got '%s[]'",
                                    varargElemType.getName(), argType.getName()
                            );
                            Report report = Report.newError(
                                    Stage.SEMANTIC,
                                    actualArgs.get(regularParamCount).getLine(),
                                    actualArgs.get(regularParamCount).getColumn(),
                                    message,
                                    null
                            );
                            addReport(report);
                            System.out.println("DEBUG [MethodCallCheck]: Reported vararg array type mismatch");
                        }
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("DEBUG [MethodCallCheck]: Error getting vararg argument type: " + e.getMessage());
                }
            }

            for (int i = regularParamCount; i < actualArgs.size(); i++) {
                try {
                    Type argType = typeUtils.getExprType(actualArgs.get(i));
                    System.out.println("DEBUG [MethodCallCheck]: Vararg element " + (i - regularParamCount) +
                            " type: " + argType.getName() + (argType.isArray() ? "[]" : ""));

                    if (argType.isArray() || !varargElemType.getName().equals(argType.getName())) {
                        String message = String.format(
                                "Incompatible type for vararg parameter at position %d: expected '%s' but got '%s%s'",
                                i + 1, varargElemType.getName(),
                                argType.getName(), argType.isArray() ? "[]" : ""
                        );
                        Report report = Report.newError(
                                Stage.SEMANTIC,
                                actualArgs.get(i).getLine(),
                                actualArgs.get(i).getColumn(),
                                message,
                                null
                        );
                        addReport(report);
                        System.out.println("DEBUG [MethodCallCheck]: Reported vararg element type mismatch");
                    }
                } catch (Exception e) {
                    System.out.println("DEBUG [MethodCallCheck]: Error getting vararg element type: " + e.getMessage());
                }
            }
        }
    }

    private boolean isTypeCompatible(Type expected, Type actual, SymbolTable table) {

        if (expected.getName().equals(actual.getName()) &&
                expected.isArray() == actual.isArray()) {
            return true;
        }

        if (table instanceof JmmSymbolTable) {
            JmmSymbolTable jmmTable = (JmmSymbolTable) table;
            if (jmmTable.getClassName().equals(actual.getName()) &&
                    jmmTable.getSuper() != null &&
                    jmmTable.getSuper().equals(expected.getName())) {
                return true;
            }
        }

        return false;
    }

    private Type guessReturnTypeFromContext(JmmNode methodCallNode, SymbolTable table) {

        String methodName = "";
        if (methodCallNode.hasAttribute("name")) {
            methodName = methodCallNode.get("name");
        }
        System.out.println("DEBUG [MethodCallCheck]: Guessing return type for method: " + methodName);

        JmmSymbolTable jmmTable = (JmmSymbolTable) table;

        if (methodCallNode.getChildren().size() > 0) {
            JmmNode objectExpr = methodCallNode.getChildren().get(0);

            if (isThisExpression(objectExpr)) {
                if (methodName.startsWith("set") || methodName.startsWith("add") ||
                        methodName.startsWith("with") || methodName.startsWith("build")) {
                    System.out.println("DEBUG [MethodCallCheck]: Method follows fluent pattern, assuming it returns this");
                    return new Type(jmmTable.getClassName(), false);
                }
            }

            if ("MethodCallExpr".equals(objectExpr.getKind())) {
                Type previousCallType = methodCallTypes.get(objectExpr);

                if (previousCallType != null) {
                    if (previousCallType.getName().equals(jmmTable.getClassName())) {
                        return previousCallType;
                    }
                }

                Type originalType = originalObjectTypes.get(objectExpr);
                if (originalType != null && originalType.getName().equals(jmmTable.getClassName())) {
                    return originalType;
                }
            }
        }

        JmmNode parent = methodCallNode;
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();

            if ("RETURN_STMT".equals(parent.getKind())) {
                JmmNode methodNode = findEnclosingMethod(parent);
                if (methodNode != null && methodNode.hasAttribute("name")) {
                    String enclosingMethodName = methodNode.get("name");
                    Type returnType = jmmTable.getReturnType(enclosingMethodName);
                    if (returnType != null) {
                        System.out.println("DEBUG [MethodCallCheck]: Using return type from enclosing method: " +
                                returnType.getName());
                        return returnType;
                    }
                }
            }

            if (("ASSIGN_STMT".equals(parent.getKind()) || "AssignStatement".equals(parent.getKind())) &&
                    parent.getChildren().size() >= 2) {

                JmmNode lhs = parent.getChildren().get(0);
                if (lhs.hasAttribute("name")) {
                    String varName = lhs.get("name");

                    for (Symbol local : table.getLocalVariables(currentMethod)) {
                        if (local.getName().equals(varName)) {
                            System.out.println("DEBUG [MethodCallCheck]: Using type from assignment target: " +
                                    local.getType().getName());
                            return local.getType();
                        }
                    }

                    for (Symbol param : table.getParameters(currentMethod)) {
                        if (param.getName().equals(varName)) {
                            return param.getType();
                        }
                    }

                    for (Symbol field : table.getFields()) {
                        if (field.getName().equals(varName)) {
                            return field.getType();
                        }
                    }
                }
            }

            if (parent.getKind().contains("ADDITIVE") || parent.getKind().contains("MULTIPLICATIVE")) {
                return new Type("int", false);
            }

            if (parent.getKind().contains("LOGICAL") || parent.getKind().contains("RELATIONAL")) {
                return new Type("boolean", false);
            }
        }

        if (methodCallNode.getChildren().size() > 0) {
            JmmNode objectExpr = methodCallNode.getChildren().get(0);
            TypeUtils typeUtils = new TypeUtils(table);
            typeUtils.setCurrentMethod(currentMethod);

            try {
                Type objectType = typeUtils.getExprType(objectExpr);
                if (objectType != null) {

                    if (objectType.getName().equals(jmmTable.getClassName())) {
                        return objectType;
                    }
                }
            } catch (Exception e) {
                System.out.println("DEBUG [MethodCallCheck]: Error getting object type for standalone call: " + e.getMessage());
            }
        }

        return null;
    }

    private JmmNode findEnclosingMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("METHOD_DECL".equals(current.getKind())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }
}