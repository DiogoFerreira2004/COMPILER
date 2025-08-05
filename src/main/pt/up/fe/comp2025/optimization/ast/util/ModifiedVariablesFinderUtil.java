package pt.up.fe.comp2025.optimization.ast.util;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import java.util.HashSet;
import java.util.Set;

/**
 * Utilitário para encontrar variáveis modificadas em uma subárvore da AST.
 * Implementa um visitante que percorre a árvore e identifica todas as
 * atribuições que modificam variáveis.
 */
public class ModifiedVariablesFinderUtil {

    /**
     * Encontra todas as variáveis modificadas em uma subárvore.
     *
     * @param node Nó raiz da subárvore a ser analisada
     * @return Conjunto de nomes de variáveis que são modificadas na subárvore
     */
    public static Set<String> findModifiedVariables(JmmNode node) {
        Set<String> modifiedVars = new HashSet<>();
        ModifiedVariablesFinder finder = new ModifiedVariablesFinder();
        return finder.visit(node, modifiedVars);
    }

    /**
     * Implementação do visitante que percorre a AST para identificar
     * variáveis modificadas em atribuições.
     */
    private static class ModifiedVariablesFinder extends PreorderJmmVisitor<Set<String>, Set<String>> {

        @Override
        protected void buildVisitor() {
            // Registrar visitantes para nós de atribuição
            addVisit("ASSIGN_STMT", this::visitAssignStmt);
            addVisit("AssignStatement", this::visitAssignStmt);

            // Visita padrão para outros tipos de nós
            setDefaultVisit(this::defaultVisit);
        }

        /**
         * Processa nós de atribuição para identificar variáveis modificadas.
         */
        private Set<String> visitAssignStmt(JmmNode node, Set<String> modifiedVars) {
            // Verificar se o nó tem filhos (lado esquerdo da atribuição)
            if (node.getNumChildren() > 0) {
                JmmNode leftSide = node.getChild(0);

                // Se o nó tem um atributo "name", é uma variável sendo modificada
                if (leftSide.hasAttribute("name")) {
                    modifiedVars.add(leftSide.get("name"));
                }
                // Para acesso a arrays, capturar o nome da variável base
                else if (leftSide.getKind().contains("ArrayAccess") && leftSide.hasAttribute("name")) {
                    modifiedVars.add(leftSide.get("name"));
                }
            }

            // Continuar a visita nos filhos para expressões aninhadas
            for (JmmNode child : node.getChildren()) {
                visit(child, modifiedVars);
            }

            return modifiedVars;
        }

        /**
         * Método padrão para visitar nós não específicos.
         */
        private Set<String> defaultVisit(JmmNode node, Set<String> modifiedVars) {
            // Percorrer recursivamente todos os filhos
            for (JmmNode child : node.getChildren()) {
                visit(child, modifiedVars);
            }

            return modifiedVars;
        }
    }
}