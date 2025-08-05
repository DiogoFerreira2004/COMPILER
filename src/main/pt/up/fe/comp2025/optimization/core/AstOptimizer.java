package pt.up.fe.comp2025.optimization.core;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.optimization.ast.optimizer.ConstantFoldingVisitor;
import pt.up.fe.comp2025.optimization.ast.optimizer.ConstantPropagationVisitor;
import pt.up.fe.comp2025.optimization.ast.optimizer.VarargHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Responsável pela otimização da AST.
 * Aplica diferentes técnicas como dobramento de constantes e propagação de constantes.
 */
public class AstOptimizer {

    private final JmmSemanticsResult semanticsResult;
    private final List<Report> reports;
    private final boolean optimizeEnabled;
    private static final int MAX_ITERATIONS = 10;  // Evitar loops infinitos

    /**
     * Constrói um otimizador de AST.
     *
     * @param semanticsResult Resultado da análise semântica
     */
    public AstOptimizer(JmmSemanticsResult semanticsResult) {
        this.semanticsResult = semanticsResult;
        this.reports = new ArrayList<>();
        this.optimizeEnabled = Boolean.parseBoolean(
                semanticsResult.getConfig().getOrDefault("optimize", "false"));
        System.out.println("DEBUG [AstOptimizer]: Created with optimizeEnabled: " + optimizeEnabled);
    }

    /**
     * Aplica otimizações na AST.
     * Realiza várias passagens de otimização até que não haja mais mudanças.
     *
     * @return Resultado semântico otimizado
     */
    public JmmSemanticsResult optimize() {
        System.out.println("DEBUG [AstOptimizer]: optimize() called");

        // Se otimizações não estão habilitadas, retornar sem alterações
        if (!optimizeEnabled) {
            System.out.println("DEBUG [AstOptimizer]: Optimizations disabled, skipping");
            return semanticsResult;
        }

        System.out.println("DEBUG [AstOptimizer]: Starting optimization process");

        JmmNode ast = semanticsResult.getRootNode();
        boolean changed;
        int iteration = 0;

        // Criar instâncias dos visitantes uma única vez para preservar estado
        ConstantPropagationVisitor constPropagator = new ConstantPropagationVisitor(semanticsResult.getSymbolTable());
        ConstantFoldingVisitor constFolder = new ConstantFoldingVisitor(semanticsResult.getSymbolTable());
        VarargHandler varargHandler = new VarargHandler(semanticsResult.getSymbolTable());

        // Primeira passagem para identificar variáveis de loop
        System.out.println("DEBUG [AstOptimizer]: Executando primeira passagem para identificar variáveis de loop");
        constPropagator.visit(ast, new HashMap<>());

        // Obter registro de variáveis modificadas em loops
        Set<String> loopVariables = constPropagator.getLoopModifiedVariables();
        System.out.println("DEBUG [AstOptimizer]: Variáveis de loop identificadas: " + loopVariables);
        Set<String> loopConditionVars = constPropagator.getLoopConditionVariables();
        // Compartilhar informações com outros visitors
        constFolder.setLoopModifiedVariables(loopVariables);
        constFolder.setLoopConditionVariables(loopConditionVars);

        // Aplicar otimizações até que não haja mais mudanças ou atinja limite
        do {
            changed = false;
            iteration++;

            // 1. Aplicar propagação de constantes com tratamento especial para loops
            constPropagator.visit(ast, new HashMap<>());
            boolean propChanged = constPropagator.hasChanged();

            // CORREÇÃO: Debug adicional para identificar mudanças com precisão
            if (propChanged) {
                System.out.println("DEBUG [AstOptimizer]: Propagação de constantes aplicou mudanças");
                System.out.println("DEBUG [AstOptimizer]: AST após propagação: " + ast.toTree());
                changed = true;
            }

            // 2. Após propagar constantes, aplicar dobramento de constantes
            constFolder.visit(ast);
            boolean foldChanged = constFolder.hasChanged();

            // Se houve mudanças no dobramento, atualizar flag global
            if (foldChanged) {
                System.out.println("DEBUG [AstOptimizer]: Constant folding applied changes");
                changed = true;
            }

            // 3. Aplicar processamento de parâmetros varargs, se necessário
            varargHandler.visit(ast);
            boolean varargChanged = varargHandler.hasChanged();

            // Se houve mudanças no tratamento de varargs, atualizar flag global
            if (varargChanged) {
                System.out.println("DEBUG [AstOptimizer]: Vararg handling applied changes");
                changed = true;
            }

            System.out.println("DEBUG [AstOptimizer]: Iteration " + iteration +
                    " completed, changes: " + changed +
                    " (prop=" + propChanged +
                    ", fold=" + foldChanged +
                    ", vararg=" + varargChanged + ")");

            // Limitar número de iterações para evitar loops infinitos
            if (iteration >= MAX_ITERATIONS) {
                System.out.println("DEBUG [AstOptimizer]: Reached maximum iterations (" +
                        MAX_ITERATIONS + "), stopping optimization");
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION,
                        -1, -1, "Maximum optimization iterations reached"));
                break;
            }

        } while (changed);  // Continuar enquanto houver mudanças

        // Registrar informações sobre variáveis modificadas em loops na SymbolTable
        // para que possam ser usadas pelo gerador OLLIR
        semanticsResult.getSymbolTable().putObject("loopModifiedVariables", loopVariables);
        System.out.println("DEBUG [AstOptimizer]: Registered loop variables in SymbolTable: " + loopVariables);

        System.out.println("DEBUG [AstOptimizer]: Optimization process completed, changed: " + changed);

        // Criar novo resultado semântico com AST otimizada
        return new JmmSemanticsResult(ast, semanticsResult.getSymbolTable(),
                semanticsResult.getReports(), semanticsResult.getConfig());
    }
    /**
     * Obtém os relatórios gerados durante a otimização.
     */
    public List<Report> getReports() {
        return reports;
    }
}