package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ControlFlowCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("IfStmt", this::visitIfStmt);
        addVisit("WhileStmt", this::visitWhileStmt);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [ControlFlowCheck]: Entering method: " + currentMethod);
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitIfStmt(JmmNode ifStmt, SymbolTable table) {
        System.out.println("DEBUG [ControlFlowCheck]: Checking if statement at line " + ifStmt.getLine());

        for (JmmNode child : ifStmt.getChildren()) {
            visit(child, table);
        }

        if (ifStmt.getChildren().isEmpty()) {
            String message = "If statement missing condition";
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    ifStmt.getLine(),
                    ifStmt.getColumn(),
                    message,
                    null
            );
            addReport(report);
            return null;
        }

        JmmNode condition = ifStmt.getChildren().get(0);
        System.out.println("DEBUG [ControlFlowCheck]: If condition kind: " + condition.getKind());

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        try {
            Type conditionType = typeUtils.getExprType(condition);
            System.out.println("DEBUG [ControlFlowCheck]: Condition type: " + conditionType.getName());

            if (!"boolean".equals(conditionType.getName())) {
                String message = String.format(
                        "If statement condition must be of type boolean, but found '%s'",
                        conditionType.getName()
                );
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        message,
                        null
                );
                addReport(report);
                System.out.println("DEBUG [ControlFlowCheck]: Reported error for non-boolean condition");
            }
        } catch (Exception e) {
            String message = "Error evaluating condition type: " + e.getMessage();
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [ControlFlowCheck]: Exception while checking condition: " + e.getMessage());
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        System.out.println("DEBUG [ControlFlowCheck]: Checking while statement at line " + whileStmt.getLine());

        for (JmmNode child : whileStmt.getChildren()) {
            visit(child, table);
        }

        if (whileStmt.getChildren().isEmpty()) {
            String message = "While statement missing condition";
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    whileStmt.getLine(),
                    whileStmt.getColumn(),
                    message,
                    null
            );
            addReport(report);
            return null;
        }

        JmmNode condition = whileStmt.getChildren().get(0);
        System.out.println("DEBUG [ControlFlowCheck]: While condition kind: " + condition.getKind());

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);

        try {
            Type conditionType = typeUtils.getExprType(condition);
            System.out.println("DEBUG [ControlFlowCheck]: Condition type: " + conditionType.getName());

            if (!"boolean".equals(conditionType.getName())) {
                String message = String.format(
                        "While loop condition must be of type boolean, but found '%s'",
                        conditionType.getName()
                );
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        message,
                        null
                );
                addReport(report);
                System.out.println("DEBUG [ControlFlowCheck]: Reported error for non-boolean condition");
            }
        } catch (Exception e) {
            String message = "Error evaluating condition type: " + e.getMessage();
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [ControlFlowCheck]: Exception while checking condition: " + e.getMessage());
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