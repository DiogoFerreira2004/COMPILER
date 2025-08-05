package pt.up.fe.comp2025.optimization.core;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.optimization.ollir.formatter.OllirCodeFormatter;
import pt.up.fe.comp2025.optimization.ollir.generator.OllirGeneratorVisitor;
import pt.up.fe.comp2025.optimization.ollir.processor.OllirPostProcessor;
import pt.up.fe.comp2025.optimization.util.LabelManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for generating OLLIR code from the AST.
 * A generic implementation that coordinates the generation process.
 */
public class OllirGenerator {

    private List<Report> reports;

    public OllirGenerator() {
        this.reports = new ArrayList<>();
    }

    /**
     * Generates OLLIR code from the abstract syntax tree.
     *
     * @param semanticsResult The result of semantic analysis
     * @return The OLLIR generation result
     */
    public OllirResult generateOllir(JmmSemanticsResult semanticsResult) {
        LabelManager.getInstance().resetGlobalCounter();
        JmmNode rootNode = semanticsResult.getRootNode();
        SymbolTable symbolTable = semanticsResult.getSymbolTable();

        List<Report> reports = new ArrayList<>(semanticsResult.getReports());

        try {
            // Log do início do processo
            System.out.println("Iniciando geração de código OLLIR");

            // Criar e configurar o visitor para geração de OLLIR
            OllirGeneratorVisitor visitor = new OllirGeneratorVisitor(symbolTable);

            // Gerar código OLLIR
            String ollirCode = visitor.visit(rootNode);
            System.out.println("Código OLLIR bruto gerado");
            System.out.println(ollirCode);

            // Validar e formatar o código OLLIR
            ollirCode = new OllirCodeFormatter().format(ollirCode);
            System.out.println("Código OLLIR formatado");
            System.out.println(ollirCode);

            // Verificar se há erros na geração
            if (visitor.hasErrors()) {
                reports.addAll(visitor.getErrors().stream()
                        .map(error -> new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, -1, error))
                        .collect(Collectors.toList()));

                throw new RuntimeException("Errors occurred during OLLIR generation");
            }

            // Criar resultado OLLIR
            OllirResult result = new OllirResult(semanticsResult, ollirCode, reports);
            System.out.println("Resultado OLLIR criado. Iniciando pós-processamento");

            // Aplicar o pós-processador diretamente no objeto OllirResult
            OllirPostProcessor processor = new OllirPostProcessor();
            result = processor.process(result);

            // Adicionar relatórios do pós-processador
            reports.addAll(processor.getReports());
            System.out.println("Pós-processamento concluído");
            // Verificar se o código OLLIR é válido
            try {
                System.out.println("Validando código OLLIR final");
                result.getOllirClass();
                System.out.println("Código OLLIR validado com sucesso");
            } catch (Exception e) {
                System.err.println("Erro ao validar código OLLIR: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Generated OLLIR code is not valid: " + e.getMessage());
            }

            System.out.println("Geração de código OLLIR finalizada com sucesso");
            System.out.println("Código OLLIR final:");
            System.out.println(result.getOllirCode());

            return result;

        } catch (Exception e) {
            System.err.println("Erro durante a geração de código OLLIR: " + e.getMessage());
            e.printStackTrace();
            reports.add(new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, -1,
                    "Failed in OLLIR code generation: " + e.getMessage()));

            throw new RuntimeException("Failed in OLLIR code generation: " + e.getMessage(), e);
        }
    }
}