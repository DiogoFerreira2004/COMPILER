package pt.up.fe.comp2025.optimization.ollir.processor;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processador que garante a correta formatação e completude do código OLLIR gerado.
 * Implementação totalmente genérica que não depende de conhecimento específico de métodos.
 */
public class OllirPostProcessor {

    private List<Report> reports;
    private static final Pattern TEMP_PATTERN = Pattern.compile("\\btmp\\d+\\b"); // palavra inteira

    public OllirPostProcessor() {
        this.reports = new ArrayList<>();
    }

    /**
     * Processa o resultado OLLIR para garantir sua validade e completude.
     *
     * @param ollirResult O resultado OLLIR a ser processado
     * @return O resultado OLLIR processado
     */
    public OllirResult process(OllirResult ollirResult) {
        // Log para depuração
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Iniciando processamento de código OLLIR"));
        System.out.println("Iniciando processamento de código OLLIR");

        // Extrair o código OLLIR do resultado
        String ollirCode = ollirResult.getOllirCode();
        System.out.println(ollirCode);

        // Processar o código OLLIR
        String processedCode = processOllirCode(ollirCode);

        // Obter a configuração do resultado original
        Map<String, String> config = ollirResult.getConfig();

        // Criar um novo resultado com o código processado
        OllirResult result = new OllirResult(processedCode, config);

        // Adicionar todos os relatórios
        List<Report> combinedReports = new ArrayList<>(ollirResult.getReports());
        combinedReports.addAll(reports);

        // Uma vez que não podemos modificar diretamente os relatórios no OllirResult,
        // retornamos o resultado e também atualizamos nossa lista de relatórios para
        // que o chamador possa acessá-los
        this.reports = combinedReports;

        return result;
    }

    /**
     * Processa o código OLLIR como string.
     *
     * @param ollirCode O código OLLIR como string
     * @return O código OLLIR processado
     */
    private String processOllirCode(String ollirCode) {
        String processedCode = ollirCode;

        // Corrigir formato de imports
        processedCode = fixImportFormat(processedCode);
        System.out.println("Corrigido formato de imports");

        // Verificar e corrigir a ordem dos elementos
        processedCode = enforceElementOrder(processedCode);
        System.out.println("Corrigida ordem dos elementos");

        // Verificar e corrigir métodos vazios
        processedCode = fixEmptyMethods(processedCode);
        System.out.println("Corrigidos métodos vazios");

        // Corrigir sintaxe de chamadas de método
        processedCode = fixMethodCallSyntax(processedCode);
        System.out.println("Corrigida sintaxe de chamadas de método");

        // Indentar os corpos dos métodos para melhor legibilidade
        processedCode = indentMethodBodies(processedCode);
        System.out.println("Indentados corpos dos métodos");

        // Renumerar variáveis temporárias para garantir sequência contínua
        processedCode = renumberTemporaries(processedCode);
        System.out.println("Renumeradas variáveis temporárias");
        System.out.println(processedCode);

        return processedCode;
    }

