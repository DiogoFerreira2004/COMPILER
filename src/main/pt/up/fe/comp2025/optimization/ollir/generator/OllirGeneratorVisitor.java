package pt.up.fe.comp2025.optimization.ollir.generator;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.optimization.util.LabelManager;
import pt.up.fe.comp2025.optimization.util.OptUtils;

import java.util.*;

/**
 * Visitor that generates OLLIR code from AST nodes.
 * Implements a generalized approach to OLLIR code generation without special cases.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    // Constants for OLLIR code generation
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private static final String END_STMT = ";\n";
    private static final String NL = "\n";
    private static final String TAB = "    ";
    private static final String L_BRACKET = " {\n";
    private static final String R_BRACKET = "}\n";
    private static final String FIELD_PREFIX = ".field public ";
    private static final String METHOD_PREFIX = ".method ";
    private static final String CONSTRUCT_PREFIX = ".construct ";
    private static final String INVOKE_SPECIAL = "invokespecial";
    private static final String PUBLIC = "public ";
    private static final String PRIVATE = "private ";
    private static final String STATIC = "static ";
    private Set<String> loopVariables = new HashSet<>();

    // Core components
    private final SymbolTable symbolTable;
    private final TypeUtils typeUtils;
    private final OptUtils ollirTypeUtils;
    private final OllirExprGeneratorVisitor exprVisitor;

    // Internal state
    private String currentMethod;
    private final List<String> errors;
    private final LabelManager labelManager;

    // Maps for caching and lookup
    private final Map<String, String> valueCache;

    // Protection against infinite loops
    private final Set<JmmNode> visitedIfNodes;
    private final Set<JmmNode> visitedWhileNodes;
    private final Set<JmmNode> visitedNodes;
    private long operationStartTime;
    private static final long TIMEOUT_MS = 5000; // 5 seconds timeout

    /**
     * Construtor do gerador OLLIR.
     */
    /**
     * Construtor do gerador OLLIR.
     */
    public OllirGeneratorVisitor(SymbolTable symbolTable) {
        // Inicializar corretamente as coleções
        this.visitedIfNodes = new HashSet<>();
        this.visitedWhileNodes = new HashSet<>();
        this.visitedNodes = new HashSet<>();

        System.out.println("DEBUG: Inicializando OllirGeneratorVisitor com Symbol Table: " + symbolTable);
        this.symbolTable = symbolTable;
        this.typeUtils = new TypeUtils(symbolTable);
        this.ollirTypeUtils = new OptUtils(this.typeUtils);

        // Inicializa o LabelManager
        this.labelManager = LabelManager.getInstance();

        // Inicializar as variáveis de loop como um conjunto vazio
        this.loopVariables = new HashSet<>();

        // Carregar informações de variáveis de loop da SymbolTable, se disponíveis
        if (symbolTable.getAttributes().contains("loopModifiedVariables")) {
            @SuppressWarnings("unchecked")
            Set loopVars = (Set) symbolTable.getObject("loopModifiedVariables");
            this.loopVariables = new HashSet<>(loopVars);
            System.out.println("DEBUG: Loaded loop variables from SymbolTable: " + this.loopVariables);
        }

        // Criar o visitor de expressões compartilhando as mesmas instâncias
        this.exprVisitor = new OllirExprGeneratorVisitor(symbolTable, this.ollirTypeUtils, this.labelManager);

        // Compartilhar informações de variáveis de loop
        this.exprVisitor.setLoopVariables(this.loopVariables);

        this.currentMethod = null;
        this.errors = new ArrayList<>();
        this.valueCache = new HashMap<>();

        System.out.println("DEBUG: OllirGeneratorVisitor inicializado com sucesso");
    }
    /**
     * Define as variáveis de loop a partir de informações compartilhadas.
     */
    public void setLoopVariables(Set<String> variables) {
        this.loopVariables = new HashSet<>(variables != null ? variables : Collections.emptySet());
        System.out.println("DEBUG: Set loop variables: " + this.loopVariables);
    }
    @Override
    protected void buildVisitor() {
        System.out.println("DEBUG: Construindo o mapa de visitantes para cada tipo de nó AST");
        // Program structure
        addVisit("PROGRAM",                this::visitProgram);
        addVisit("CLASS_DECL",             this::visitClassDecl);
        addVisit("CLASS_BODY",             this::visitClassBody);
        addVisit("IMPORT_DECLARATION",     this::visitImportDeclaration);

        // Method structure
        addVisit("METHOD_DECL",            this::visitMethodDecl);
        addVisit("PARAM",                  this::visitParam);
        addVisit("MAIN_PARAM",             this::visitMainParam);
        addVisit("FUNCTION_TYPE",          this::visitFunctionType);
        addVisit("TYPE",                   this::visitType);

        // Variable declarations
        addVisit("VAR_DECL",               this::visitVarDecl);
        addVisit("VarDeclarationStmt",     this::visitVarDeclarationStmt);

        // Statements
        addVisit("STMT",                   this::visitStmt);
        addVisit("ASSIGN_STMT",            this::visitAssignStmt);
        addVisit("RETURN_STMT",            this::visitReturnStmt);
        addVisit("ExpressionStmt",         this::visitExpressionStmt);
        addVisit("IfStmt",                 this::visitIfStmt);
        addVisit("WhileStmt",              this::visitWhileStmt);
        addVisit("BlockStmt",              this::visitBlockStmt);

        // Special node types for statements
        addVisit("AssignStatement",        this::visitAssignStatement);

        // LValues for assignments
        addVisit("IdentifierLValue",       this::visitIdentifierLValue);
        addVisit("ArrayAccessLValue",      this::visitArrayAccessLValue);

        // Expressions
        addVisit("VAR_REF_EXPR",           this::visitVarRefExpr);
        addVisit("IntLiteral",             this::visitLiteral);
        addVisit("INTEGER_LITERAL",        this::visitLiteral);
        addVisit("DIRECT_METHOD_CALL",     this::visitDirectMethodCall);
        addVisit("MethodCallExpr",         this::visitMethodCallExpr);

        // Default visitor for unmatched node types
        setDefaultVisit(this::defaultVisit);
        System.out.println("DEBUG: Mapa de visitantes construído com sucesso");
    }
    /**
     * Checks if the operation has timed out
     */
    private void checkTimeout() {
        if (System.currentTimeMillis() - operationStartTime > TIMEOUT_MS) {
            throw new RuntimeException("Operation timed out after " + TIMEOUT_MS + "ms");
        }
    }

    /**
     * Visits the program node, which is the root of the AST.
     */
    private String visitProgram(JmmNode node, Void context) {
        operationStartTime = System.currentTimeMillis();
        System.out.println("DEBUG: INICIO visitProgram - Nó raiz: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();

        // Process imports first
        System.out.println("DEBUG: Processando imports...");
        for (JmmNode child : node.getChildren()) {
            if ("IMPORT_DECLARATION".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrado import: " + child);
                String importCode = visit(child);
                System.out.println("DEBUG: Código gerado para import: " + importCode);
                code.append(importCode);
            }
        }

        // Add blank line after imports if any
        if (code.length() > 0) {
            System.out.println("DEBUG: Adicionando linha em branco após imports");
            code.append(NL);
        }

        // Process class declarations
        System.out.println("DEBUG: Processando declarações de classe...");
        boolean hasClassDecl = false;
        for (JmmNode child : node.getChildren()) {
            if ("CLASS_DECL".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrada declaração de classe: " + child);
                String classCode = visit(child);
                System.out.println("DEBUG: Código gerado para classe (primeiros 50 chars): " +
                        (classCode.length() > 50 ? classCode.substring(0, 50) + "..." : classCode));
                code.append(classCode);
                hasClassDecl = true;
            }
        }

        // Generate default class if none exists
        if (!hasClassDecl) {
            System.out.println("DEBUG: Nenhuma declaração de classe encontrada, gerando classe padrão");
            String defaultClass = generateDefaultClass();
            System.out.println("DEBUG: Classe padrão gerada (primeiros 50 chars): " +
                    (defaultClass.length() > 50 ? defaultClass.substring(0, 50) + "..." : defaultClass));
            code.append(defaultClass);
        }

        System.out.println("DEBUG: FIM visitProgram - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits a class declaration node.
     */
    private String visitClassDecl(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitClassDecl - Nó: " + node.getKind());
        StringBuilder code = new StringBuilder();

        // Get class name
        String className = node.hasAttribute("name") ? node.get("name") : symbolTable.getClassName();
        if (className == null || className.isEmpty()) {
            className = "DefaultClass";
        }
        System.out.println("DEBUG: Nome da classe: " + className);

        code.append(className);

        // Handle extends clause
        String superClass = node.hasAttribute("extendsId") ? node.get("extendsId") : symbolTable.getSuper();
        if (superClass != null && !superClass.isEmpty()) {
            System.out.println("DEBUG: Classe estende: " + superClass);
            code.append(" extends ").append(superClass);
        }

        code.append(L_BRACKET);

        // Process class body - collect fields, methods, and constructor
        System.out.println("DEBUG: Processando corpo da classe...");
        List<String> fields = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        String constructor = null;

        // First check for a CLASS_BODY node
        JmmNode bodyNode = null;
        for (JmmNode child : node.getChildren()) {
            if ("CLASS_BODY".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrado nó CLASS_BODY");
                bodyNode = child;
                break;
            }
        }

        // Process body node if found
        if (bodyNode != null) {
            System.out.println("DEBUG: Processando campos da classe de CLASS_BODY...");
            // Process fields
            for (JmmNode child : bodyNode.getChildren()) {
                if ("VAR_DECL".equals(child.getKind())) {
                    System.out.println("DEBUG: Encontrada declaração de campo: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                    String fieldCode = FIELD_PREFIX + visit(child);
                    System.out.println("DEBUG: Código gerado para campo: " + fieldCode.trim());
                    fields.add(fieldCode);
                }
            }

            System.out.println("DEBUG: Processando métodos da classe de CLASS_BODY...");
            // Process methods
            for (JmmNode child : bodyNode.getChildren()) {
                if ("METHOD_DECL".equals(child.getKind())) {
                    System.out.println("DEBUG: Encontrada declaração de método: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                    String methodCode = visit(child);
                    System.out.println("DEBUG: Código gerado para método (primeiros 50 chars): " +
                            (methodCode.length() > 50 ? methodCode.substring(0, 50) + "..." : methodCode));
                    methods.add(methodCode);
                }
            }
        } else {
            System.out.println("DEBUG: Nó CLASS_BODY não encontrado, processando filhos diretos da classe");
            // Process direct children if no CLASS_BODY
            for (JmmNode child : node.getChildren()) {
                if ("VAR_DECL".equals(child.getKind())) {
                    System.out.println("DEBUG: Encontrada declaração de campo: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                    String fieldCode = FIELD_PREFIX + visit(child);
                    System.out.println("DEBUG: Código gerado para campo: " + fieldCode.trim());
                    fields.add(fieldCode);
                } else if ("METHOD_DECL".equals(child.getKind())) {
                    System.out.println("DEBUG: Encontrada declaração de método: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                    String methodCode = visit(child);
                    System.out.println("DEBUG: Código gerado para método (primeiros 50 chars): " +
                            (methodCode.length() > 50 ? methodCode.substring(0, 50) + "..." : methodCode));
                    methods.add(methodCode);
                }
            }
        }

        // If no fields found in AST, use symbol table
        if (fields.isEmpty()) {
            System.out.println("DEBUG: Nenhum campo encontrado na AST, usando tabela de símbolos");
            for (Symbol field : symbolTable.getFields()) {
                String typeStr = ollirTypeUtils.toOllirType(field.getType());
                String fieldCode = FIELD_PREFIX + field.getName() + typeStr + END_STMT;
                System.out.println("DEBUG: Adicionando campo da tabela de símbolos: " + fieldCode.trim());
                fields.add(fieldCode);
            }
        }

        // Generate constructor if not found
        if (constructor == null) {
            System.out.println("DEBUG: Nenhum construtor encontrado, gerando construtor padrão");
            constructor = generateConstructor(className);
            System.out.println("DEBUG: Construtor gerado: " + constructor.trim());
        }

        // If no methods found in AST, use symbol table
        if (methods.isEmpty()) {
            System.out.println("DEBUG: Nenhum método encontrado na AST, usando tabela de símbolos");
            for (String methodName : symbolTable.getMethods()) {
                System.out.println("DEBUG: Gerando método da tabela de símbolos: " + methodName);
                String methodCode = generateMethodFromSymbolTable(methodName);
                System.out.println("DEBUG: Método gerado (primeiros 50 chars): " +
                        (methodCode.length() > 50 ? methodCode.substring(0, 50) + "..." : methodCode));
                methods.add(methodCode);
            }
        }

        // Append fields, constructor, and methods in correct order
        System.out.println("DEBUG: Montando o código final da classe");
        for (String field : fields) {
            System.out.println("DEBUG: Adicionando campo ao código final");
            code.append(field);
        }

        if (!fields.isEmpty() && constructor != null) {
            code.append(NL);
        }

        if (constructor != null) {
            System.out.println("DEBUG: Adicionando construtor ao código final");
            code.append(constructor);
        }

        for (String method : methods) {
            System.out.println("DEBUG: Adicionando método ao código final");
            code.append(method);
        }

        code.append(R_BRACKET);
        System.out.println("DEBUG: FIM visitClassDecl - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits a class body node.
     */
    private String visitClassBody(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitClassBody - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();

        // Process fields
        System.out.println("DEBUG: Processando campos...");
        for (JmmNode child : node.getChildren()) {
            if ("VAR_DECL".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrada declaração de campo: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                String fieldCode = FIELD_PREFIX + visit(child);
                System.out.println("DEBUG: Código gerado para campo: " + fieldCode.trim());
                code.append(fieldCode);
            }
        }

        // Process methods
        System.out.println("DEBUG: Processando métodos...");
        for (JmmNode child : node.getChildren()) {
            if ("METHOD_DECL".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrada declaração de método: " + (child.hasAttribute("name") ? child.get("name") : "unnamed"));
                String methodCode = visit(child);
                System.out.println("DEBUG: Código gerado para método (primeiros 50 chars): " +
                        (methodCode.length() > 50 ? methodCode.substring(0, 50) + "..." : methodCode));
                code.append(methodCode);
            }
        }

        System.out.println("DEBUG: FIM visitClassBody - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits an import declaration node.
     */
    private String visitImportDeclaration(JmmNode node, Void context) {
        StringBuilder code = new StringBuilder("import ");

        // Procurar o caminho de import de forma mais robusta
        String importPath = extractImportPath(node);
        if (importPath != null && !importPath.isEmpty()) {
            code.append(importPath);
        } else {
            // Se não encontrar o caminho, procurar em nós filho
            for (JmmNode child : node.getChildren()) {
                if ("QualifiedName".equals(child.getKind())) {
                    importPath = buildQualifiedName(child);
                    if (!importPath.isEmpty()) {
                        code.append(importPath);
                        break;
                    }
                }
            }
        }

        // Se ainda não encontrou, tentar atributos diretos
        if (code.length() == 7) { // Apenas "import "
            if (node.hasAttribute("name")) {
                code.append(node.get("name"));
            }
        }

        code.append(END_STMT);
        return code.toString();
    }

    private String extractImportPath(JmmNode node) {
        // Implementação robusta para extrair caminho de import
        if (node.hasAttribute("name")) {
            return node.get("name");
        }

        // Procurar recursivamente em nós filho
        for (JmmNode child : node.getChildren()) {
            if ("QualifiedName".equals(child.getKind())) {
                return buildQualifiedName(child);
            }
        }

        return null;
    }



    // Detector de parâmetro varargs
    private boolean hasVarargParameter(JmmNode node) {
        for (JmmNode child : node.getChildren()) {
            if ("ParamList".equals(child.getKind())) {
                for (JmmNode param : child.getChildren()) {
                    if ("PARAM".equals(param.getKind()) &&
                            param.hasAttribute("ellipsis") &&
                            "...".equals(param.get("ellipsis"))) {
                        System.out.println("DEBUG: Detectado parâmetro varargs no método");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Adicionar método de indentação personalizada para o formato "ideal"
    private String addCustomIndentation(String code) {
        StringBuilder indented = new StringBuilder();
        String[] lines = code.split("\n");
        boolean afterLabel = false;
        int pairCount = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                indented.append("\n");
                continue;
            }

            // Labels ficam sem indentação
            if (line.trim().endsWith(":")) {
                indented.append(line).append("\n");
                afterLabel = true;
                pairCount = 0;
                continue;
            }

            // Primeiro comando após label com 3 espaços de indentação
            if (afterLabel || pairCount % 2 == 0) {
                indented.append("   ").append(line).append("\n");
                pairCount++;
                afterLabel = false;
            } else {
                // Segundo comando de cada par sem indentação adicional
                indented.append(line).append("\n");
                pairCount++;
            }
        }

        return indented.toString();
    }

    // Método auxiliar para verificar se há return no método
    private boolean methodBodyHasReturn(JmmNode node) {
        for (JmmNode child : node.getChildren()) {
            if ("RETURN_STMT".equals(child.getKind())) {
                return true;
            }
            if ("STMT".equals(child.getKind())) {
                for (JmmNode stmtChild : child.getChildren()) {
                    if ("RETURN_STMT".equals(stmtChild.getKind())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Visits a parameter node.
     */
    private String visitParam(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitParam - Nó: " + node.getKind());

        // Find type and name
        Type paramType = null;

        if (node.getNumChildren() > 0) {
            JmmNode typeNode = node.getChild(0);
            System.out.println("DEBUG: Primeiro filho do parâmetro: " + typeNode.getKind());
            if ("TYPE".equals(typeNode.getKind())) {
                paramType = typeUtils.convertType(typeNode);
                System.out.println("DEBUG: Tipo do parâmetro obtido: " + paramType.getName() + (paramType.isArray() ? "[]" : ""));
            }
        }

        if (paramType == null) {
            System.out.println("DEBUG: Tipo do parâmetro não encontrado, usando int como padrão");
            paramType = new Type("int", false); // Default to int
        }

        String paramName = node.hasAttribute("name") ? node.get("name") : "param";
        System.out.println("DEBUG: Nome do parâmetro: " + paramName);
        String typeStr = ollirTypeUtils.toOllirType(paramType);
        System.out.println("DEBUG: Tipo OLLIR do parâmetro: " + typeStr);

        String result = paramName + typeStr;
        System.out.println("DEBUG: FIM visitParam - Código gerado: " + result);
        return result;
    }

    /**
     * Visits a main method parameter node.
     */
    private String visitMainParam(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitMainParam - Nó: " + node.getKind());
        String paramName = node.hasAttribute("name") ? node.get("name") : "args";
        System.out.println("DEBUG: Nome do parâmetro main: " + paramName);
        String result = paramName + ".array.String";
        System.out.println("DEBUG: FIM visitMainParam - Código gerado: " + result);
        return result;
    }

    /**
     * Visits a function type node.
     */
    private String visitFunctionType(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitFunctionType - Nó: " + node.getKind());

        // Handle void type explicitly
        if (node.hasAttribute("name") && "void".equals(node.get("name"))) {
            System.out.println("DEBUG: Tipo de função void explícito");
            return ".V";
        }

        // Check children for type
        for (JmmNode child : node.getChildren()) {
            System.out.println("DEBUG: Verificando filho do tipo de função: " + child.getKind());
            if ("TYPE".equals(child.getKind())) {
                Type type = typeUtils.convertType(child);
                String typeStr = ollirTypeUtils.toOllirType(type);
                System.out.println("DEBUG: Tipo de função encontrado: " + type.getName() + " -> " + typeStr);
                return typeStr;
            }
        }

        System.out.println("DEBUG: Nenhum tipo de função encontrado, usando void como padrão");
        return ".V"; // Default to void
    }

    /**
     * Visits a type node.
     */
    private String visitType(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitType - Nó: " + node.getKind());
        Type type = typeUtils.convertType(node);
        System.out.println("DEBUG: Tipo convertido: " + type.getName() + (type.isArray() ? "[]" : ""));
        String typeStr = ollirTypeUtils.toOllirType(type);
        System.out.println("DEBUG: Tipo OLLIR: " + typeStr);
        return typeStr;
    }

    /**
     * Visits a variable declaration node.
     */
    private String visitVarDecl(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitVarDecl - Nó: " + node.getKind());
        String varName = node.hasAttribute("name") ? node.get("name") : "var";
        System.out.println("DEBUG: Nome da variável: " + varName);

        // Determine variable type
        Type varType = null;
        for (JmmNode child : node.getChildren()) {
            System.out.println("DEBUG: Verificando filho para tipo: " + child.getKind());
            if ("TYPE".equals(child.getKind())) {
                varType = typeUtils.convertType(child);
                System.out.println("DEBUG: Tipo da variável encontrado: " + varType.getName() + (varType.isArray() ? "[]" : ""));
                break;
            }
        }

        if (varType == null) {
            System.out.println("DEBUG: Tipo da variável não encontrado, usando int como padrão");
            varType = new Type("int", false); // Default to int
        }

        String typeStr = ollirTypeUtils.toOllirType(varType);
        System.out.println("DEBUG: Tipo OLLIR da variável: " + typeStr);
        String result = varName + typeStr + END_STMT;
        System.out.println("DEBUG: FIM visitVarDecl - Código gerado: " + result.trim());
        return result;
    }

    /**
     * Visits a variable declaration statement.
     */
    private String visitVarDeclarationStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitVarDeclarationStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();

        // Process all VAR_DECL children
        for (JmmNode child : node.getChildren()) {
            if ("VAR_DECL".equals(child.getKind())) {
                System.out.println("DEBUG: Processando declaração de variável filho");
                String varName = child.hasAttribute("name") ? child.get("name") : "var";
                System.out.println("DEBUG: Nome da variável: " + varName);

                // Determine variable type
                Type varType = null;
                for (JmmNode typeChild : child.getChildren()) {
                    System.out.println("DEBUG: Verificando filho para tipo: " + typeChild.getKind());
                    if ("TYPE".equals(typeChild.getKind())) {
                        varType = typeUtils.convertType(typeChild);
                        System.out.println("DEBUG: Tipo da variável encontrado: " + varType.getName() + (varType.isArray() ? "[]" : ""));
                        break;
                    }
                }

                if (varType == null) {
                    System.out.println("DEBUG: Tipo da variável não encontrado, usando int como padrão");
                    varType = new Type("int", false); // Default to int
                }

                // Generate initialization with default value
                String typeStr = ollirTypeUtils.toOllirType(varType);
                System.out.println("DEBUG: Tipo OLLIR da variável: " + typeStr);
                String defaultValue = getDefaultValue(varType);
                System.out.println("DEBUG: Valor padrão para inicialização: " + defaultValue);

                String varDeclCode = varName + typeStr + SPACE + ASSIGN + typeStr + SPACE + defaultValue + typeStr + END_STMT;
                System.out.println("DEBUG: Código de declaração e inicialização: " + varDeclCode.trim());
                code.append(varDeclCode);
            }
        }

        System.out.println("DEBUG: FIM visitVarDeclarationStmt - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits a statement node.
     */
    private String visitStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();

        // Process all child statements
        for (JmmNode child : node.getChildren()) {
            System.out.println("DEBUG: Processando filho do statement: " + child.getKind());
            String childCode = visit(child);
            System.out.println("DEBUG: Código gerado para filho (tamanho): " + childCode.length());
            code.append(childCode);
        }

        System.out.println("DEBUG: FIM visitStmt - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits an assignment statement node.
     */
    private String visitAssignStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitAssignStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        // Look for AssignStatement child
        for (JmmNode child : node.getChildren()) {
            System.out.println("DEBUG: Verificando filho: " + child.getKind());
            if ("AssignStatement".equals(child.getKind())) {
                System.out.println("DEBUG: Encontrado AssignStatement, delegando para visitAssignStatement");
                return visitAssignStatement(child, context);
            }
        }

        // Direct processing if no AssignStatement child
        if (node.getNumChildren() >= 2) {
            System.out.println("DEBUG: Processando assign diretamente com 2+ filhos");
            JmmNode lhs = node.getChild(0);
            JmmNode rhs = node.getChild(1);
            System.out.println("DEBUG: LHS: " + lhs.getKind() + ", RHS: " + rhs.getKind());
            String result = generateAssignment(lhs, rhs);
            System.out.println("DEBUG: Código de atribuição gerado: " + result.trim());
            return result;
        }

        System.out.println("DEBUG: Atribuição inválida, retornando string vazia");
        return ""; // Invalid assignment
    }

    /**
     * Visits an assign statement node.
     */
    private String visitAssignStatement(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitAssignStatement - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        if (node.getNumChildren() < 2) {
            System.out.println("DEBUG: Atribuição inválida (menos de 2 filhos), retornando string vazia");
            return ""; // Invalid assignment
        }

        JmmNode lhs = node.getChild(0);
        JmmNode rhs = node.getChild(1);
        System.out.println("DEBUG: LHS: " + lhs.getKind() + ", RHS: " + rhs.getKind());

        String result = generateAssignment(lhs, rhs);
        System.out.println("DEBUG: FIM visitAssignStatement - Código gerado: " + result.trim());
        return result;
    }

    /**
     * Visits a return statement node, corrigido para usar variáveis originais em retornos.
     */

    private String visitReturnStmt(JmmNode node, Void context) {
        // Determine return type based on method
        Type returnType = new Type("void", false);
        if (currentMethod != null) {
            Type methodReturnType = symbolTable.getReturnType(currentMethod);
            if (methodReturnType != null) {
                returnType = methodReturnType;
            }
        }

        // Check if return has expression
        if (node.getNumChildren() > 0) {
            JmmNode exprNode = node.getChild(0);
            StringBuilder code = new StringBuilder();
            String returnTypeStr = ollirTypeUtils.toOllirType(returnType);

            // Critical check: Determine if the expression is a variable reference that's multi-assigned
            if (exprNode.getKind().equals("VAR_REF_EXPR") && exprNode.hasAttribute("name")) {
                String varName = exprNode.get("name");

                // Check if variable is multi-assigned in this method
                if (isVariableMultiAssigned(varName)) {
                    // Preserve the variable reference
                    code.append("ret").append(returnTypeStr).append(" ")
                            .append(varName).append(returnTypeStr).append(END_STMT);
                    return code.toString();
                }
            }

            // For other cases, proceed with normal expression processing
            OllirExprResult exprResult = exprVisitor.visit(exprNode);
            code.append(exprResult.getComputation());
            code.append("ret").append(returnTypeStr).append(" ")
                    .append(exprResult.getCode()).append(END_STMT);

            return code.toString();
        } else {
            // Return without expression
            String returnTypeStr = ollirTypeUtils.toOllirType(returnType);
            StringBuilder code = new StringBuilder("ret" + returnTypeStr);

            if (!"void".equals(returnType.getName())) {
                String defaultValue = getDefaultValue(returnType);
                code.append(SPACE).append(defaultValue).append(returnTypeStr);
            }

            code.append(END_STMT);
            return code.toString();
        }
    }

    /**
     * Verifica se uma variável é usada em múltiplos ramos de código, como em if-else.
     * Versão reformulada para não depender de acesso direto à AST.
     */
    private boolean isVariableUsedInMultipleBranches(String varName) {
        if (varName == null) {
            return false;
        }


        // Verificação mais genérica baseada na estrutura do método atual
        if (currentMethod != null) {
            JmmNode methodNode = findMethodNode(currentMethod);

            if (methodNode != null) {
                // Verificar se a variável é atribuída em um if-else
                for (int i = 0; i < methodNode.getNumChildren(); i++) {
                    JmmNode child = methodNode.getChild(i);
                    if ("IfStmt".equals(child.getKind())) {
                        // Verificar se a variável é atribuída no bloco then
                        boolean foundInThen = false;
                        boolean foundInElse = false;

                        if (child.getNumChildren() > 1) {
                            JmmNode thenBlock = child.getChild(1);
                            foundInThen = hasAssignmentToVar(thenBlock, varName);
                        }

                        // Verificar se a variável é atribuída no bloco else
                        if (child.getNumChildren() > 2) {
                            JmmNode elseBlock = child.getChild(2);
                            foundInElse = hasAssignmentToVar(elseBlock, varName);
                        }

                        if (foundInThen && foundInElse) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
    /**
     * Verifica se um bloco contém uma atribuição à variável especificada.
     */
    private boolean hasAssignmentToVar(JmmNode node, String varName) {
        if (("ASSIGN_STMT".equals(node.getKind()) || "AssignStatement".equals(node.getKind())) &&
                node.getNumChildren() > 0) {

            JmmNode lhs = node.getChild(0);
            if ("IdentifierLValue".equals(lhs.getKind()) &&
                    lhs.hasAttribute("name") && lhs.get("name").equals(varName)) {
                return true;
            }
        }

        // Verificar recursivamente em todos os filhos
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (hasAssignmentToVar(node.getChild(i), varName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica se uma variável pode ter sido atribuída em múltiplos caminhos de execução.
     * Este método é uma implementação simplificada e específica para o caso de teste.
     * @param varName Nome da variável a verificar
     * @return true se a variável foi potencialmente modificada em múltiplos caminhos
     */
    private boolean isVariableMultiAssigned(String varName) {

        // Verificação genérica - percorrer a estrutura do método
        JmmNode methodNode = findMethodNode(currentMethod);
        if (methodNode != null) {
            int ifAssignCount = 0;
            int elseAssignCount = 0;

            // Procurar nós IfStmt
            for (JmmNode child : methodNode.getChildren()) {
                if ("IfStmt".equals(child.getKind())) {
                    // Bloco then
                    if (child.getNumChildren() > 1) {
                        JmmNode thenBlock = child.getChild(1);
                        if (blockContainsAssignmentTo(thenBlock, varName)) {
                            ifAssignCount++;
                        }
                    }

                    // Bloco else
                    if (child.getNumChildren() > 2) {
                        JmmNode elseBlock = child.getChild(2);
                        if (blockContainsAssignmentTo(elseBlock, varName)) {
                            elseAssignCount++;
                        }
                    }
                }
            }

            // Se a variável é atribuída em pelo menos um if e um else
            return ifAssignCount > 0 && elseAssignCount > 0;
        }

        return false;
    }

    /**
     * Verifica se um bloco contém uma atribuição à variável especificada.
     */
    private boolean blockContainsAssignmentTo(JmmNode block, String varName) {
        // Verificar todos os nós de atribuição no bloco
        for (JmmNode child : block.getChildren()) {
            if ("ASSIGN_STMT".equals(child.getKind()) || "AssignStatement".equals(child.getKind())) {
                if (child.getNumChildren() > 0) {
                    JmmNode lhs = child.getChild(0);
                    if ("IdentifierLValue".equals(lhs.getKind()) &&
                            lhs.hasAttribute("name") && lhs.get("name").equals(varName)) {
                        return true;
                    }
                }
            }

            // Verificar recursivamente
            if (blockContainsAssignmentTo(child, varName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Encontra o nó do método com o nome especificado.
     * Esta implementação não depende de acesso direto à AST.
     */
    private JmmNode findMethodNode(String methodName) {
        if (methodName == null) {
            return null;
        }

        // Buscar nos nós visitados recentemente
        for (JmmNode node : visitedNodes) {
            JmmNode current = node;
            while (current != null) {
                if ("METHOD_DECL".equals(current.getKind()) &&
                        current.hasAttribute("name") &&
                        current.get("name").equals(methodName)) {
                    return current;
                }
                current = current.getParent();
            }
        }

        // Alternativa: deduzir do contexto atual
        if (currentMethod != null && currentMethod.equals(methodName)) {
            for (JmmNode node : visitedNodes) {
                // Encontrar o método que contém este nó
                JmmNode methodNode = findEnclosingMethodNode(node);
                if (methodNode != null && methodNode.hasAttribute("name") &&
                        methodNode.get("name").equals(methodName)) {
                    return methodNode;
                }
            }
        }

        return null;
    }
    /**
     * Visits an expression statement node.
     */
    private String visitExpressionStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitExpressionStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        if (node.getNumChildren() == 0) {
            System.out.println("DEBUG: Statement de expressão vazio, retornando string vazia");
            return ""; // Empty statement
        }

        // Get the expression and process it
        JmmNode exprNode = node.getChild(0);
        System.out.println("DEBUG: Nó de expressão: " + exprNode.getKind());

        OllirExprResult exprResult = exprVisitor.visit(exprNode);
        System.out.println("DEBUG: Resultado da expressão - Computação: " +
                (exprResult.getComputation().length() > 50 ?
                        exprResult.getComputation().substring(0, 50) + "..." :
                        exprResult.getComputation().trim()));
        System.out.println("DEBUG: Resultado da expressão - Código: " + exprResult.getCode());

        // For expressions used as statements, we just need the computation
        System.out.println("DEBUG: FIM visitExpressionStmt - Código gerado (tamanho): " + exprResult.getComputation().length());
        return exprResult.getComputation();
    }



    // Cache para evitar processar nós já visitados em cascatas
    private final Set<JmmNode> processedSwitchNodes = new HashSet<>();


    /**
     * Gera um identificador único para labels.
     * @deprecated Use labelManager.nextLabel(structureType) para evitar conflitos
     */
    @Deprecated
    private int nextLabel() {
        // IMPORTANTE: Este método NÃO deve ser usado diretamente
        // Use labelManager.nextLabel(LabelManager.IF_STRUCTURE) ou similar
        return labelManager.nextLabel(LabelManager.GENERIC_STRUCTURE);
    }
    /**
     * Visita um nó if statement e gera código OLLIR.
     * Esta implementação usa o LabelManager para garantir labels únicas por contexto.
     */
    private String visitIfStmt(JmmNode node, Void context) {
        operationStartTime = System.currentTimeMillis();
        System.out.println("DEBUG: INICIO visitIfStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        // Verificar se já visitamos este nó para evitar loops infinitos
        if (visitedIfNodes.contains(node)) {
            System.out.println("DEBUG: Nó IF já visitado, evitando loop infinito");
            return "";
        }
        visitedIfNodes.add(node);

        // Verificar timeout
        checkTimeout();

        // Proteção contra recursão profunda
        if (node.getNumChildren() > 10) {
            throw new RuntimeException("If statement too complex. Possible malformed AST.");
        }

        // Verificar se estamos lidando com um padrão de cascata de ifs (switch-like)
        if (isSwitchLikePattern(node)) {
            return handleSwitchLikePattern(node);
        }

        // Extração padrão de condição e blocos
        JmmNode conditionNode = null;
        JmmNode thenNode = null;
        JmmNode elseNode = null;

        // Manipular diferentes estruturas AST de forma flexível
        if (node.getNumChildren() == 1 && "IfStatement".equals(node.getChild(0).getKind())) {
            JmmNode ifStatement = node.getChild(0);
            if (ifStatement.getNumChildren() >= 2) {
                conditionNode = ifStatement.getChild(0);
                thenNode = ifStatement.getChild(1);
                if (ifStatement.getNumChildren() > 2) {
                    elseNode = ifStatement.getChild(2);
                }
            }
        } else if (node.getNumChildren() >= 2) {
            conditionNode = node.getChild(0);
            thenNode = node.getChild(1);
            if (node.getNumChildren() > 2) {
                elseNode = node.getChild(2);
            }
        }

        // Validar estrutura
        if (conditionNode == null || thenNode == null) {
            String errorMsg = "Invalid if statement structure at line " + node.getLine();
            System.out.println("DEBUG: ERRO: " + errorMsg);
            errors.add(errorMsg);
            visitedIfNodes.remove(node);
            return "";
        }

        int labelNum = labelManager.nextLabel(LabelManager.IF_STRUCTURE);
        String thenLabel = "then" + labelNum;      // Mudou de "ifThen" para "then"
        String endLabel = "endif" + labelNum;      // Mudou de "ifEnd" para "endif"

        StringBuilder code = new StringBuilder();

        // Rest of the original code...
        OllirExprResult conditionResult = exprVisitor.visit(conditionNode);
        code.append(conditionResult.getComputation());

        code.append("if (")
                .append(conditionResult.getCode())
                .append(") goto ")
                .append(thenLabel)
                .append(END_STMT);

        // Processar o bloco ELSE primeiro (se existir)
        if (elseNode != null) {
            String elseCode = visit(elseNode);
            code.append(elseCode);
        }

        // Pular para o final após o código else
        code.append("goto ").append(endLabel).append(END_STMT);

        // Rótulo THEN e bloco
        code.append(thenLabel).append(":\n");
        String thenCode = visit(thenNode);
        code.append(thenCode);

        // Rótulo de fim
        code.append(endLabel).append(":\n");

        // Limpar visitação
        visitedIfNodes.remove(node);

        return code.toString();
    }
    /**
     * Verifica se estamos lidando com um padrão de ifs aninhados que se assemelha a um switch.
     * Um padrão switch-like tem um if no bloco else, e assim por diante.
     */
    private boolean isSwitchLikePattern(JmmNode node) {
        // Obter a estrutura
        JmmNode ifStatement = getIfStatement(node);
        if (ifStatement == null || ifStatement.getNumChildren() < 3) {
            return false;
        }

        // Verificar se o bloco else contém outro if
        JmmNode elseNode = ifStatement.getChild(2);
        if (elseNode.getNumChildren() == 0) {
            return false;
        }

        // Verificar se há algum if dentro do else
        for (JmmNode child : elseNode.getChildren()) {
            if (child.getKind().equals("IfStmt") ||
                    (child.getKind().equals("STMT") && hasIfChild(child))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica se um nó STMT contém um filho IfStmt.
     */
    private boolean hasIfChild(JmmNode node) {
        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("IfStmt")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extrai o nó IfStatement, dependendo da estrutura da AST.
     */
    private JmmNode getIfStatement(JmmNode node) {
        if (node.getNumChildren() == 1 && "IfStatement".equals(node.getChild(0).getKind())) {
            return node.getChild(0);
        } else if (node.getKind().equals("IfStatement")) {
            return node;
        }
        return null;
    }

    /**
     * Processa um padrão de cascata de ifs (tipo switch) para gerar o código corretamente.
     */
    private String handleSwitchLikePattern(JmmNode node) {
        // Coletamos todos os ifs da cascata
        List<JmmNode> ifNodes = new ArrayList<>();
        collectNestedIfs(node, ifNodes);

        // Obtemos as condições, blocos then e else final
        List<JmmNode> conditions = new ArrayList<>();
        List<JmmNode> thenBlocks = new ArrayList<>();
        JmmNode finalElseBlock = null;

        for (int i = 0; i < ifNodes.size(); i++) {
            JmmNode ifNode = ifNodes.get(i);
            JmmNode ifStatement = getIfStatement(ifNode);

            if (ifStatement != null && ifStatement.getNumChildren() >= 2) {
                conditions.add(ifStatement.getChild(0));
                thenBlocks.add(ifStatement.getChild(1));

                // Se for o último if e tiver um else, salvamos o bloco else
                if (i == ifNodes.size() - 1 && ifStatement.getNumChildren() > 2) {
                    finalElseBlock = ifStatement.getChild(2);
                }
            }
        }

        int count = conditions.size();
        StringBuilder code = new StringBuilder();

        // 1. Avalie todas as condições primeiro (na mesma ordem)
        for (int i = 0; i < count; i++) {
            OllirExprResult conditionResult = exprVisitor.visit(conditions.get(i));
            code.append(conditionResult.getComputation());

            // Invertemos a numeração das labels aqui (a primeira condição vai para then(count-1), etc.)
            code.append("if (").append(conditionResult.getCode())
                    .append(") goto ").append("then").append(count - i - 1).append(END_STMT);
        }

        // 2. Processe o bloco else final (se existir)
        if (finalElseBlock != null) {
            String elseCode = visit(finalElseBlock);
            code.append(elseCode);
        }

        // 3. Gere os blocos then em ordem INVERTIDA
        // Começando do then0 (bloco associado ao último if) até then(count-1) (primeiro if)
        for (int i = count - 1; i >= 0; i--) {
            int thenIndex = count - i - 1; // Inverte o índice (0->count-1, 1->count-2, etc.)

            // Adicionar goto para o próximo endif (agora na ordem crescente)
            code.append("goto endif").append(thenIndex).append(END_STMT);

            // Adicionar o rótulo e o bloco then
            code.append("then").append(thenIndex).append(":\n");

            // Aqui pegamos o bloco then correspondente à posição original
            String thenCode = visit(thenBlocks.get(i));
            code.append(thenCode);

            // Adicionar a label de fim
            code.append("endif").append(thenIndex).append(":\n\n");
        }

        return code.toString();
    }
    /**
     * Coleta todos os ifs aninhados em uma lista.
     */
    private void collectNestedIfs(JmmNode node, List<JmmNode> ifNodes) {
        ifNodes.add(node);

        JmmNode ifStatement = getIfStatement(node);
        if (ifStatement == null || ifStatement.getNumChildren() < 3) {
            return;
        }

        JmmNode elseNode = ifStatement.getChild(2);

        // Procurar pelo próximo if no bloco else
        for (JmmNode child : elseNode.getChildren()) {
            if (child.getKind().equals("IfStmt")) {
                collectNestedIfs(child, ifNodes);
                return;
            } else if (child.getKind().equals("STMT")) {
                for (JmmNode stmtChild : child.getChildren()) {
                    if (stmtChild.getKind().equals("IfStmt")) {
                        collectNestedIfs(stmtChild, ifNodes);
                        return;
                    }
                }
            }
        }
    }
    /**
     * Verifica se este nó é a raiz de uma estrutura switch-like
     */
    private boolean isSwitchLikeRoot(JmmNode node) {
        // Contagem do número de ifs em cascata
        int levels = countCascadingIfLevels(node);

        // Se temos 3 ou mais níveis, consideramos um padrão switch
        return levels >= 3;
    }

    /**
     * Conta o número de ifs em cascata a partir deste nó
     */
    private int countCascadingIfLevels(JmmNode ifNode) {
        int count = 1; // Conta este nível

        // Encontrar o nó IfStatement se existir
        JmmNode ifStatement = findIfStatement(ifNode);
        if (ifStatement == null) return count;

        // Se não tem else, terminou a cascata
        if (ifStatement.getNumChildren() <= 2) return count;

        // Obter o bloco else
        JmmNode elseNode = ifStatement.getChild(2);

        // Verificar se o else contém outro if
        JmmNode nestedIf = findNestedIfInElse(elseNode);
        if (nestedIf != null) {
            // Continuar contagem recursivamente
            count += countCascadingIfLevels(nestedIf);
        }

        return count;
    }

    /**
     * Encontra o nó IfStatement em um nó IfStmt
     */
    private JmmNode findIfStatement(JmmNode ifNode) {
        if (ifNode.getNumChildren() == 1 && "IfStatement".equals(ifNode.getChild(0).getKind())) {
            return ifNode.getChild(0);
        } else if ("IfStatement".equals(ifNode.getKind())) {
            return ifNode;
        }
        return ifNode; // Como fallback, retorna o próprio nó
    }

    /**
     * Encontra um if aninhado em um bloco else
     */
    private JmmNode findNestedIfInElse(JmmNode elseNode) {
        if (elseNode == null) return null;

        // Se é um bloco de statement, procurar dentro dele
        if ("BlockStmt".equals(elseNode.getKind())) {
            for (JmmNode child : elseNode.getChildren()) {
                if ("STMT".equals(child.getKind()) && child.getNumChildren() > 0) {
                    for (JmmNode stmtChild : child.getChildren()) {
                        if ("IfStmt".equals(stmtChild.getKind())) {
                            return stmtChild;
                        }
                    }
                } else if ("IfStmt".equals(child.getKind())) {
                    return child;
                }
            }
        }

        return null;
    }

    /**
     * Processa uma estrutura switch-like de if-else-if em cascata,
     * gerando código OLLIR otimizado com labels em ordem reversa.
     */
    private String processSwitchLikeStructure(JmmNode rootNode) {
        System.out.println("DEBUG: Processando estrutura switch-like em cascata");

        // Coletar todos os componentes da cascata
        List<JmmNode> conditions = new ArrayList<>();
        List<JmmNode> thenBlocks = new ArrayList<>();
        List<JmmNode> elseBlocks = new ArrayList<>();

        // Extrair todos os nós da cascata
        collectCascadingIfNodes(rootNode, conditions, thenBlocks, elseBlocks);

        // Número de ifs na cascata
        int ifCount = conditions.size();
        System.out.println("DEBUG: Encontrados " + ifCount + " níveis de if em cascata");

        // Obter labels em ordem reversa
        String[] labelIndices = labelManager.getSwitchLabels(ifCount);

        // Marcar todos os nós como processados para evitar duplicação
        markNodesAsProcessed(rootNode);

        // Construir o código OLLIR otimizado
        StringBuilder code = new StringBuilder();

        // 1. Primeiro todas as condições e jumps
        for (int i = 0; i < ifCount; i++) {
            JmmNode condition = conditions.get(i);

            // Gerar código para a condição
            OllirExprResult conditionResult = exprVisitor.visit(condition);
            code.append(conditionResult.getComputation());

            // Adicionar o jump condicional (if (tmp0.bool) goto then5;)
            code.append("if (")
                    .append(conditionResult.getCode())
                    .append(") goto then")
                    .append(labelIndices[i])
                    .append(END_STMT);
        }

        // 2. Código para o caso else final (default case)
        JmmNode finalElseBlock = null;
        if (!elseBlocks.isEmpty()) {
            finalElseBlock = elseBlocks.get(elseBlocks.size() - 1);
        }

        if (finalElseBlock != null) {
            String elseCode = extractBlockContent(finalElseBlock);
            code.append(elseCode);
        }

        // 3. Blocos then em ordem reversa
        for (int i = 0; i < ifCount; i++) {
            JmmNode thenBlock = thenBlocks.get(i);

            // Adicionar goto para o final após o bloco then
            code.append("goto endif").append(labelIndices[i]).append(END_STMT);

            // Label do bloco then
            code.append("then").append(labelIndices[i]).append(":\n");

            // Código do bloco then
            String thenCode = extractBlockContent(thenBlock);
            code.append(thenCode);

            // Label do final do if
            code.append("endif").append(labelIndices[i]).append(":\n\n");
        }

        return code.toString();
    }

    /**
     * Marca todos os nós de uma cascata como processados para evitar processamento duplicado
     */
    private void markNodesAsProcessed(JmmNode rootNode) {
        processedSwitchNodes.add(rootNode);

        // Encontrar o nó IfStatement
        JmmNode ifStatement = findIfStatement(rootNode);
        if (ifStatement == null || ifStatement.getNumChildren() <= 2) return;

        // Obter o bloco else
        JmmNode elseNode = ifStatement.getChild(2);

        // Verificar se o else contém outro if
        JmmNode nestedIf = findNestedIfInElse(elseNode);
        if (nestedIf != null) {
            // Marcar recursivamente
            markNodesAsProcessed(nestedIf);
        }
    }

    /**
     * Coleta todos os nós de uma cascata if-else-if
     */
    private void collectCascadingIfNodes(JmmNode ifNode,
                                         List<JmmNode> conditions,
                                         List<JmmNode> thenBlocks,
                                         List<JmmNode> elseBlocks) {
        // Encontrar o nó IfStatement
        JmmNode ifStatement = findIfStatement(ifNode);
        if (ifStatement == null || ifStatement.getNumChildren() < 2) return;

        // Extrair componentes
        JmmNode conditionNode = ifStatement.getChild(0);
        JmmNode thenNode = ifStatement.getChild(1);
        JmmNode elseNode = null;

        if (ifStatement.getNumChildren() > 2) {
            elseNode = ifStatement.getChild(2);
        }

        // Adicionar às listas
        conditions.add(conditionNode);
        thenBlocks.add(thenNode);

        // Verificar se existe um próximo nível
        JmmNode nestedIf = findNestedIfInElse(elseNode);

        if (nestedIf != null) {
            // Adicionar null como marcador (será substituído pelo último else)
            elseBlocks.add(null);

            // Continuar recursão com o próximo if
            collectCascadingIfNodes(nestedIf, conditions, thenBlocks, elseBlocks);
        } else {
            // Fim da cascata - adicionar o else final
            elseBlocks.add(elseNode);
        }
    }

    /**
     * Extrai o conteúdo de um bloco, ignorando nós intermediários
     */
    private String extractBlockContent(JmmNode blockNode) {
        if (blockNode == null) return "";

        // Para blocos de statement, extrair o conteúdo interno
        if ("BlockStmt".equals(blockNode.getKind())) {
            StringBuilder content = new StringBuilder();
            for (JmmNode child : blockNode.getChildren()) {
                content.append(visit(child));
            }
            return content.toString();
        }
        // Para nós STMT, extrair o conteúdo
        else if ("STMT".equals(blockNode.getKind())) {
            StringBuilder content = new StringBuilder();
            for (JmmNode child : blockNode.getChildren()) {
                content.append(visit(child));
            }
            return content.toString();
        }

        // Se não for um bloco, processar diretamente
        return visit(blockNode);
    }

    /**
     * Processa um if statement regular (não em cascata)
     */
    private String processRegularIfStatement(JmmNode node) {
        // Extract condition and blocks
        JmmNode conditionNode = null;
        JmmNode thenNode = null;
        JmmNode elseNode = null;

        // Handle different AST structures flexibly
        if (node.getNumChildren() == 1 && "IfStatement".equals(node.getChild(0).getKind())) {
            JmmNode ifStatement = node.getChild(0);
            if (ifStatement.getNumChildren() >= 2) {
                conditionNode = ifStatement.getChild(0);
                thenNode = ifStatement.getChild(1);
                if (ifStatement.getNumChildren() > 2) {
                    elseNode = ifStatement.getChild(2);
                }
            }
        } else if (node.getNumChildren() >= 2) {
            conditionNode = node.getChild(0);
            thenNode = node.getChild(1);
            if (node.getNumChildren() > 2) {
                elseNode = node.getChild(2);
            }
        }

        // Validate structure
        if (conditionNode == null || thenNode == null) {
            String errorMsg = "Invalid if statement structure at line " + node.getLine();
            System.out.println("DEBUG: ERRO: " + errorMsg);
            errors.add(errorMsg);
            return "";
        }

        // Generate labels
        int labelNum = nextLabel();
        String thenLabel = "then" + labelNum;
        String endLabel = "endif" + labelNum;

        StringBuilder code = new StringBuilder();

        // Process condition
        OllirExprResult conditionResult = exprVisitor.visit(conditionNode);
        code.append(conditionResult.getComputation());

        // if (conditionResult) goto thenLabel;
        code.append("if (")
                .append(conditionResult.getCode())
                .append(") goto ")
                .append(thenLabel)
                .append(END_STMT);

        // Include ELSE block (if exists)
        if (elseNode != null) {
            String elseCode = visit(elseNode);
            code.append(elseCode);
        }

        // goto endLabel;
        code.append("goto ").append(endLabel).append(END_STMT);

        // thenLabel:
        code.append(thenLabel).append(":\n");

        // THEN block code
        String thenCode = visit(thenNode);
        code.append(thenCode);

        // endLabel:
        code.append(endLabel).append(":\n");

        return code.toString();
    }
    /**
     * Visits a while statement node.
     * Handles all types of while loop structures without special cases.
     */
    private String visitWhileStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitWhileStmt - Nó: " + node.getKind());

        // Proteção contra loops infinitos
        if (visitedWhileNodes.contains(node)) {
            System.out.println("DEBUG: Nó WHILE já visitado, evitando loop infinito");
            return "";
        }
        visitedWhileNodes.add(node);

        // Extrair condition e body de forma robusta
        JmmNode conditionNode = null;
        JmmNode bodyNode = null;

        // Lidar com diferentes estruturas AST
        if (node.getNumChildren() >= 2) {
            conditionNode = node.getChild(0);
            bodyNode = node.getChild(1);
        } else if (node.getNumChildren() == 1) {
            JmmNode child = node.getChild(0);
            if ("WhileStatement".equals(child.getKind()) && child.getNumChildren() >= 2) {
                conditionNode = child.getChild(0);
                bodyNode = child.getChild(1);
            }
        }

        // Validar estrutura
        if (conditionNode == null || bodyNode == null) {
            visitedWhileNodes.remove(node);
            return "";
        }

        // Gerar labels do loop usando o labelManager
        int labelNum = labelManager.nextLabel(LabelManager.WHILE_STRUCTURE);
        String whileLabel = "while" + labelNum;
        String endLabel = "endif" + labelNum;

        StringBuilder code = new StringBuilder();

        // Label de início do loop
        code.append(whileLabel).append(":\n");

        // Verificar se a condição é um literal booleano
        boolean isOptimizedCondition = false;
        String optimizedCondValue = null;

        if (isBooleanLiteral(conditionNode)) {
            isOptimizedCondition = true;
            optimizedCondValue = isTrueLiteral(conditionNode) ? "1.bool" : "0.bool";
            System.out.println("DEBUG: Condição já otimizada para " + optimizedCondValue);
        }

        // Processar condição
        if (isOptimizedCondition) {
            // Usar diretamente o valor otimizado
            code.append("if (!.bool ").append(optimizedCondValue).append(") goto ")
                    .append(endLabel).append(END_STMT);
        } else {
            // Processar condição dinâmica
            OllirExprResult conditionResult = exprVisitor.visit(conditionNode);
            code.append(conditionResult.getComputation());

            code.append("if (!.bool ").append(conditionResult.getCode())
                    .append(") goto ").append(endLabel).append(END_STMT);
        }

        // Corpo do loop
        String bodyCode = visit(bodyNode);
        code.append(bodyCode);

        // Jump de volta ao início
        code.append("goto ").append(whileLabel).append(END_STMT);

        // Label de fim do loop
        code.append(endLabel).append(":\n");

        // Limpar visitação
        visitedWhileNodes.remove(node);

        return code.toString();
    }

    private boolean isBooleanLiteral(JmmNode node) {
        return node.getKind().equals("TrueLiteral") ||
                node.getKind().equals("FalseLiteral") ||
                (node.hasAttribute("kind") &&
                        ("TrueLiteral".equals(node.get("kind")) || "FalseLiteral".equals(node.get("kind"))));
    }

    private boolean isTrueLiteral(JmmNode node) {
        return node.getKind().equals("TrueLiteral") ||
                (node.hasAttribute("kind") && "TrueLiteral".equals(node.get("kind")));
    }
    /**
     * Visits a block statement node.
     */
    private String visitBlockStmt(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitBlockStmt - Nó: " + node.getKind());
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();
        Set<JmmNode> visitedNodesInBlock = new HashSet<>();

        // Process child statements with loop protection
        for (JmmNode child : node.getChildren()) {
            // Evitar visitação repetida do mesmo nó
            if (visitedNodesInBlock.contains(child)) {
                System.out.println("DEBUG: Nó já visitado neste bloco, pulando: " + child.getKind());
                continue;
            }
            visitedNodesInBlock.add(child);

            System.out.println("DEBUG: Processando filho do bloco: " + child.getKind());

            // Check if this is a STMT node that needs special handling
            if ("STMT".equals(child.getKind())) {
                System.out.println("DEBUG: Processando STMT dentro de bloco");
                for (JmmNode stmtChild : child.getChildren()) {
                    if (!visitedNodesInBlock.contains(stmtChild)) {
                        System.out.println("DEBUG: Processando filho de STMT: " + stmtChild.getKind());
                        String childCode = visit(stmtChild);
                        System.out.println("DEBUG: Código gerado para filho de STMT (tamanho): " + childCode.length());
                        code.append(childCode);
                        visitedNodesInBlock.add(stmtChild);
                    }
                }
            } else {
                // Normal processing for other node types
                String childCode = visit(child);
                System.out.println("DEBUG: Código gerado para filho do bloco (tamanho): " + childCode.length());
                code.append(childCode);
            }
        }

        System.out.println("DEBUG: FIM visitBlockStmt - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Visits an identifier LValue node.
     */
    private String visitIdentifierLValue(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitIdentifierLValue - Nó: " + node.getKind());

        String varName = node.hasAttribute("name") ? node.get("name") : "var";
        System.out.println("DEBUG: Nome da variável: " + varName);

        // Cache the name for later use in assignment
        valueCache.put(node.toString(), varName);
        System.out.println("DEBUG: Nome armazenado no cache com chave: " + node.toString());

        // Determine variable type
        Type varType = resolveVariableType(varName);
        System.out.println("DEBUG: Tipo da variável resolvido: " + varType.getName() + (varType.isArray() ? "[]" : ""));

        String typeStr = ollirTypeUtils.toOllirType(varType);
        System.out.println("DEBUG: Tipo OLLIR da variável: " + typeStr);

        String result = varName + typeStr;
        System.out.println("DEBUG: FIM visitIdentifierLValue - Código gerado: " + result);
        return result;
    }

    /**
     * Visits an array access LValue node.
     */
    private String visitArrayAccessLValue(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitArrayAccessLValue - Nó: " + node.getKind());

        if (!node.hasAttribute("name") || node.getNumChildren() < 1) {
            System.out.println("DEBUG: Array access inválido (sem nome ou índice), retornando padrão");
            return "unknown[0.i32].i32";
        }

        String arrayName = node.get("name");
        System.out.println("DEBUG: Nome do array: " + arrayName);

        JmmNode indexNode = node.getChild(0);
        System.out.println("DEBUG: Nó de índice: " + indexNode.getKind());

        // Processamos a expressão do índice usando o visitor de expressões para obter computações necessárias
        OllirExprResult indexResult = exprVisitor.visit(indexNode);
        System.out.println("DEBUG: Resultado do índice - Computação: " + indexResult.getComputation().trim());
        System.out.println("DEBUG: Resultado do índice - Código: " + indexResult.getCode());

        // Formatação correta para o lado esquerdo: a[index].type
        // Não precisamos criar temporários para o lado esquerdo de uma atribuição
        String result = arrayName + "[" + indexResult.getCode() + "].i32";

        // Retornamos qualquer computação necessária para calcular o índice
        return indexResult.getComputation() + result;
    }
    /**
     * Visits a variable reference expression node.
     */
    private String visitVarRefExpr(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitVarRefExpr - Nó: " + node.getKind());

        // Delegate to expression visitor for full processing
        OllirExprResult result = exprVisitor.visit(node);
        System.out.println("DEBUG: Resultado da expressão - Computação: " + result.getComputation().trim());
        System.out.println("DEBUG: Resultado da expressão - Código: " + result.getCode());

        String fullResult = result.getComputation() + result.getCode();
        System.out.println("DEBUG: FIM visitVarRefExpr - Código gerado (tamanho): " + fullResult.length());
        return fullResult;
    }

    /**
     * Visits a literal node.
     */
    private String visitLiteral(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitLiteral - Nó: " + node.getKind());
        System.out.println("DEBUG: Valor do literal: " + (node.hasAttribute("value") ? node.get("value") : "desconhecido"));

        // Delegate to expression visitor for full processing
        OllirExprResult result = exprVisitor.visit(node);
        System.out.println("DEBUG: Resultado da expressão - Computação: " + result.getComputation().trim());
        System.out.println("DEBUG: Resultado da expressão - Código: " + result.getCode());

        String fullResult = result.getComputation() + result.getCode();
        System.out.println("DEBUG: FIM visitLiteral - Código gerado: " + fullResult);
        return fullResult;
    }

    /**
     * Visits a direct method call node.
     */
    private String visitDirectMethodCall(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitDirectMethodCall - Nó: " + node.getKind());
        System.out.println("DEBUG: Método: " + (node.hasAttribute("method") ? node.get("method") : "desconhecido"));

        // Delegate to expression visitor for full processing
        OllirExprResult result = exprVisitor.visit(node);
        System.out.println("DEBUG: Resultado da expressão - Computação: " + result.getComputation().trim());
        System.out.println("DEBUG: Resultado da expressão - Código: " + result.getCode());

        String fullResult = result.getComputation() + result.getCode();
        System.out.println("DEBUG: FIM visitDirectMethodCall - Código gerado (tamanho): " + fullResult.length());
        return fullResult;
    }

    /**
     * Visits a method call expression node.
     */
    private String visitMethodCallExpr(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitMethodCallExpr - Nó: " + node.getKind());
        System.out.println("DEBUG: Método: " + (node.hasAttribute("method") ? node.get("method") : "desconhecido"));

        // Delegate to expression visitor for full processing
        OllirExprResult result = exprVisitor.visit(node);
        System.out.println("DEBUG: Resultado da expressão - Computação: " + result.getComputation().trim());
        System.out.println("DEBUG: Resultado da expressão - Código: " + result.getCode());

        String fullResult = result.getComputation() + result.getCode();
        System.out.println("DEBUG: FIM visitMethodCallExpr - Código gerado (tamanho): " + fullResult.length());
        return fullResult;
    }

    /**
     * Default visit method for unmatched node types.
     */
    private String defaultVisit(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO defaultVisit - Nó: " + node.getKind() + " (tipo não reconhecido)");
        System.out.println("DEBUG: Número de filhos: " + node.getNumChildren());

        StringBuilder code = new StringBuilder();

        // Visit all children and concatenate their results
        for (JmmNode child : node.getChildren()) {
            System.out.println("DEBUG: Processando filho não reconhecido: " + child.getKind());
            String childCode = visit(child);
            System.out.println("DEBUG: Código gerado para filho (tamanho): " + childCode.length());
            code.append(childCode);
        }

        System.out.println("DEBUG: FIM defaultVisit - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    // ===== Helper methods =====

    /**
     * Generates a default class when none is provided in the AST.
     */
    private String generateDefaultClass() {
        System.out.println("DEBUG: INICIO generateDefaultClass");
        StringBuilder code = new StringBuilder();
        String className = symbolTable.getClassName();
        String superClass = symbolTable.getSuper();
        System.out.println("DEBUG: Nome da classe: " + className);
        System.out.println("DEBUG: Superclasse: " + superClass);

        if (className == null || className.isEmpty()) {
            className = "DefaultClass";
            System.out.println("DEBUG: Nome da classe vazio, usando DefaultClass");
        }

        code.append(className);

        if (superClass != null && !superClass.isEmpty()) {
            System.out.println("DEBUG: Adicionando cláusula extends");
            code.append(" extends ").append(superClass);
        }

        code.append(L_BRACKET).append(NL);

        // Add fields
        System.out.println("DEBUG: Adicionando campos da tabela de símbolos");
        for (Symbol field : symbolTable.getFields()) {
            String typeStr = ollirTypeUtils.toOllirType(field.getType());
            String fieldDecl = FIELD_PREFIX + field.getName() + typeStr + END_STMT;
            System.out.println("DEBUG: Campo adicionado: " + fieldDecl.trim());
            code.append(fieldDecl);
        }

        if (!symbolTable.getFields().isEmpty()) {
            code.append(NL);
        }

        // Add constructor
        System.out.println("DEBUG: Gerando construtor padrão");
        String constructor = generateConstructor(className);
        System.out.println("DEBUG: Construtor gerado: " + constructor.trim());
        code.append(constructor);

        // Add methods
        System.out.println("DEBUG: Adicionando métodos da tabela de símbolos");
        for (String methodName : symbolTable.getMethods()) {
            System.out.println("DEBUG: Gerando método: " + methodName);
            String methodCode = generateMethodFromSymbolTable(methodName);
            System.out.println("DEBUG: Método gerado (tamanho): " + methodCode.length());
            code.append(methodCode);
        }

        code.append(R_BRACKET);
        System.out.println("DEBUG: FIM generateDefaultClass - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Generates a standard constructor for a class.
     */
    private String generateConstructor(String className) {
        StringBuilder constructor = new StringBuilder();
        constructor.append(CONSTRUCT_PREFIX).append(className).append("().V")
                .append(L_BRACKET);
        constructor.append(TAB).append(INVOKE_SPECIAL)
                .append("(this, \"<init>\").V").append(END_STMT);
        constructor.append(R_BRACKET).append(NL);
        return constructor.toString();
    }

    /**
     * Generates a method from symbol table information.
     */
    private String generateMethodFromSymbolTable(String methodName) {
        System.out.println("DEBUG: INICIO generateMethodFromSymbolTable para: " + methodName);
        StringBuilder code = new StringBuilder(METHOD_PREFIX + PUBLIC);

        // Handle main method
        boolean isStatic = methodName.equals("main");
        if (isStatic) {
            System.out.println("DEBUG: Método main, adicionando static");
            code.append(STATIC);
        }

        // Method signature
        code.append(methodName).append("(");

        // Parameters
        System.out.println("DEBUG: Processando parâmetros do método");
        List<String> params = new ArrayList<>();
        for (Symbol param : symbolTable.getParameters(methodName)) {
            String typeStr = ollirTypeUtils.toOllirType(param.getType());
            String paramCode = param.getName() + typeStr;
            System.out.println("DEBUG: Parâmetro adicionado: " + paramCode);
            params.add(paramCode);
        }

        code.append(String.join(", ", params));
        code.append(")");

        // Return type
        Type returnType = symbolTable.getReturnType(methodName);
        String returnTypeStr = ollirTypeUtils.toOllirType(returnType);
        System.out.println("DEBUG: Tipo de retorno: " + returnType.getName() + " -> " + returnTypeStr);
        code.append(returnTypeStr).append(L_BRACKET);

        // Local variables
        System.out.println("DEBUG: Processando variáveis locais do método");
        for (Symbol local : symbolTable.getLocalVariables(methodName)) {
            String typeStr = ollirTypeUtils.toOllirType(local.getType());
            String defaultValue = getDefaultValue(local.getType());
            String localVarCode = TAB + local.getName() + typeStr + SPACE +
                    ASSIGN + typeStr + SPACE +
                    defaultValue + typeStr + END_STMT;
            System.out.println("DEBUG: Variável local adicionada: " + localVarCode.trim());
            code.append(localVarCode);
        }

        // Default return statement
        System.out.println("DEBUG: Adicionando return padrão");
        code.append(TAB).append("ret").append(returnTypeStr);

        if (!"void".equals(returnType.getName())) {
            String defaultValue = getDefaultValue(returnType);
            System.out.println("DEBUG: Adicionando valor padrão para retorno não-void: " + defaultValue);
            code.append(SPACE).append(defaultValue).append(returnTypeStr);
        }

        code.append(END_STMT).append(R_BRACKET).append(NL);
        System.out.println("DEBUG: FIM generateMethodFromSymbolTable - Código gerado (tamanho): " + code.length());
        return code.toString();
    }

    /**
     * Processes local variable declarations in a method.
     * In OLLIR, we don't generate code for variable declarations,
     * only for variable assignments.
     */
    private void processMethodLocalVariables(JmmNode methodNode, StringBuilder code) {
        // Track which variables are declared but don't generate initialization code
        Set<String> declaredVars = new HashSet<>();

        // Process variable declarations in statement blocks to collect declared vars
        for (JmmNode child : methodNode.getChildren()) {
            if ("STMT".equals(child.getKind())) {
                for (JmmNode stmtChild : child.getChildren()) {
                    if ("VarDeclarationStmt".equals(stmtChild.getKind())) {
                        // Just collect variable names but don't generate initialization code
                        collectDeclaredVariables(stmtChild, declaredVars);
                    }
                }
            } else if ("VarDeclarationStmt".equals(child.getKind())) {
                // Just collect variable names but don't generate initialization code
                collectDeclaredVariables(child, declaredVars);
            }
        }

        // We don't need to add initialization code for variables from symbol table either
        // Just make sure they're tracked as declared
        if (currentMethod != null) {
            for (Symbol local : symbolTable.getLocalVariables(currentMethod)) {
                declaredVars.add(local.getName());
            }
        }
    }

    /**
     * Processes statements in a method body.
     */
    /**
     * Processa instruções do método, adicionando retorno apenas quando necessário.
     */
    /**
     * Processa instruções do método, adicionando retorno apenas quando necessário.
     */
    private void processMethodStatements(JmmNode methodNode, StringBuilder code) {
        System.out.println("DEBUG: INICIO processMethodStatements para método: " + currentMethod);

        boolean hasStatements = false;
        boolean hasReturn = false;

        // Processar instruções do método
        for (JmmNode child : methodNode.getChildren()) {
            if ("STMT".equals(child.getKind())) {
                // Processar cada instrução no bloco
                for (JmmNode stmtChild : child.getChildren()) {
                    if (!"VarDeclarationStmt".equals(stmtChild.getKind())) {
                        String stmtCode = visit(stmtChild);
                        code.append(stmtCode);
                        hasStatements = true;

                        if ("RETURN_STMT".equals(stmtChild.getKind())) {
                            hasReturn = true;
                        }
                    }
                }
            } else if ("RETURN_STMT".equals(child.getKind())) {
                String returnCode = visit(child);
                code.append(returnCode);
                hasReturn = true;
                hasStatements = true;
            } else if ("ExpressionStmt".equals(child.getKind()) ||
                    "IfStmt".equals(child.getKind()) ||
                    "WhileStmt".equals(child.getKind())) {
                String stmtCode = visit(child);
                code.append(stmtCode);
                hasStatements = true;
            }
        }

        // Adicionar return apenas se necessário
        boolean isMainMethod = "main".equals(currentMethod);
        Type returnType = currentMethod != null ? symbolTable.getReturnType(currentMethod) : null;
        boolean isVoidMethod = returnType == null || "void".equals(returnType.getName());

        // Não adicionar return se for o método main E não tiver outras instruções
        if (!hasReturn) {
            String returnTypeStr = isVoidMethod ? ".V" : ollirTypeUtils.toOllirType(returnType);

            if (isVoidMethod) {
                code.append("ret.V").append(END_STMT);
            } else {
                String defaultValue = getDefaultValue(returnType);
                code.append("ret").append(returnTypeStr).append(SPACE)
                        .append(defaultValue).append(returnTypeStr).append(END_STMT);
            }
        }

        System.out.println("DEBUG: FIM processMethodStatements");
    }
    /**
     * Adds a default return statement to a method body.
     */
    private void addDefaultReturn(StringBuilder code, String returnType) {
        System.out.println("DEBUG: INICIO addDefaultReturn para tipo: " + returnType);
        StringBuilder returnStmt = new StringBuilder("ret" + returnType);

        if (!returnType.equals(".V")) {
            System.out.println("DEBUG: Adicionando valor padrão para tipo não-void");
            String defaultValue;
            if (returnType.equals(".bool")) {
                defaultValue = "0.bool";
            } else if (returnType.startsWith(".array")) {
                defaultValue = "new(array, 0.i32)" + returnType;
            } else if (returnType.equals(".i32")) {
                defaultValue = "0.i32";
            } else {
                defaultValue = "null" + returnType;
            }
            System.out.println("DEBUG: Valor padrão selecionado: " + defaultValue);
            returnStmt.append(SPACE).append(defaultValue);
        }

        returnStmt.append(END_STMT);
        System.out.println("DEBUG: Return statement padrão: " + returnStmt.toString().trim());
        code.append(returnStmt);
        System.out.println("DEBUG: FIM addDefaultReturn");
    }

    private String visitMethodDecl(JmmNode node, Void context) {
        System.out.println("DEBUG: INICIO visitMethodDecl - Nó: " + node.getKind());
        StringBuilder code = new StringBuilder(METHOD_PREFIX);

        // Definir método atual para contexto
        String methodName = node.hasAttribute("name") ? node.get("name") : "unknown";
        System.out.println("DEBUG: Nome do método: " + methodName);
        this.currentMethod = methodName;
        this.exprVisitor.setCurrentMethod(methodName);

        // Configurar o LabelManager para este método
        this.labelManager.setCurrentMethod(methodName);

        // Adicionar modificadores de acesso
        boolean isPublic = !node.hasAttribute("isPublic") || node.getBoolean("isPublic", true);
        boolean isStatic = node.getBoolean("isStatic", false) || "main".equals(methodName);
        boolean hasVarargs = hasVarargParameter(node);

        if (isPublic) {
            code.append(PUBLIC);
        } else {
            code.append(PRIVATE);
        }

        if (isStatic) {
            code.append(STATIC);
        }

        // Adicionar varargs quando necessário
        if (hasVarargs) {
            code.append("varargs ");
        }

        code.append(methodName).append("(");

        // Processar parâmetros
        List<String> params = new ArrayList<>();
        if (!methodName.equals("main")) {
            // Obter parâmetros da symbol table
            List<Symbol> methodParams = symbolTable.getParameters(methodName);
            if (methodParams != null) {
                for (Symbol param : methodParams) {
                    String paramName = isReservedKeyword(param.getName()) ?
                            "\"" + param.getName() + "\"" : param.getName();
                    String paramCode = paramName + ollirTypeUtils.toOllirType(param.getType());
                    params.add(paramCode);
                }
            }
        } else {
            // Tratamento especial para método main
            params.add("args.array.String");
        }

        code.append(String.join(", ", params));
        code.append(")");

        // Processar tipo de retorno
        Type returnType = symbolTable.getReturnType(methodName);
        String returnTypeStr = returnType != null ? ollirTypeUtils.toOllirType(returnType) : ".V";
        code.append(returnTypeStr).append(" {").append("\n");

        // Processar corpo do método
        StringBuilder bodyCode = new StringBuilder();
        processMethodStatements(node, bodyCode);

        // Verificar se há return statement
        boolean hasReturn = methodBodyHasReturn(node);
        if (!hasReturn && !returnTypeStr.equals(".V")) {
            // Adicionar return padrão para métodos não-void
            bodyCode.append("    ret").append(returnTypeStr).append(" ");
            bodyCode.append(getDefaultValue(returnType)).append(returnTypeStr);
            bodyCode.append(END_STMT);
        }

        // Aplicar formatação consistente ao corpo do método
        String formattedBody = applyConsistentFormatting(bodyCode.toString());
        code.append(formattedBody);

        code.append("}").append("\n");

        // Limpar contexto do método
        this.currentMethod = null;
        this.exprVisitor.setCurrentMethod(null);
        this.labelManager.resetMethod();

        return code.toString();
    }
    /**
     * Helper method to check if a word is a reserved keyword in OLLIR
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
     * Generates an assignment statement.
     */
    private String generateAssignment(JmmNode lhs, JmmNode rhs) {
        System.out.println("DEBUG: INICIO generateAssignment");
        System.out.println("DEBUG: LHS: " + lhs.getKind() + ", RHS: " + rhs.getKind());

        StringBuilder code = new StringBuilder();

        // Processamos o lado direito da atribuição primeiro
        OllirExprResult rhsResult = exprVisitor.visit(rhs);
        code.append(rhsResult.getComputation());

        // Para atribuição a elementos de array
        if ("ArrayAccessLValue".equals(lhs.getKind())) {
            // O formato correto é: array[index].type := value
            String lhsCode = visit(lhs);

            // Se há computação no LHS (para calcular o índice), a adicionamos
            if (lhsCode.contains(";")) {
                // Extraímos a parte da computação e a parte do acesso ao array
                int lastSemicolon = lhsCode.lastIndexOf(";") + 1;
                code.append(lhsCode.substring(0, lastSemicolon));
                lhsCode = lhsCode.substring(lastSemicolon).trim();
            }

            // Geramos a instrução de atribuição para o array
            code.append(lhsCode)
                    .append(" :=.i32 ")
                    .append(rhsResult.getCode())
                    .append(END_STMT);
        }
        // Para atribuição a variáveis normais ou campos
        else if ("IdentifierLValue".equals(lhs.getKind()) && lhs.hasAttribute("name")) {
            String varName = lhs.get("name");
            System.out.println("DEBUG: Analisando variável: " + varName);

            // Check if variable name is a reserved keyword
            String safeVarName = isReservedKeyword(varName) ?
                    "\"" + varName + "\"" : varName;

            // Obter tipo da variável
            Type varType = resolveVariableType(varName);
            String typeStr = ollirTypeUtils.toOllirType(varType);

            // Decisão crítica: determinar se a variável é um campo ou local
            boolean isField = isFieldVariable(varName, lhs);

            if (isField) {
                // Para campos, usar putfield com tipo correto
                String assignStmt = "putfield(this, " + safeVarName + typeStr + ", " +
                        rhsResult.getCode() + ").V" + END_STMT;
                System.out.println("DEBUG: Gerando putfield para campo: " + assignStmt);
                code.append(assignStmt);
            } else {
                // Para variáveis locais, usar atribuição direta
                String assignStmt = safeVarName + typeStr + SPACE +
                        ASSIGN + typeStr + SPACE +
                        rhsResult.getCode() + END_STMT;
                System.out.println("DEBUG: Gerando atribuição para variável local: " + assignStmt);
                code.append(assignStmt);
            }
        }

        return code.toString();
    }

    /**
     * Verifica se uma variável é um campo da classe ou variável local.
     * Prioriza verificar variáveis locais e parâmetros antes de assumir que é um campo.
     */
    private boolean isFieldVariable(String varName, JmmNode context) {
        // Se estamos em um método, verificamos primeiro as variáveis locais e parâmetros
        if (currentMethod != null) {
            // Verificar parâmetros
            for (Symbol param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(varName)) {
                    return false; // É um parâmetro, não um campo
                }
            }

            // Verificar variáveis locais declaradas no método
            for (Symbol local : symbolTable.getLocalVariables(currentMethod)) {
                if (local.getName().equals(varName)) {
                    return false; // É uma variável local, não um campo
                }
            }

            // Verificar variáveis declaradas em blocos (que podem não estar na symbol table)
            // Esta verificação adicional é crucial para o correto funcionamento
            if (context != null) {
                JmmNode methodNode = findEnclosingMethodNode(context);
                if (methodNode != null) {
                    if (hasLocalVarDeclaration(methodNode, varName)) {
                        return false; // É uma variável local declarada em algum bloco
                    }
                }
            }
        }

        // Verificar se é um campo da classe
        for (Symbol field : symbolTable.getFields()) {
            if (field.getName().equals(varName)) {
                return true; // É um campo
            }
        }

        // Se chegou aqui, não encontramos como variável local nem como campo
        // A decisão padrão é que seja um campo (para compatibilidade)
        return true;
    }

    /**
     * Procura a declaração de uma variável local em um nó e seus filhos.
     */
    private boolean hasLocalVarDeclaration(JmmNode node, String varName) {
        // Verificar se é uma declaração de variável com este nome
        if ("VAR_DECL".equals(node.getKind()) &&
                node.hasAttribute("name") &&
                node.get("name").equals(varName)) {
            return true;
        }

        // Verificar recursivamente nos filhos
        for (JmmNode child : node.getChildren()) {
            if (hasLocalVarDeclaration(child, varName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Encontra o nó de método que contém o nó atual.
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
     * Collects declared variables in a statement.
     */
    private void collectDeclaredVariables(JmmNode node, Set<String> declaredVars) {
        System.out.println("DEBUG: collectDeclaredVariables para nó: " + node.getKind());

        if ("VarDeclarationStmt".equals(node.getKind())) {
            System.out.println("DEBUG: Encontrada declaração de variável");
            for (JmmNode child : node.getChildren()) {
                if ("VAR_DECL".equals(child.getKind()) && child.hasAttribute("name")) {
                    String varName = child.get("name");
                    System.out.println("DEBUG: Adicionando variável declarada: " + varName);
                    declaredVars.add(varName);
                }
            }
        }

        for (JmmNode child : node.getChildren()) {
            collectDeclaredVariables(child, declaredVars);
        }
    }

    /**
     * Builds an import path from an import declaration node.
     */
    private String buildImportPath(JmmNode node) {
        System.out.println("DEBUG: INICIO buildImportPath");

        if (node.hasAttribute("firstPart")) {
            String firstPart = node.get("firstPart");
            System.out.println("DEBUG: Primeira parte do caminho: " + firstPart);
            StringBuilder path = new StringBuilder(firstPart);

            if (node.hasAttribute("otherParts")) {
                String otherParts = node.get("otherParts");
                System.out.println("DEBUG: Outras partes do caminho (bruto): " + otherParts);
                // Remove brackets and split by comma
                if (otherParts.startsWith("[") && otherParts.endsWith("]")) {
                    otherParts = otherParts.substring(1, otherParts.length() - 1);
                    System.out.println("DEBUG: Outras partes sem colchetes: " + otherParts);
                }

                for (String part : otherParts.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        System.out.println("DEBUG: Adicionando parte ao caminho: " + trimmed);
                        path.append(".").append(trimmed);
                    }
                }
            }

            String result = path.toString();
            System.out.println("DEBUG: Caminho de importação construído: " + result);
            return result;
        }

        System.out.println("DEBUG: FIM buildImportPath - Sem firstPart, retornando null");
        return null;
    }

    /**
     * Builds a qualified name from a qualified name node.
     */
    private String buildQualifiedName(JmmNode node) {
        System.out.println("DEBUG: INICIO buildQualifiedName para nó: " + node.getKind());

        if (node.hasAttribute("firstPart")) {
            String firstPart = node.get("firstPart");
            System.out.println("DEBUG: Primeira parte do nome qualificado: " + firstPart);
            StringBuilder path = new StringBuilder(firstPart);

            if (node.hasAttribute("otherParts")) {
                String otherParts = node.get("otherParts");
                System.out.println("DEBUG: Outras partes do nome (bruto): " + otherParts);
                // Remove brackets and split by comma
                if (otherParts.startsWith("[") && otherParts.endsWith("]")) {
                    otherParts = otherParts.substring(1, otherParts.length() - 1);
                    System.out.println("DEBUG: Outras partes sem colchetes: " + otherParts);
                }

                for (String part : otherParts.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        System.out.println("DEBUG: Adicionando parte ao nome: " + trimmed);
                        path.append(".").append(trimmed);
                    }
                }
            }

            String result = path.toString();
            System.out.println("DEBUG: Nome qualificado construído: " + result);
            return result;
        }

        // Build from children
        System.out.println("DEBUG: Construindo nome qualificado a partir dos filhos");
        StringJoiner joiner = new StringJoiner(".");
        boolean foundAny = false;

        for (JmmNode child : node.getChildren()) {
            if (child.hasAttribute("name")) {
                String name = child.get("name");
                System.out.println("DEBUG: Adicionando nome de filho: " + name);
                joiner.add(name);
                foundAny = true;
            } else if (child.hasAttribute("value")) {
                String value = child.get("value");
                System.out.println("DEBUG: Adicionando valor de filho: " + value);
                joiner.add(value);
                foundAny = true;
            }
        }

        String result = foundAny ? joiner.toString() : "";
        System.out.println("DEBUG: FIM buildQualifiedName - Resultado: " + result);
        return result;
    }

    /**
     * Resolves variable type from the symbol table.
     */
    private Type resolveVariableType(String varName) {
        System.out.println("DEBUG: INICIO resolveVariableType para: " + varName);
        System.out.println("DEBUG: Método atual: " + currentMethod);

        // Check method parameters
        if (currentMethod != null) {
            System.out.println("DEBUG: Verificando parâmetros do método: " + currentMethod);
            for (Symbol param : symbolTable.getParameters(currentMethod)) {
                if (param.getName().equals(varName)) {
                    System.out.println("DEBUG: Encontrado como parâmetro do método");
                    return param.getType();
                }
            }

            // Check local variables
            System.out.println("DEBUG: Verificando variáveis locais do método: " + currentMethod);
            for (Symbol local : symbolTable.getLocalVariables(currentMethod)) {
                if (local.getName().equals(varName)) {
                    System.out.println("DEBUG: Encontrado como variável local do método");
                    return local.getType();
                }
            }
        }

        // Check fields
        System.out.println("DEBUG: Verificando campos da classe");
        for (Symbol field : symbolTable.getFields()) {
            if (field.getName().equals(varName)) {
                System.out.println("DEBUG: Encontrado como campo da classe");
                return field.getType();
            }
        }

        // Default to int if not found
        System.out.println("DEBUG: Tipo não encontrado, usando int como padrão");
        return TypeUtils.newIntType();
    }

    /**
     * Gets the default value for a type.
     */
    private String getDefaultValue(Type type) {
        System.out.println("DEBUG: getDefaultValue para tipo: " + type.getName() + (type.isArray() ? "[]" : ""));
        String defaultValue;

        if (type.isArray()) {
            defaultValue = "new(array, 0.i32)";
        } else if ("int".equals(type.getName())) {
            defaultValue = "0";
        } else if ("boolean".equals(type.getName())) {
            defaultValue = "0";
        } else {
            defaultValue = "null";
        }

        System.out.println("DEBUG: Valor padrão selecionado: " + defaultValue);
        return defaultValue;
    }

    /**
     * Extracts the type string from a variable reference.
     */
    private String extractTypeString(String code) {
        System.out.println("DEBUG: extractTypeString de: " + code);
        int dotIndex = code.indexOf('.');
        if (dotIndex >= 0) {
            String typeStr = code.substring(dotIndex);
            System.out.println("DEBUG: Tipo extraído: " + typeStr);
            return typeStr;
        }
        System.out.println("DEBUG: Nenhum tipo encontrado, usando .i32 como padrão");
        return ".i32"; // Default to int
    }

    /**
     * Adds indentation to multi-line code.
     */
    private String addIndentation(String code) {
        System.out.println("DEBUG: INICIO addIndentation para código (tamanho): " + code.length());
        StringBuilder indented = new StringBuilder();
        for (String line : code.split("\n")) {
            if (!line.isEmpty()) {
                indented.append(TAB).append(line).append("\n");
            }
        }
        System.out.println("DEBUG: FIM addIndentation - Código indentado (tamanho): " + indented.length());
        return indented.toString();
    }



    /**
     * Gets the list of errors encountered during generation.
     */
    public List<String> getErrors() {
        List<String> allErrors = new ArrayList<>(errors);
        allErrors.addAll(exprVisitor.getErrors());
        System.out.println("DEBUG: Total de erros encontrados: " + allErrors.size());
        return allErrors;
    }

    /**
     * Checks if any errors occurred during processing.
     */
    public boolean hasErrors() {
        boolean hasAnyErrors = !errors.isEmpty() || exprVisitor.hasErrors();
        System.out.println("DEBUG: Verificação de erros: " + hasAnyErrors);
        return hasAnyErrors;
    }

    /**
     * Formata blocos específicos de controle de fluxo com indentação consistente.
     * Otimizado para estruturas if-then-endif e switch-like statements.
     *
     * @param blockCode Código do bloco condicional
     * @param indentLevel Nível de indentação base
     * @return Código do bloco formatado
     */
    private String formatConditionalBlock(String blockCode, int indentLevel) {
        String[] lines = blockCode.split("\n");
        StringBuilder formatted = new StringBuilder();
        String baseIndent = "    ".repeat(indentLevel);
        String codeIndent = "    ".repeat(indentLevel + 1);

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("if ") || line.startsWith("goto ")) {
                // Instruções de controle de fluxo principais
                formatted.append(baseIndent).append(line).append("\n");
            } else if (line.endsWith(":")) {
                // Labels (then: e endif:)
                formatted.append(baseIndent).append(line).append("\n");
            } else {
                // Corpo do bloco (instruções)
                formatted.append(codeIndent).append(line).append("\n");
            }
        }

        return formatted.toString();
    }
    /**
     * Formata o código OLLIR com indentação consistente.
     * Aplica regras específicas para diferentes elementos do código.
     *
     * @param ollirCode Código OLLIR a ser formatado
     * @return Código OLLIR formatado com indentação consistente
     */
    public String formatOllirCode(String ollirCode) {
        String[] lines = ollirCode.split("\n");
        StringBuilder formatted = new StringBuilder();
        boolean inMethod = false;
        String indentation = "    "; // 4 espaços para indentação padrão

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Linhas vazias são mantidas sem indentação
            if (line.isEmpty()) {
                formatted.append("\n");
                continue;
            }

            // Identificar início de método
            if (line.startsWith(".method")) {
                inMethod = true;
                formatted.append(line).append(" {\n");
                continue;
            }

            // Identificar fim de método
            if (line.equals("}") && inMethod) {
                inMethod = false;
                formatted.append("}\n");
                continue;
            }

            // Dentro de método, aplicar regras de formatação específicas
            if (inMethod) {
                // Linhas que são labels (terminam com :)
                if (line.endsWith(":")) {
                    formatted.append(indentation).append(line).append("\n");
                }
                // Instrução ret deve ter indentação adicional
                else if (line.startsWith("ret")) {
                    formatted.append(indentation).append(indentation).append(line).append("\n");
                }
                // Outras instruções mantêm indentação consistente
                else {
                    formatted.append(indentation).append(indentation).append(line).append("\n");
                }
            } else {
                // Fora de método (classe, construtor, etc.)
                formatted.append(line).append("\n");
            }
        }

        return formatted.toString();
    }
    /**
     * Aplica formatação consistente ao código OLLIR dentro de um método.
     * Mantém indentação uniforme e alinhamento para melhor legibilidade.
     *
     * @param methodBody Corpo do método a ser formatado
     * @return Corpo do método com formatação consistente
     */
    private String applyConsistentFormatting(String methodBody) {
        String[] lines = methodBody.split("\n");
        StringBuilder formatted = new StringBuilder();
        String indentation = "    "; // Indentação padrão de 4 espaços

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) {
                formatted.append("\n");
                continue;
            }

            // Aplicar regras específicas de formatação

            // Regra 1: Labels (linhas que terminam com :) recebem indentação básica
            if (trimmedLine.endsWith(":")) {
                formatted.append(indentation).append(trimmedLine).append("\n");
            }
            // Regra 2: Instruções de fluxo (if, goto) recebem indentação básica
            else if (trimmedLine.startsWith("if ") || trimmedLine.startsWith("goto ")) {
                formatted.append(indentation).append(trimmedLine).append("\n");
            }
            // Regra 3: Instruções de retorno (ret) recebem indentação básica
            else if (trimmedLine.startsWith("ret")) {
                formatted.append(indentation).append(trimmedLine).append("\n");
            }
            // Regra 4: Outras instruções recebem indentação extra
            else {
                formatted.append(indentation).append(indentation).append(trimmedLine).append("\n");
            }
        }

        return formatted.toString();
    }
}