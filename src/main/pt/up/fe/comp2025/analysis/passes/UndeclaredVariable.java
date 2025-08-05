package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;
    private String currentClass;
    private JmmNode currentMethodNode;

    private Deque<Set<String>> scopeStack = new ArrayDeque<>();

    private static final Set<String> reportedUndeclaredVars = new HashSet<>();

    @Override
    public void buildVisitor() {
        addVisit("VAR_REF_EXPR", this::visitVarRefExpr);
        addVisit("IdentifierReference", this::visitVarRefExpr);
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("CLASS_DECL", this::visitClassDecl);
        addVisit("VAR_DECL", this::visitVarDecl);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            currentMethodNode = method;
            System.out.println("DEBUG: Visiting method: " + currentMethod);

            scopeStack.clear();
            Set<String> methodScope = new HashSet<>();

            List<JmmNode> paramListNodes = method.getChildren("ParamList");
            if (!paramListNodes.isEmpty()) {
                JmmNode paramList = paramListNodes.get(0);
                for (JmmNode paramNode : paramList.getChildren("PARAM")) {
                    if (paramNode.hasAttribute("name")) {
                        String paramName = paramNode.get("name");
                        methodScope.add(paramName);
                        System.out.println("DEBUG: Added parameter '" + paramName + "' to method scope from AST");
                    }
                }
            }

            if (currentMethod != null) {
                for (Symbol param : table.getParameters(currentMethod)) {
                    methodScope.add(param.getName());
                    System.out.println("DEBUG: Added parameter '" + param.getName() + "' to method scope from table");
                }

                for (Symbol local : table.getLocalVariables(currentMethod)) {
                    methodScope.add(local.getName());
                    System.out.println("DEBUG: Added local variable '" + local.getName() + "' to method scope");
                }
            }

            scopeStack.push(methodScope);
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        if (classDecl.hasAttribute("name")) {
            currentClass = classDecl.get("name");
            System.out.println("DEBUG: Visiting class: " + currentClass);
        }

        for (JmmNode child : classDecl.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        if (varDecl.hasAttribute("name")) {
            String varName = varDecl.get("name");
            System.out.println("DEBUG: Variable declaration: " + varName);

            if (!scopeStack.isEmpty()) {
                scopeStack.peek().add(varName);
                System.out.println("DEBUG: Added variable '" + varName + "' to current scope.");
            }
        }

        for (JmmNode child : varDecl.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        if (currentMethod == null) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    "Variable reference outside method context",
                    null
            );
            addReport(report);
            return null;
        }

        String varRefName = varRefExpr.hasAttribute("name") ? varRefExpr.get("name") : "";
        System.out.println("DEBUG: Visiting AA reference: " + varRefName);
        // print all attributes
        for (String attribute : varRefExpr.getAttributes()) {
            System.out.println("DEBUG: Attribute: " + attribute + " = " + varRefExpr.get(attribute));
        }
        if (varRefName.isEmpty()) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    "Variable reference with empty name",
                    null
            );
            addReport(report);
            return null;
        }

        String varKey = currentMethod + ":" + varRefName + ":" +
                varRefExpr.getLine() + ":" + varRefExpr.getColumn();

        if (reportedUndeclaredVars.contains(varKey)) {
            return null;
        }

        if (table.getImports().contains(varRefName)) {
            System.out.println("DEBUG: Found imported class: " + varRefName);
            return null;
        }

        if (currentMethodNode != null) {
            for (JmmNode child : currentMethodNode.getChildren("ParamList")) {
                for (JmmNode param : child.getChildren("PARAM")) {
                    if (param.hasAttribute("name") && param.get("name").equals(varRefName)) {
                        System.out.println("DEBUG: Found parameter in AST: " + varRefName);
                        return null;
                    }
                }
            }
        }

        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            System.out.println("DEBUG: Found parameter in symbol table: " + varRefName);
            return null;
        }

        boolean foundInScope = false;
        for (Set<String> scope : scopeStack) {
            if (scope.contains(varRefName)) {
                foundInScope = true;
                break;
            }
        }
        if (foundInScope) {
            System.out.println("DEBUG: Found variable in scope: " + varRefName);
            return null;
        }

        if (table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varRefName))) {
            System.out.println("DEBUG: Found field: " + varRefName);
            return null;
        }

        String message = String.format("Variable '%s' does not exist.", varRefName);
        System.out.println("DEBUG: Reporting undeclared variable: " + varRefName);

        Report report = Report.newError(
                Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                message,
                null
        );
        addReport(report);

        reportedUndeclaredVars.add(varKey);

        return null;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        if (node.getKind().equals("BlockStmt") || node.getKind().equals("block")) {
            scopeStack.push(new HashSet<>());
            for (JmmNode child : node.getChildren()) {
                visit(child, table);
            }
            scopeStack.pop();
        } else {
            for (JmmNode child : node.getChildren()) {
                visit(child, table);
            }
        }
        return null;
    }


}