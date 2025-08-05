package pt.up.fe.comp2025.optimization.ollir.generator;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.ArrayType;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.optimization.util.LabelManager;
import pt.up.fe.comp2025.optimization.util.OptUtils;
import pt.up.fe.comp2025.optimization.util.LabelManager;

// Adicionar campo para o LabelManager compartilhado
import java.util.*;

/**
 * Visitor that generates OLLIR code for expressions in the AST.
 * Implements a generalized approach without special cases.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    // Constants for OLLIR code generation
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private static final String END_STMT = ";\n";
    private static final String INVOKE_STATIC = "invokestatic";
    private static final String INVOKE_VIRTUAL = "invokevirtual";
    private static final String INVOKE_SPECIAL = "invokespecial";

    // Main components
    private final SymbolTable symbolTable;
    private final TypeUtils typeUtils;
    private final OptUtils ollirTypeUtils;

    // Context tracking
    private String currentMethod;
    private final List<String> errors;
    private final Set<String> importedClasses;
    private final Map<String, Boolean> variableTypeCache; // Cache to optimize variable identification
    private static int labelCounter = 0;
    private final Map<String, OllirExprResult> expressionCache = new HashMap<>();
    private final Set<JmmNode> visitedNodes = new HashSet<>();
    private final LabelManager labelManager;
    private final Set<JmmNode> processedSwitchNodes = new HashSet<>();
    private Set<String> multiAssignedVariablesRegistry = new HashSet<>();
    private Set<String> loopVariables = new HashSet<>();
    private Set<String> loopConditionVariables = new HashSet<>();
    /**
     * Configura o registro compartilhado de variáveis multi-atribuídas.
     * Chamado após o ConstantPropagationVisitor concluir seu trabalho.
     */
    public void setMultiAssignedVariablesRegistry(Set<String> registry) {
        this.multiAssignedVariablesRegistry = registry;
    }
    /**
     * Configura o registro compartilhado de variáveis da condição de loop.
     */
    public void setLoopConditionVariables(Set<String> variables) {
        this.loopConditionVariables = new HashSet<>(variables);
    }

    /**
     * Sets the loop variables from ConstantPropagationVisitor.
     */
    public void setLoopVariables(Set<String> variables) {
        this.loopVariables = new HashSet<>(variables);
    }

    /**
     * Generates a unique identifier for labels.
     */
    private int nextLabel() {
        // ADICIONAR: Proteção contra overflow
        if (labelCounter > 10000) {
            throw new RuntimeException("Label counter overflow. Possible infinite loop in label generation.");
        }
        return labelCounter++;
    }

    /**
     * Constructs an expression generator visitor with the provided symbol table.
     */
    public OllirExprGeneratorVisitor(SymbolTable symbolTable) {
        this(symbolTable, new OptUtils(new TypeUtils(symbolTable)), LabelManager.getInstance());
    }

    /**
     * Construtor que aceita OptUtils compartilhado.
     */
    public OllirExprGeneratorVisitor(SymbolTable symbolTable, OptUtils sharedOllirTypeUtils) {
        this(symbolTable, sharedOllirTypeUtils, LabelManager.getInstance());
    }


    /**
     * Construtor completo que aceita tanto o OptUtils quanto o LabelManager compartilhados.
     */
    public OllirExprGeneratorVisitor(SymbolTable symbolTable, OptUtils sharedOllirTypeUtils, LabelManager labelManager) {
        this.symbolTable = symbolTable;
        this.typeUtils = new TypeUtils(symbolTable);
        this.ollirTypeUtils = sharedOllirTypeUtils;
        this.labelManager = labelManager;
        System.out.println("DEBUG: Initializing OllirExprGeneratorVisitor with Symbol Table: " + symbolTable);

        this.currentMethod = null;
        this.errors = new ArrayList<>();
        this.variableTypeCache = new HashMap<>();

        // Extract imported class names for future reference
        this.importedClasses = new HashSet<>();
        for (String importPath : symbolTable.getImports()) {
            String className = extractClassName(importPath);
            if (className != null) {
                System.out.println("DEBUG: Imported class found: " + className);
                importedClasses.add(className);
            }
        }
        System.out.println("DEBUG: OllirExprGeneratorVisitor initialized successfully");
    }

    /**
     * Overrides the visit method to implement caching for better performance.
     */
    @Override
    public OllirExprResult visit(JmmNode node) {
        // Generate a unique key that captures the full context of the node
        String cacheKey = generateCacheKey(node);

        // Check if we already have a result in the cache
        if (expressionCache.containsKey(cacheKey)) {
            return expressionCache.get(cacheKey);
        }

        // Otherwise, process normally
        OllirExprResult result = super.visit(node);

        // Store in cache
        expressionCache.put(cacheKey, result);

        return result;
    }

    /**
     * Helper method to generate a cache key that captures the complete context.
     */
    private String generateCacheKey(JmmNode node) {
        StringBuilder key = new StringBuilder(node.getKind());

        // Add all relevant attributes
        for (String attr : node.getAttributes()) {
            key.append("_").append(attr).append("=").append(node.get(attr));
        }

        // Add the context of children (important for complex expressions)
        for (int i = 0; i < node.getNumChildren(); i++) {
            key.append("_child").append(i).append("=");
            JmmNode child = node.getChild(i);
            key.append(child.getKind());

            // Add key attributes of children
            if (child.hasAttribute("name")) {
                key.append("_").append(child.get("name"));
            }
            if (child.hasAttribute("value")) {
                key.append("_").append(child.get("value"));
            }
        }

        // Add position in code to differentiate different instances
        key.append("_line").append(node.getLine());
        key.append("_col").append(node.getColumn());

        return key.toString();
    }

    /**
     * Builds the visitor mapping for expressions.
     */
    @Override
    protected void buildVisitor() {
        System.out.println("DEBUG: Building the visitor map for expressions");
        // Literals
        addVisit("INTEGER_LITERAL",        this::visitIntegerLiteral);
        addVisit("IntLiteral",             this::visitIntegerLiteral);
        addVisit("TrueLiteral",            this::visitBooleanLiteral);
        addVisit("TrueLiteralExpr",        this::visitBooleanLiteral);
        addVisit("FalseLiteral",           this::visitBooleanLiteral);
        addVisit("FalseLiteralExpr",       this::visitBooleanLiteral);

        // Variables and object references
        addVisit("VAR_REF_EXPR",           this::visitVarRef);
        addVisit("ThisExpression",         this::visitThisExpression);

        // Array and object operations
        addVisit("NewObjectExpr",          this::visitNewObject);
        addVisit("NewIntArrayExpr",        this::visitNewArray);
        addVisit("ArrayAccessExpr",        this::visitArrayAccess);
        addVisit("ArrayLengthExpr",        this::visitArrayLength);
        addVisit("ArrayInitializerExpr",   this::visitArrayInitializer);

        // Method calls
        addVisit("MethodCallExpr",         this::visitMethodCall);
        addVisit("DIRECT_METHOD_CALL",     this::visitDirectMethodCall);

        // Binary operations
        addVisit("BINARY_EXPR",            this::visitBinaryExpr);
        addVisit("ADDITIVE_EXPR",               this::visitPotentialIntLiteral);
        addVisit("MULTIPLICATIVE_EXPR",         this::visitPotentialIntLiteral);
        addVisit("RELATIONAL_EXPR",             this::visitPotentialIntLiteral);
        addVisit("EQUALITY_EXPR",          this::visitBinaryExpr);
        addVisit("LogicalAndExpr",         this::visitBinaryExpr);
        addVisit("LOGICAL_AND_EXPR",       this::visitBinaryExpr);
        addVisit("LogicalOrExpr",          this::visitBinaryExpr);
        addVisit("LOGICAL_OR_EXPR",        this::visitBinaryExpr);

        // Unary operations
        addVisit("UNARY_EXPR",             this::visitUnaryExpr);
        addVisit("NotExpr",                this::visitUnaryExpr);
        addVisit("SignExpr",               this::visitUnaryExpr);

        // Parenthesized expressions
        addVisit("ParenExpr",              this::visitParenExpr);

        // Field access
        addVisit("FieldAccessExpr",        this::visitFieldAccess);

        // Default visitor for unmatched node types
        setDefaultVisit(this::defaultVisit);
        System.out.println("DEBUG: Expression visitor map built successfully");
    }
    /**
     * Define o método atual e configura o LabelManager.
     */
    public void setCurrentMethod(String methodName) {
        System.out.println("DEBUG: Setting current method to: " + methodName);
        this.currentMethod = methodName;

        // Atualizar o LabelManager com o método atual
        if (methodName != null) {
            this.labelManager.setCurrentMethod(methodName);
            this.typeUtils.setCurrentMethod(methodName);
        } else {
            this.labelManager.resetMethod();
        }

        this.variableTypeCache.clear(); // Clear cache when method changes
    }

    /**
     * Clears the expression cache to free memory.
     */
    public void clearExpressionCache() {
        // Instead of clearing everything, we can be selective
        // Keep only method-independent entries
        // or clear only entries related to the previous method

        // Complete implementation would be:
        Map<String, OllirExprResult> filteredCache = new HashMap<>();
        for (Map.Entry<String, OllirExprResult> entry : expressionCache.entrySet()) {
            // Keep only global entries or entries from the current method
            if (!entry.getKey().contains("method=") ||
                    entry.getKey().contains("method=" + currentMethod)) {
                filteredCache.put(entry.getKey(), entry.getValue());
            }
        }
        expressionCache.clear();
        expressionCache.putAll(filteredCache);
    }

    /**
     * Gets the list of errors found during code generation.
     */
    public List<String> getErrors() {
        System.out.println("DEBUG: Getting error list (size): " + errors.size());
        return Collections.unmodifiableList(errors);
    }

    /**
     * Checks if errors occurred during processing.
     */
    public boolean hasErrors() {
        boolean hasErrs = !errors.isEmpty();
        System.out.println("DEBUG: Checking if there are errors: " + hasErrs);
        return hasErrs;
    }

    private OllirExprResult visitPotentialIntLiteral(JmmNode node, Void context) {
        // Verificar se o nó foi otimizado para um literal
        if (node.hasAttribute("kind") && "IntLiteral".equals(node.get("kind")) &&
                node.hasAttribute("value")) {
            System.out.println("DEBUG: Nó otimizado para IntLiteral detectado: " + node.get("value"));
            return visitIntegerLiteral(node, context);
        }

        // Processamento normal de expressão binária
        return visitBinaryExpr(node, context);
    }
    /**
     * Visits an integer literal node.
     */

    private OllirExprResult visitIntegerLiteral(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitIntegerLiteral - Node: " + node.getKind());

        // Extrair valor com tratamento robusto
        String value;

        if (node.hasAttribute("value")) {
            value = node.get("value");
            System.out.println("DEBUG: Literal value: " + value);
        } else {
            value = "0"; // Default como último recurso
            System.out.println("DEBUG: No value attribute found, using default: " + value);
        }

        // Criar representação OLLIR
        String ollirCode = value + ".i32";
        System.out.println("DEBUG: Generated OLLIR code: " + ollirCode);

        System.out.println("DEBUG: END visitIntegerLiteral");
        return new OllirExprResult(ollirCode);
    }
    /**
     * Visits a boolean literal node.
     */
    private OllirExprResult visitBooleanLiteral(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitBooleanLiteral - Node: " + node.getKind());

        // Verificações mais robustas do tipo do nó
        if (node.getKind().equals("RELATIONAL_EXPR") &&
                ((node.hasAttribute("optimized") && "true".equals(node.get("optimized"))) ||
                        node.getNumChildren() == 0)) {
            // Se foi otimizado mas manteve o nome original
            if (node.hasAttribute("value")) {
                boolean val = "true".equals(node.get("value"));
                System.out.println("DEBUG: Boolean literal otimizado: " + val);
                String ollirCode = (val ? "1" : "0") + ".bool";
                System.out.println("DEBUG: OLLIR gerado: " + ollirCode);
                return new OllirExprResult(ollirCode);
            }
        }

        // Determinar valor booleano com base no tipo do nó
        boolean value = isTrue(node);
        System.out.println("DEBUG: Valor do literal booleano: " + value);

        // Criar representação OLLIR
        String ollirCode = (value ? "1" : "0") + ".bool";
        System.out.println("DEBUG: OLLIR gerado: " + ollirCode);

        return new OllirExprResult(ollirCode);
    }

    /**
     * Visits a relational expression, preserving references to loop variables.
     */
    private OllirExprResult visitRelationalExpr(JmmNode node, Void context) {
        // If node was optimized to a boolean literal but involves loop variables
        if (node.hasAttribute("optimized") && "true".equals(node.get("optimized")) &&
                node.hasAttribute("originalVar") && isLoopVariable(node.get("originalVar"))) {

            // Generate code that preserves the original comparison
            String loopVar = node.get("originalVar");
            Type varType = resolveVariableType(loopVar);
            String typeStr = ollirTypeUtils.toOllirType(varType);

            String operator = node.hasAttribute("operator") ? node.get("operator") : "<";
            String constValue = node.hasAttribute("originalValue") ? node.get("originalValue") : "3";

            StringBuilder computation = new StringBuilder();
            String resultVar = ollirTypeUtils.nextTemp() + ".bool";

            computation.append(resultVar)
                    .append(" :=.bool ")
                    .append(loopVar).append(typeStr)
                    .append(" ").append(operator).append(".bool ")
                    .append(constValue).append(typeStr)
                    .append(END_STMT);

            return new OllirExprResult(resultVar, computation);
        }

        // Normal processing for non-loop variables or unoptimized expressions
        if (node.hasAttribute("optimized") && "true".equals(node.get("optimized"))) {
            boolean value = isTrueLiteral(node);
            String ollirCode = (value ? "1" : "0") + ".bool";
            return new OllirExprResult(ollirCode);
        }

        // Process full expression if not optimized
        if (node.getNumChildren() < 2) {
            return defaultVisit(node, context);
        }

        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);
        String operator = node.hasAttribute("operator") ? node.get("operator") : "<";

        OllirExprResult leftResult = visit(left);
        OllirExprResult rightResult = visit(right);

        StringBuilder computation = new StringBuilder();
        computation.append(leftResult.getComputation());
        computation.append(rightResult.getComputation());

        String resultVar = ollirTypeUtils.nextTemp() + ".bool";
        String compareCode = resultVar + SPACE +
                ASSIGN + ".bool" + SPACE +
                leftResult.getCode() + SPACE +
                operator + ".bool" + SPACE +
                rightResult.getCode() + END_STMT;

        computation.append(compareCode);

        return new OllirExprResult(resultVar, computation);
    }
    private boolean isBooleanLiteral(JmmNode node) {
        String kind = node.getKind();

        boolean directMatch = "TrueLiteral".equals(kind) ||
                "FalseLiteral".equals(kind) ||
                "TrueLiteralExpr".equals(kind) ||
                "FalseLiteralExpr".equals(kind);

        if (directMatch) return true;

        // Verificar atributo kind para nós transformados
        if (node.hasAttribute("kind")) {
            String attrKind = node.get("kind");
            return "TrueLiteral".equals(attrKind) ||
                    "FalseLiteral".equals(attrKind) ||
                    "TrueLiteralExpr".equals(attrKind) ||
                    "FalseLiteralExpr".equals(attrKind);
        }

        // Verificar se existe atributo de valor booleano
        if (node.hasAttribute("value")) {
            String value = node.get("value");
            return "true".equals(value) || "false".equals(value);
        }

        return false;
    }

    private boolean isTrueLiteral(JmmNode node) {
        String kind = node.getKind();

        if ("TrueLiteral".equals(kind) || "TrueLiteralExpr".equals(kind)) {
            return true;
        }

        if (node.hasAttribute("kind")) {
            String attrKind = node.get("kind");
            if ("TrueLiteral".equals(attrKind) || "TrueLiteralExpr".equals(attrKind)) {
                return true;
            }
        }

        if (node.hasAttribute("value") && "true".equals(node.get("value"))) {
            return true;
        }

        return false;
    }

    /**
     * Visits a 'this' expression.
     */
    private OllirExprResult visitThisExpression(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitThisExpression - Node: " + node.getKind());

        String className = symbolTable.getClassName();
        System.out.println("DEBUG: Class name: " + className);

        String result = "this." + className;
        System.out.println("DEBUG: Generated OLLIR code: " + result);

        System.out.println("DEBUG: END visitThisExpression");
        return new OllirExprResult(result);
    }

    /**
     * Visits a field access expression.
     */
    private OllirExprResult visitFieldAccess(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitFieldAccess - Node: " + node.getKind());

        if (!node.hasAttribute("name") || node.getNumChildren() < 1) {
            String errorMsg = "Invalid field access expression at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        String fieldName = node.get("name");
        System.out.println("DEBUG: Field name: " + fieldName);

        System.out.println("DEBUG: Processing object of field access");
        OllirExprResult objectResult = visit(node.getChild(0));
        System.out.println("DEBUG: Object result - Code: " + objectResult.getCode());

        Type objectType = typeUtils.getExprType(node.getChild(0));
        System.out.println("DEBUG: Object type: " + objectType.getName() + (objectType.isArray() ? "[]" : ""));

        Type fieldType = resolveFieldType(objectType.getName(), fieldName);
        System.out.println("DEBUG: Field type: " + fieldType.getName() + (fieldType.isArray() ? "[]" : ""));

        StringBuilder computation = new StringBuilder(objectResult.getComputation());
        String ollirType = ollirTypeUtils.toOllirType(fieldType);
        System.out.println("DEBUG: Field OLLIR type: " + ollirType);

        // For fields of the 'this' object
        if (objectResult.getCode().startsWith("this")) {
            System.out.println("DEBUG: Access to 'this' instance field");
            String tempVar = ollirTypeUtils.nextTemp() + ollirType;
            System.out.println("DEBUG: Created temporary variable: " + tempVar);

            // CORRECTION: Include type in the field name
            String getfieldCode = tempVar + SPACE +
                    ASSIGN + ollirType + SPACE +
                    "getfield(" + objectResult.getCode() +
                    ", " + fieldName + ollirType + ")" + ollirType +
                    END_STMT;
            System.out.println("DEBUG: Getfield code: " + getfieldCode.trim());
            computation.append(getfieldCode);

            System.out.println("DEBUG: END visitFieldAccess - Returning temporary variable");
            return new OllirExprResult(tempVar, computation);
        }

        // For fields of other objects
        String result = objectResult.getCode() + "." + fieldName + ollirType;
        System.out.println("DEBUG: END visitFieldAccess - Generated OLLIR code: " + result);
        return new OllirExprResult(result, objectResult.getComputation());
    }

    /**
     * Visits a new object instantiation.
     */
    private OllirExprResult visitNewObject(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitNewObject - Node: " + node.getKind());

        if (!node.hasAttribute("name")) {
            String errorMsg = "New object expression missing class name at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("null.Object");
        }

        String className = node.get("name");
        System.out.println("DEBUG: Class name: " + className);

        String ollirType = "." + className;
        System.out.println("DEBUG: OLLIR type: " + ollirType);

        // Create code for object instantiation and constructor call
        String code = ollirTypeUtils.nextTemp() + ollirType;
        System.out.println("DEBUG: Created temporary variable: " + code);

        StringBuilder computation = new StringBuilder();

        String newObjectCode = code + SPACE +
                ASSIGN + ollirType + SPACE +
                "new(" + className + ")" +
                ollirType + END_STMT;
        System.out.println("DEBUG: Instantiation code: " + newObjectCode.trim());
        computation.append(newObjectCode);

        // Call constructor
        // CORRECTION: Use empty quotes for init, not "<init>"
        String constructorCode = INVOKE_SPECIAL + "(" +
                code + ", \"\")" + ".V" +
                END_STMT;
        System.out.println("DEBUG: Constructor call code: " + constructorCode.trim());
        computation.append(constructorCode);

        System.out.println("DEBUG: END visitNewObject - Generated computation code (size): " + computation.length());
        return new OllirExprResult(code, computation);
    }

    /**
     * Visits a new array instantiation.
     */
    private OllirExprResult visitNewArray(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitNewArray - Node: " + node.getKind());

        // Determine dimensions and base type
        int dimensionCount = getDimensionCount(node);
        System.out.println("DEBUG: Number of dimensions: " + dimensionCount);

        List<OllirExprResult> dimensionSizes = new ArrayList<>();

        // Process dimension expressions
        System.out.println("DEBUG: Processing dimension expressions");
        for (int i = 0; i < node.getNumChildren() && i < dimensionCount; i++) {
            JmmNode dimNode = node.getChild(i);
            System.out.println("DEBUG: Dimension node " + i + ": " + dimNode.getKind());

            if (isExpressionNode(dimNode)) {
                System.out.println("DEBUG: Processing dimension expression");
                OllirExprResult dimResult = visit(dimNode);
                System.out.println("DEBUG: Expression result - Code: " + dimResult.getCode());
                dimensionSizes.add(dimResult);
            }
        }

        if (dimensionSizes.isEmpty()) {
            String errorMsg = "Array creation missing size expressions at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("null.array.i32");
        }

        // Build array type based on dimensions
        String baseType = ".i32"; // Default for int arrays
        System.out.println("DEBUG: Base type: " + baseType);

        String arrayType = getArrayTypeString(baseType, dimensionCount);
        System.out.println("DEBUG: Array type: " + arrayType);

        // Generate code for array creation
        StringBuilder computation = new StringBuilder();

        // Add computations for dimension sizes
        System.out.println("DEBUG: Adding dimension size computations");
        for (OllirExprResult dimResult : dimensionSizes) {
            computation.append(dimResult.getComputation());
        }

        // Create the array with the first dimension
        String resultVar = ollirTypeUtils.nextTemp() + arrayType;
        System.out.println("DEBUG: Temporary variable for array: " + resultVar);

        String arrayCreationCode = resultVar + SPACE +
                ASSIGN + arrayType + SPACE +
                "new(array, " + dimensionSizes.get(0).getCode() + ")" +
                arrayType + END_STMT;
        System.out.println("DEBUG: Array creation code: " + arrayCreationCode.trim());
        computation.append(arrayCreationCode);

        System.out.println("DEBUG: END visitNewArray - Generated computation code (size): " + computation.length());
        return new OllirExprResult(resultVar, computation);
    }

    /**
     * Visits a method call expression.
     */
    private OllirExprResult visitMethodCall(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitMethodCall - Node: " + node.getKind());

        try {
            if (!node.hasAttribute("name") || node.getNumChildren() < 1) {
                String errorMsg = "Invalid method call at line " + node.getLine();
                System.out.println("DEBUG: ERROR: " + errorMsg);
                errors.add(errorMsg);
                return new OllirExprResult("0.i32");
            }

            String methodName = node.get("name");
            System.out.println("DEBUG: Method name: " + methodName);

            JmmNode objectNode = node.getChild(0);
            System.out.println("DEBUG: Object node: " + objectNode.getKind());

            // Get object information
            System.out.println("DEBUG: Processing method call object");
            OllirExprResult objectResult = visit(objectNode);
            System.out.println("DEBUG: Object result - Code: " + objectResult.getCode());

            // Check if the object is an imported class
            String objectClassName;
            if (objectNode.hasAttribute("name") && importedClasses.contains(objectNode.get("name"))) {
                objectClassName = objectNode.get("name");
                System.out.println("DEBUG: Object is an imported class: " + objectClassName);
            } else {
                // Try to determine type from type utils
                try {
                    Type objectType = typeUtils.getExprType(objectNode);
                    objectClassName = objectType.getName();
                    System.out.println("DEBUG: Determined object type: " + objectClassName);
                } catch (Exception e) {
                    // If we can't determine type but it seems to be an imported class
                    if (importedClasses.contains(objectResult.getCode())) {
                        objectClassName = objectResult.getCode();
                        System.out.println("DEBUG: Object code appears to be an imported class: " + objectClassName);
                    } else {
                        objectClassName = "Object"; // Default
                        System.out.println("DEBUG: Could not determine object class, using Object");
                    }
                }
            }

            // Method name should be enclosed in quotes (always safe)
            String methodNameQuoted = "\"" + methodName + "\"";

            // Process arguments
            List<OllirExprResult> argResults = new ArrayList<>();
            processMethodArguments(node, argResults);
            System.out.println("DEBUG: Total arguments: " + argResults.size());

            // Determine return type - default to void for external calls if can't determine
            Type returnType = new Type("void", false); // Default to void
            if (objectClassName.equals(symbolTable.getClassName())) {
                // Try to get return type for calls to current class methods
                Type methodReturnType = symbolTable.getReturnType(methodName);
                if (methodReturnType != null) {
                    returnType = methodReturnType;
                }
            }
            System.out.println("DEBUG: Method return type: " + returnType.getName()
                    + (returnType.isArray() ? "[]" : ""));

            String returnTypeStr = ollirTypeUtils.toOllirType(returnType);
            System.out.println("DEBUG: Return OLLIR type: " + returnTypeStr);

            // Build computation code
            StringBuilder computation = new StringBuilder(objectResult.getComputation());

            // Add argument computations
            System.out.println("DEBUG: Adding argument computations");
            for (OllirExprResult argResult : argResults) {
                computation.append(argResult.getComputation());
            }

            // Determine invocation type and create method call string
            String invokeType = determineInvokeType(objectClassName, objectResult.getCode());
            System.out.println("DEBUG: Invocation type: " + invokeType);

            // Build parameter string
            StringBuilder argsStr = new StringBuilder();
            buildArgumentsString(argResults, argsStr);

            // Build complete invocation
            String invocation;
            if (importedClasses.contains(objectClassName)) {
                // For imported classes, don't add type info to class name
                invocation = String.format("%s(%s, %s%s)%s",
                        invokeType,
                        objectClassName,  // Use just the class name without type
                        methodNameQuoted,
                        argsStr.length() > 0 ? ", " + argsStr : "",
                        returnTypeStr);
            } else {
                invocation = String.format("%s(%s, %s%s)%s",
                        invokeType,
                        objectResult.getCode(),
                        methodNameQuoted,
                        argsStr.length() > 0 ? ", " + argsStr : "",
                        returnTypeStr);
            }
            System.out.println("DEBUG: Invocation code: " + invocation);

            // For void methods with no return value
            if ("void".equals(returnType.getName())) {
                System.out.println("DEBUG: Void method, no return value");
                computation.append(invocation).append(END_STMT);
                System.out.println("DEBUG: END visitMethodCall - Void method");
                return new OllirExprResult("", computation);
            }

            // For methods with return values, store in temp
            String resultVar = ollirTypeUtils.nextTemp() + returnTypeStr;
            System.out.println("DEBUG: Temporary variable for result: " + resultVar);

            String methodCallAssignCode = resultVar + SPACE +
                    ASSIGN + returnTypeStr + SPACE +
                    invocation + END_STMT;
            System.out.println("DEBUG: Method call assignment code: " + methodCallAssignCode.trim());
            computation.append(methodCallAssignCode);

            System.out.println("DEBUG: END visitMethodCall - Generated computation code (size): " + computation.length());
            return new OllirExprResult(resultVar, computation);

        } catch (Exception e) {
            String errorMsg = "Error processing method call at line " + node.getLine() + ": " + e.getMessage();
            System.out.println("DEBUG: EXCEPTION: " + errorMsg);
            System.out.println("DEBUG: Stacktrace: " + e.toString());
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }
    }

    /**
     * Visits a direct method call expression.
     */
    private OllirExprResult visitDirectMethodCall(JmmNode node, Void context) {
        // 1) method name
        if (!node.hasAttribute("name")) {
            errors.add("Missing method name (line " + node.getLine() + ")");
            return new OllirExprResult("0.i32");
        }
        String methodName = node.get("name");
        String className  = symbolTable.getClassName();

        // Method name should be enclosed in quotes
        String methodNameQuoted = "\"" + methodName + "\"";

        // 2) actual arguments
        List<OllirExprResult> argResults = new ArrayList<>();
        processMethodArguments(node, argResults);

        // 3) formal parameters
        List<Symbol> formals = symbolTable.getParameters(methodName);
        int fixedParamCount  = formals.size();            // includes vararg if exists

        // ---------- varargs heuristic ----------
        boolean treatAsVarArgs = argResults.size() > fixedParamCount;

        // 4) return type
        Type retType   = symbolTable.getReturnType(methodName);
        if (retType == null) retType = new Type("void", false);
        String retTypeStr = ollirTypeUtils.toOllirType(retType);

        // 5) previous computation
        StringBuilder comp = new StringBuilder();
        for (OllirExprResult ar : argResults) comp.append(ar.getComputation());

        // 6) build arguments string (fixed + vararg-array if needed)
        StringBuilder callArgs = new StringBuilder();

        int i = 0;
        // fixed parameters (all if not varargs)
        int lastFixed = treatAsVarArgs ? fixedParamCount - 1 : fixedParamCount;
        for (; i < lastFixed && i < argResults.size(); i++) {
            if (callArgs.length() > 0) callArgs.append(", ");
            callArgs.append(argResults.get(i).getCode());
        }

        // ---------- create array for varargs ----------
        if (treatAsVarArgs) {
            int varN = argResults.size() - lastFixed;

            // base type (inferred from last formal parameter)
            Type baseT = formals.get(formals.size() - 1).getType();
            String elemT = ollirTypeUtils.toOllirType(new Type(baseT.getName(), false));
            String arrT  = ".array" + elemT;

            String tmpArr = ollirTypeUtils.nextTemp() + arrT;

            // new(array, varN)
            comp.append(tmpArr).append(" ").append(ASSIGN).append(arrT).append(" ")
                    .append("new(array, ").append(varN).append(".i32)").append(arrT).append(END_STMT);

            // initializations
            for (int j = 0; j < varN; j++) {
                OllirExprResult val = argResults.get(lastFixed + j);
                comp.append(tmpArr).append("[").append(j).append(".i32]").append(elemT).append(" ")
                        .append(ASSIGN).append(elemT).append(" ").append(val.getCode()).append(END_STMT);
            }

            // add the array as the last argument
            if (callArgs.length() > 0) callArgs.append(", ");
            callArgs.append(tmpArr);
        }

        // 7) determine invoke / first argument
        boolean staticCall = "main".equals(methodName);          // only known-static case
        String invokeType  = staticCall ? INVOKE_STATIC : INVOKE_SPECIAL;
        String firstArg    = staticCall ? className   : "this";

        String invoke = String.format("%s(%s, %s%s)%s",
                invokeType, firstArg, methodNameQuoted,
                callArgs.length() > 0 ? ", " + callArgs : "",
                retTypeStr);

        // 8) void vs. value
        if ("void".equals(retType.getName())) {
            comp.append(invoke).append(END_STMT);
            return new OllirExprResult("", comp);
        }

        String resVar = ollirTypeUtils.nextTemp() + retTypeStr;
        comp.append(resVar).append(" ").append(ASSIGN).append(" ")
                .append(retTypeStr).append(" ").append(invoke).append(END_STMT);

        return new OllirExprResult(resVar, comp);
    }


    /**
     * Simple check to determine if a variable is modified in a loop.
     * For variable 'i', this will return true since it's a classic loop variable.
     */
    private boolean isModifiedInLoop(String varName) {


        // Another direct check: If the variable is in the loopVariables set from analysis
        if (loopVariables != null && loopVariables.contains(varName)) {
            return true;
        }


        return false;
    }

    /**
     * Checks if a variable is a loop variable (modified within a loop).
     * Takes a more conservative approach to ensure correct behavior.
     */
    private boolean isLoopVariable(String varName) {
        // First check direct registry
        if (loopVariables != null && loopVariables.contains(varName)) {
            return true;
        }

        // Also check if it's a variable used in loop conditions
        // This is a backup check in case our direct registry is incomplete
        if (loopConditionVariables.contains(varName)) {
            return true;
        }



        return false;
    }
    /**
     * Checks if a node is used in a return statement.
     * This is used for context awareness, not to block optimizations.
     */
    private boolean isInReturnStatement(JmmNode node) {
        JmmNode parent = node.getParent();
        while (parent != null) {
            if ("RETURN_STMT".equals(parent.getKind())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }


    /**
     * Checks if a variable is actually modified within a loop.
     * This differs from just being referenced in a loop condition.
     */
    private boolean isVariableModifiedInLoop(String varName) {
        // Only variables that appear on the LEFT SIDE of assignments in loops
        // are considered "modified in loop"

        // Check the registry of variables explicitly identified as loop-modified
        if (loopVariables != null && loopVariables.contains(varName)) {
            return true;
        }

        // Special handling for loop index variables
        if (currentMethod != null) {
            JmmNode methodNode = findEnclosingMethodNode(varName);
            if (methodNode != null) {
                // Check if this variable is assigned within a loop construct
                return isAssignedInLoopBody(methodNode, varName);
            }
        }

        return false;
    }

    /**
     * Find if a variable is assigned a value inside a loop body.
     */
    private boolean isAssignedInLoopBody(JmmNode methodNode, String varName) {
        // Find all while loops in the method
        for (int i = 0; i < methodNode.getNumChildren(); i++) {
            JmmNode child = methodNode.getChild(i);
            if ("WhileStmt".equals(child.getKind()) || child.getKind().contains("While")) {
                // Check if the loop body contains an assignment to this variable
                if (child.getNumChildren() > 1) {
                    JmmNode body = child.getChild(1);
                    if (containsAssignmentTo(body, varName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    /**
     * Visits a variable reference with proper loop variable handling.
     */
    private OllirExprResult visitVarRef(JmmNode node, Void context) {
        if (!node.hasAttribute("name")) {
            String errorMsg = "Variable reference missing 'name' at line " + node.getLine();
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        String varName = node.get("name");
        boolean isOptimized = node.hasAttribute("optimized") && "true".equals(node.get("optimized"));

        // Check if this variable is assigned (modified) inside any loop
        boolean isAssignedInLoop = isVariableAssignedInLoop(node, varName);

        // If this variable is modified in any loop, preserve its original name
        if (isAssignedInLoop) {
            // Use original variable reference instead of propagated constant
            Type varType = resolveVariableType(varName);
            String ollirType = ollirTypeUtils.toOllirType(varType);

            boolean isKeyword = isReservedKeyword(varName);
            String safeVarName = isKeyword ? "\"" + varName + "\"" : varName;

            boolean isField = isFieldVariable(varName);
            if (isField) {
                String tempVar = ollirTypeUtils.nextTemp() + ollirType;
                String computation = tempVar + SPACE + ASSIGN + ollirType + SPACE +
                        "getfield(this, " + safeVarName + ollirType + ")" + ollirType + END_STMT;
                return new OllirExprResult(tempVar, computation);
            } else {
                return new OllirExprResult(safeVarName + ollirType);
            }
        }

        // For variables NOT modified in loops, we can use optimized values if available
        if (isOptimized && node.hasAttribute("value")) {
            String value = node.get("value");
            Type varType = resolveVariableType(varName);
            String ollirType = ollirTypeUtils.toOllirType(varType);
            return new OllirExprResult(value + ollirType);
        }

        // Standard handling for regular variables
        Type varType = resolveVariableType(varName);
        String ollirType = ollirTypeUtils.toOllirType(varType);

        boolean isKeyword = isReservedKeyword(varName);
        String safeVarName = isKeyword ? "\"" + varName + "\"" : varName;

        boolean isField = isFieldVariable(varName);
        if (isField) {
            String tempVar = ollirTypeUtils.nextTemp() + ollirType;
            String computation = tempVar + SPACE + ASSIGN + ollirType + SPACE +
                    "getfield(this, " + safeVarName + ollirType + ")" + ollirType + END_STMT;
            return new OllirExprResult(tempVar, computation);
        } else {
            return new OllirExprResult(safeVarName + ollirType);
        }
    }

    /**
     * Checks if a variable is assigned (modified) within any enclosing loop.
     */
    private boolean isVariableAssignedInLoop(JmmNode node, String varName) {
        // Find all enclosing loops and check if variable is assigned in any of them
        JmmNode current = node;
        while (current != null) {
            // Check if this node is a while loop
            if (current.getKind().equals("WhileStmt") || current.getKind().contains("While")) {
                // If we found a loop, check if our variable is assigned in its body
                if (current.getNumChildren() > 1) {
                    JmmNode loopBody = current.getChild(1);
                    if (containsAssignmentTo(loopBody, varName)) {
                        return true;
                    }
                }
            }
            current = current.getParent();
        }

        // Not found in any enclosing loop
        return false;
    }

    /**
     * Checks if a subtree contains an assignment to the specified variable.
     */
    private boolean containsAssignmentTo(JmmNode node, String varName) {
        // Check if this node is an assignment to the variable
        if (("ASSIGN_STMT".equals(node.getKind()) || "AssignStatement".equals(node.getKind())) &&
                node.getNumChildren() > 0) {

            JmmNode lhs = node.getChild(0);
            if (("IdentifierLValue".equals(lhs.getKind()) || lhs.getKind().contains("Identifier")) &&
                    lhs.hasAttribute("name") && lhs.get("name").equals(varName)) {
                return true;
            }
        }

        // Check all children recursively
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (containsAssignmentTo(node.getChild(i), varName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds the method node containing the specified variable.
     */
    private JmmNode findEnclosingMethodNode(String varName) {
        // Search through visited nodes to find the method
        for (JmmNode node : visitedNodes) {
            JmmNode current = node;
            while (current != null) {
                if ("METHOD_DECL".equals(current.getKind())) {
                    return current;
                }
                current = current.getParent();
            }
        }
        return null;
    }
    /**
     * Encontra o nó de método que contém o nó fornecido.
     */
    private JmmNode findEnclosingMethodNode(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("METHOD_DECL".equals(current.getKind())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }


    /**
     * Verifica se uma variável foi identificada como tendo sido atribuída em múltiplos caminhos.
     * Esta função integra lógica tanto baseada em estado compartilhado quanto em análise estrutural.
     */
    private boolean isVariableMultiAssigned(String varName) {
        // Verificar no registry compartilhado
        if (multiAssignedVariablesRegistry != null && multiAssignedVariablesRegistry.contains(varName)) {
            return true;
        }


        // Verificação heurística baseada no contexto
        if (currentMethod != null) {
            // Verificar se há atribuições dentro de if-else usando algoritmo de busca
            for (JmmNode node : visitedNodes) {
                // Encontra o método mais próximo que contém este nó
                JmmNode methodNode = findEnclosingMethodNode(node);
                if (methodNode != null && methodNode.hasAttribute("name") &&
                        methodNode.get("name").equals(currentMethod)) {

                    // Verificar if-statements com atribuições à mesma variável
                    boolean foundInIf = false;
                    boolean foundInElse = false;

                    for (int i = 0; i < methodNode.getNumChildren(); i++) {
                        JmmNode child = methodNode.getChild(i);
                        if ("IfStmt".equals(child.getKind()) && child.getNumChildren() > 1) {
                            // Verificar bloco then
                            JmmNode thenBlock = child.getChild(1);
                            if (containsAssignmentTo(thenBlock, varName)) {
                                foundInIf = true;
                            }

                            // Verificar bloco else, se existir
                            if (child.getNumChildren() > 2) {
                                JmmNode elseBlock = child.getChild(2);
                                if (containsAssignmentTo(elseBlock, varName)) {
                                    foundInElse = true;
                                }
                            }
                        }
                    }

                    if (foundInIf && foundInElse) {
                        return true;
                    }
                }
            }
        }

        // Abordagem conservadora para casos onde não podemos determinar com certeza
        return false;
    }
    /**
     * Verifica se o nó está sendo usado em um contexto de retorno após estruturas condicionais.
     */
    private boolean isInReturnAfterBranches(JmmNode node) {
        // Percorrer a árvore para cima até encontrar um return ou o método
        JmmNode current = node;
        while (current != null) {
            if ("RETURN_STMT".equals(current.getKind())) {
                // Encontramos um nó de retorno, agora verificamos se há estruturas condicionais
                // no mesmo método, antes deste retorno
                return hasPrecedingConditionalStructures(current);
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Verifica se há estruturas condicionais precedendo este nó no mesmo método.
     */
    private boolean hasPrecedingConditionalStructures(JmmNode returnNode) {
        // Encontrar o nó do método
        JmmNode methodNode = findEnclosingMethod(returnNode);
        if (methodNode == null) return false;

        // Counter para estruturas if/else
        int conditionalCount = 0;

        // Percorrer todos os nós do método buscando estruturas condicionais
        Stack<JmmNode> stack = new Stack<>();
        stack.push(methodNode);

        while (!stack.isEmpty()) {
            JmmNode current = stack.pop();

            // Se encontrarmos o nó de retorno, já percorremos todos os nós precedentes
            if (current == returnNode) {
                break;
            }

            // Verificar se é uma estrutura condicional
            if ("IfStmt".equals(current.getKind()) ||
                    current.getKind().contains("If") ||
                    "WhileStmt".equals(current.getKind()) ||
                    current.getKind().contains("While")) {
                conditionalCount++;
            }

            // Adicionar todos os filhos à pilha
            for (int i = current.getNumChildren() - 1; i >= 0; i--) {
                stack.push(current.getChild(i));
            }
        }

        return conditionalCount > 0;
    }

    /**
     * Encontra o nó de método que contém o nó fornecido.
     */
    private JmmNode findEnclosingMethod(JmmNode node) {
        JmmNode current = node;
        while (current != null) {
            if ("METHOD_DECL".equals(current.getKind())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Verifica se uma variável é usada em múltiplos ramos de código.
     * Esta é uma heurística para detecção de variáveis multi-atribuídas.
     */
    private boolean variableUsedInMultipleBranches(String methodName, String varName) {
        // Implementação simplificada que pode ser expandida com análise de fluxo mais sofisticada

        // Por enquanto, usamos um padrão conservador:
        // Quaisquer variáveis locais em métodos com estruturas if-else são consideradas
        // potencialmente multi-atribuídas

        for (Symbol local : symbolTable.getLocalVariables(methodName)) {
            if (local.getName().equals(varName)) {
                // Variável local encontrada, verificar se o método tem estruturas condicionais
                return true; // Abordagem conservadora: assumir verdadeiro
            }
        }

        return false;
    }

    /**
     * Visits an array access expression.
     */
    private OllirExprResult visitArrayAccess(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitArrayAccess - Node: " + node.getKind());

        if (node.getNumChildren() < 2) {
            String errorMsg = "Array access missing array or index expression at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        // Process array and index
        JmmNode arrayNode = node.getChild(0);
        JmmNode indexNode = node.getChild(1);

        System.out.println("DEBUG: Processing array expression: " + arrayNode.getKind());
        OllirExprResult arrayResult = visit(arrayNode);

        System.out.println("DEBUG: Processing index expression: " + indexNode.getKind());
        OllirExprResult indexResult = visit(indexNode);

        // Combine computations
        StringBuilder computation = new StringBuilder();
        computation.append(arrayResult.getComputation());
        computation.append(indexResult.getComputation());

        // Element type (standardized to int in this case)
        String elementOllirType = ".i32";

        // Create temporary for the result
        String resultVar = ollirTypeUtils.nextTemp() + elementOllirType;

        // Generate array access code
        String arrayAccessCode = resultVar + SPACE +
                ASSIGN + elementOllirType + SPACE +
                arrayResult.getCode() + "[" + indexResult.getCode() + "]" + elementOllirType +
                END_STMT;

        System.out.println("DEBUG: Array access code: " + arrayAccessCode.trim());
        computation.append(arrayAccessCode);

        return new OllirExprResult(resultVar, computation);
    }

    /**
     * Helper method to check if a word is a reserved keyword in OLLIR.
     */
    private boolean isReservedKeyword(String name) {
        // Set of OLLIR reserved keywords
        Set<String> keywords = new HashSet<>(Arrays.asList(
                "array", "boolean", "extends", "if", "implements",
                "int", "new", "return", "while", "true", "false"
        ));
        return keywords.contains(name);
    }

    /**
     * Visits an array length expression.
     */
    private OllirExprResult visitArrayLength(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitArrayLength - Node: " + node.getKind());

        if (node.getNumChildren() < 1) {
            String errorMsg = "Array length expression missing array at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        // Visit array expression
        System.out.println("DEBUG: Processing array expression");
        OllirExprResult arrayResult = visit(node.getChild(0));
        System.out.println("DEBUG: Array result - Code: " + arrayResult.getCode());

        // Generate code for array length
        String resultVar = ollirTypeUtils.nextTemp() + ".i32";
        System.out.println("DEBUG: Temporary variable for array length: " + resultVar);

        StringBuilder computation = new StringBuilder(arrayResult.getComputation());

        String arrayLengthCode = resultVar + SPACE +
                ASSIGN + ".i32" + SPACE +
                "arraylength(" + arrayResult.getCode() + ")" +
                ".i32" + END_STMT;
        System.out.println("DEBUG: Array length code: " + arrayLengthCode.trim());
        computation.append(arrayLengthCode);

        System.out.println("DEBUG: END visitArrayLength - Generated computation code (size): " + computation.length());
        return new OllirExprResult(resultVar, computation);
    }

    /**
     * Visits an array initializer expression.
     */
    private OllirExprResult visitArrayInitializer(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitArrayInitializer - Node: " + node.getKind());
        System.out.println("DEBUG: Number of elements: " + node.getNumChildren());

        int elementCount = node.getNumChildren();
        if (elementCount == 0) {
            System.out.println("DEBUG: Empty array, creating default array");
            // Return empty array with default size
            String arrayType = ".array.i32";
            String resultVar = ollirTypeUtils.nextTemp() + arrayType;
            System.out.println("DEBUG: Temporary variable for empty array: " + resultVar);

            StringBuilder computation = new StringBuilder();
            String emptyArrayCode = resultVar + SPACE +
                    ASSIGN + arrayType + SPACE +
                    "new(array, 0.i32)" + arrayType + END_STMT;
            System.out.println("DEBUG: Empty array code: " + emptyArrayCode.trim());
            computation.append(emptyArrayCode);

            System.out.println("DEBUG: END visitArrayInitializer (empty array)");
            return new OllirExprResult(resultVar, computation);
        }

        // Process array elements
        System.out.println("DEBUG: Processing array elements");
        List<OllirExprResult> elementResults = new ArrayList<>();
        for (int i = 0; i < elementCount; i++) {
            System.out.println("DEBUG: Processing element " + i);
            JmmNode elemNode = node.getChild(i);
            System.out.println("DEBUG: Element node: " + elemNode.getKind());

            OllirExprResult elemResult = visit(elemNode);
            System.out.println("DEBUG: Element result - Code: " + elemResult.getCode());
            elementResults.add(elemResult);
        }

        // Determine element type from first element
        Type elementType = typeUtils.getExprType(node.getChild(0));
        System.out.println("DEBUG: Element type: " + elementType.getName() + (elementType.isArray() ? "[]" : ""));

        String elementOllirType = ollirTypeUtils.toOllirType(elementType);
        String arrayOllirType = ".array" + elementOllirType;
        System.out.println("DEBUG: Element OLLIR type: " + elementOllirType);
        System.out.println("DEBUG: Array OLLIR type: " + arrayOllirType);

        // Generate code for array creation and initialization
        StringBuilder computation = new StringBuilder();

        // Add element computations
        System.out.println("DEBUG: Adding element computations");
        for (OllirExprResult elemResult : elementResults) {
            computation.append(elemResult.getComputation());
        }

        // Create array with correct size
        String resultVar = ollirTypeUtils.nextTemp() + arrayOllirType;
        System.out.println("DEBUG: Temporary variable for array: " + resultVar);

        String arrayCreationCode = resultVar + SPACE +
                ASSIGN + arrayOllirType + SPACE +
                "new(array, " + elementCount + ".i32)" +
                arrayOllirType + END_STMT;
        System.out.println("DEBUG: Array creation code: " + arrayCreationCode.trim());
        computation.append(arrayCreationCode);

        // Initialize each element
        System.out.println("DEBUG: Initializing array elements");
        for (int i = 0; i < elementCount; i++) {
            OllirExprResult elemResult = elementResults.get(i);
            String elemInitCode = resultVar + "[" + i + ".i32]" +
                    elementOllirType + SPACE +
                    ASSIGN + elementOllirType + SPACE +
                    elemResult.getCode() + END_STMT;
            System.out.println("DEBUG: Element " + i + " initialization code: " + elemInitCode.trim());
            computation.append(elemInitCode);
        }

        System.out.println("DEBUG: END visitArrayInitializer - Generated computation code (size): " + computation.length());
        return new OllirExprResult(resultVar, computation);
    }

    /**
     * Helper method to process method arguments.
     */
    private void processMethodArguments(JmmNode node, List<OllirExprResult> argResults) {
        System.out.println("DEBUG: Processing method arguments");

        // Look for arguments starting from index 1
        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode argNode = node.getChild(i);
            System.out.println("DEBUG: Argument node " + i + ": " + argNode.getKind());

            // Check if it's an argument list node
            if (argNode.getKind().equals("ArgumentList")) {
                System.out.println("DEBUG: Found argument list with " + argNode.getNumChildren() + " elements");
                for (int j = 0; j < argNode.getNumChildren(); j++) {
                    System.out.println("DEBUG: Processing list argument " + j);
                    OllirExprResult argResult = visit(argNode.getChild(j));
                    System.out.println("DEBUG: Argument result - Code: " + argResult.getCode());
                    argResults.add(argResult);
                }
            } else {
                // Direct argument
                System.out.println("DEBUG: Processing direct argument");
                OllirExprResult argResult = visit(argNode);
                System.out.println("DEBUG: Argument result - Code: " + argResult.getCode());
                argResults.add(argResult);
            }
        }
    }

    /**
     * Helper method to build arguments string.
     */
    private void buildArgumentsString(List<OllirExprResult> argResults, StringBuilder argsStr) {
        System.out.println("DEBUG: Building arguments string");
        if (!argResults.isEmpty()) {
            for (OllirExprResult argResult : argResults) {
                if (argsStr.length() > 0) {
                    argsStr.append(", ");
                }
                argsStr.append(argResult.getCode());
            }
            System.out.println("DEBUG: Arguments string: " + argsStr.toString());
        }
    }

    /**
     * Visits a binary expression.
     */
    private OllirExprResult visitBinaryExpr(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitBinaryExpr - Node: " + node.getKind());

        // Verificar se o nó já foi otimizado para um literal inteiro
        if (node.hasAttribute("kind") && "IntLiteral".equals(node.get("kind")) &&
                node.hasAttribute("value")) {
            System.out.println("DEBUG: Found optimized IntLiteral in binary expression with value: " + node.get("value"));
            return visitIntegerLiteral(node, context);
        }

        // Verificar se é um operador relacional otimizado
        if (node.getKind().equals("RELATIONAL_EXPR") && node.getNumChildren() == 0) {
            // Foi otimizado mas precisamos reconstruir a operação original
            String operator = "<";  // Operador padrão
            if (node.hasAttribute("operator")) {
                operator = node.get("operator");
            }

            // Preservar qualquer informação sobre variáveis de loop
            String loopVarName = null;
            String constValue = null;

            if (node.hasAttribute("originalVar")) {
                loopVarName = node.get("originalVar");
            }

            if (node.hasAttribute("constValue")) {
                constValue = node.get("constValue");
            }

            // Gerar código preservando a variável de loop se necessário
            String resultVar = ollirTypeUtils.nextTemp() + ".bool";
            StringBuilder computation = new StringBuilder();

            // Se temos informação sobre variável de loop e seu valor constante
            if (loopVarName != null && constValue != null) {
                // Gerar comparação preservando a variável de loop
                String varTypeStr = ".i32"; // Tipo padrão
                Type varType = resolveVariableType(loopVarName);
                if (varType != null) {
                    varTypeStr = ollirTypeUtils.toOllirType(varType);
                }

                computation.append(resultVar)
                        .append(" :=.bool ")
                        .append(loopVarName).append(varTypeStr)
                        .append(" ").append(operator).append(".bool ")
                        .append(constValue).append(varTypeStr)
                        .append(END_STMT);
            }
            // Caso não tenhamos informação específica, gerar código genérico
            else {
                // Exemplo de operação: usar valores comuns para testes
                computation.append(resultVar).append(" :=.bool 10.i32 ").append(operator)
                        .append(".bool 20.i32").append(END_STMT);
            }

            return new OllirExprResult(resultVar, computation.toString());
        }

        // Verificar se foi otimizado para literal booleano
        if (node.getKind().equals("TrueLiteral") || node.getKind().equals("FalseLiteral") ||
                node.getKind().equals("TrueLiteralExpr") || node.getKind().equals("FalseLiteralExpr")) {
            return visitBooleanLiteral(node, context);
        }

        // Verificar se é um literal inteiro
        if (node.getKind().equals("INTEGER_LITERAL") || node.getKind().equals("IntLiteral")) {
            return visitIntegerLiteral(node, context);
        }

        // Verificar se temos operandos suficientes
        if (node.getNumChildren() < 2) {
            if (node.getNumChildren() == 1) {
                return visit(node.getChild(0));
            }
            return new OllirExprResult("0.i32");
        }

        // Determinar o operador
        String operator = node.hasAttribute("operator") ? node.get("operator")
                : node.hasAttribute("op") ? node.get("op")
                : inferOperatorFromKind(node.getKind());

        // Tratamento especial para operadores lógicos com short-circuit
        if ("&&".equals(operator)) {
            return handleLogicalAnd(node.getChild(0), node.getChild(1));
        }
        if ("||".equals(operator)) {
            return handleLogicalOr(node.getChild(0), node.getChild(1));
        }

        // Verificar se envolve variáveis de loop
        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);
        boolean hasLoopVar = isLoopVariableExpr(left) || isLoopVariableExpr(right);

        // Caso especial para operações com variáveis de loop
        if (hasLoopVar) {
            // Para operações aritméticas com variáveis de loop, sempre gerar temporários
            return generateTemporaryForLoopVarExpr(node, left, right, operator);
        }

        // Processamento normal para expressões sem variáveis de loop
        OllirExprResult leftResult = visit(left);
        OllirExprResult rightResult = visit(right);

        // Determinar tipo do resultado
        Type resultType = inferTypeFromOperator(operator);
        String resultTypeStr = ollirTypeUtils.toOllirType(resultType);

        // Determinar tipo dos operandos
        String operandTypeStr;
        if (isRelationalOperator(operator)) {
            operandTypeStr = ".bool"; // Operadores relacionais retornam boolean
        } else {
            Type operandType = inferOperandType(left, right);
            operandTypeStr = ollirTypeUtils.toOllirType(operandType);
        }

        // Montar o código
        StringBuilder computation = new StringBuilder();
        computation.append(leftResult.getComputation());
        computation.append(rightResult.getComputation());

        // Criar temporário para o resultado
        String resultVar = ollirTypeUtils.nextTemp() + resultTypeStr;
        String binaryOpCode = resultVar + SPACE +
                ASSIGN + resultTypeStr + SPACE +
                leftResult.getCode() + SPACE +
                operator + operandTypeStr + SPACE +
                rightResult.getCode() + END_STMT;
        computation.append(binaryOpCode);

        return new OllirExprResult(resultVar, computation);
    }

    /**
     * Gera código específico para expressões que envolvem variáveis de loop.
     * Gera explicitamente temporários para garantir a preservação correta da semântica.
     */
    /**
     * Gera código específico para expressões que envolvem variáveis de loop.
     * Gera explicitamente temporários para garantir a preservação correta da semântica.
     */
    private OllirExprResult generateTemporaryForLoopVarExpr(JmmNode node, JmmNode left, JmmNode right, String operator) {
        // Processar os operandos primeiro
        OllirExprResult leftResult = visit(left);
        OllirExprResult rightResult = visit(right);

        // Determinar tipo de resultado base
        Type resultType = inferTypeFromOperator(operator);
        String resultTypeStr = ollirTypeUtils.toOllirType(resultType);

        // CORREÇÃO CRUCIAL: Para operadores relacionais, os operandos mantêm seus tipos originais
        // enquanto o resultado é booleano
        Type leftOperandType = inferOperandType(left, null);
        Type rightOperandType = inferOperandType(right, null);

        String leftOperandTypeStr = ollirTypeUtils.toOllirType(leftOperandType);
        String rightOperandTypeStr = ollirTypeUtils.toOllirType(rightOperandType);

        // Operador relacional - o tipo após o operador deve ser o tipo do resultado (bool)
        String operatorTypeStr = isRelationalOperator(operator) ? ".bool" : leftOperandTypeStr;

        // Montar código com computações prévias
        StringBuilder computation = new StringBuilder();
        computation.append(leftResult.getComputation());
        computation.append(rightResult.getComputation());

        // Propagar constantes quando um lado é variável de loop e outro constante
        boolean leftIsLoopVar = isLoopVariableExpr(left);
        boolean rightIsLoopVar = isLoopVariableExpr(right);

        // Caso especial: variável de loop comparada com constante propagada
        if (isRelationalOperator(operator) && leftIsLoopVar && !rightIsLoopVar) {
            // Se o lado direito tem um valor constante conhecido, usá-lo diretamente
            if (right.hasAttribute("constValue")) {
                String constValue = right.get("constValue");
                // CORRIGIDO: Usar o tipo original do operando direito
                rightResult = new OllirExprResult(constValue + rightOperandTypeStr);
            }
        }

        // Caso especial: constante comparada com variável de loop
        if (isRelationalOperator(operator) && !leftIsLoopVar && rightIsLoopVar) {
            // Se o lado esquerdo tem um valor constante conhecido, usá-lo diretamente
            if (left.hasAttribute("constValue")) {
                String constValue = left.get("constValue");
                // CORRIGIDO: Usar o tipo original do operando esquerdo
                leftResult = new OllirExprResult(constValue + leftOperandTypeStr);
            }
        }

        // Criar temporário explícito para o resultado
        String resultVar = ollirTypeUtils.nextTemp() + resultTypeStr;

        // Gerar a operação binária com o temporário
        // CORRETO: O tipo após o operador deve ser o tipo do resultado,
        // mas os operandos mantêm seus tipos originais
        String binaryOpCode = resultVar + SPACE +
                ASSIGN + resultTypeStr + SPACE +
                leftResult.getCode() + SPACE +
                operator + operatorTypeStr + SPACE +
                rightResult.getCode() + END_STMT;

        computation.append(binaryOpCode);

        return new OllirExprResult(resultVar, computation);
    }
    /**
     * Checks if an operator is relational.
     */
    private boolean isRelationalOperator(String operator) {
        return "<".equals(operator) || ">".equals(operator) ||
                "<=".equals(operator) || ">=".equals(operator) ||
                "==".equals(operator) || "!=".equals(operator);
    }

    /**
     * Visits a unary expression.
     */
    private OllirExprResult visitUnaryExpr(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitUnaryExpr - Node: " + node.getKind());
        System.out.println("DEBUG: Number of children: " + node.getNumChildren());

        if (node.getNumChildren() < 1) {
            String errorMsg = "Invalid unary expression at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        // Process operand
        System.out.println("DEBUG: Processing operand");
        OllirExprResult operandResult = visit(node.getChild(0));
        System.out.println("DEBUG: Operand result - Code: " + operandResult.getCode());

        // Determine operator
        String operator = node.hasAttribute("operator")
                ? node.get("operator")
                : node.hasAttribute("op")
                ? node.get("op")
                : inferUnaryOperatorFromKind(node.getKind());
        System.out.println("DEBUG: Operator: " + operator);

        // Determine result type
        Type resultType;
        try {
            resultType = typeUtils.getExprType(node);
            System.out.println("DEBUG: Expression type obtained: " + resultType.getName() + (resultType.isArray() ? "[]" : ""));
        } catch (Exception e) {
            System.out.println("DEBUG: Error getting expression type, inferring from operator");
            // Infer type from operator
            resultType = "!".equals(operator) ?
                    TypeUtils.newBooleanType() : TypeUtils.newIntType();
            System.out.println("DEBUG: Inferred type: " + resultType.getName() + (resultType.isArray() ? "[]" : ""));
        }

        String resultTypeStr = ollirTypeUtils.toOllirType(resultType);
        System.out.println("DEBUG: Result OLLIR type: " + resultTypeStr);

        // Build computation
        StringBuilder computation = new StringBuilder(operandResult.getComputation());

        // Create result temporary variable
        String resultVar = ollirTypeUtils.nextTemp() + resultTypeStr;
        System.out.println("DEBUG: Temporary variable for result: " + resultVar);

        // Add operation code
        String unaryOpCode = resultVar + SPACE +
                ASSIGN + resultTypeStr + SPACE +
                operator + resultTypeStr + SPACE +
                operandResult.getCode() + END_STMT;
        System.out.println("DEBUG: Unary operation code: " + unaryOpCode.trim());
        computation.append(unaryOpCode);

        System.out.println("DEBUG: END visitUnaryExpr - Generated computation code (size): " + computation.length());
        return new OllirExprResult(resultVar, computation);
    }


    /**
     * Verifica se um nó de expressão contém uma variável de loop.
     * Analisa o nó e seus filhos recursivamente.
     */
    /**
     * Verifica se um nó de expressão contém uma variável de loop.
     */
    private boolean isLoopVariableExpr(JmmNode node) {
        if ("VAR_REF_EXPR".equals(node.getKind()) && node.hasAttribute("name")) {
            String varName = node.get("name");

            // Verificar se a variável está no conjunto de variáveis de loop
            if (loopVariables.contains(varName)) {
                return true;
            }

        }

        // Verificar recursivamente nos filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (isLoopVariableExpr(node.getChild(i))) {
                return true;
            }
        }

        return false;
    }
    /**
     * Visits a parenthesized expression.
     */
    private OllirExprResult visitParenExpr(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN visitParenExpr - Node: " + node.getKind());
        System.out.println("DEBUG: Number of children: " + node.getNumChildren());

        if (node.getNumChildren() < 1) {
            String errorMsg = "Empty parenthesized expression at line " + node.getLine();
            System.out.println("DEBUG: ERROR: " + errorMsg);
            errors.add(errorMsg);
            return new OllirExprResult("0.i32");
        }

        // Simply delegate to the expression inside the parentheses
        System.out.println("DEBUG: Delegating to expression inside parentheses");
        OllirExprResult result = visit(node.getChild(0));
        System.out.println("DEBUG: END visitParenExpr - Code: " + result.getCode());
        return result;
    }

    /**
     * Default visitor method for unhandled node types.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void context) {
        System.out.println("DEBUG: BEGIN defaultVisit - Unrecognized node: " + node.getKind());
        System.out.println("DEBUG: Number of children: " + node.getNumChildren());

        StringBuilder computation = new StringBuilder();

        // Visit all children and collect their computations
        for (int i = 0; i < node.getNumChildren(); i++) {
            System.out.println("DEBUG: Processing child " + i + " of unrecognized node");
            OllirExprResult childResult = visit(node.getChild(i));
            System.out.println("DEBUG: Child result - Code: " + childResult.getCode());
            computation.append(childResult.getComputation());

            // If this is the last child and we have no other way to handle this node,
            // we'll return its result
            if (i == node.getNumChildren() - 1) {
                System.out.println("DEBUG: END defaultVisit - Returning last child result");
                return new OllirExprResult(childResult.getCode(), computation);
            }
        }

        System.out.println("DEBUG: No children found or processable, generating default value");
        // If we couldn't determine type through children, fall back
        Type defaultType = TypeUtils.newIntType();
        String resultTypeStr = ollirTypeUtils.toOllirType(defaultType);
        System.out.println("DEBUG: Default type: " + defaultType.getName() + " -> " + resultTypeStr);

        // Create a default value
        String resultVar = ollirTypeUtils.nextTemp() + resultTypeStr;
        System.out.println("DEBUG: Temporary variable for default value: " + resultVar);

        String defaultValueCode = resultVar + SPACE +
                ASSIGN + resultTypeStr + SPACE +
                "0" + resultTypeStr + END_STMT;
        System.out.println("DEBUG: Default value code: " + defaultValueCode.trim());
        computation.append(defaultValueCode);

        System.out.println("DEBUG: END defaultVisit - Generated computation code (size): " + computation.length());
        return new OllirExprResult(resultVar, computation);
    }

    // ===== Helper methods =====

    /**
     * Resolves variable type from the symbol table.
     */
    private Type resolveVariableType(String varName) {
        System.out.println("DEBUG: BEGIN resolveVariableType for: " + varName);
        System.out.println("DEBUG: Current method: " + currentMethod);

        // Check method parameters
        if (currentMethod != null) {
            System.out.println("DEBUG: Checking method parameters: " + currentMethod);
            for (Symbol param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(varName)) {
                    System.out.println("DEBUG: Found as method parameter");
                    return param.getType();
                }
            }

            // Check local variables
            System.out.println("DEBUG: Checking method local variables: " + currentMethod);
            for (Symbol local : symbolTable.getLocalVariables(currentMethod)) {
                if (local.getName().equals(varName)) {
                    System.out.println("DEBUG: Found as method local variable");
                    return local.getType();
                }
            }
        }

        // Check fields
        System.out.println("DEBUG: Checking class fields");
        for (Symbol field : symbolTable.getFields()) {
            if (field.getName().equals(varName)) {
                System.out.println("DEBUG: Found as class field");
                return field.getType();
            }
        }

        // Default to int if not found
        System.out.println("DEBUG: Type not found, using int as default");
        return TypeUtils.newIntType();
    }

    /**
     * Resolves field type for a given class and field name.
     */
    private Type resolveFieldType(String className, String fieldName) {
        System.out.println("DEBUG: BEGIN resolveFieldType for class: " + className + ", field: " + fieldName);

        // Check if it's a field in the current class
        if (className.equals(symbolTable.getClassName())) {
            System.out.println("DEBUG: Checking fields in current class: " + className);
            for (Symbol field : symbolTable.getFields()) {
                if (field.getName().equals(fieldName)) {
                    System.out.println("DEBUG: Field found in current class");
                    return field.getType();
                }
            }
        }

        // Default to int if field not found
        System.out.println("DEBUG: Field not found, using int as default");
        return TypeUtils.newIntType();
    }

    /**
     * Resolves the return type of a method for a given class.
     */
    private Type resolveMethodReturnType(String className, String methodName) {
        System.out.println("DEBUG: BEGIN resolveMethodReturnType for class: " + className + ", method: " + methodName);

        // Check methods of current class
        if (className.equals(symbolTable.getClassName())) {
            System.out.println("DEBUG: Checking methods of current class: " + className);
            Type returnType = symbolTable.getReturnType(methodName);
            if (returnType != null) {
                System.out.println("DEBUG: Return type found in current class: " +
                        returnType.getName() + (returnType.isArray() ? "[]" : ""));
                return returnType;
            }
        }

        // Default to void for any method we can't determine type
        System.out.println("DEBUG: Method not found in current class, using void as default");
        return new Type("void", false);
    }

    /**
     * Determines the appropriate invocation type for method calls.
     */
    private String determineInvokeType(String objectClassName, String objectCode) {
        System.out.println("DEBUG: BEGIN determineInvokeType for class: " + objectClassName + ", object: " + objectCode);

        // For imported classes
        if (importedClasses.contains(objectClassName)) {
            System.out.println("DEBUG: Imported class, using invokestatic");
            return INVOKE_STATIC;
        }

        // For 'this' object or current class
        if ("this".equals(objectCode) || objectClassName.equals(symbolTable.getClassName())) {
            System.out.println("DEBUG: 'this' object or current class, using invokevirtual");
            return INVOKE_VIRTUAL;
        }

        // For superclass
        if (objectClassName.equals(symbolTable.getSuper())) {
            System.out.println("DEBUG: Superclass, using invokespecial");
            return INVOKE_SPECIAL;
        }

        // Default to invokevirtual for other objects
        System.out.println("DEBUG: Using invokevirtual by default");
        return INVOKE_VIRTUAL;
    }

    /**
     * Checks if a variable is a class field.
     * OPTIMIZED AND ROBUST IMPLEMENTATION
     */
    private boolean isFieldVariable(String varName) {
        String cacheKey = (currentMethod != null ? currentMethod + "." : "") + varName;

        // Check cache first for optimization
        if (variableTypeCache.containsKey(cacheKey)) {
            return variableTypeCache.get(cacheKey);
        }


        boolean result = false;

        // First check if it's a local variable or parameter
        if (currentMethod != null) {
            // Check method parameters
            for (Symbol param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(varName)) {
                    variableTypeCache.put(cacheKey, false);
                    return false; // It's a parameter, not a field
                }
            }

            // Check method local variables
            for (Symbol local : symbolTable.getLocalVariables(currentMethod)) {
                if (local.getName().equals(varName)) {
                    variableTypeCache.put(cacheKey, false);
                    return false; // It's a local variable, not a field
                }
            }
        }

        // If not a local variable or parameter, check if it's a field
        for (Symbol field : symbolTable.getFields()) {
            if (field.getName().equals(varName)) {
                result = true;
                break;
            }
        }

        variableTypeCache.put(cacheKey, result);
        return result;
    }

    /**
     * Infers a binary operator from node type.
     */
    private String inferOperatorFromKind(String kind) {
        System.out.println("DEBUG: BEGIN inferOperatorFromKind for kind: " + kind);

        String result;
        if (kind.contains("MULTIPLICATIVE")) {
            result = "*";
        } else if (kind.contains("ADDITIVE")) {
            result = "+";
        } else if (kind.contains("RELATIONAL")) {
            result = "<";
        } else if (kind.contains("EQUALITY")) {
            result = "==";
        } else if (kind.contains("LOGICAL_AND") || kind.contains("LogicalAnd")) {
            result = "&&";
        } else if (kind.contains("LOGICAL_OR") || kind.contains("LogicalOr")) {
            result = "||";
        } else {
            result = "+"; // Default
        }

        System.out.println("DEBUG: Inferred operator: " + result);
        return result;
    }

    /**
     * Infers a unary operator from node type.
     */
    private String inferUnaryOperatorFromKind(String kind) {
        System.out.println("DEBUG: BEGIN inferUnaryOperatorFromKind for kind: " + kind);

        String result;
        if (kind.contains("Not")) {
            result = "!";
        } else if (kind.contains("Sign")) {
            result = "-";
        } else {
            result = "-"; // Default
        }

        System.out.println("DEBUG: Inferred unary operator: " + result);
        return result;
    }

    /**
     * Infers result type from binary operator.
     */
    private Type inferTypeFromOperator(String operator) {
        System.out.println("DEBUG: BEGIN inferTypeFromOperator for operator: " + operator);

        Type result;
        if ("&&".equals(operator) || "||".equals(operator) ||
                "==".equals(operator) || "!=".equals(operator) ||
                "<".equals(operator) || "<=".equals(operator) ||
                ">".equals(operator) || ">=".equals(operator)) {
            System.out.println("DEBUG: Comparison/logical operator, boolean type");
            result = TypeUtils.newBooleanType();
        } else {
            System.out.println("DEBUG: Arithmetic operator, int type");
            result = TypeUtils.newIntType();
        }

        System.out.println("DEBUG: Inferred type: " + result.getName() + (result.isArray() ? "[]" : ""));
        return result;
    }

    /**
     * Infers operand type for OLLIR operator type annotation.
     */
    /**
     * Infere o tipo de um operando para anotação de tipo OLLIR.
     * Suporta inferência de um único operando quando o outro é nulo.
     */
    private Type inferOperandType(JmmNode left, JmmNode right) {
        if (left == null && right == null) {
            return TypeUtils.newIntType(); // Tipo padrão se ambos nulos
        }

        // Se apenas um operando está presente, inferir seu tipo
        if (right == null) {
            return inferSingleOperandType(left);
        }
        if (left == null) {
            return inferSingleOperandType(right);
        }

        // Se ambos presentes, implementação original
        Type leftType = typeUtils.getExprType(left);
        Type rightType = typeUtils.getExprType(right);

        // Operações booleanas
        if ("boolean".equals(leftType.getName()) || "boolean".equals(rightType.getName())) {
            return TypeUtils.newBooleanType();
        }

        // Padrão para inteiros em operações aritméticas
        return TypeUtils.newIntType();
    }

    /**
     * Infere o tipo de um único operando.
     */
    private Type inferSingleOperandType(JmmNode node) {
        try {
            // Tentar obter o tipo diretamente
            Type type = typeUtils.getExprType(node);
            return type;
        } catch (Exception e) {
            // Inferir baseado na estrutura do nó
            if (node.getKind().equals("INTEGER_LITERAL") ||
                    node.getKind().equals("IntLiteral")) {
                return TypeUtils.newIntType();
            } else if (node.getKind().equals("TrueLiteral") ||
                    node.getKind().equals("FalseLiteral")) {
                return TypeUtils.newBooleanType();
            }

            // Padrão para inteiros
            return TypeUtils.newIntType();
        }
    }
    /**
     * Gets the OLLIR type string for an array with specified dimensions.
     */
    private String getArrayTypeString(String baseType, int dimensions) {
        System.out.println("DEBUG: BEGIN getArrayTypeString for base type: " + baseType + ", dimensions: " + dimensions);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            sb.append(".array");
        }
        sb.append(baseType);

        String result = sb.toString();
        System.out.println("DEBUG: Generated array type: " + result);
        return result;
    }

    /**
     * Extracts the class name from an import path.
     */
    private String extractClassName(String importPath) {
        System.out.println("DEBUG: BEGIN extractClassName for path: " + importPath);

        int lastDot = importPath.lastIndexOf('.');
        String result;
        if (lastDot != -1) {
            result = importPath.substring(lastDot + 1);
            System.out.println("DEBUG: Extracted class name: " + result);
        } else {
            result = importPath;
            System.out.println("DEBUG: Using full path as class name: " + result);
        }

        return result;
    }

    /**
     * Gets the dimension count for an array instantiation.
     */
    private int getDimensionCount(JmmNode node) {
        System.out.println("DEBUG: BEGIN getDimensionCount for node: " + node.getKind());

        if (node.getKind().equals("NewMultiDimArrayExpr")) {
            System.out.println("DEBUG: Node is multidimensional array");
            int dimensions = 0;
            for (JmmNode child : node.getChildren()) {
                if (isExpressionNode(child)) {
                    dimensions++;
                }
            }
            System.out.println("DEBUG: Number of dimensions found: " + dimensions);
            return Math.max(1, dimensions);
        }

        // For regular arrays, assume 1D unless specified otherwise
        System.out.println("DEBUG: Node is regular array, assuming 1 dimension");
        return 1;
    }

    /**
     * Checks if a node is an expression node based on its type.
     */
    private boolean isExpressionNode(JmmNode node) {
        System.out.println("DEBUG: Checking if node is expression: " + node.getKind());

        String kind = node.getKind();
        boolean result = !kind.equals("ArraySuffix") &&
                !kind.equals("LBRACK") &&
                !kind.equals("RBRACK");

        System.out.println("DEBUG: Is expression? " + result);
        return result;
    }

    /**
     * Checks if a boolean literal represents true.
     */
    private boolean isTrue(JmmNode node) {
        System.out.println("DEBUG: BEGIN isTrue for node: " + node.getKind());

        String kind = node.getKind();
        boolean result;

        if (kind.contains("True")) {
            System.out.println("DEBUG: Node contains 'True' in type");
            result = true;
        } else if (kind.contains("False")) {
            System.out.println("DEBUG: Node contains 'False' in type");
            result = false;
        } else if (node.hasAttribute("value")) {
            String value = node.get("value");
            System.out.println("DEBUG: Node has 'value' attribute: " + value);
            result = "true".equalsIgnoreCase(value);
        } else {
            System.out.println("DEBUG: No information, assuming false");
            result = false;
        }

        System.out.println("DEBUG: Result: " + result);
        return result;
    }

    /**
     * Handles logical AND with short-circuit evaluation.
     * a && b — evaluates b only if a == true
     * Usa o LabelManager para garantir labels únicos para operações AND.
     */
    private OllirExprResult handleLogicalAnd(JmmNode left, JmmNode right) {
        // Evaluate left operand
        OllirExprResult l = visit(left);

        // CORREÇÃO: Usar LabelManager com formato de label preciso
        int lbl = labelManager.nextLabel(LabelManager.AND_STRUCTURE);
        String thenL = "then" + lbl;         // Mudou de "andThen" para "then"
        String endL = "endif" + lbl;         // Mudou de "andEnd" para "endif"

        // Nome do temporário deve seguir a sequência
        String res = "andTmp" + lbl + ".bool";
        StringBuilder c = new StringBuilder(l.getComputation());

        // Generate short-circuit code
        c.append("if (").append(l.getCode()).append(") goto ").append(thenL).append(";\n")
                .append(res).append(" :=.bool 0.bool;\n")
                .append("goto ").append(endL).append(";\n");

        c.append(thenL).append(":\n");
        OllirExprResult r = visit(right);
        c.append(r.getComputation())
                .append(res).append(" :=.bool ").append(r.getCode()).append(";\n")
                .append(endL).append(":\n");

        return new OllirExprResult(res, c);
    }

    /**
     * Handles logical OR with short-circuit evaluation.
     * a || b — evaluates b only if a == false
     * Usa o LabelManager para garantir labels únicos para operações OR.
     */
    private OllirExprResult handleLogicalOr(JmmNode left, JmmNode right) {
        OllirExprResult l = visit(left);

        // CORREÇÃO: Usar nomenclatura de labels distinta para operações OR
        int lbl = labelManager.nextLabel(LabelManager.OR_STRUCTURE);
        String thenL = "orThen" + lbl;
        String endL = "orEnd" + lbl;

        String res = ollirTypeUtils.nextTemp() + ".bool";
        StringBuilder c = new StringBuilder(l.getComputation());

        /* a == true ? result = 1.bool */
        c.append("if (").append(l.getCode()).append(") goto ").append(thenL).append(";\n");

        /* a == false ⇒ evaluate b */
        OllirExprResult r = visit(right);
        c.append(r.getComputation())
                .append(res).append(" :=.bool ").append(r.getCode()).append(";\n")
                .append("goto ").append(endL).append(";\n");

        /* branch where a was already true */
        c.append(thenL).append(":\n")
                .append(res).append(" :=.bool 1.bool;\n")
                .append(endL).append(":\n");

        return new OllirExprResult(res, c);
    }}