package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.*;

public class DuplicateDeclarationCheck extends AnalysisVisitor {

    private String currentMethod;
    private final Map<String, List<Symbol>> methodParamsMap = new HashMap<>();
    private final Set<String> methodSignatures = new HashSet<>();
    private final Map<String, Set<String>> scopeVariables = new HashMap<>();
    private final Deque<Map<String, JmmNode>> localScopeVars = new ArrayDeque<>();

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("PARAM", this::visitParam);
        addVisit("VAR_DECL", this::visitVarDecl);
        addVisit("BlockStmt", this::visitBlockScope);
        addVisit("IfStmt", this::visitControlScope);
        addVisit("WhileStmt", this::visitControlScope);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (!method.hasAttribute("name")) {
            return null;
        }

        String methodName = method.get("name");
        currentMethod = methodName;
        localScopeVars.clear();
        localScopeVars.push(new HashMap<>());

        System.out.println("DEBUG [DuplicateCheck]: Analyzing method " + methodName);

        // Check for method overloading (same name, different parameters)
        List<Symbol> parameters = extractMethodParameters(method);
        String signature = buildMethodSignature(methodName, parameters);

        if (methodSignatures.contains(signature)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    "Duplicate method declaration with same signature: " + signature,
                    null
            ));
        } else {
            methodSignatures.add(signature);
            methodParamsMap.put(methodName, new ArrayList<>(parameters));
        }

        // Create a scope for method parameters
        scopeVariables.put(methodName, new HashSet<>());

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        if (!localScopeVars.isEmpty()) {
            localScopeVars.pop();
        }

        return null;
    }

    private Void visitParam(JmmNode param, SymbolTable table) {
        if (!param.hasAttribute("name")) {
            return null;
        }

        String paramName = param.get("name");

        // Check for duplicate parameter names in the same method
        Set<String> methodParams = scopeVariables.get(currentMethod);
        if (methodParams != null) {
            if (methodParams.contains(paramName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        param.getLine(),
                        param.getColumn(),
                        "Duplicate parameter name '" + paramName + "' in method '" + currentMethod + "'",
                        null
                ));
            } else {
                methodParams.add(paramName);
                Map<String, JmmNode> currentScope = localScopeVars.peek();
                if (currentScope != null) {
                    currentScope.put(paramName, param);
                }
            }
        }

        for (JmmNode child : param.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        if (!varDecl.hasAttribute("name")) {
            return null;
        }

        String varName = varDecl.get("name");
        JmmNode parent = varDecl.getParent();

        // Skip checking fields
        if (parent != null && ("CLASS_BODY".equals(parent.getKind()) || isClassBodyDescendant(parent))) {
            checkDuplicateField(varDecl, varName, table);
            return null;
        }

        // Check local variables
        if (currentMethod != null && !localScopeVars.isEmpty()) {
            Map<String, JmmNode> currentScope = localScopeVars.peek();

            // Check if variable is already declared in current scope
            if (currentScope.containsKey(varName)) {
                JmmNode previousDecl = currentScope.get(varName);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        "Duplicate variable declaration '" + varName + "' in the same scope, previously declared at line " + previousDecl.getLine(),
                        null
                ));
            } else {
                currentScope.put(varName, varDecl);
            }

            // Also check parameters to ensure no shadowing of parameters
            Set<String> methodParams = scopeVariables.get(currentMethod);
            if (methodParams != null && methodParams.contains(varName) && currentScope == localScopeVars.getFirst()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        "Variable '" + varName + "' shadows a parameter of method '" + currentMethod + "'",
                        null
                ));
            }
        }

        for (JmmNode child : varDecl.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private void checkDuplicateField(JmmNode varDecl, String fieldName, SymbolTable table) {
        JmmSymbolTable jmmTable = (JmmSymbolTable) table;
        boolean isDuplicate = false;

        for (Symbol field : jmmTable.getFields()) {
            if (field.getName().equals(fieldName)) {
                isDuplicate = true;
                break;
            }
        }

        if (isDuplicate) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    "Duplicate field declaration: '" + fieldName + "'",
                    null
            ));
        }
    }

    private boolean isClassBodyDescendant(JmmNode node) {
        if (node == null) return false;
        if ("CLASS_BODY".equals(node.getKind())) return true;
        return isClassBodyDescendant(node.getParent());
    }

    private Void visitBlockScope(JmmNode blockStmt, SymbolTable table) {
        // Push a new scope level for blocks
        localScopeVars.push(new HashMap<>());

        for (JmmNode child : blockStmt.getChildren()) {
            visit(child, table);
        }

        // Pop when leaving the scope
        if (!localScopeVars.isEmpty()) {
            localScopeVars.pop();
        }

        return null;
    }

    private Void visitControlScope(JmmNode stmt, SymbolTable table) {
        // For if/while statements, create a new scope for their bodies
        for (JmmNode child : stmt.getChildren()) {
            if ("BlockStmt".equals(child.getKind())) {
                // The block statement will create its own scope
                visit(child, table);
            } else {
                // Create a scope for non-block child (e.g., single statement in an if)
                localScopeVars.push(new HashMap<>());
                visit(child, table);
                localScopeVars.pop();
            }
        }

        return null;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }

    private List<Symbol> extractMethodParameters(JmmNode methodNode) {
        List<Symbol> parameters = new ArrayList<>();

        JmmNode paramListNode = null;
        for (JmmNode child : methodNode.getChildren()) {
            if ("ParamList".equals(child.getKind())) {
                paramListNode = child;
                break;
            }
        }

        if (paramListNode != null) {
            for (JmmNode paramNode : paramListNode.getChildren("PARAM")) {
                String paramName = paramNode.hasAttribute("name") ? paramNode.get("name") : null;
                if (paramName != null) {
                    parameters.add(new Symbol(extractParameterType(paramNode), paramName));
                }
            }
        }

        return parameters;
    }

    private pt.up.fe.comp.jmm.analysis.table.Type extractParameterType(JmmNode paramNode) {
        for (JmmNode child : paramNode.getChildren()) {
            if ("TYPE".equals(child.getKind())) {
                String typeName = "int"; // Default
                boolean isArray = false;

                if (child.hasAttribute("name")) {
                    typeName = child.get("name");
                }

                for (JmmNode typeChild : child.getChildren()) {
                    if ("ArraySuffix".equals(typeChild.getKind())) {
                        isArray = true;
                        break;
                    }
                }

                return new pt.up.fe.comp.jmm.analysis.table.Type(typeName, isArray);
            }
        }

        return new pt.up.fe.comp.jmm.analysis.table.Type("int", false);
    }

    private String buildMethodSignature(String methodName, List<Symbol> methodParams) {
        StringBuilder sb = new StringBuilder(methodName);
        sb.append('(');

        if (!methodParams.isEmpty()) {
            for (int i = 0; i < methodParams.size(); i++) {
                Symbol param = methodParams.get(i);
                sb.append(param.getType().getName());
                if (param.getType().isArray()) {
                    sb.append("[]");
                }

                if (i < methodParams.size() - 1) {
                    sb.append(',');
                }
            }
        }

        sb.append(')');
        return sb.toString();
    }
}
