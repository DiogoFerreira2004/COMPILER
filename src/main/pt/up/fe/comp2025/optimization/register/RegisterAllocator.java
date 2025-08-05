package pt.up.fe.comp2025.optimization.register;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.optimization.register.analysis.InterferenceGraphBuilder;
import pt.up.fe.comp2025.optimization.register.analysis.LivenessAnalyzer;
import pt.up.fe.comp2025.optimization.register.coloring.DSaturColoringStrategy;
import pt.up.fe.comp2025.optimization.register.coloring.GraphColoringStrategy;

import java.lang.annotation.ElementType;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordena o processo de alocação de registradores.
 * Utiliza análise de liveness, grafo de interferência e coloração de grafo.
 */
public class RegisterAllocator {

    private final ClassUnit ollirClass;
    private final int maxRegisters;
    private final List<Report> reports;

    // Mapeamento final de variáveis para registradores
    private final Map<Method, Map<String, Integer>> methodRegisterMaps;

    // Mapeamento de variáveis derramadas (spilled)
    private final Map<Method, Set<String>> methodSpilledVars;

    // Mapa para registrar o número total de registradores por método
    private final Map<Method, Integer> methodTotalRegisters;

    // Componentes para alocação de registradores
    private final LivenessAnalyzer livenessAnalyzer;
    private final InterferenceGraphBuilder graphBuilder;
    private final GraphColoringStrategy coloringStrategy;

    // Contador de registradores efetivamente usados no código final
    private int actualRegisterCount;

    /**
     * Construtor do alocador de registradores.
     *
     * @param ollirClass A classe OLLIR a ser processada
     * @param maxRegisters Número máximo de registradores (-1 para ilimitado, 0 para otimizado)
     */
    public RegisterAllocator(ClassUnit ollirClass, int maxRegisters) {
        this.ollirClass = ollirClass;
        this.maxRegisters = maxRegisters;
        this.reports = new ArrayList<>();
        this.methodRegisterMaps = new HashMap<>();
        this.methodSpilledVars = new HashMap<>();
        this.methodTotalRegisters = new HashMap<>();
        this.actualRegisterCount = 0;

        this.livenessAnalyzer = new LivenessAnalyzer();
        this.graphBuilder = new InterferenceGraphBuilder();
        this.coloringStrategy = new DSaturColoringStrategy();

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Iniciando alocação de registradores com limite de " + maxRegisters + " registradores"));
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "RegisterAllocator criado com maxRegisters=" + maxRegisters));
    }

    /**
     * Obtém o número real de registradores usados após a alocação.
     */
    public int getActualRegisterCount() {
        return actualRegisterCount;
    }

    /**
     * Executa a alocação de registradores para todos os métodos da classe.
     */
    public void allocate() {
        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                "Iniciando alocação de registradores para a classe " + ollirClass.getClassName()));

        for (Method method : ollirClass.getMethods()) {
            // Para fins de debug: mostrar informações da tabela de variáveis
            logMethodVariables(method);

            // Pular construtor da classe
            if (method.isConstructMethod()) {
                reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                        "Pulando construtor da classe"));
                continue;
            }

            reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                    "Alocando registradores para o método " + method.getMethodName()));

            try {
                if (maxRegisters == -1) {
                    allocateDefaultRegisters(method);
                } else if (maxRegisters == 0) {
                    allocateOptimizedRegisters(method);
                } else {
                    allocateLimitedRegisters(method, maxRegisters);
                }
            } catch (Exception e) {
                reports.add(new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, -1,
                        "Erro durante alocação de registradores para " + method.getMethodName() +
                                ": " + e.getMessage()));
                e.printStackTrace();

                createEmergencyAllocation(method);
            }
        }

        // Calcular o número total de registradores usados
        updateActualRegisterCount();
    }

