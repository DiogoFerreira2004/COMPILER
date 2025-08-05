package pt.up.fe.comp2025.optimization.core;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.optimization.register.RegisterAllocator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Responsável por aplicar otimizações ao código OLLIR.
 * Atualmente foca na alocação de registradores.
 */
public class OllirOptimizer {

    private final OllirResult ollirResult;
    private final int maxRegisters;
    private final List<Report> reports;

    /**
     * Constrói um otimizador OLLIR.
     *
     * @param ollirResult O resultado da geração OLLIR
     * @param maxRegisters Número máximo de registradores a usar (-1 para sem limite)
     */
    public OllirOptimizer(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;
        this.reports = new ArrayList<>(ollirResult.getReports());
    }

    /**
     * Aplica todas as otimizações OLLIR, principalmente alocação de registradores.
     *
     * @return O resultado OLLIR otimizado
     */
    public OllirResult optimize() {
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Iniciando alocação de registradores " +
                        (maxRegisters == 0 ? "otimizada" : "com limite de " + maxRegisters + " registradores")));

        try {
            // Verificar se a classe OLLIR está construída
            if (ollirResult.getOllirClass() == null) {
                throw new RuntimeException("Não é possível realizar alocação de registradores: classe OLLIR não disponível");
            }

            // Construir CFGs (necessário para análise de liveness)
            ollirResult.getOllirClass().buildCFGs();

            // Criar e aplicar alocador de registradores
            RegisterAllocator allocator = new RegisterAllocator(ollirResult.getOllirClass(), maxRegisters);

            // Em caso de erro, continuamos para aplicar a alocação de emergência
            try {
                allocator.allocate();
            } catch (Exception e) {
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                        "Erro durante alocação de registradores: " + e.getMessage() +
                                ". Tentando alocação de emergência."));
            }

            // Aplicar o mapeamento de registradores ao código OLLIR
            String optimizedCode = allocator.applyAllocation(ollirResult.getOllirCode());

            // Adicionar relatórios do alocador
            reports.addAll(allocator.getReports());

            // Registrar sucesso
            reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                    "Alocação de registradores concluída com sucesso"));

            // Criar novo resultado OLLIR com código otimizado
            OllirResult newResult = new OllirResult(
                    getJmmSemanticsResult(ollirResult),
                    optimizedCode,
                    reports
            );

            // CORREÇÃO: Preservar o objeto ollirClass modificado no novo resultado
            try {
                Field ollirClassField = OllirResult.class.getDeclaredField("ollirClass");
                ollirClassField.setAccessible(true);
                ollirClassField.set(newResult, ollirResult.getOllirClass());
            } catch (Exception e) {
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                        "Não foi possível preservar a estrutura interna da alocação de registradores: " + e.getMessage()));
            }

            return newResult;

        } catch (Exception e) {
            // Registrar erro
            String errorMsg = "Erro na alocação de registradores: " + e.getMessage();
            reports.add(new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, -1, errorMsg));
            e.printStackTrace();

            // Importante: em vez de falhar, retornamos o código original sem otimização
            return new OllirResult(
                    getJmmSemanticsResult(ollirResult),
                    ollirResult.getOllirCode(),
                    reports
            );
        }
    }

    /**
     * Obtém o JmmSemanticsResult de um OllirResult.
     * Usa reflexão para acessar o campo semanticsResult; se falhar, cria um novo.
     */
    private JmmSemanticsResult getJmmSemanticsResult(OllirResult ollirResult) {
        try {
            // Tentar usar reflexão para obter o JmmSemanticsResult do ollirResult
            Field semanticsResultField = OllirResult.class.getDeclaredField("semanticsResult");
            semanticsResultField.setAccessible(true);
            return (JmmSemanticsResult) semanticsResultField.get(ollirResult);
        } catch (Exception e) {
            // Se a reflexão falhar, criar um JmmSemanticsResult básico novo
            return new JmmSemanticsResult(null, null, Collections.emptyList(), ollirResult.getConfig());
        }
    }
}