package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.ArrayType;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

public class ObjectAssignmentCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("ASSIGN_STMT", this::visitAssignStmt);
        addVisit("AssignStatement", this::visitAssignExpression);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [ObjectAssignmentCheck]: Entering method: " + currentMethod);
        }

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        System.out.println("DEBUG [ObjectAssignmentCheck]: Visiting assignment statement at line " + assignStmt.getLine());

        for (JmmNode child : assignStmt.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitAssignExpression(JmmNode assignExpr, SymbolTable table) {
        System.out.println("DEBUG [ObjectAssignmentCheck]: Visiting assignment expression at line " + assignExpr.getLine());

        if (assignExpr.getChildren().size() < 2) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Assignment doesn't have both sides");
            return null;
        }

        JmmNode lhs = assignExpr.getChildren().get(0);
        JmmNode rhs = assignExpr.getChildren().get(1);

        if (!lhs.getKind().equals("IdentifierLValue")) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: LHS is not an identifier");
            return null;
        }

        if (!lhs.hasAttribute("name")) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: LHS identifier has no name");
            return null;
        }

        String varName = lhs.get("name");
        System.out.println("DEBUG [ObjectAssignmentCheck]: Assignment to variable: " + varName);

        if (containsImportedMethodCall(rhs, table)) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: RHS contains imported method call, assuming compatible type");
            return null;
        }

        if ("MethodCallExpr".equals(rhs.getKind()) && !rhs.getChildren().isEmpty()) {
            JmmNode objectExpr = rhs.getChildren().get(0);
            if ("VAR_REF_EXPR".equals(objectExpr.getKind()) && objectExpr.hasAttribute("name")) {
                String objectName = objectExpr.get("name");
                if (table.getImports().contains(objectName)) {
                    System.out.println("DEBUG [ObjectAssignmentCheck]: Skipping check for imported class method");
                    return null;
                }

                TypeUtils typeUtils = new TypeUtils(table);
                typeUtils.setCurrentMethod(currentMethod);
                Type objectType = typeUtils.getExprType(objectExpr);
                if (table.getImports().contains(objectType.getName())) {
                    System.out.println("DEBUG [ObjectAssignmentCheck]: Skipping check for method on variable of imported type");
                    return null;
                }
            }
        }

        Type lhsType = findVariableType(varName, table);
        if (lhsType == null) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Could not find type for variable: " + varName);
            return null;
        }

        System.out.println("DEBUG [ObjectAssignmentCheck]: LHS type: " + lhsType.getName() +
                (lhsType.isArray() ? "[]" : ""));

        TypeUtils typeUtils = new TypeUtils(table);
        typeUtils.setCurrentMethod(currentMethod);
        Type rhsType;

        try {
            rhsType = typeUtils.getExprType(rhs);
            System.out.println("DEBUG [ObjectAssignmentCheck]: RHS type: " + rhsType.getName() +
                    (rhsType.isArray() ? "[]" : ""));
        } catch (Exception e) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Error getting expression type: " + e.getMessage());
            return null;
        }

        if ("boolean".equals(lhsType.getName()) && isIntegerType(rhs)) {
            String message = "Cannot assign an integer value to a boolean variable.";
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    assignExpr.getLine(),
                    assignExpr.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [ObjectAssignmentCheck]: Added error report for boolean = int");
            return null;
        }

        if (!areTypesCompatible(lhsType, rhsType, (JmmSymbolTable)table)) {
            String message = String.format(
                    "Cannot assign value of type '%s' to variable of type '%s'",
                    rhsType.getName(), lhsType.getName()
            );
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    assignExpr.getLine(),
                    assignExpr.getColumn(),
                    message,
                    null
            );
            addReport(report);
            System.out.println("DEBUG [ObjectAssignmentCheck]: Added error report for incompatible types");
        }

        return null;
    }

    private boolean isIntegerType(JmmNode node) {
        String kind = node.getKind();
        return "IntLiteral".equals(kind) || "INTEGER_LITERAL".equals(kind);
    }

    private Type findVariableType(String varName, SymbolTable table) {
        for (Symbol param : table.getParameters(currentMethod)) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        for (Symbol local : table.getLocalVariables(currentMethod)) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        return null;
    }

    private boolean areTypesCompatible(Type lhsType, Type rhsType, JmmSymbolTable table) {
        System.out.println("DEBUG [ObjectAssignmentCheck]: Verificando compatibilidade entre tipos: " +
                formatTypeWithDimensions(lhsType) + " e " + formatTypeWithDimensions(rhsType));

        if (lhsType.getName().equals(rhsType.getName()) && lhsType.isArray() == rhsType.isArray()) {
            if (lhsType.isArray()) {
                int lhsDimensions = extractArrayDimensions(lhsType);
                int rhsDimensions = extractArrayDimensions(rhsType);

                System.out.println("DEBUG [ObjectAssignmentCheck]: Dimensões - LHS: " +
                        lhsDimensions + ", RHS: " + rhsDimensions);

                if (isArrayInitializer(rhsType)) {
                    int initializerDimensions = extractDimensionsFromInitializer(rhsType);
                    System.out.println("DEBUG [ObjectAssignmentCheck]: Dimensões ajustadas de inicializador: " +
                            initializerDimensions);

                    return lhsDimensions == initializerDimensions;
                }

                return lhsDimensions == rhsDimensions;
            }
            return true;
        }

        if (lhsType.isArray() != rhsType.isArray()) {
            return false;
        }

        if (isPrimitiveType(lhsType.getName()) || isPrimitiveType(rhsType.getName())) {
            return false;
        }

        boolean lhsImported = isImportedType(lhsType.getName(), table);
        boolean rhsImported = isImportedType(rhsType.getName(), table);

        System.out.println("DEBUG [ObjectAssignmentCheck]: LHS imported: " + lhsImported + ", RHS imported: " + rhsImported);
        System.out.println("DEBUG [ObjectAssignmentCheck]: All imports: " + table.getImports());

        if (rhsType.getName().equals(table.getClassName()) &&
                lhsType.getName().equals(table.getSuper())) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Assignment from class to its superclass - compatible");
            return true;
        }

        if (lhsType.getName().equals(table.getClassName()) &&
                rhsType.getName().equals(table.getSuper())) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Assignment from superclass to subclass - incompatible");
            return false;
        }

        if (lhsImported && rhsImported) {
            System.out.println("DEBUG [ObjectAssignmentCheck]: Both types are imported - assuming compatible");
            return true;
        }

        System.out.println("DEBUG [ObjectAssignmentCheck]: No compatibility rule matched - incompatible");
        return false;
    }

    public static int extractArrayDimensions(Type type) {

        if (!type.isArray()) return 0;

        if (type instanceof ArrayType) {
            return ((ArrayType) type).getDimensions();
        }

        String typeName = type.getName();
        int countBrackets = 0;
        int index = typeName.indexOf("[]");
        while (index != -1) {
            countBrackets++;
            index = typeName.indexOf("[]", index + 2);
        }

        return Math.max(countBrackets, 1);
    }

    private boolean isArrayInitializer(Type type) {
        if (type == null) return false;

        if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            JmmNode sourceNode = arrayType.getSourceNode();
            if (sourceNode == null) return false;

            String kind = sourceNode.getKind();
            return "ArrayInitializerExpr".equals(kind) || "ArrayInitializer".equals(kind);
        }

        return false;
    }

    private int extractDimensionsFromInitializer(Type type) {

        if (!(type instanceof ArrayType)) {
            return 1;
        }

        ArrayType arrayType = (ArrayType) type;
        JmmNode node = arrayType.getSourceNode();
        if (node == null) return 1;

        int dimensions = 1;

        if ("ArrayInitializerExpr".equals(node.getKind())) {
            for (JmmNode child : node.getChildren()) {
                if ("ArrayInitializer".equals(child.getKind())) {
                    for (JmmNode element : child.getChildren()) {
                        if ("ArrayElement".equals(element.getKind())) {
                            for (JmmNode content : element.getChildren()) {
                                if ("ArrayInitializerExpr".equals(content.getKind()) ||
                                        "ArrayInitializer".equals(content.getKind())) {
                                    return 2;
                                }
                            }
                        }
                    }
                }
            }
        }

        return dimensions;
    }

    private String formatTypeWithDimensions(Type type) {
        StringBuilder sb = new StringBuilder(type.getName());

        if (type.isArray()) {
            int dimensions = extractArrayDimensions(type);
            for (int i = 0; i < dimensions; i++) {
                sb.append("[]");
            }
        }

        return sb.toString();
    }

    private int getDimensions(Type type) {
        if (!type.isArray()) return 0;

        if (type instanceof ArrayType) {
            return ((ArrayType)type).getDimensions();
        }

        String typeName = type.toString();
        int count = 1;

        for (int i = 0; i < typeName.length() - 1; i++) {
            if (typeName.charAt(i) == '[' && typeName.charAt(i+1) == ']') {
                count++;
            }
        }

        return count;
    }

    private boolean isImportedType(String typeName, JmmSymbolTable table) {

        if (table.getImports().contains(typeName)) {
            return true;
        }

        for (String importName : table.getImports()) {
            if (importName.endsWith("." + typeName)) {
                return true;
            }

            if (importName.equals(typeName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPrimitiveType(String typeName) {
        return "int".equals(typeName) || "boolean".equals(typeName) || "void".equals(typeName);
    }

    private boolean containsImportedMethodCall(JmmNode node, SymbolTable table) {
        if ("MethodCallExpr".equals(node.getKind()) && !node.getChildren().isEmpty()) {
            JmmNode objectExpr = node.getChildren().get(0);
            if ("VAR_REF_EXPR".equals(objectExpr.getKind()) && objectExpr.hasAttribute("name")) {
                String objectName = objectExpr.get("name");
                if (isImportedTypeOrClass(objectName, table)) {
                    return true;
                }
            }
        }

        for (JmmNode child : node.getChildren()) {
            if (containsImportedMethodCall(child, table)) {
                return true;
            }
        }

        return false;
    }

    private boolean isImportedTypeOrClass(String name, SymbolTable table) {

        if (table.getImports().contains(name)) {
            return true;
        }

        for (String importName : table.getImports()) {
            if (importName.endsWith("." + name)) {
                return true;
            }
            if (importName.equals(name)) {
                return true;
            }
        }

        return false;
    }

    private Void visitDefault(JmmNode node, SymbolTable table) {
        for (JmmNode child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }
}