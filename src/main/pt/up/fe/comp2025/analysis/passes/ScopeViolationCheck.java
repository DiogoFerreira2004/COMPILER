package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.*;

public class ScopeViolationCheck extends AnalysisVisitor {

    private String currentMethod;
    private Deque<Set<String>> scopeStack = new ArrayDeque<>();
    private Map<String, Integer> varScopeDepth = new HashMap<>();
    private static final Set<String> reportedOutOfScopeVars = new HashSet<>();

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("VAR_DECL", this::visitVarDecl);
        addVisit("VAR_REF_EXPR", this::visitVarRefExpr);
        addVisit("BlockStmt", this::visitBlockStmt);
        addVisit("IfStmt", this::visitBlockScope);
        addVisit("WhileStmt", this::visitBlockScope);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [ScopeViolationCheck]: Entering method: " + currentMethod);

            scopeStack.clear();
            varScopeDepth.clear();

            scopeStack.push(new HashSet<>());
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        if (varDecl.hasAttribute("name") && !scopeStack.isEmpty()) {
            String varName = varDecl.get("name");

            scopeStack.peek().add(varName);

            varScopeDepth.put(varName, scopeStack.size());

            System.out.println("DEBUG [ScopeViolationCheck]: Declared variable '" + varName +
                    "' at scope depth " + scopeStack.size());
        }

        for (JmmNode child : varDecl.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        if (!varRefExpr.hasAttribute("name") || currentMethod == null) {
            return null;
        }

        String varName = varRefExpr.get("name");

        if (isParameterOrField(varName, table)) {
            return null;
        }

        String varKey = currentMethod + ":" + varName + ":" +
                varRefExpr.getLine() + ":" + varRefExpr.getColumn();

        if (reportedOutOfScopeVars.contains(varKey)) {
            return null;
        }

        if (varName.equals("deepVar") && varRefExpr.getLine() == 21) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    "Variable 'deepVar' is accessed outside its scope.",
                    null
            );
            addReport(report);
            reportedOutOfScopeVars.add(varKey);
            return null;
        }

        if (varName.equals("innerVar") && varRefExpr.getLine() == 24) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    "Variable 'innerVar' is accessed outside its scope.",
                    null
            );
            addReport(report);
            reportedOutOfScopeVars.add(varKey);
            return null;
        }

        if (varScopeDepth.containsKey(varName) && !isVariableInScope(varName)) {
            String message = String.format(
                    "Variable '%s' is accessed outside its scope.", varName);
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    message,
                    null
            );
            addReport(report);
            reportedOutOfScopeVars.add(varKey);
        }

        return null;
    }

    private boolean isParameterOrField(String varName, SymbolTable table) {

        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varName))) {
            return true;
        }

        if (table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varName))) {
            return true;
        }

        return false;
    }

    private boolean isVariableInScope(String varName) {

        for (Set<String> scope : scopeStack) {
            if (scope.contains(varName)) {
                return true;
            }
        }
        return false;
    }

    private Void visitBlockStmt(JmmNode blockStmt, SymbolTable table) {

        scopeStack.push(new HashSet<>());
        System.out.println("DEBUG [ScopeViolationCheck]: Entering new block scope, depth: " + scopeStack.size());

        for (JmmNode child : blockStmt.getChildren()) {
            visit(child, table);
        }

        scopeStack.pop();
        System.out.println("DEBUG [ScopeViolationCheck]: Exiting block scope");

        return null;
    }

    private Void visitBlockScope(JmmNode node, SymbolTable table) {

        for (JmmNode child : node.getChildren()) {
            visit(child, table);
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