    /**
     * Renumera todas as variáveis temporárias (tmp0, tmp1, ...) para garantir uma sequência contínua.
     *
     * @param ollirCode O código OLLIR com possíveis falhas na sequência de temporários
     * @return O código OLLIR com temporários renumerados sequencialmente
     */
    private String renumberTemporaries(String ollirCode) {
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Iniciando renumeração de variáveis temporárias…"));
        System.out.println("DEBUG: Iniciando renumeração de variáveis temporárias…");
        System.out.println("OllirCode: " + ollirCode);
        /* 1. Descobrir todos os temporários em ordem de aparecimento */
        Map<String, String> tempMap = new LinkedHashMap<>();    // mantém ordem de inserção
        Matcher scan = TEMP_PATTERN.matcher(ollirCode);
        System.out.println("Encontrando temporários");
        System.out.println("OllirCode: " + ollirCode);
        while (scan.find()) {
            String oldName = scan.group();                      // e.g. tmp7
            tempMap.computeIfAbsent(oldName, k -> "tmp" + tempMap.size());
        }
        System.out.println("Encontrados temporários: " + tempMap.size() + " → " + tempMap);
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Temporários distintos encontrados: " + tempMap.size() + " → " + tempMap));

        if (tempMap.size() <= 1) {              // nada a fazer
            return ollirCode;
        }
        System.out.println("Substituindo temporários");
        /* 2. Substituir todas as ocorrências de forma estável */
        StringBuffer out = new StringBuffer();
        scan.reset();                           // reaproveita o mesmo Matcher

        while (scan.find()) {
            scan.appendReplacement(out, tempMap.get(scan.group()));
        }
        scan.appendTail(out);

        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                "Renumeração concluída. Total renumerado: " + tempMap.size()));
        return out.toString();
    }
    /**
     * Corrige a sintaxe de chamadas de método em código OLLIR.
     * Abordagem genérica que não depende de conhecimento específico de métodos.
     */
    private String fixMethodCallSyntax(String ollirCode) {
        // Identificar todos os métodos no código
        Pattern methodPattern = Pattern.compile("\\.method\\s+(?:public|private|static\\s+)*\\s*([\\w<>]+)\\s*\\([^)]*\\)\\s*\\.([\\w\\.]+)\\s*\\{([^}]*)\\}");
        Matcher methodMatcher = methodPattern.matcher(ollirCode);

        StringBuilder processedCode = new StringBuilder(ollirCode);
        int offset = 0;

        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String returnType = methodMatcher.group(2);
            String methodBody = methodMatcher.group(3);

            // Verificar se o método precisa de processamento (tem referências a imports mas sem invoke)
            if (!methodBody.contains("invoke") && methodContainsExternalReferences(methodBody, ollirCode)) {
                // Obter referências a objetos externos
                Set<String> externalRefs = extractExternalReferences(methodBody, ollirCode);

                // Para cada referência externa, corrigir qualquer expressão que deveria ser uma chamada de método
                for (String externalRef : externalRefs) {
                    // Identificar padrões de uso que podem ser chamadas de método
                    Pattern usagePattern = Pattern.compile("\\b" + Pattern.quote(externalRef) + "\\.(\\w+)\\b");
                    Matcher usageMatcher = usagePattern.matcher(methodBody);

                    // Verificar cada possível chamada de método
                    while (usageMatcher.find()) {
                        String potentialMethod = usageMatcher.group(1);

                        // Ignorar referências que já são parte de uma chamada de método
                        if (methodBody.contains("invoke(" + externalRef + ", \"" + potentialMethod + "\"")) {
                            continue;
                        }

                        // Determinar tipo de retorno pelo contexto
                        String inferredReturnType = inferReturnTypeFromContext(methodBody, usageMatcher.start(), usageMatcher.end());

                        // Construir um invoke
                        String invokeExpr = "invokestatic(" + externalRef + ", \"" + potentialMethod + "\")." + inferredReturnType;

                        // Obter contexto para decidir como inserir o invoke
                        int usageStart = methodMatcher.start(3) + usageMatcher.start() + offset;
                        int usageEnd = methodMatcher.start(3) + usageMatcher.end() + offset;

                        // Decidir como tratar a chamada de método com base no contexto
                        if (isUsedInExpression(processedCode.toString(), usageStart, usageEnd)) {
                            // Substituir direto se usada em expressão
                            processedCode.replace(usageStart, usageEnd, invokeExpr);

                            // Ajustar offset
                            offset += invokeExpr.length() - (usageEnd - usageStart);
                        } else {
                            // Se não for parte de expressão, adicionar como statement
                            // Encontrar o próximo ponto e vírgula ou final de linha
                            int statementEnd = findStatementEnd(processedCode.toString(), usageEnd);

                            if (statementEnd > usageEnd) {
                                // Se já é um statement, substituir com statement de invoke
                                processedCode.replace(usageStart, statementEnd, invokeExpr + ";");

                                // Ajustar offset
                                offset += (invokeExpr + ";").length() - (statementEnd - usageStart);
                            }
                        }
                    }
                }
            }
        }

        return processedCode.toString();
    }

    /**
     * Verifica se um método contém referências a classes externas (de imports).
     */
    private boolean methodContainsExternalReferences(String methodBody, String ollirCode) {
        // Extrair lista de imports
        List<String> imports = extractImports(ollirCode);

        // Para cada import, verificar se é referenciado no corpo do método
        for (String importPath : imports) {
            // Obter o nome simples da classe importada
            String className = extractSimpleClassName(importPath);

            // Verificar se o nome da classe aparece no corpo do método
            if (methodBody.contains(className + ".")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extrai referências a objetos externos no corpo de um método.
     */
    private Set<String> extractExternalReferences(String methodBody, String ollirCode) {
        Set<String> references = new HashSet<>();

        // Extrair lista de imports
        List<String> imports = extractImports(ollirCode);

        // Para cada import, procurar referências no corpo do método
        for (String importPath : imports) {
            // Obter o nome simples da classe importada
            String className = extractSimpleClassName(importPath);

            // Verificar referências à classe importada
            Pattern refPattern = Pattern.compile("\\b" + Pattern.quote(className) + "\\.(\\w+)\\b");
            Matcher refMatcher = refPattern.matcher(methodBody);

            if (refMatcher.find()) {
                references.add(className);
            }
        }

        return references;
    }

    /**
     * Extrai o nome simples de uma classe a partir de um caminho de importação.
     */
    private String extractSimpleClassName(String importPath) {
        int lastDot = importPath.lastIndexOf('.');
        return lastDot != -1 ? importPath.substring(lastDot + 1) : importPath;
    }

    /**
     * Infere o tipo de retorno com base no contexto onde a expressão é usada.
     * Usa apenas análise sintática, sem depender de conhecimento específico de métodos.
     */
    private String inferReturnTypeFromContext(String code, int startPos, int endPos) {
        // Verificar se a expressão é usada em um contexto de tipo específico

        // Contexto: Atribuição (procurar := antes ou depois)
        Pattern assignPattern = Pattern.compile("(\\w+)\\.(\\w+)\\s*:=\\s*");
        Matcher assignMatcher = assignPattern.matcher(code);

        // Ajustar para procurar antes da posição especificada
        assignMatcher.region(0, startPos);
        if (assignMatcher.find() && assignMatcher.end() == startPos) {
            // Expressão usada no lado direito de uma atribuição
            String varType = assignMatcher.group(2);

            // Converter tipo de variável para tipo OLLIR
            switch (varType) {
                case "i32": return "i32";
                case "bool": return "bool";
                case "array": return "array.i32"; // Assumir array de inteiros como padrão
                default: return varType;
            }
        }

        // Contexto: Condição (if ou while)
        int ifPos = code.lastIndexOf("if", startPos);
        int whilePos = code.lastIndexOf("while", startPos);

        if ((ifPos > 0 || whilePos > 0) && code.substring(Math.max(ifPos, whilePos), startPos).trim().matches("\\s*\\([^)]*")) {
            // Expressão usada em condição - retorno booleano
            return "bool";
        }

        // Contexto: Operação aritmética
        Pattern arithPattern = Pattern.compile("[+\\-*/]\\s*$");
        Matcher arithMatcher = arithPattern.matcher(code.substring(0, startPos));
        if (arithMatcher.find()) {
            // Expressão usada em operação aritmética - retorno inteiro
            return "i32";
        }

        // Se não conseguir determinar, retornar void como padrão
        return "V";
    }

    /**
     * Verifica se a expressão na posição especificada é usada dentro de outra expressão.
     */
    private boolean isUsedInExpression(String code, int startPos, int endPos) {
        // Verificar se a expressão é seguida por operadores ou parte de outra expressão
        if (endPos < code.length()) {
            char nextChar = code.charAt(endPos);
            return nextChar == '+' || nextChar == '-' || nextChar == '*' || nextChar == '/' ||
                    nextChar == '=' || nextChar == '<' || nextChar == '>' || nextChar == '&' ||
                    nextChar == '|' || nextChar == '!';
        }
        return false;
    }

    /**
     * Encontra o final do statement atual.
     */
    private int findStatementEnd(String code, int startPos) {
        int semiPos = code.indexOf(';', startPos);
        int newLinePos = code.indexOf('\n', startPos);

        if (semiPos == -1) return newLinePos != -1 ? newLinePos : code.length();
        if (newLinePos == -1) return semiPos;

        return Math.min(semiPos, newLinePos);
    }

    /**
     * Fixa métodos vazios adicionando uma instrução de retorno válida se necessário.
     */
    private String fixEmptyMethods(String ollirCode) {
        Pattern methodPattern = Pattern.compile("\\.method\\s+(?:public|private)\\s+([\\w<>]+)\\s*\\(([^)]*)\\)\\s*\\.([\\w\\.]+)\\s*\\{([^}]*)\\}");
        Matcher methodMatcher = methodPattern.matcher(ollirCode);

        StringBuffer result = new StringBuffer();

        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String params = methodMatcher.group(2);
            String returnType = methodMatcher.group(3);
            String body = methodMatcher.group(4).trim();

            // Verificar se o corpo está vazio ou contém apenas comentários
            boolean isEmpty = body.isEmpty() || body.matches("\\s*(?://.*\\n?)*\\s*");

            if (isEmpty) {
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                        "Método '" + methodName + "' tem corpo vazio. Adicionando instrução de retorno."));

                // Gerar instrução de retorno apropriada com base no tipo de retorno
                String returnValue = generateDefaultReturnValue(returnType);
                String fixedBody = "\n    " + returnValue + "\n";

                methodMatcher.appendReplacement(result,
                        ".method " + (methodName.equals("main") ? "public static" : "public") +
                                " " + methodName + "(" + params + ")." + returnType + " {" + fixedBody + "}");
            } else {
                methodMatcher.appendReplacement(result, methodMatcher.group(0));
            }
        }

        methodMatcher.appendTail(result);
        return result.toString();
    }

    /**
     * Gera um valor de retorno padrão com base no tipo.
     * Abordagem genérica que funciona para qualquer tipo.
     */
    private String generateDefaultReturnValue(String ollirType) {
        if ("V".equals(ollirType)) {
            return "ret.V";
        } else if ("i32".equals(ollirType)) {
            return "ret.i32 0.i32";
        } else if ("bool".equals(ollirType)) {
            return "ret.bool 0.bool";
        } else if (ollirType.startsWith("array.")) {
            String elemType = ollirType.substring(6);
            return "temp.array." + elemType + " :=.array." + elemType + " new(array, 0.i32).array." + elemType + ";\nret.array." + elemType + " temp.array." + elemType;
        } else {
            return "ret." + ollirType + " null." + ollirType;
        }
    }

    /**
     * Fixa o formato de declarações de importação.
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
     * Extrai os nomes das classes importadas do código OLLIR.
     */
    private List<String> extractImports(String ollirCode) {
        List<String> imports = new ArrayList<>();

        Pattern importPattern = Pattern.compile("import\\s+([\\w\\.]+);");
        Matcher importMatcher = importPattern.matcher(ollirCode);

        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }

        return imports;
    }

    /**
     * Força a ordem correta dos elementos OLLIR: campos, construtores, métodos.
     */
    private String enforceElementOrder(String ollirCode) {
        // Verifica se há declarações de campo após construtores
        Pattern fieldAfterConstructPattern = Pattern.compile("\\.construct.*?\\}\\s*\\.field", Pattern.DOTALL);
        Matcher fieldAfterConstructMatcher = fieldAfterConstructPattern.matcher(ollirCode);

        if (fieldAfterConstructMatcher.find()) {
            reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                    "Encontradas declarações de campo após construtor. Reestruturando código OLLIR."));

            // Encontrar a declaração da classe
            Pattern classPattern = Pattern.compile("(\\w+(?:\\s+extends\\s+\\w+)?\\s*\\{)");
            Matcher classMatcher = classPattern.matcher(ollirCode);

            if (classMatcher.find()) {
                String classDeclaration = classMatcher.group(1);

                // Extrair declarações de campo
                List<String> fieldDeclarations = new ArrayList<>();
                Pattern fieldPattern = Pattern.compile("\\.field\\s+(?:public|private)\\s+\\w+\\.[\\w\\.]+;");
                Matcher fieldMatcher = fieldPattern.matcher(ollirCode);

                while (fieldMatcher.find()) {
                    fieldDeclarations.add(fieldMatcher.group(0));
                }

                // Extrair construtores
                List<String> constructorDeclarations = new ArrayList<>();
                Pattern constructorPattern = Pattern.compile("\\.construct\\s+\\w+\\([^)]*\\)\\.[^{]*\\{[^}]*\\}");
                Matcher constructorMatcher = constructorPattern.matcher(ollirCode);

                while (constructorMatcher.find()) {
                    constructorDeclarations.add(constructorMatcher.group(0));
                }

                // Extrair métodos
                List<String> methodDeclarations = new ArrayList<>();
                Pattern methodPattern = Pattern.compile("\\.method\\s+(?:public|private|protected|static)?\\s+\\w+\\([^)]*\\)\\.[^{]*\\{[^}]*\\}");
                Matcher methodMatcher = methodPattern.matcher(ollirCode);

                while (methodMatcher.find()) {
                    methodDeclarations.add(methodMatcher.group(0));
                }

                // Extrair importações
                List<String> imports = extractImports(ollirCode);

                // Reconstruir o código OLLIR na ordem correta
                StringBuilder reorderedCode = new StringBuilder();

                // Imports primeiro
                for (String importDecl : imports) {
                    reorderedCode.append("import ").append(importDecl).append(";\n");
                }
                if (!imports.isEmpty()) {
                    reorderedCode.append("\n");
                }

                // Classe e sua abertura
                reorderedCode.append(classDeclaration).append("\n\n");

                // Campos primeiro
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
                        "Código OLLIR reestruturado para garantir a ordem correta dos elementos."));

                return reorderedCode.toString();
            }
        }

        return ollirCode;
    }

    /**
     * Indenta os corpos dos métodos para melhor legibilidade.
     */
    private String indentMethodBodies(String ollirCode) {
        String[] lines = ollirCode.split("\n");
        StringBuilder result = new StringBuilder();

        boolean inMethod = false;

        for (String line : lines) {
            // Verificar se está entrando em um método ou construtor
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
     * Retorna os relatórios gerados durante o processamento.
     */
    public List<Report> getReports() {
        return reports;
    }

    // Classes utilitárias internas

    private static class HashSet<T> extends java.util.HashSet<T> {
        // Apenas para reduzir a verbosidade da importação
    }
}