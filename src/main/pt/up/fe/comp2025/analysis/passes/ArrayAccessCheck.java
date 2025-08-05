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

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;

public class ArrayAccessCheck extends AnalysisVisitor {

    private String currentMethod;
    private JmmSymbolTable table;
    private TypeUtils typeUtils;

    private final Map<JmmNode, Type> typeCache = new HashMap<>();
    private final Map<JmmNode, Integer> dimensionCache = new HashMap<>();

    @Override
    public void buildVisitor() {
        addVisit("METHOD_DECL", this::visitMethodDecl);
        addVisit("ArrayAccessExpr", this::visitArrayAccessExpr);
        addVisit("ArrayLengthExpr", this::visitArrayLengthExpr);
        addVisit("ArrayInitializer", this::visitArrayInitializer);
        addVisit("ArrayInitializerExpr", this::visitArrayInitializerExpr);
        addVisit("VAR_DECL", this::visitVarDecl);
        addVisit("NewIntArrayExpr", this::visitNewIntArrayExpr);

        setDefaultVisit(this::visitDefault);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable symbolTable) {
        if (method.hasAttribute("name")) {
            currentMethod = method.get("name");
            System.out.println("DEBUG [ArrayAccessCheck]: Entering method: " + currentMethod);
        }

        if (symbolTable instanceof JmmSymbolTable) {
            this.table = (JmmSymbolTable) symbolTable;
        }
        this.typeUtils = new TypeUtils(symbolTable);
        this.typeUtils.setCurrentMethod(currentMethod);

        typeCache.clear();
        dimensionCache.clear();

        for (JmmNode child : method.getChildren()) {
            visit(child, symbolTable);
        }
        return null;
    }

