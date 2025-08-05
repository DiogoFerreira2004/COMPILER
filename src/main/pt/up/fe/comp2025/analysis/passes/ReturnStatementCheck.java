package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor para verificar os return statements nos métodos.
 * Verifica se todos os métodos não-void têm pelo menos um return e se todos os caminhos de execução terminam com return.
 */
public class ReturnStatementCheck extends AnalysisVisitor {

    private Map<String, Type> methodReturnTypes = new HashMap<>();
    private Map<String, Boolean> methodHasReturn = new HashMap<>();
    private String currentMethod;
    private Type currentReturnType;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("RETURN_STMT", this::visitReturnStmt);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (!method.hasAttribute("name")) {
            return null;
        }

        String methodName = method.get("name");
        currentMethod = methodName;
        methodHasReturn.put(methodName, false);

        // Extrair o tipo de retorno do método
        Type returnType = table.getReturnType(methodName);
        currentReturnType = returnType;
        methodReturnTypes.put(methodName, returnType);

        System.out.println("DEBUG [ReturnCheck]: Analyzing method " + methodName +
                " with return type " + (returnType != null ? returnType.getName() : "null"));

        // Visitar os filhos para buscar return statements
        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        // Verificar se um método não-void precisa de um return
        if (returnType != null && !"void".equals(returnType.getName())) {
            Boolean hasReturn = methodHasReturn.get(methodName);

            if (!Boolean.TRUE.equals(hasReturn)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        "Missing return statement in method '" + methodName + "' with return type '" +
                                returnType.getName() + (returnType.isArray() ? "[]" : "") + "'",
                        null
                ));
            }

            // Análise de fluxo para verificar se todos os caminhos têm return
            boolean allPathsReturn = checkAllControlPathsReturn(method);
            if (!allPathsReturn && !Boolean.TRUE.equals(hasReturn)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        "Not all execution paths in method '" + methodName + "' return a value",
                        null
                ));
            }
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        if (currentMethod != null) {
            methodHasReturn.put(currentMethod, true);

            Type returnType = methodReturnTypes.get(currentMethod);

            // Verificar return vazio em métodos não void
            if (returnType != null && !"void".equals(returnType.getName()) && returnStmt.getChildren().isEmpty()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        "Empty return in non-void method '" + currentMethod + "'",
                        null
                ));
            }

            // Verificar return com expressão em métodos void
            if (returnType != null && "void".equals(returnType.getName()) && !returnStmt.getChildren().isEmpty()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        "Cannot return a value from a method with void result type",
                        null
                ));
            }
        }

        return null;
    }

    private boolean checkAllControlPathsReturn(JmmNode methodNode) {
        // Encontrar o corpo do método (normalmente um BlockStmt)
        JmmNode methodBody = null;
        for (JmmNode child : methodNode.getChildren()) {
            if ("BlockStmt".equals(child.getKind()) || "STMT".equals(child.getKind())) {
                methodBody = child;
                break;
            }
        }

        if (methodBody == null) {
            return false;
        }

        return analyzeControlFlow(methodBody);
    }

    /**
     * Analisa o fluxo de controle para verificar se todos os caminhos terminam com return.
     */
    private boolean analyzeControlFlow(JmmNode node) {
        if (node == null) {
            return false;
        }

        // Se encontrarmos um return statement diretamente, este caminho retorna
        if ("RETURN_STMT".equals(node.getKind())) {
            return true;
        }

        // Para blocos, verificamos se tem um return ou se termina com return
        if ("BlockStmt".equals(node.getKind())) {
            List<JmmNode> statements = node.getChildren();

            if (statements.isEmpty()) {
                return false;
            }

            // Verificar se alguma das instruções do bloco é um return
            for (JmmNode stmt : statements) {
                if ("RETURN_STMT".equals(stmt.getKind())) {
                    return true;
                }
            }

            // Verificar a última instrução
            JmmNode lastStmt = statements.get(statements.size() - 1);

            if ("IfStmt".equals(lastStmt.getKind())) {
                // Um if como última declaração precisa ter return em ambos os branches
                return checkIfStatementReturns(lastStmt);
            } else if ("WhileStmt".equals(lastStmt.getKind())) {
                // Um while como última declaração não garante retorno (pode não executar)
                return false;
            } else {
                // Verificar recursivamente a última instrução
                return analyzeControlFlow(lastStmt);
            }
        }

        // Para if statements, verificamos se ambos os branches (then e else) têm return
        if ("IfStmt".equals(node.getKind())) {
            return checkIfStatementReturns(node);
        }

        // Para outros tipos de nós, verificamos seus filhos
        for (JmmNode child : node.getChildren()) {
            if (analyzeControlFlow(child)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfStatementReturns(JmmNode ifStmt) {
        // Um if precisa ter pelo menos 2 filhos (condição e corpo do then)
        if (ifStmt.getChildren().size() < 2) {
            return false;
        }

        // Pegar o corpo do then (normalmente o segundo filho, após a condição)
        JmmNode thenBody = ifStmt.getChildren().get(1);
        boolean thenReturns = analyzeControlFlow(thenBody);

        // Verificar se há um else (terceiro filho)
        boolean elseReturns = false;
        if (ifStmt.getChildren().size() > 2) {
            JmmNode elseBody = ifStmt.getChildren().get(2);
            elseReturns = analyzeControlFlow(elseBody);
        }

        // Ambos os branches devem retornar para garantir que o if retorna
        return thenReturns && elseReturns;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }
}