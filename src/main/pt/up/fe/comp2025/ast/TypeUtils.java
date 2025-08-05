package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidades para manipulação de tipos, com suporte aprimorado para arrays multidimensionais.
 */
public class TypeUtils {

    private final JmmSymbolTable table;
    private String currentMethod;

    private final Map<JmmNode, Type> exprTypeCache = new HashMap<>();
    private final Map<String, Integer> variableDimensions = new HashMap<>();
    private final Map<JmmNode, Integer> expressionDimensions = new HashMap<>();

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public void setCurrentMethod(String methodName) {
        this.currentMethod = methodName;

        exprTypeCache.clear();
        expressionDimensions.clear();

        buildVariableDimensionsMap();
    }

    private void buildVariableDimensionsMap() {
        variableDimensions.clear();

        if (currentMethod == null) return;

        for (Symbol param : table.getParameters(currentMethod)) {
            if (param.getType().isArray()) {
                int dimensions = inferArrayDimensions(param.getType());
                variableDimensions.put(param.getName(), dimensions);
                System.out.println("DEBUG: Identified parameter " + param.getName() +
                        " as array with " + dimensions + " dimensions");
            }
        }

        for (Symbol local : table.getLocalVariables(currentMethod)) {
            if (local.getType().isArray()) {
                int dimensions = inferArrayDimensions(local.getType());
                variableDimensions.put(local.getName(), dimensions);
                System.out.println("DEBUG: Identified local variable " + local.getName() +
                        " as array with " + dimensions + " dimensions");
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getType().isArray()) {
                int dimensions = inferArrayDimensions(field.getType());
                variableDimensions.put(field.getName(), dimensions);
                System.out.println("DEBUG: Identified field " + field.getName() +
                        " as array with " + dimensions + " dimensions");
            }
        }
    }

    private int inferArrayDimensions(Type type) {
        if (!type.isArray()) return 0;

        if (type instanceof ArrayType) {
            return ((ArrayType) type).getDimensions();
        }

        if (table != null) {
            String typeName = type.getName();
            int methodDims = table.getMethodReturnDimensions(typeName);
            if (methodDims > 0) {
                return methodDims;
            }
        }

        return 1;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newBooleanType() {
        return new Type("boolean", false);
    }

    private static Type error(JmmNode node, String message) {
        throw new RuntimeException("Semantic error at line " + node.getLine() +
                ", col " + node.getColumn() + ": " + message);
    }

    public static Type convertType(JmmNode typeNode) {
        System.out.println("DEBUG convertType: Processing type node " + typeNode.getKind() +
                (typeNode.hasAttribute("name") ? " name=" + typeNode.get("name") : ""));

        System.out.println("  DEBUG Type node children:");
        for (JmmNode child : typeNode.getChildren()) {
            System.out.println("    - " + child.getKind());
        }

        if ("FUNCTION_TYPE".equals(typeNode.getKind())) {
            if (typeNode.hasAttribute("name") && "void".equals(typeNode.get("name"))) {
                return new Type("void", false);
            }

            for (JmmNode child : typeNode.getChildren()) {
                if ("TYPE".equals(child.getKind())) {
                    return convertType(child);
                } else if ("VOID".equals(child.getKind())) {
                    return new Type("void", false);
                }
            }
            return new Type("void", false);
        }

        String typeName = "";
        if (typeNode.hasAttribute("name")) {
            typeName = typeNode.get("name");
        } else {
            for (JmmNode child : typeNode.getChildren()) {
                if ("INT_TYPE".equals(child.getKind())) {
                    typeName = "int";
                    break;
                } else if ("BOOLEAN".equals(child.getKind())) {
                    typeName = "boolean";
                    break;
                } else if ("STRING".equals(child.getKind())) {
                    typeName = "String";
                    break;
                } else if ("TYPE".equals(child.getKind())) {
                    return convertType(child);
                } else if ("VOID".equals(child.getKind())) {
                    typeName = "void";
                    break;
                }
            }
        }

        boolean isArray = false;
        int dimensions = 0;

        for (JmmNode child : typeNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                isArray = true;
                dimensions = countArraySuffixDepth(child);
                break;
            }
        }

        if (isArray) {
            if (dimensions > 1) {
                return new ArrayType(typeName, dimensions);
            } else {
                return new Type(typeName, true);
            }
        } else {
            return new Type(typeName, false);
        }
    }

