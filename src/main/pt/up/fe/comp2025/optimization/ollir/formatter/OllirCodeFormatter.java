package pt.up.fe.comp2025.optimization.ollir.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

/**
 * Responsible for formatting OLLIR code to ensure correct syntax.
 * Follows the Single Responsibility Principle by focusing only on code formatting.
 */
public class OllirCodeFormatter {

    private List<Report> reports;

    public OllirCodeFormatter() {
        this.reports = new ArrayList<>();
    }

    /**
     * Formats OLLIR code, fixing common syntax issues.
     *
     * @param ollirCode The unformatted OLLIR code
     * @return The formatted OLLIR code
     */
    public String format(String ollirCode) {
        String formattedCode = ollirCode;

        // Corrigir formato de import (remover colchetes e pontos incorretos)
        formattedCode = fixImportFormat(formattedCode);

        // Verificar e corrigir a ordem dos elementos
        formattedCode = enforceElementOrder(formattedCode);

        // Indentar os corpos dos métodos para melhor legibilidade
        formattedCode = indentMethodBodies(formattedCode);

        return formattedCode;
    }

    /**
     * Enforces the correct order of OLLIR elements: fields, constructors, methods.
     * This is a critical fix for the OLLIR parser which strictly requires this order.
     *
     * @param ollirCode The OLLIR code to fix
     * @return The OLLIR code with elements in the correct order
     */
    private String enforceElementOrder(String ollirCode) {
        // Para uma solução completa e definitiva, vamos reconstruir o código OLLIR
        // organizando explicitamente os elementos na ordem correta

        // Verificar se existem declarações de campo após construtores
        Pattern fieldAfterConstructPattern = Pattern.compile("\\.construct.*?\\}\\s*\\.field", Pattern.DOTALL);
        Matcher fieldAfterConstructMatcher = fieldAfterConstructPattern.matcher(ollirCode);

        if (fieldAfterConstructMatcher.find()) {
            reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                    "Found field declarations after constructor. Restructuring OLLIR code."));

            // Encontrar a declaração da classe
            Pattern classPattern = Pattern.compile("(\\w+(?:\\s+extends\\s+\\w+)?\\s*\\{)");
            Matcher classMatcher = classPattern.matcher(ollirCode);

            if (classMatcher.find()) {
                String classDeclaration = classMatcher.group(1);

                // Extrair todas as declarações de campo
                List<String> fieldDeclarations = new ArrayList<>();
                Pattern fieldPattern = Pattern.compile("\\.field\\s+(?:public|private)\\s+\\w+\\.[\\w\\.]+;");
                Matcher fieldMatcher = fieldPattern.matcher(ollirCode);

                while (fieldMatcher.find()) {
                    fieldDeclarations.add(fieldMatcher.group(0));
                }

                // Extrair todas as declarações de construtores
                List<String> constructorDeclarations = new ArrayList<>();
                Pattern constructorPattern = Pattern.compile("\\.construct\\s+\\w+\\([^)]*\\)\\.[^{]*\\{[^}]*\\}");
                Matcher constructorMatcher = constructorPattern.matcher(ollirCode);

                while (constructorMatcher.find()) {
                    constructorDeclarations.add(constructorMatcher.group(0));
                }

                // Extrair todas as declarações de métodos
                List<String> methodDeclarations = new ArrayList<>();
                Pattern methodPattern = Pattern.compile("\\.method\\s+(?:public|private|protected)?\\s+\\w+\\([^)]*\\)\\.[^{]*\\{[^}]*\\}");
                Matcher methodMatcher = methodPattern.matcher(ollirCode);

                while (methodMatcher.find()) {
                    methodDeclarations.add(methodMatcher.group(0));
                }

                // Extrair importações
                List<String> imports = new ArrayList<>();
                Pattern importPattern = Pattern.compile("import\\s+[\\w\\.]+;");
                Matcher importMatcher = importPattern.matcher(ollirCode);

                while (importMatcher.find()) {
                    imports.add(importMatcher.group(0));
                }

                // Reconstruir o código OLLIR na ordem correta
                StringBuilder reorderedCode = new StringBuilder();

                // Imports primeiro
                for (String importDecl : imports) {
                    reorderedCode.append(importDecl).append("\n");
                }

                // Classe e sua abertura
                reorderedCode.append(classDeclaration).append("\n\n");

                // Campos em primeiro lugar
                for (String field : fieldDeclarations) {
                    reorderedCode.append(field).append("\n");
                }

                // Espaço entre campos e construtores
                if (!fieldDeclarations.isEmpty() && !constructorDeclarations.isEmpty()) {
                    reorderedCode.append("\n");
                }

                // Construtores em segundo lugar
                for (String constructor : constructorDeclarations) {
                    reorderedCode.append(constructor).append("\n\n");
                }

                // Métodos por último
                for (String method : methodDeclarations) {
                    reorderedCode.append(method).append("\n\n");
                }

                // Fechar a classe
                reorderedCode.append("}");

                reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                        "OLLIR code has been restructured to enforce correct element order."));

                return reorderedCode.toString();
            }
        }

        return ollirCode;
    }

    /**
     * Fixes the format of import statements to remove incorrect brackets and dots.
     *
     * @param ollirCode The OLLIR code to fix
     * @return The OLLIR code with fixed import statements
     */
    private String fixImportFormat(String ollirCode) {
        // Expressão regular para encontrar imports com formato incorreto
        Pattern pattern = Pattern.compile("import\\s+([\\w\\.]+)\\s*\\.\\s*\\[\\s*\\]\\s*;");
        Matcher matcher = pattern.matcher(ollirCode);

        StringBuilder correctedCode = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // Adicionar texto antes do import atual
            correctedCode.append(ollirCode, lastEnd, matcher.start());

            // Obter caminho de import (sem o ponto e colchetes)
            String importPath = matcher.group(1);

            // Adicionar import corrigido
            correctedCode.append("import ").append(importPath).append(";");

            // Atualizar posição final
            lastEnd = matcher.end();
        }

        // Adicionar código restante após o último import
        if (lastEnd < ollirCode.length()) {
            correctedCode.append(ollirCode.substring(lastEnd));
        }

        return correctedCode.toString();
    }

    /**
     * Indents method bodies for better readability.
     *
     * @param ollirCode The OLLIR code to indent
     * @return The OLLIR code with properly indented method bodies
     */
    private String indentMethodBodies(String ollirCode) {
        // Dividir código em linhas
        String[] lines = ollirCode.split("\n");
        StringBuilder result = new StringBuilder();

        boolean inMethod = false;

        for (String line : lines) {
            // Verificar se está entrando em um método
            if (line.matches("\\s*\\.(method|construct)\\s+.*\\{\\s*")) {
                inMethod = true;
                result.append(line).append("\n");
                continue;
            }

            // Verificar se está saindo de um método
            if (line.matches("\\s*}\\s*") && inMethod) {
                inMethod = false;
                result.append(line).append("\n");
                continue;
            }

            // Dentro do método, adicionar indentação
            if (inMethod && !line.trim().isEmpty()) {
                result.append("    ").append(line).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Returns reports generated during formatting.
     */
    public List<Report> getReports() {
        return reports;
    }
}