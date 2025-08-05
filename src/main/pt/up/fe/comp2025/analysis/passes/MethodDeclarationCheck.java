package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class MethodDeclarationCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (!method.hasAttribute("name")) {
            return null;
        }

        String methodName = method.get("name");
        System.out.println("DEBUG [MethodDeclarationCheck]: Checking method declaration: " + methodName);

        JmmNode returnTypeNode = null;
        for (JmmNode child : method.getChildren()) {
            if ("FUNCTION_TYPE".equals(child.getKind())) {
                returnTypeNode = child;
                break;
            }
        }

        if (returnTypeNode != null) {
            Type returnType = TypeUtils.convertType(returnTypeNode);
            if (returnType.isArray()) {
                int dimensions = countArrayDimensions(returnTypeNode);

                if (dimensions > 0 && table instanceof JmmSymbolTable) {
                    ((JmmSymbolTable)table).registerMethodReturnDimensions(methodName, dimensions);
                    System.out.println("DEBUG [MethodDeclarationCheck]: Method " + methodName +
                            " returns array with " + dimensions + " dimensions");
                }
            }
        }
        JmmNode paramList = null;
        for (JmmNode child : method.getChildren()) {
            if (child.getKind().equals("ParamList")) {
                paramList = child;
                break;
            }
        }

        if (paramList == null || paramList.getChildren().isEmpty()) {
            return null;
        }

        List<JmmNode> paramNodes = paramList.getChildren();

        int varargCount = 0;
        int lastVarargIndex = -1;

        for (int i = 0; i < paramNodes.size(); i++) {
            JmmNode param = paramNodes.get(i);

            boolean isVararg = false;

            if (param.hasAttribute("ellipsis")) {
                isVararg = true;
            } else if (param.getKind().equals("PARAM")) {
                isVararg = param.hasAttribute("ellipsis") && "...".equals(param.get("ellipsis"));
            }

            if (isVararg) {
                varargCount++;
                lastVarargIndex = i;
                System.out.println("DEBUG [MethodDeclarationCheck]: Found vararg parameter at index " + i);
            }
        }

        if (varargCount > 1) {
            String message = String.format(
                    "Method '%s': Only one parameter can be a vararg",
                    methodName
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [MethodDeclarationCheck]: Reported multiple varargs");
        }

        if (varargCount > 0 && lastVarargIndex != paramNodes.size() - 1) {
            String message = String.format(
                    "Method '%s': Vararg parameter must be the last parameter",
                    methodName
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [MethodDeclarationCheck]: Reported vararg not last parameter");
        }

        return null;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }

    private int countArrayDimensions(JmmNode typeNode) {
        if (typeNode == null) return 0;

        int dimensions = 0;

        for (JmmNode child : typeNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                dimensions += countArraySuffixDepth(child);
                break;
            }
        }

        if (dimensions == 0) {
            Type type = TypeUtils.convertType(typeNode);
            if (type.isArray()) {
                dimensions = 1;
            }
        }

        return dimensions;
    }

    private int countArraySuffixDepth(JmmNode node) {
        if (node == null) return 0;

        int count = 1;

        for (JmmNode child : node.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                count += countArraySuffixDepth(child);
                break;
            }
        }

        return count;
    }

    private int determineDimensionsInArrayInitializer(JmmNode node) {

        if (node == null || (!node.getKind().equals("ArrayInitializer") &&
                !node.getKind().equals("ArrayInitializerExpr"))) {
            return 0;
        }

        boolean hasNestedInitializer = false;

        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("ArrayInitializer") ||
                    child.getKind().equals("ArrayInitializerExpr")) {
                hasNestedInitializer = true;
                break;
            } else if (child.getKind().equals("ArrayElement")) {
                for (JmmNode grandchild : child.getChildren()) {
                    if (grandchild.getKind().equals("ArrayInitializer") ||
                            grandchild.getKind().equals("ArrayInitializerExpr")) {
                        hasNestedInitializer = true;
                        break;
                    }
                }
                if (hasNestedInitializer) break;
            }
        }

        if (hasNestedInitializer) {
            int maxNestedDimensions = 0;

            for (JmmNode child : node.getChildren()) {
                JmmNode initializerNode = null;

                if (child.getKind().equals("ArrayInitializer") ||
                        child.getKind().equals("ArrayInitializerExpr")) {
                    initializerNode = child;
                } else if (child.getKind().equals("ArrayElement")) {
                    for (JmmNode grandchild : child.getChildren()) {
                        if (grandchild.getKind().equals("ArrayInitializer") ||
                                grandchild.getKind().equals("ArrayInitializerExpr")) {
                            initializerNode = grandchild;
                            break;
                        }
                    }
                }

                if (initializerNode != null) {
                    int nestedDimensions = determineDimensionsInArrayInitializer(initializerNode);
                    maxNestedDimensions = Math.max(maxNestedDimensions, nestedDimensions);
                }
            }

            return maxNestedDimensions + 1;
        } else {
            return 1;
        }
    }
}