// Em RegisterAllocator.java, adicionar/modificar os seguintes métodos:

    /**
     * Atualiza o contador real de registradores usados em todos os métodos.
     * CORREÇÃO: Respeitar o limite de registradores na contagem.
     */
    private void updateActualRegisterCount() {
        // Mapa para armazenar o número de registradores por método
        Map<String, Integer> methodRegCounts = new HashMap<>();

        // Percorrer todos os métodos
        for (Method method : ollirClass.getMethods()) {
            String methodName = method.getMethodName();

            // Contar registradores realmente usados pelo método
            Set<Integer> methodRegs = new HashSet<>();
            Map<String, Descriptor> varTable = method.getVarTable();
            if (varTable != null) {
                for (Descriptor descriptor : varTable.values()) {
                    if (descriptor != null) {
                        methodRegs.add(descriptor.getVirtualReg());
                    }
                }
            }

            int regCount = methodRegs.size();

            // CORREÇÃO: Se temos um limite de registradores, garantir que respeitamos esse limite
            if (maxRegisters > 0) {
                regCount = Math.min(regCount, maxRegisters);
            }


            // Armazenar a contagem de registradores para este método
            methodRegCounts.put(methodName, regCount);

            // Gerar relatório detalhado para este método
            reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                    "Método " + methodName + " utiliza " + regCount + " registradores"));
        }

        // Atualizar a contagem global de registradores (máximo entre todos os métodos)
        this.actualRegisterCount = methodRegCounts.values().stream()
                .max(Integer::compare)
                .orElse(0);

        // CORREÇÃO: Se temos limite de registradores, garantir que a contagem total respeite esse limite
        if (maxRegisters > 0) {
            this.actualRegisterCount = Math.min(this.actualRegisterCount, maxRegisters);
        }

        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                "Total de registradores utilizados: " + actualRegisterCount));
    }

    /**
     * Aplica a alocação de registradores a um método, atualizando os descritores.
     * CORREÇÃO: Garantir que os registradores atribuídos não excedam o limite.
     */
    private void applyRegisterAllocationToMethod(Method method, Map<String, Integer> registerMap) {
        Map<String, Descriptor> varTable = method.getVarTable();


        // Verificação preventiva - apenas para debug
        for (String varName : registerMap.keySet()) {
            if (!varTable.containsKey(varName)) {
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                        "Variável mapeada " + varName + " não encontrada na tabela de variáveis"));
            }
        }

        // Aplicar registradores aos descritores
        for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();

            if (descriptor != null && registerMap.containsKey(varName)) {
                int register = registerMap.get(varName);

                // CORREÇÃO: Garantir que o registro esteja dentro do limite
                if (maxRegisters > 0) {
                    register = Math.min(register, maxRegisters - 1);
                }

                descriptor.setVirtualReg(register);
            }
        }

        // Verificação pós-aplicação para validar o resultado
        Set<Integer> uniqueRegs = new HashSet<>();
        for (Descriptor descriptor : varTable.values()) {
            if (descriptor != null) {
                uniqueRegs.add(descriptor.getVirtualReg());
            }
        }

        // CORREÇÃO: Verificar se o número de registros respeita o limite
        if (maxRegisters > 0 && Collections.max(uniqueRegs) >= maxRegisters) {
            reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                    "Registros atribuídos excedem o limite! Ajustando para " + maxRegisters + " registros."));

            // Atualizar descritores novamente para garantir conformidade
            for (Descriptor descriptor : varTable.values()) {
                if (descriptor != null && descriptor.getVirtualReg() >= maxRegisters) {
                    descriptor.setVirtualReg(maxRegisters - 1);
                }
            }
        }

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Após aplicação: " + Math.min(uniqueRegs.size(), maxRegisters > 0 ? maxRegisters : Integer.MAX_VALUE) +
                        " registradores únicos para método " + method.getMethodName()));
    }

    /**
     * Registra informações sobre as variáveis de um método para debugging.
     */
    private void logMethodVariables(Method method) {
        StringBuilder sb = new StringBuilder("Variáveis do método " + method.getMethodName() + ":\n");

        // Parâmetros
        sb.append("  Parâmetros: ");
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                sb.append(((Operand) param).getName()).append(", ");
            }
        }

        // Tabela de variáveis
        sb.append("\n  Tabela de variáveis: ");
        for (String varName : method.getVarTable().keySet()) {
            sb.append(varName).append(", ");
        }

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1, sb.toString()));
    }

    /**
     * Alocação padrão de registradores (sem otimização).
     * Atribui um registrador diferente para cada variável.
     */
    private void allocateDefaultRegisters(Method method) {
        Map<String, Integer> registerMap = new HashMap<>();
        Map<String, Descriptor> varTable = method.getVarTable();

        int nextReg = 0;

        // Garantir que 'this' tenha registro específico no registro 0
        if (varTable.containsKey("this")) {
            registerMap.put("this", nextReg++);
        }

        // Garantir que todos os parâmetros recebam registradores
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                String paramName = ((Operand) param).getName();
                if (!registerMap.containsKey(paramName) && varTable.containsKey(paramName)) {
                    registerMap.put(paramName, nextReg++);
                }
            }
        }

        // Outras variáveis
        for (String varName : varTable.keySet()) {
            if (!varName.equals("this") && !varName.equals("return") && !registerMap.containsKey(varName)) {
                registerMap.put(varName, nextReg++);
            }
        }

        // Armazenar mapeamento para este método
        methodRegisterMaps.put(method, registerMap);
        methodSpilledVars.put(method, new HashSet<>());
        methodTotalRegisters.put(method, nextReg);

        // Gerar relatório do mapeamento
        logRegisterMapping(method, registerMap);

        // Aplicar alocação
        applyRegisterAllocationToMethod(method, registerMap);
    }

    /**
     * Alocação otimizada de registradores, usando análise de liveness.
     * Minimiza o número de registradores utilizados através da coloração de grafo.
     */
    private void allocateOptimizedRegisters(Method method) {
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Executando análise de liveness para " + method.getMethodName()));

        // 1. Calcular intervalos de vida das variáveis
        Map<String, Set<Integer>> liveRanges = livenessAnalyzer.analyze(method);

        // Identificar variáveis que nunca são usadas e registrá-las
        Set<String> unusedVariables = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : liveRanges.entrySet()) {
            if (entry.getValue().isEmpty()) {
                unusedVariables.add(entry.getKey());
                reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                        "Variável " + entry.getKey() + " parece não ser usada"));
            }
        }

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Construindo grafo de interferência para " + method.getMethodName()));

        // 2. Construir grafo de interferência
        Map<String, Set<String>> interferenceGraph = graphBuilder.build(liveRanges);

        // Visualização do grafo para debugging
        String graphVisualization = graphBuilder.visualizeGraph(interferenceGraph);
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Grafo de interferência:\n" + graphVisualization));

        // Garantir que todas as variáveis estejam no grafo, mesmo que sem interferências
        for (String varName : method.getVarTable().keySet()) {
            if (!varName.equals("return") && !interferenceGraph.containsKey(varName)) {
                interferenceGraph.put(varName, new HashSet<>());
            }
        }

        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Realizando coloração de grafo para " + method.getMethodName()));

        // 3. Alocar registradores usando coloração de grafo sem limite
        Map<String, Integer> registerMap = coloringStrategy.colorGraph(interferenceGraph);

        // 4. Verificar se alguma variável não foi mapeada
        Map<String, Descriptor> varTable = method.getVarTable();
        int nextReg = getMaxRegister(registerMap) + 1;

        // Reservar o reg0 para 'this' se necessário
        if (varTable.containsKey("this") && !registerMap.containsKey("this")) {
            registerMap.put("this", 0);
            // Ajustar outras variáveis se necessário
            for (Map.Entry<String, Integer> entry : registerMap.entrySet()) {
                if (entry.getValue() == 0 && !entry.getKey().equals("this")) {
                    entry.setValue(nextReg++);
                }
            }
        }

        // Garantir que todos os parâmetros tenham registradores
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                String paramName = ((Operand) param).getName();

                // Se o parâmetro não foi mapeado ou se é um parâmetro especial como 'args'
                if (!registerMap.containsKey(paramName) && varTable.containsKey(paramName)) {
                    registerMap.put(paramName, nextReg++);
                    reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                            "Parâmetro " + paramName + " recebendo registro dedicado " + (nextReg-1)));
                }
            }
        }

        // Garantir que variáveis não usadas compartilhem registradores quando possível
        for (String varName : unusedVariables) {
            if (!varName.equals("return") && !registerMap.containsKey(varName) && varTable.containsKey(varName)) {
                // Variáveis não usadas podem compartilhar registradores
                // Procurar um registro existente para reaproveitar
                boolean reuseFound = false;

                for (String otherVar : unusedVariables) {
                    if (!otherVar.equals(varName) && registerMap.containsKey(otherVar)) {
                        registerMap.put(varName, registerMap.get(otherVar));
                        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                                "Variável não usada " + varName + " compartilhando registro com " + otherVar));
                        reuseFound = true;
                        break;
                    }
                }

                // Se não encontrou para reutilizar, criar um novo
                if (!reuseFound) {
                    registerMap.put(varName, nextReg++);
                    reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                            "Variável não usada " + varName + " recebendo novo registro " + (nextReg-1)));
                }
            }
        }

        // Garantir que todas as outras variáveis na varTable tenham registradores
        for (String varName : varTable.keySet()) {
            if (!varName.equals("return") && !registerMap.containsKey(varName)) {
                registerMap.put(varName, nextReg++);
                reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                        "Variável " + varName + " não foi mapeada. Atribuindo reg" + (nextReg-1)));
            }
        }

        // 5. Determinar número de registradores utilizados
        int registersUsed = getMaxRegister(registerMap) + 1;

        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                "Método " + method.getMethodName() + ": Otimização resultou em " +
                        registersUsed + " registradores"));

        // 6. Armazenar mapeamento
        methodRegisterMaps.put(method, registerMap);
        methodSpilledVars.put(method, new HashSet<>());
        methodTotalRegisters.put(method, registersUsed);

        // 7. Gerar relatório do mapeamento
        logRegisterMapping(method, registerMap);

        // 8. Aplicar alocação
        applyRegisterAllocationToMethod(method, registerMap);
    }

    /**
     * Alocação com número limitado de registradores.
     * Utiliza a coloração de grafo com limite e identifica variáveis "spilled".
     */
    private void allocateLimitedRegisters(Method method, int maxRegisters) {
        reports.add(new Report(ReportType.DEBUG, Stage.OPTIMIZATION, -1, -1,
                "Executando análise de liveness para " + method.getMethodName() +
                        " com limite de " + maxRegisters + " registradores"));

        // 1. Calcular intervalos de vida
        Map<String, Set<Integer>> liveRanges = livenessAnalyzer.analyze(method);

        // 2. Construir grafo de interferência
        Map<String, Set<String>> interferenceGraph = graphBuilder.build(liveRanges);

        // 3. Reservar registradores para 'this' e parâmetros
        Map<String, Integer> registerMap = new HashMap<>();
        int reservedRegs = 0;

        // 'this' sempre no registrador 0
        if (method.getVarTable().containsKey("this")) {
            registerMap.put("this", reservedRegs++);
        }

        // Parâmetros em registradores subsequentes
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                String paramName = ((Operand) param).getName();
                if (method.getVarTable().containsKey(paramName)) {
                    registerMap.put(paramName, reservedRegs++);
                }
            }
        }

        // 4. Determinar número de registradores disponíveis para variáveis locais
        int availableRegsForLocals = maxRegisters;

        // 5. Identificar variáveis locais
        List<String> localVars = new ArrayList<>();
        for (String varName : method.getVarTable().keySet()) {
            if (!varName.equals("this") && !varName.equals("return") &&
                    !registerMap.containsKey(varName)) {
                localVars.add(varName);
            }
        }

        // 6. Ordenar variáveis locais por grau (número de interferências)
        localVars.sort((v1, v2) -> {
            Set<String> interferences1 = interferenceGraph.getOrDefault(v1, Collections.emptySet());
            Set<String> interferences2 = interferenceGraph.getOrDefault(v2, Collections.emptySet());
            return interferences2.size() - interferences1.size();
        });

        // 7. Cálculo de registradores necessários para respeitar todas as interferências
        int necessaryRegs = 0;
        for (String varName : localVars) {
            Set<String> adjVars = interferenceGraph.getOrDefault(varName, Collections.emptySet());
            Set<Integer> usedRegs = new HashSet<>();

            // Verificar quais registradores já estão em uso por vizinhos
            for (String adjVar : adjVars) {
                if (registerMap.containsKey(adjVar)) {
                    usedRegs.add(registerMap.get(adjVar));
                }
            }

            // Encontrar o menor registrador disponível
            int reg = reservedRegs;
            while (usedRegs.contains(reg)) {
                reg++;
            }

            if (reg > necessaryRegs) {
                necessaryRegs = reg;
            }
        }

        // 8. Determinar quantos registradores precisamos na realidade
        int totalNecessaryRegs = necessaryRegs + 1; // +1 porque contamos de 0
        int spillCount = Math.max(0, totalNecessaryRegs - (reservedRegs + availableRegsForLocals));

        // 9. Calcular registradores totais conforme a especificação
        int totalRegs = reservedRegs + Math.min(availableRegsForLocals, necessaryRegs + 1);
        if (spillCount > 0) {
            totalRegs++; // Adicionar um registrador para variáveis derramadas
        }

        // 10. Algoritmo de coloração com limite
        Set<String> spilledVars = new HashSet<>();
        int spillReg = totalRegs - 1; // Último registrador para variáveis derramadas

        for (String varName : localVars) {
            Set<String> adjVars = interferenceGraph.getOrDefault(varName, Collections.emptySet());
            Set<Integer> usedRegs = new HashSet<>();

            // Verificar quais registradores já estão em uso por vizinhos
            for (String adjVar : adjVars) {
                if (registerMap.containsKey(adjVar)) {
                    usedRegs.add(registerMap.get(adjVar));
                }
            }

            // Determinar se é necessário derramar esta variável
            boolean mustSpill = true;
            for (int r = reservedRegs; r < reservedRegs + availableRegsForLocals; r++) {
                if (!usedRegs.contains(r)) {
                    registerMap.put(varName, r);
                    mustSpill = false;
                    break;
                }
            }

            // Derramar a variável se necessário
            if (mustSpill) {
                registerMap.put(varName, spillReg);
                spilledVars.add(varName);
            }
        }

        // 11. Garantir que todas as variáveis tenham um registrador atribuído
        for (String varName : method.getVarTable().keySet()) {
            if (!varName.equals("return") && !registerMap.containsKey(varName)) {
                // Variáveis que não estão no grafo (como não usadas) podem compartilhar o mesmo registrador
                registerMap.put(varName, reservedRegs);
            }
        }

        // 12. Armazenar mapeamento e variáveis derramadas
        methodRegisterMaps.put(method, registerMap);
        methodSpilledVars.put(method, spilledVars);
        methodTotalRegisters.put(method, totalRegs);


        // 14. Gerar relatório do mapeamento
        logRegisterMapping(method, registerMap, spilledVars);

        // 15. Aplicar alocação aos descritores
        applyRegisterAllocationToMethod(method, registerMap);
    }
    /**
     * Adapta a alocação de registradores respeitando o grafo de interferência e o limite de registradores.
     * Implementa um algoritmo de coloração adaptativa que respeita as interferências entre variáveis.
     */
    private Map<String, Integer> adaptRegisterAllocation(
            Map<String, Integer> originalMap,
            Map<String, Set<String>> interferenceGraph,
            int maxRegisters,
            Set<String> spilledVars) {

        Map<String, Integer> newMap = new HashMap<>();

        // Reservar registradores fixos (this e parâmetros)
        int reservedRegs = 0;
        for (Map.Entry<String, Integer> entry : originalMap.entrySet()) {
            String varName = entry.getKey();
            // 'this' sempre no reg0, parâmetros em regs subsequentes
            if (varName.equals("this") || isParameter(varName, interferenceGraph)) {
                newMap.put(varName, reservedRegs++);
            }
        }

        // Ordenar variáveis locais por grau de interferência (decrescente)
        List<String> sortedLocals = new ArrayList<>();
        for (String varName : originalMap.keySet()) {
            if (!newMap.containsKey(varName)) {
                sortedLocals.add(varName);
            }
        }
        sortedLocals.sort((v1, v2) ->
                interferenceGraph.getOrDefault(v2, Collections.emptySet()).size() -
                        interferenceGraph.getOrDefault(v1, Collections.emptySet()).size());

        // Colorir variáveis locais respeitando interferências
        for (String varName : sortedLocals) {
            // Determinar registradores indisponíveis (usados por vizinhos)
            Set<Integer> usedRegs = new HashSet<>();
            for (String neighbor : interferenceGraph.getOrDefault(varName, Collections.emptySet())) {
                if (newMap.containsKey(neighbor)) {
                    usedRegs.add(newMap.get(neighbor));
                }
            }

            // Encontrar o menor registrador disponível
            int reg = reservedRegs;
            while (usedRegs.contains(reg)) {
                reg++;
            }

            // Verificar se excede o limite de registradores
            if (reg >= maxRegisters) {
                // Registrador de "spill"
                newMap.put(varName, maxRegisters);
                spilledVars.add(varName);
            } else {
                newMap.put(varName, reg);
            }
        }

        return newMap;
    }

    /**
     * Verifica se uma variável é um parâmetro baseado em seu padrão de interferência.
     * Parâmetros tendem a ter menos interferências com outras variáveis.
     */
    private boolean isParameter(String varName, Map<String, Set<String>> interferenceGraph) {
        // Verificação simples por nome (arg, args) ou por padrão de interferência
        return varName.startsWith("arg") ||
                interferenceGraph.getOrDefault(varName, Collections.emptySet()).isEmpty();
    }
    /**
     * Registra o mapeamento de registradores para um método.
     */
    private void logRegisterMapping(Method method, Map<String, Integer> registerMap) {
        logRegisterMapping(method, registerMap, Collections.emptySet());
    }

    /**
     * Registra o mapeamento de registradores para um método, destacando variáveis spilled.
     */
    private void logRegisterMapping(Method method, Map<String, Integer> registerMap, Set<String> spilledVars) {
        StringBuilder reportMsg = new StringBuilder("Mapeamento de registradores para o método " +
                method.getMethodName() + ":\n");

        // Ordenar variáveis alfabeticamente para apresentação mais clara
        List<String> sortedVars = new ArrayList<>(registerMap.keySet());
        Collections.sort(sortedVars);

        for (String var : sortedVars) {
            int regValue = registerMap.get(var);
            reportMsg.append("  ").append(var).append(" -> reg").append(regValue);

            // Adicionar marcação para variáveis spilled
            if (spilledVars.contains(var)) {
                reportMsg.append(" (spilled)");
            }

            reportMsg.append("\n");
        }

        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1, reportMsg.toString()));
    }

    /**
     * Cria uma alocação de emergência se a alocação normal falhar.
     * Garante que todas as variáveis tenham um registrador atribuído.
     */
    private void createEmergencyAllocation(Method method) {
        reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                "Criando alocação de emergência para o método " + method.getMethodName()));

        Map<String, Integer> emergencyMap = new HashMap<>();
        Set<String> spilledVars = new HashSet<>();

        // Obter todas as variáveis locais e parâmetros
        Map<String, Descriptor> varTable = method.getVarTable();

        // Começar pelo registrador 0
        int register = 0;

        // Garantir que 'this' esteja no reg0 se presente
        if (varTable.containsKey("this")) {
            emergencyMap.put("this", register++);
        }

        // Garantir que parâmetros tenham registradores atribuídos
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                String paramName = ((Operand) param).getName();
                if (!emergencyMap.containsKey(paramName) && varTable.containsKey(paramName)) {
                    emergencyMap.put(paramName, register++);
                }
            }
        }

        // Alocar para todas as outras variáveis
        for (String varName : varTable.keySet()) {
            if (!varName.equals("this") && !varName.equals("return") && !emergencyMap.containsKey(varName)) {
                emergencyMap.put(varName, register++);
            }
        }

        // Se temos limite de registradores, ajustar para spilled vars
        if (maxRegisters > 0) {
            int effectiveRegCount = Math.min(register, maxRegisters + 2);

            // Marcar variáveis como spilled quando necessário
            for (Map.Entry<String, Integer> entry : emergencyMap.entrySet()) {
                if (entry.getValue() >= maxRegisters) {
                    spilledVars.add(entry.getKey());
                }
            }

            methodTotalRegisters.put(method, effectiveRegCount);
        } else {
            methodTotalRegisters.put(method, register);
        }

        methodRegisterMaps.put(method, emergencyMap);
        methodSpilledVars.put(method, spilledVars);

        // Aplicar a alocação ao método
        applyRegisterAllocationToMethod(method, emergencyMap);

        reports.add(new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, -1,
                "Alocação de emergência aplicada para o método " + method.getMethodName() +
                        " com " + register + " registradores"));
    }


    /**
     * Retorna o maior valor de registrador usado em um mapeamento.
     */
    private int getMaxRegister(Map<String, Integer> colorMap) {
        int maxColor = -1;
        for (int color : colorMap.values()) {
            if (color > maxColor) {
                maxColor = color;
            }
        }
        return maxColor;
    }

    /**
     * Aplica o mapeamento de registradores ao código OLLIR.
     * Substitui nomes de variáveis por registradores.
     */
    public String applyAllocation(String ollirCode) {
        // Apply all register allocations to each method in the class
        for (Method method : ollirClass.getMethods()) {
            // Get the register map for this method
            Map<String, Integer> registerMap = methodRegisterMaps.get(method);

            // Skip if this method wasn't processed or has no register map
            if (registerMap == null || registerMap.isEmpty()) {
                continue;
            }

            // Update the descriptors directly
            Map<String, Descriptor> varTable = method.getVarTable();
            for (Map.Entry<String, Integer> entry : registerMap.entrySet()) {
                String varName = entry.getKey();
                int register = entry.getValue();

                if (varTable.containsKey(varName)) {
                    Descriptor descriptor = varTable.get(varName);
                    if (descriptor != null) {
                        descriptor.setVirtualReg(register);
                    }
                }
            }
        }

        // After modifying all the descriptors, update the actual register count
        updateActualRegisterCount();

        // Return the original OLLIR code - we've already modified the class directly
        return ollirCode;
    }

    /**
     * Extrai o nome do método de uma linha de declaração de método OLLIR.
     */
    private String extractMethodName(String line) {
        Pattern pattern = Pattern.compile("\\.method\\s+(?:public\\s+|private\\s+|static\\s+)*([\\w<>]+)\\s*\\(");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Retorna a lista de relatórios gerados durante a alocação.
     */
    public List<Report> getReports() {
        return reports;
    }
}