    private Void visitVarDecl(JmmNode node, SymbolTable symbolTable) {
        if (node.hasAttribute("name")) {
            String varName = node.get("name");
            JmmNode typeNode = findTypeNode(node);
            if (typeNode != null) {
                Type type = TypeUtils.convertType(typeNode);
                if (type.isArray()) {
                    int dimensions = countArraySuffixes(typeNode);
                    if (table != null) {
                        table.registerArrayDimensions(currentMethod, varName, dimensions);
                    }
                    System.out.println("DEBUG [ArrayAccessCheck]: Variable " + varName +
                            " is array with " + dimensions + " dimensions based on syntax");
                }
            }
        }

        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }
        return null;
    }

    private JmmNode findTypeNode(JmmNode varDeclNode) {
        for (JmmNode child : varDeclNode.getChildren()) {
            if ("TYPE".equals(child.getKind())) {
                return child;
            }
        }
        return null;
    }

    private int countArraySuffixes(JmmNode typeNode) {
        int count = 0;
        for (JmmNode child : typeNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                count++;
            }
        }

        if (count == 0 && typeNode.hasAttribute("arrayDimensions")) {
            try {
                count = Integer.parseInt(typeNode.get("arrayDimensions"));
            } catch (NumberFormatException e) {
                count = 0;
            }
        }

        if (count == 0) {
            Type type = TypeUtils.convertType(typeNode);
            if (type.isArray()) {
                count = 1;
            }
        }
        return count;
    }

    private Void visitNewIntArrayExpr(JmmNode node, SymbolTable symbolTable) {
        System.out.println("DEBUG [ArrayAccessCheck]: Analyzing array creation at line " + node.getLine());

        int dimensions = 0;

        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("ArrayDimensions") || child.getKind().contains("Dimension")) {
                for (JmmNode bracketNode : child.getChildren()) {
                    if (bracketNode.getKind().equals("LBRACK")) {
                        dimensions++;
                    }
                }
            }
        }

        if (node.getKind().equals("NewMultiDimArrayExpr")) {
            dimensions = (node.getChildren().size() - 1) / 2;
        }

        System.out.println("DEBUG [ArrayAccessCheck]: Creating array with " + dimensions + " dimensions");


        String baseType = "int";
        String arrayTypeName = baseType;
        for (int i = 0; i < dimensions; i++) {
            arrayTypeName += "[]";
        }


        Type arrayType = new ArrayType(baseType, dimensions);


        node.put("type", arrayTypeName);
        node.put("isArray", "true");
        node.put("arrayDimensions", String.valueOf(dimensions));

        for (JmmNode child : node.getChildren()) {
            if (!child.getKind().equals("INT_TYPE") &&
                    !child.getKind().equals("LBRACK") &&
                    !child.getKind().equals("RBRACK")) {
                visit(child, symbolTable);
            }
        }
        return null;
    }

    private int countNewArrayDimensions(JmmNode node) {
        int dimensions = 1;
        boolean hasMultipleDimensions = false;
        for (JmmNode child : node.getChildren()) {
            if ("ArrayDimensions".equals(child.getKind())) {
                hasMultipleDimensions = true;
                for (JmmNode dimChild : child.getChildren()) {
                    if ("LBRACK".equals(dimChild.getKind())) {
                        dimensions++;
                    }
                }
            } else if ("EmptyDimension".equals(child.getKind()) ||
                    (child.getKind().contains("Dimension") && child.getChildren().isEmpty())) {
                hasMultipleDimensions = true;
                dimensions++;
            }
        }
        if (node.hasAttribute("dimensions")) {
            try {
                int attrDimensions = Integer.parseInt(node.get("dimensions"));
                dimensions = Math.max(dimensions, attrDimensions);
            } catch (NumberFormatException e) {
                // Mantém o valor calculado
            }
        }
        System.out.println("DEBUG [ArrayAccessCheck]: Array creation has " + dimensions +
                " dimensions, hasMultipleDimensions=" + hasMultipleDimensions);
        return dimensions;
    }

    private JmmNode findAssignmentTarget(JmmNode expr) {
        JmmNode current = expr;
        JmmNode parent = current.getParent();
        while (parent != null) {
            if ("ASSIGN_STMT".equals(parent.getKind()) ||
                    "AssignStatement".equals(parent.getKind())) {
                if (parent.getChildren().size() > 0) {
                    return parent.getChildren().get(0);
                }
                return null;
            }
            current = parent;
            parent = current.getParent();
        }
        return null;
    }

    private Void visitArrayAccessExpr(JmmNode node, SymbolTable symbolTable) {
        System.out.println("DEBUG [ArrayAccessCheck]: Analyzing array access at line " + node.getLine());

        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }

        if (node.getChildren().size() < 2) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Malformed array access - missing array or index",
                    null
            ));
            return null;
        }

        JmmNode arrayExpr = node.getChildren().get(0);
        JmmNode indexExpr = node.getChildren().get(1);

        System.out.println("DEBUG [ArrayAccessCheck]: Array expression kind: " + arrayExpr.getKind());
        System.out.println("DEBUG [ArrayAccessCheck]: Index expression kind: " + indexExpr.getKind());


        Type indexType = this.typeUtils.getExprType(indexExpr);
        if (!"int".equals(indexType.getName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    indexExpr.getLine(),
                    indexExpr.getColumn(),
                    "Array index must be an integer, got: " + indexType.getName(),
                    null
            ));
        }

        Type arrayType = this.typeUtils.getExprType(arrayExpr);

        if (!arrayType.isArray()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayExpr.getLine(),
                    arrayExpr.getColumn(),
                    "Cannot perform array access on non-array type: " + arrayType.getName(),
                    null
            ));
            return null;
        }

        int dimensions = determineDimensions(arrayExpr, arrayType);
        System.out.println("DEBUG [ArrayAccessCheck]: Array tem " + dimensions + " dimensões");

        int remainingDimensions = Math.max(0, dimensions - 1);

        Type resultType;
        if (remainingDimensions > 0) {
            resultType = new ArrayType(arrayType.getName(), remainingDimensions);
            System.out.println("DEBUG [ArrayAccessCheck]: Resultado é array com " +
                    remainingDimensions + " dimensões");
        } else {
            resultType = new Type(arrayType.getName(), false);
            System.out.println("DEBUG [ArrayAccessCheck]: Resultado é valor escalar");
        }

        node.put("type", resultType.getName());
        node.put("isArray", String.valueOf(remainingDimensions > 0));
        if (remainingDimensions > 0) {
            node.put("arrayDimensions", String.valueOf(remainingDimensions));
        }

        return null;
    }

    private int determineDimensions(JmmNode arrayExpr, Type arrayType) {

        if (arrayType instanceof ArrayType) {
            int dims = ((ArrayType) arrayType).getDimensions();
            System.out.println("DEBUG [ArrayAccessCheck]: Array dimensions from ArrayType: " + dims);
            return dims;
        }

        if ("VAR_REF_EXPR".equals(arrayExpr.getKind()) && arrayExpr.hasAttribute("name")) {
            String varName = arrayExpr.get("name");


            if (table != null) {

                int registeredDims = table.getArrayDimensions(currentMethod, varName);
                if (registeredDims > 0) {
                    System.out.println("DEBUG [ArrayAccessCheck]: Array dimensions from symbol table: " +
                            registeredDims + " for " + varName);
                    return registeredDims;
                }
            }

            Symbol symbol = findSymbol(currentMethod, varName);
            if (symbol != null && symbol.getType().isArray()) {

                String typeName = symbol.getType().getName();
                Type type = symbol.getType();

                int bracketCount = countArrayBrackets(typeName);
                if (bracketCount > 0) {
                    return bracketCount;
                }
            }
        }

        if ("ArrayAccessExpr".equals(arrayExpr.getKind())) {

            if (arrayExpr.hasAttribute("arrayDimensions")) {
                try {
                    int annotatedDims = Integer.parseInt(arrayExpr.get("arrayDimensions"));
                    System.out.println("DEBUG [ArrayAccessCheck]: Array dimensions from annotation: " + annotatedDims);
                    return annotatedDims;
                } catch (NumberFormatException ignored) {}
            }


            if (arrayExpr.getChildren().size() >= 1) {
                JmmNode innerArray = arrayExpr.getChildren().get(0);
                Type innerType = typeUtils.getExprType(innerArray);

                int innerDims = determineDimensions(innerArray, innerType);

                return Math.max(0, innerDims - 1);
            }
        }

        return arrayType.isArray() ? 1 : 0;
    }

    private int determineArrayDimensions(JmmNode arrayExpr, Type arrayType) {
        Integer cachedDimensions = dimensionCache.get(arrayExpr);
        if (cachedDimensions != null) {
            return cachedDimensions;
        }

        int dimensions = inferDimensionsFromType(arrayType);
        if (dimensions > 0) {
            dimensionCache.put(arrayExpr, dimensions);
            return dimensions;
        }

        if ("VAR_REF_EXPR".equals(arrayExpr.getKind()) && arrayExpr.hasAttribute("name")) {
            String varName = arrayExpr.get("name");

            Symbol symbol = findSymbol(currentMethod, varName);
            if (symbol != null && symbol.getType().isArray()) {
                dimensions = inferDimensionsFromDeclaration(symbol);
                dimensionCache.put(arrayExpr, dimensions);
                return dimensions;
            }
        }

        if ("ArrayAccessExpr".equals(arrayExpr.getKind())) {
            if (arrayExpr.getChildren().size() >= 2) {
                JmmNode innerArrayExpr = arrayExpr.getChildren().get(0);
                Type innerArrayType = this.typeUtils.getExprType(innerArrayExpr);
                int innerDimensions = determineArrayDimensions(innerArrayExpr, innerArrayType);
                if (innerDimensions > 0) {
                    int resultDimensions = innerDimensions - 1;
                    dimensionCache.put(arrayExpr, resultDimensions);
                    return resultDimensions;
                }
            }
        }

        if ("NewIntArrayExpr".equals(arrayExpr.getKind())) {
            dimensions = analyzeArrayCreationExpression(arrayExpr);
            dimensionCache.put(arrayExpr, dimensions);
            return dimensions;
        }

        dimensions = inferArrayDimensionsFromUsage(arrayExpr);
        if (dimensions > 0) {
            dimensionCache.put(arrayExpr, dimensions);
            return dimensions;
        }

        if (arrayType.isArray()) {
            dimensionCache.put(arrayExpr, 1);
            return 1;
        }

        return 0;
    }

    private int inferDimensionsFromType(Type type) {
        if (!type.isArray()) {
            return 0;
        }
        if (type instanceof ArrayType) {
            return ((ArrayType) type).getDimensions();
        }
        String typeName = type.getName();
        int dimensions = countArrayDimensions(typeName);
        return Math.max(1, dimensions);
    }

    private int countArrayDimensions(String typeName) {
        int count = 0;
        int index = typeName.indexOf("[]");
        while (index != -1) {
            count++;
            index = typeName.indexOf("[]", index + 2);
        }
        return count;
    }

    private int inferArrayDimensionsFromUsage(JmmNode arrayExpr) {
        JmmNode parent = arrayExpr.getParent();
        if (parent != null && "ArrayAccessExpr".equals(parent.getKind())) {
            int accessDepth = countNestedArrayAccesses(arrayExpr);
            return accessDepth;
        }
        return 0;
    }

    private int countNestedArrayAccesses(JmmNode node) {
        int depth = 0;
        JmmNode current = node;
        while (current != null) {
            if ("ArrayAccessExpr".equals(current.getKind())) {
                depth++;
            }
            if (current.getParent() != null &&
                    "ArrayAccessExpr".equals(current.getParent().getKind()) &&
                    current.getParent().getChildren().get(0) == current) {
                current = current.getParent();
            } else {
                break;
            }
        }
        return depth;
    }

    private Void visitArrayLengthExpr(JmmNode node, SymbolTable symbolTable) {
        System.out.println("DEBUG [ArrayAccessCheck]: Checking array length access at line " + node.getLine());
        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }
        if (node.getChildren().isEmpty()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Invalid array length expression - missing array reference",
                    null
            ));
            return null;
        }
        JmmNode arrayExpr = node.getChildren().get(0);
        try {
            Type arrayType = this.typeUtils.getExprType(arrayExpr);
            System.out.println("DEBUG [ArrayAccessCheck]: Array type for length: " + arrayType.getName() +
                    (arrayType.isArray() ? "[]" : ""));
            int dimensions = determineArrayDimensions(arrayExpr, arrayType);
            if (dimensions <= 0) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        arrayExpr.getLine(),
                        arrayExpr.getColumn(),
                        "Cannot access length of non-array type '" + arrayType.getName() + "'",
                        null
                ));
            }
        } catch (Exception e) {
            System.out.println("DEBUG [ArrayAccessCheck]: Error checking array length: " + e.getMessage());
        }
        return null;
    }

    private Void visitArrayInitializerExpr(JmmNode node, SymbolTable symbolTable) {
        System.out.println("DEBUG [ArrayAccessCheck]: Processing array initializer expression at line " + node.getLine());
        int dimensions = countDimensionsInArrayInitializer(node);
        dimensionCache.put(node, dimensions);
        System.out.println("DEBUG [ArrayAccessCheck]: Array initializer has " + dimensions + " dimensions based on structure");

        JmmNode assignTarget = findAssignmentTarget(node);
        if (assignTarget != null && assignTarget.hasAttribute("name")) {
            String varName = assignTarget.get("name");
            if (table != null) {
                table.registerArrayDimensions(currentMethod, varName, dimensions);
            }
            System.out.println("DEBUG [ArrayAccessCheck]: Updating variable " + varName +
                    " to have " + dimensions + " dimensions based on initializer");
        }

        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }
        return null;
    }

    private int countDimensionsInArrayInitializer(JmmNode node) {
        int dimensions = 1;
        boolean hasNestedInitializer = false;
        if (node.getKind().equals("ArrayInitializer")) {
            for (JmmNode element : node.getChildren()) {
                if (element.getKind().equals("ArrayElement")) {
                    for (JmmNode content : element.getChildren()) {
                        if (content.getKind().equals("ArrayInitializer") ||
                                content.getKind().equals("ArrayInitializerExpr")) {
                            hasNestedInitializer = true;
                            break;
                        }
                    }
                }
                if (hasNestedInitializer) {
                    break;
                }
            }
        }
        if (hasNestedInitializer) {
            dimensions++;
        }
        return dimensions;
    }

    private Void visitArrayInitializer(JmmNode node, SymbolTable symbolTable) {
        System.out.println("DEBUG [ArrayAccessCheck]: Checking array initializer at line " + node.getLine());
        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }
        if (node.getChildren().size() < 2) {
            return null;
        }
        try {
            JmmNode firstElement = node.getChildren().get(0);
            Type firstType = this.typeUtils.getExprType(firstElement);
            System.out.println("DEBUG [ArrayAccessCheck]: First element type: " + firstType.getName() +
                    (firstType.isArray() ? "[]" : ""));
            for (int i = 1; i < node.getChildren().size(); i++) {
                JmmNode element = node.getChildren().get(i);
                Type elementType = this.typeUtils.getExprType(element);
                System.out.println("DEBUG [ArrayAccessCheck]: Element " + i + " type: " +
                        elementType.getName() + (elementType.isArray() ? "[]" : ""));
                if (!elementType.getName().equals(firstType.getName()) ||
                        elementType.isArray() != firstType.isArray()) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            element.getLine(),
                            element.getColumn(),
                            "Array initializer contains mixed element types: '" +
                                    firstType.getName() + (firstType.isArray() ? "[]" : "") +
                                    "' and '" + elementType.getName() +
                                    (elementType.isArray() ? "[]" : "") + "'",
                            null
                    ));
                    break;
                }
            }
            checkInitializerAssignmentCompatibility(node, firstType, symbolTable);
        } catch (Exception e) {
            System.out.println("DEBUG [ArrayAccessCheck]: Error checking array initializer: " + e.getMessage());
        }
        return null;
    }

    private void checkInitializerAssignmentCompatibility(JmmNode node, Type elementType, SymbolTable symbolTable) {
        JmmNode parent = node.getParent();
        if (parent == null || !"ArrayInitializerExpr".equals(parent.getKind())) {
            return;
        }
        JmmNode assignStmt = parent.getParent();
        if (assignStmt == null ||
                (!("ASSIGN_STMT".equals(assignStmt.getKind()) || "AssignStatement".equals(assignStmt.getKind())))) {
            return;
        }
        if (assignStmt.getChildren().size() < 2) {
            return;
        }
        JmmNode target = assignStmt.getChildren().get(0);
        if (!target.hasAttribute("name")) {
            return;
        }
        String varName = target.get("name");
        Symbol targetSymbol = findSymbol(null, varName);
        if (targetSymbol == null) {
            return;
        }
        Type targetType = targetSymbol.getType();
        if (!targetType.isArray()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    "Cannot assign array initializer to non-array variable '" + varName + "'",
                    null
            ));
            return;
        }
        if (!targetType.getName().equals(elementType.getName())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    "Cannot initialize array of type '" + targetType.getName() + "[]' with elements of type '" +
                            elementType.getName() + (elementType.isArray() ? "[]" : "") + "'",
                    null
            ));
        }
    }

    private Symbol findSymbol(String methodName, String symbolName) {
        if (symbolName == null) {
            return null;
        }

        if (methodName != null) {
            for (Symbol param : table.getParameters(methodName)) {
                if (param.getName().equals(symbolName)) {
                    return param;
                }
            }
            for (Symbol local : table.getLocalVariables(methodName)) {
                if (local.getName().equals(symbolName)) {
                    return local;
                }
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(symbolName)) {
                return field;
            }
        }

        if (methodName == null) {
            for (String m : table.getMethods()) {
                Symbol found = findSymbol(m, symbolName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String getFullyQualifiedName(String methodName, String symbolName) {
        if (methodName == null) {
            return symbolName;
        }
        return methodName + "." + symbolName;
    }

    private Void visitDefault(JmmNode node, SymbolTable symbolTable) {
        for (JmmNode child : node.getChildren()) {
            visit(child, symbolTable);
        }
        return null;
    }

    private int inferDimensionsFromDeclaration(Symbol symbol) {
        if (symbol == null || !symbol.getType().isArray()) {
            return 0;
        }


        if (symbol.getType() instanceof ArrayType) {
            return ((ArrayType) symbol.getType()).getDimensions();
        }

        if (table != null && currentMethod != null) {
            int dimensions = table.getArrayDimensions(currentMethod, symbol.getName());
            if (dimensions > 0) {
                return dimensions;
            }
        }

        String typeName = symbol.getType().getName();
        int dimensionsFromBrackets = countArrayBrackets(typeName);
        if (dimensionsFromBrackets > 0) {
            return dimensionsFromBrackets;
        }

        return 1;
    }

    private int countArrayBrackets(String typeName) {
        int count = 0;
        int index = typeName.indexOf("[]");
        while (index != -1) {
            count++;
            index = typeName.indexOf("[]", index + 2);
        }
        return count;
    }

    private int analyzeArrayCreationExpression(JmmNode node) {
        String kind = node.getKind();

        if ("NewMultiDimArrayExpr".equals(kind)) {
            for (JmmNode child : node.getChildren()) {
                if ("MultiDimArrayDeclaration".equals(child.getKind())) {

                    int dimensions = 0;
                    for (JmmNode dimChild : child.getChildren()) {
                        if (!dimChild.getKind().equals("LBRACK") &&
                                !dimChild.getKind().equals("RBRACK")) {
                            dimensions++;
                        }
                    }

                    System.out.println("DEBUG [ArrayAccessCheck]: Nova expressão de array multidimensional com " +
                            dimensions + " dimensões");
                    return dimensions;
                }
            }

            return 2;
        }

        if ("NewIntArrayExpr".equals(kind)) {
            int dimensions = 1;

            for (JmmNode child : node.getChildren()) {
                if (child == node.getChildren().get(0)) {
                    continue;
                }

                if ("LBRACK".equals(child.getKind())) {
                    dimensions++;
                } else if (child.getKind().contains("Dimension") ||
                        "ArrayDimensions".equals(child.getKind())) {
                    for (JmmNode bracket : child.getChildren()) {
                        if ("LBRACK".equals(bracket.getKind())) {
                            dimensions++;
                        }
                    }
                }
            }

            dimensions = dimensions / 2 + (dimensions % 2);

            System.out.println("DEBUG [ArrayAccessCheck]: Nova expressão de array com " +
                    dimensions + " dimensões");
            return dimensions;
        }

        return 1;
    }
}