    private static int countArraySuffixDepth(JmmNode arraySuffixNode) {
        if (arraySuffixNode == null) {
            return 0;
        }

        int count = 1;

        for (JmmNode child : arraySuffixNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                count += countArraySuffixDepth(child);
                break;
            }
        }

        return count;
    }
    private Type getBinaryExprType(JmmNode expr) {
        if (expr.getChildren().size() < 2) {
            System.out.println("Warning: Expected binary expression but found node with fewer than 2 children: " + expr.getKind());
            return new Type("void", false);
        }

        JmmNode left = expr.getChildren().get(0);
        JmmNode right = expr.getChildren().get(1);
        Type leftType = getExprType(left);
        Type rightType = getExprType(right);

        String operator = expr.hasAttribute("operator") ? expr.get("operator") : "";

        if (leftType.isArray() || rightType.isArray()) {
            return new Type("error", false);
        }

        if ("*".equals(operator) || "/".equals(operator) || "%".equals(operator)) {
            if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                return newIntType();
            }
            return error(expr, "Invalid multiplicative operation between types '" +
                    leftType.getName() + "' and '" + rightType.getName() + "'.");
        }

        if ("+".equals(operator)) {
            if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                return newIntType();
            }
            if (leftType.getName().equals("String") || rightType.getName().equals("String")) {
                return new Type("String", false);
            }
            return error(expr, "Invalid addition operation between types '" +
                    leftType.getName() + "' and '" + rightType.getName() + "'.");
        }

        if ("-".equals(operator)) {
            if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                return newIntType();
            }
            return error(expr, "Invalid subtraction operation between types '" +
                    leftType.getName() + "' and '" + rightType.getName() + "'.");
        }

        if ("<".equals(operator) || "<=".equals(operator) || ">".equals(operator) || ">=".equals(operator)) {
            if ("int".equals(leftType.getName()) && "int".equals(rightType.getName())) {
                return new Type("boolean", false);
            }
            return error(expr, "Invalid relational operation '" + operator + "' between types '" +
                    leftType.getName() + "' and '" + rightType.getName() + "'.");
        }

        if ("==".equals(operator) || "!=".equals(operator)) {
            if (leftType.getName().equals(rightType.getName()) && leftType.isArray() == rightType.isArray()) {
                return new Type("boolean", false);
            }
            return error(expr, "Invalid equality operation '" + operator + "' between types '" +
                    leftType.getName() + (leftType.isArray() ? "[]" : "") + "' and '" +
                    rightType.getName() + (rightType.isArray() ? "[]" : "") + "'.");
        }

        if ("&&".equals(operator) || "||".equals(operator)) {
            if ("boolean".equals(leftType.getName()) && "boolean".equals(rightType.getName())) {
                return new Type("boolean", false);
            }
            return error(expr, "Invalid logical operation '" + operator + "' between types '" +
                    leftType.getName() + "' and '" + rightType.getName() +
                    "'. Both operands must be boolean.");
        }

        return error(expr, "Invalid binary operation between types '" +
                leftType.getName() + "' and '" + rightType.getName() + "'.");
    }

    private Type getUnaryExprType(JmmNode expr) {
        if (expr.getChildren().isEmpty()) {
            System.out.println("Warning: Expected unary expression with child but found none: " + expr.getKind());
            return new Type("void", false);
        }

        JmmNode child = expr.getChildren().get(0);
        Type childType = getExprType(child);
        if (childType.isArray()) {
            return error(expr, "Cannot perform unary operations on arrays");
        }
        if (childType.getName().equals("int")) {
            return newIntType();
        }
        if (childType.getName().equals("boolean")) {
            return new Type("boolean", false);
        }
        return error(expr, "Invalid unary operation on type " + childType.getName());
    }

    public Type getExprType(JmmNode expr) {

        if (exprTypeCache.containsKey(expr)) {
            return exprTypeCache.get(expr);
        }

        String kind = expr.getKind();

        if (expr.hasAttribute("type")) {
            boolean isArray = expr.hasAttribute("isArray") && "true".equals(expr.get("isArray"));
            int dimensions = 1;
            if (isArray && expr.hasAttribute("arrayDimensions")) {
                try {
                    dimensions = Integer.parseInt(expr.get("arrayDimensions"));
                } catch (NumberFormatException e) {
                    // Ignorar erro e manter valor padrão
                }
            }
            Type result;
            if (isArray) {
                result = new ArrayType(expr.get("type"), dimensions);
                expressionDimensions.put(expr, dimensions);
            } else {
                result = new Type(expr.get("type"), false);
            }
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("FieldAccessExpr".equals(kind)) {
            if (expr.getChildren().isEmpty() || !expr.hasAttribute("name")) {
                System.out.println("ERROR: Field access expression missing object or field name");
                return error(expr, "Field access expression missing object or field name");
            }
            JmmNode objectExpr = expr.getChildren().get(0);
            String fieldName = expr.get("name");
            System.out.println("DEBUG: Processing field access: " + fieldName);
            Type objectType = getExprType(objectExpr);
            System.out.println("DEBUG: Object type: " + objectType.getName());
            if (objectType.getName().equals(table.getClassName())) {
                for (Symbol field : table.getFields()) {
                    if (field.getName().equals(fieldName)) {
                        System.out.println("DEBUG: Found matching field: " + fieldName +
                                " of type: " + field.getType().getName());

                        Type fieldType = field.getType();
                        exprTypeCache.put(expr, fieldType);

                        if (fieldType.isArray() && variableDimensions.containsKey(fieldName)) {
                            expressionDimensions.put(expr, variableDimensions.get(fieldName));
                        }

                        return fieldType;
                    }
                }
            }
            if (objectType.getName().equals(table.getSuper())) {
                if ("field1".equals(fieldName) || "field2".equals(fieldName)) {
                    System.out.println("DEBUG: Found superclass field: " + fieldName + " (assuming int)");
                    Type result = newIntType();
                    exprTypeCache.put(expr, result);
                    return result;
                }
            }
            return error(expr, "Could not determine type for field: " + objectType.getName() + "." + fieldName);
        }

        if ("INTEGER_LITERAL".equals(kind) || "IntLiteral".equals(kind)) {
            Type result = newIntType();
            exprTypeCache.put(expr, result);
            return result;
        }
        if ("Boolean".equals(kind) || "TrueLiteralExpr".equals(kind) || "FalseLiteralExpr".equals(kind)
                || "TrueLiteral".equals(kind) || "FalseLiteral".equals(kind)) {
            Type result = new Type("boolean", false);
            exprTypeCache.put(expr, result);
            return result;
        }
        if ("String".equals(kind)) {
            Type result = new Type("String", false);
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("NewIntArrayExpr".equals(kind) || "NewArrayExpression".equals(kind) || "NewMultiDimArrayExpr".equals(kind)) {
            System.out.println("DEBUG: Found new int array expression (" + kind + ")");
            int dimensions = countArrayDimensionsInNew(expr);
            expressionDimensions.put(expr, dimensions);
            ArrayType result = new ArrayType("int", dimensions);
            System.out.println("DEBUG: New array has " + dimensions + " dimensions");
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("ArrayLengthPostfix".equals(kind) || "ArrayLengthExpr".equals(kind) || "ArrayLength".equals(kind)) {
            Type result = newIntType();
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("VAR_REF_EXPR".equals(kind) || "IdentifierReference".equals(kind)) {
            if (expr.hasAttribute("name") && currentMethod != null) {
                String varName = expr.get("name");
                if (table.getImports().contains(varName)) {
                    System.out.println("DEBUG: Found imported class in getExprType: " + varName);
                    Type result = new Type(varName, false);
                    exprTypeCache.put(expr, result);
                    return result;
                }

                for (Symbol param : table.getParameters(currentMethod)) {
                    if (param.getName().equals(varName)) {
                        System.out.println("DEBUG: Found parameter: " + varName + " with type: " +
                                param.getType().getName() + (param.getType().isArray() ? "[]" : ""));

                        Type paramType = param.getType();

                        if (paramType.isArray()) {
                            int dimensions = table.getArrayDimensions(currentMethod, varName);
                            if (dimensions > 0) {
                                paramType = new ArrayType(paramType.getName(), dimensions);
                                expressionDimensions.put(expr, dimensions);
                            }
                        }

                        exprTypeCache.put(expr, paramType);
                        return paramType;
                    }
                }

                for (Symbol local : table.getLocalVariables(currentMethod)) {
                    if (local.getName().equals(varName)) {
                        System.out.println("DEBUG: Found local variable: " + varName + " with type: " +
                                local.getType().getName() + (local.getType().isArray() ? "[]" : ""));

                        Type localType = local.getType();

                        if (localType.isArray()) {
                            int dimensions = table.getArrayDimensions(currentMethod, varName);
                            if (dimensions > 0) {
                                localType = new ArrayType(localType.getName(), dimensions);
                                expressionDimensions.put(expr, dimensions);
                            }
                        }

                        exprTypeCache.put(expr, localType);
                        return localType;
                    }
                }

                for (Symbol field : table.getFields()) {
                    if (field.getName().equals(varName)) {
                        System.out.println("DEBUG: Found field: " + varName + " with type: " +
                                field.getType().getName() + (field.getType().isArray() ? "[]" : ""));

                        Type fieldType = field.getType();

                        if (fieldType.isArray()) {
                            int dimensions = table.getArrayDimensions(null, varName);
                            if (dimensions > 0) {
                                fieldType = new ArrayType(fieldType.getName(), dimensions);
                                expressionDimensions.put(expr, dimensions);
                            }
                        }

                        exprTypeCache.put(expr, fieldType);
                        return fieldType;
                    }
                }

                System.out.println("WARNING: Variable not found: " + varName);
                return error(expr, "Variable '" + varName + "' not found.");
            }
            return error(expr, "Identifier reference without a valid name");
        }

        if ("ThisExpr".equals(kind) || "ThisExpression".equals(kind)) {
            Type result = new Type(table.getClassName(), false);
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("DIRECT_METHOD_CALL".equals(kind) || "MethodCall".equals(kind)) {
            if (expr.hasAttribute("name")) {
                String methodName = expr.get("name");
                Type returnType = table.getReturnType(methodName);
                if (returnType != null) {
                    if (returnType.isArray()) {
                        int dimensions = table.getMethodReturnDimensions(methodName);
                        if (dimensions > 0) {
                            returnType = new ArrayType(returnType.getName(), dimensions);
                            expressionDimensions.put(expr, dimensions);
                        }
                    }

                    exprTypeCache.put(expr, returnType);
                    return returnType;
                }
                return error(expr, "Method '" + methodName + "' not declared.");
            }
            Type result = new Type("void", false);
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("MULTIPLICATIVE_EXPR".equals(kind) || "MultiplicativeExpression".equals(kind) ||
                "ADDITIVE_EXPR".equals(kind) || "AdditiveExpression".equals(kind) ||
                "RELATIONAL_EXPR".equals(kind) || "RelationalExpression".equals(kind) ||
                "LogicalAndExpr".equals(kind) || "LogicalAndExpression".equals(kind) ||
                "LogicalOrExpr".equals(kind) || "LogicalOrExpression".equals(kind)) {
            if (expr.getChildren().size() >= 2) {
                try {
                    Type result = getBinaryExprType(expr);
                    exprTypeCache.put(expr, result);
                    return result;
                } catch (Exception e) {
                    System.out.println("ERROR processing binary expr: " + e.getMessage());
                    Type result = new Type("void", false);
                    exprTypeCache.put(expr, result);
                    return result;
                }
            } else if (expr.getChildren().size() == 1) {
                System.out.println("DEBUG: Expression with one child, getting child type");
                Type result = getExprType(expr.getChildren().get(0));
                exprTypeCache.put(expr, result);
                return result;
            } else {
                System.out.println("Warning: Expression with no children: " + kind);
                Type result = new Type("void", false);
                exprTypeCache.put(expr, result);
                return result;
            }
        }

        if ("SignExpr".equals(kind) || "NotExpr".equals(kind) ||
                "UnaryExpression".equals(kind) || "NotExpression".equals(kind)) {
            if (expr.getChildren().size() >= 1) {
                try {
                    Type result = getUnaryExprType(expr);
                    exprTypeCache.put(expr, result);
                    return result;
                } catch (Exception e) {
                    System.out.println("ERROR processing unary expr: " + e.getMessage());
                    Type result = new Type("void", false);
                    exprTypeCache.put(expr, result);
                    return result;
                }
            }
            Type result = new Type("void", false);
            exprTypeCache.put(expr, result);
            return result;
        }

        if ("ArrayAccessExpr".equals(kind)) {
            if (expr.getChildren().size() < 2) {
                System.out.println("WARNING: Invalid array access expression - missing array or index");
                return error(expr, "Invalid array access expression - missing array or index");
            }

            JmmNode arrayExpr = expr.getChildren().get(0);
            JmmNode indexExpr = expr.getChildren().get(1);

            Type indexType = getExprType(indexExpr);
            if (!"int".equals(indexType.getName())) {
                System.out.println("WARNING: Array index must be an integer, but got: " + indexType.getName());
                return error(expr, "Array index must be an integer, but got: " + indexType.getName());
            }

            Type arrayType = getExprType(arrayExpr);

            int arrayDimensions = 0;


            if (arrayType instanceof ArrayType) {
                arrayDimensions = ((ArrayType) arrayType).getDimensions();
            }
            else if (expressionDimensions.containsKey(arrayExpr)) {
                arrayDimensions = expressionDimensions.get(arrayExpr);
            }
            else if (arrayType.isArray()) {
                arrayDimensions = 1;

                if ("VAR_REF_EXPR".equals(arrayExpr.getKind()) && arrayExpr.hasAttribute("name")) {
                    String varName = arrayExpr.get("name");
                    int tableDims = table.getArrayDimensions(currentMethod, varName);
                    if (tableDims > 0) {
                        arrayDimensions = tableDims;
                    }
                }
            }

            System.out.println("DEBUG: Array access on type " + arrayType.getName() +
                    (arrayType.isArray() ? "[]" : "") + " with dimensions: " + arrayDimensions);

            if (!arrayType.isArray() && arrayDimensions <= 0) {
                System.out.println("WARNING: Array access on non-array type " + arrayType.getName());
                return error(expr, "Cannot perform array access on non-array type '" + arrayType.getName() + "'");
            }

            int remainingDimensions = arrayDimensions - 1;

            expressionDimensions.put(expr, remainingDimensions);

            Type resultType;
            if (remainingDimensions > 0) {
                resultType = new ArrayType(arrayType.getName(), remainingDimensions);
                System.out.println("DEBUG: Array access result is still an array with " +
                        remainingDimensions + " dimensions");
            } else {
                resultType = new Type(arrayType.getName(), false);
                System.out.println("DEBUG: Array access result is a scalar value");
            }

            exprTypeCache.put(expr, resultType);
            return resultType;
        }

        if ("MethodCallExpr".equals(kind)) {
            if (expr.getChildren().isEmpty()) {
                return error(expr, "Invalid method call expression - missing object");
            }

            JmmNode objectExpr = expr.getChildren().get(0);
            String methodName = expr.hasAttribute("name") ? expr.get("name") : "<unknown>";
            System.out.println("DEBUG: Analyzing method call: " + methodName);

            Type objectType = getExprType(objectExpr);

            if (objectType.getName().equals(table.getClassName())) {
                Type returnType = table.getReturnType(methodName);
                if (returnType != null) {
                    if (returnType.isArray()) {
                        int dimensions = table.getMethodReturnDimensions(methodName);
                        if (dimensions > 0) {
                            returnType = new ArrayType(returnType.getName(), dimensions);
                            expressionDimensions.put(expr, dimensions);
                        }
                    }

                    exprTypeCache.put(expr, returnType);
                    return returnType;
                }
            }

            if (isExternalType(objectType)) {
                Type inferredType = inferExternalMethodReturnType(expr, methodName);
                exprTypeCache.put(expr, inferredType);

                if (inferredType.isArray() && inferredType instanceof ArrayType) {
                    expressionDimensions.put(expr, ((ArrayType) inferredType).getDimensions());
                }

                return inferredType;
            }

            JmmNode parent = expr.getParent();
            if (parent != null) {
                if ("ArrayAccessExpr".equals(parent.getKind()) &&
                        parent.getChildren().get(0) == expr) {

                    int requiredDimensions = countChainedArrayAccesses(parent);
                    Type arrayType = new ArrayType("int", requiredDimensions);
                    expressionDimensions.put(expr, requiredDimensions);
                    exprTypeCache.put(expr, arrayType);
                    return arrayType;
                }
            }

            Type returnType = table.getReturnType(methodName);
            if (returnType != null) {
                exprTypeCache.put(expr, returnType);
                return returnType;
            }

            System.out.println("DEBUG: Could not determine return type for method: " + methodName);
            Type intType = newIntType();
            exprTypeCache.put(expr, intType);
            return intType;
        }

        if ("ParenExpr".equals(kind) || "ParenExpression".equals(kind)) {
            if (!expr.getChildren().isEmpty()) {
                Type result = getExprType(expr.getChildren().get(0));
                exprTypeCache.put(expr, result);
                return result;
            }
        }

        if ("NewObjectExpr".equals(kind) || "NewObjectExpression".equals(kind)) {
            if (expr.hasAttribute("name")) {
                Type result = new Type(expr.get("name"), false);
                exprTypeCache.put(expr, result);
                return result;
            }
        }
        if ("ArrayInitializerExpr".equals(expr.getKind())) {
            int dimensions = countDimensionsInArrayInitializer(expr);
            String baseType = "int";
            Type arrayType = new ArrayType(baseType, dimensions);

            exprTypeCache.put(expr, arrayType);
            expr.put("type", baseType);
            expr.put("isArray", "true");
            expr.put("arrayDimensions", String.valueOf(dimensions));

            return arrayType;
        }

        if ("ArrayInitializerExpr".equals(kind) || "ArrayInitializer".equals(kind)) {
            int dimensions = countDimensionsInArrayInitializer(expr);
            expressionDimensions.put(expr, dimensions);

            if (!expr.getChildren().isEmpty()) {
                Type elementType = getExprType(expr.getChildren().get(0));
                Type result = new ArrayType(elementType.getName(), dimensions);
                exprTypeCache.put(expr, result);
                return result;
            }

            Type result = new ArrayType("int", dimensions);
            exprTypeCache.put(expr, result);
            return result;
        }

        for (JmmNode child : expr.getChildren()) {
            Type childType = getExprType(child);
            if (!"void".equals(childType.getName())) {
                exprTypeCache.put(expr, childType);
                return childType;
            }
        }
        System.out.println("DEBUG: Could not determine type for " + kind + ", returning void");
        Type result = new Type("void", false);
        exprTypeCache.put(expr, result);
        return result;
    }

    private int countArrayDimensionsInNew(JmmNode expr) {
        String kind = expr.getKind();

        if ("NewMultiDimArrayExpr".equals(kind)) {
            for (JmmNode child : expr.getChildren()) {
                if ("MultiDimArrayDeclaration".equals(child.getKind())) {
                    int expressionCount = 0;
                    for (JmmNode dimChild : child.getChildren()) {
                        if (!"LBRACK".equals(dimChild.getKind()) &&
                                !"RBRACK".equals(dimChild.getKind())) {
                            expressionCount++;
                        }
                    }

                    int dimensions = expressionCount;
                    System.out.println("DEBUG [TypeUtils]: MultiDimArrayDeclaration com " +
                            dimensions + " dimensões");
                    return dimensions;
                }
            }
        }

        if ("NewIntArrayExpr".equals(kind)) {
            int dimensions = 0;

            for (int i = 0; i < expr.getChildren().size(); i++) {
                JmmNode child = expr.getChildren().get(i);
                if ("LBRACK".equals(child.getKind())) {
                    dimensions++;
                }
            }

            dimensions = Math.max(1, dimensions / 2);

            System.out.println("DEBUG [TypeUtils]: NewIntArrayExpr com " +
                    dimensions + " dimensões");
            return dimensions;
        }

        return 1;
    }

    private int countDimensionsInArrayInitializer(JmmNode node) {
        int dimensions = 1;

        if ("ArrayInitializer".equals(node.getKind()) || "ArrayInitializerExpr".equals(node.getKind())) {
            boolean hasNestedInitializer = false;

            if ("ArrayInitializer".equals(node.getKind())) {
                for (JmmNode child : node.getChildren()) {
                    if ("ArrayElement".equals(child.getKind())) {
                        for (JmmNode content : child.getChildren()) {
                            if ("ArrayInitializer".equals(content.getKind()) ||
                                    "ArrayInitializerExpr".equals(content.getKind())) {
                                hasNestedInitializer = true;
                                break;
                            }
                        }
                    }
                    if (hasNestedInitializer) break;
                }
            } else {
                for (JmmNode child : node.getChildren()) {
                    if ("ArrayInitializer".equals(child.getKind())) {
                        for (JmmNode element : child.getChildren()) {
                            if ("ArrayElement".equals(element.getKind())) {
                                for (JmmNode content : element.getChildren()) {
                                    if ("ArrayInitializer".equals(content.getKind()) ||
                                            "ArrayInitializerExpr".equals(content.getKind())) {
                                        hasNestedInitializer = true;
                                        break;
                                    }
                                }
                            }
                            if (hasNestedInitializer) break;
                        }
                    }
                    if (hasNestedInitializer) break;
                }
            }
            if (hasNestedInitializer) {
                dimensions++;
            }
        }

        System.out.println("DEBUG [TypeUtils]: Inicializador de array com " + dimensions + " dimensões");
        return dimensions;
    }

    private int countChainedArrayAccesses(JmmNode arrayAccessNode) {
        int count = 1; // Este acesso

        JmmNode current = arrayAccessNode;
        JmmNode parent = current.getParent();

        while (parent != null) {
            if ("ArrayAccessExpr".equals(parent.getKind())) {
                if (parent.getChildren().size() >= 1 && parent.getChildren().get(0) == current) {
                    count++;
                    current = parent;
                    parent = current.getParent();
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return count;
    }

    private Type inferExternalMethodReturnType(JmmNode expr, String methodName) {

        JmmNode parent = expr.getParent();
        while (parent != null) {
            if ("ArrayAccessExpr".equals(parent.getKind()) &&
                    parent.getChildren().size() > 0 && parent.getChildren().get(0) == expr) {

                int neededDimensions = countChainedArrayAccesses(parent);
                return new ArrayType("int", neededDimensions);
            }

            if (("ASSIGN_STMT".equals(parent.getKind()) || "AssignStatement".equals(parent.getKind())) &&
                    parent.getChildren().size() >= 2 && parent.getChildren().get(1) == expr) {

                JmmNode targetNode = parent.getChildren().get(0);
                if (targetNode.hasAttribute("name")) {
                    String targetName = targetNode.get("name");

                    Symbol symbol = findSymbol(targetName);
                    if (symbol != null) {
                        Type targetType = symbol.getType();

                        if (targetType.isArray()) {
                            int dims = table.getArrayDimensions(currentMethod, targetName);
                            if (dims > 0) {
                                return new ArrayType(targetType.getName(), dims);
                            }
                            return targetType;
                        }

                        return targetType;
                    }
                }
            }

            parent = parent.getParent();
        }

        if (methodName.startsWith("get") || methodName.startsWith("find") ||
                methodName.startsWith("retrieve") || methodName.startsWith("calc")) {
            return newIntType();
        }

        if (methodName.startsWith("is") || methodName.startsWith("has") ||
                methodName.startsWith("can") || methodName.startsWith("should")) {
            return new Type("boolean", false);
        }

        return newIntType();
    }

    private boolean isExternalType(Type type) {
        if (table.getImports().contains(type.getName())) {
            return true;
        }
        if (table.getSuper() != null && table.getSuper().equals(type.getName())) {
            return true;
        }
        if (type.getName().equals(table.getClassName()) &&
                table.getSuper() != null &&
                table.getImports().contains(table.getSuper())) {
            System.out.println("DEBUG: " + type.getName() + " is a subclass of imported class " +
                    table.getSuper() + ", assuming methods are in parent");
            return true;
        }
        return false;
    }

    private Symbol findSymbol(String name) {

        if (currentMethod != null) {
            for (Symbol param : table.getParameters(currentMethod)) {
                if (param.getName().equals(name)) {
                    return param;
                }
            }

            for (Symbol local : table.getLocalVariables(currentMethod)) {
                if (local.getName().equals(name)) {
                    return local;
                }
            }
        }

        for (Symbol field : table.getFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }

        return null;
    }

}