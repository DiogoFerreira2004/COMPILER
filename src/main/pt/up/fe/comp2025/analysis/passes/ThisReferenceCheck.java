package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

/**
 * Visitor para verificar o uso de "this" em contextos inválidos.
 */
public class ThisReferenceCheck extends AnalysisVisitor {

    private String currentMethod;
    private boolean isStaticContext = false;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("ThisExpression", this::visitThisExpression);
        addVisit("ThisExpr", this::visitThisExpression);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");

            // Em Java, o método main é implicitamente estático
            isStaticContext = "main".equals(currentMethod);

            System.out.println("DEBUG [ThisReferenceCheck]: Entering method: " + currentMethod +
                    (isStaticContext ? " (static context)" : ""));
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitThisExpression(JmmNode thisExpr, SymbolTable table) {
        System.out.println("DEBUG [ThisReferenceCheck]: Found 'this' reference at line " + thisExpr.getLine());

        if (isStaticContext) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    thisExpr.getLine(),
                    thisExpr.getColumn(),
                    "Cannot use 'this' in static context (method 'main')",
                    null
            ));
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
