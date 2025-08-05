package pt.up.fe.comp2025.optimization.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Gerenciador de labels para código OLLIR.
 * Implementação modificada para usar contador sequencial global.
 */
public class LabelManager {
    private static LabelManager instance;

    // NOVO: Contador sequencial global para todos os labels
    private int globalSequentialCounter = 0;

    // Mantemos esse mapa para compatibilidade, embora não o usemos para sequenciamento
    private Map<String, Map<String, Integer>> methodStructureCounters = new HashMap<>();
    private String currentMethod = null;

    // Constantes para tipos de estruturas (mantidas por compatibilidade)
    public static final String IF_STRUCTURE = "if";
    public static final String WHILE_STRUCTURE = "while";
    public static final String AND_STRUCTURE = "and";
    public static final String OR_STRUCTURE = "or";
    public static final String GENERIC_STRUCTURE = "generic";
    public static final String SWITCH_STRUCTURE = "switch";

    private LabelManager() {}

    public static synchronized LabelManager getInstance() {
        if (instance == null) {
            instance = new LabelManager();
        }
        return instance;
    }

    public void setCurrentMethod(String methodName) {
        this.currentMethod = methodName;
        if (methodName != null) {
            methodStructureCounters.putIfAbsent(methodName, new HashMap<>());
        }
    }

    /**
     * MODIFICAÇÃO CRÍTICA: Agora ignora o tipo de estrutura e retorna
     * o próximo valor da sequência global, garantindo labels únicos
     * e corretamente numerados sequencialmente.
     */
    public int nextLabel(String structureType) {
        // Ignora o tipo de estrutura e retorna o próximo na sequência global
        return globalSequentialCounter++;
    }

    /**
     * Método legado para compatibilidade.
     */
    public int nextLabel() {
        return nextLabel(GENERIC_STRUCTURE);
    }

    public void resetMethod() {
        this.currentMethod = null;
        // NÃO resetamos o contador global, mantendo a sequência
    }

    /**
     * NOVO: Método explícito para reset do contador global.
     * Útil para testes e entre execuções.
     */
    public void resetGlobalCounter() {
        this.globalSequentialCounter = 0;
    }

    /**
     * Obtém conjunto de labels em ordem reversa para estrutura switch-like.
     * Modificado para usar a sequência global.
     */
    public String[] getSwitchLabels(int count) {
        // Criar array de labels
        String[] labels = new String[count];

        // Obter count valores sequenciais
        int baseValue = globalSequentialCounter;
        globalSequentialCounter += count;

        for (int i = 0; i < count; i++) {
            labels[i] = String.valueOf(baseValue + i);
        }

        return labels;
    }
}