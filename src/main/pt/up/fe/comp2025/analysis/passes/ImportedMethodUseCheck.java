package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

public class ImportedMethodUseCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [ImportedMethodUseCheck]: Entering method: " + currentMethod);
        }

        for (JmmNode child : method.getChildren()) {
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