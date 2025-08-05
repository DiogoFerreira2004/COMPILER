package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.ArrayType;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JmmSymbolTableBuilder {

    private List<Report> reports;
    private static final boolean DEBUG = true;

    private Map<String, String> methodSignaturesToNames;
    private Map<String, List<Symbol>> methodSignaturesToParams;
    private Map<String, Type> methodSignaturesToReturnTypes;

    public List<Report> getReports() {
        return reports;
    }

    private Report newError(JmmNode node, String message) {
        return Report.newError(Stage.SEMANTIC, node.getLine(), node.getColumn(), message, null);
    }

    public JmmSymbolTable build(JmmNode root) {
        this.reports = new ArrayList<>();

        this.methodSignaturesToNames = new HashMap<>();
        this.methodSignaturesToParams = new HashMap<>();
        this.methodSignaturesToReturnTypes = new HashMap<>();

        if (DEBUG) {
            System.out.println("SymbolTableBuilder ----- Root da AST");
            System.out.println(root.toTree());
        }

        List<String> imports = buildImports(root);
        JmmNode classDecl = findClassDeclaration(root);
        if (classDecl == null) {
            if (DEBUG) {
                System.out.println(">>> 'CLASS_DECL' not found in AST!");
            }
            reports.add(newError(root, "No class declaration found in AST"));
            return createEmptyTable(imports);
        }

        String className = classDecl.get("name");
        String superClass = classDecl.hasAttribute("extendsId") ? classDecl.get("extendsId") : null;
        if (DEBUG) {
            System.out.println(">>> CLASS_DECL with name: " + className + ", super=" + superClass);
        }

        List<Symbol> fields = buildFields(classDecl);
        buildMethods(classDecl);
        List<String> methods = new ArrayList<>(new HashSet<>(methodSignaturesToNames.values()));
        Map<String, Type> returnTypes = extractReturnTypes(methods);
        Map<String, List<Symbol>> params = extractParameters(methods);
        Map<String, List<Symbol>> locals = buildLocals(classDecl, methods);

        return new JmmSymbolTable(
                className, methods, returnTypes, params, locals, superClass, imports, fields
        );
    }

    private Map<String, Type> extractReturnTypes(List<String> methods) {
        Map<String, Type> returnTypes = new HashMap<>();

        for (String methodName : methods) {
            for (Map.Entry<String, String> entry : methodSignaturesToNames.entrySet()) {
                if (entry.getValue().equals(methodName)) {
                    String signature = entry.getKey();
                    Type returnType = methodSignaturesToReturnTypes.get(signature);
                    returnTypes.put(methodName, returnType);
                    break;
                }
            }
        }

        return returnTypes;
    }

    private Map<String, List<Symbol>> extractParameters(List<String> methods) {
        Map<String, List<Symbol>> params = new HashMap<>();

        for (String methodName : methods) {
            for (Map.Entry<String, String> entry : methodSignaturesToNames.entrySet()) {
                if (entry.getValue().equals(methodName)) {
                    String signature = entry.getKey();
                    List<Symbol> methodParams = methodSignaturesToParams.get(signature);
                    params.put(methodName, methodParams != null ? methodParams : new ArrayList<>());
                    break;
                }
            }
        }

        return params;
    }

    private JmmSymbolTable createEmptyTable(List<String> imports) {
        return new JmmSymbolTable(
                "<unknownClass>", new ArrayList<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), null, imports, new ArrayList<>()
        );
    }

    private List<String> buildImports(JmmNode root) {
        List<String> importList = new ArrayList<>();
        for (JmmNode child : root.getChildren()) {
            if ("IMPORT_DECLARATION".equals(child.getKind())) {
                String importPath = extractImportPath(child);
                if (importPath != null && !importPath.equals("<notValidImport>")) {
                    importList.add(importPath);
                }
                else {
                    reports.add(newError(child, "Invalid import declaration"));
                }
            }
        }
        return importList;
    }

    private String extractImportPath(JmmNode importNode) {

        JmmNode qualifiedNameNode = findFirstChildByKind(importNode, "QualifiedName");
        if (qualifiedNameNode == null) {
            return "<notValidImport>";
        }

        List<String> importParts = new ArrayList<>();

        if (qualifiedNameNode.hasAttribute("firstPart")) {
            importParts.add(qualifiedNameNode.get("firstPart"));
        } else if (qualifiedNameNode.hasAttribute("name")) {
            importParts.add(qualifiedNameNode.get("name"));
        } else if (qualifiedNameNode.hasAttribute("ID")) {
            importParts.add(qualifiedNameNode.get("ID"));
        } else {
            importParts.addAll(extractIdentifiersFromChildren(qualifiedNameNode));
        }

        if (importParts.isEmpty()) {
            return "<notValidImport>";
        }

        if (qualifiedNameNode.hasAttribute("otherParts")) {
            String otherPartsRaw = qualifiedNameNode.get("otherParts");
            List<String> additionalParts = parseListContent(otherPartsRaw);
            importParts.addAll(additionalParts);
        }

        return String.join(".", importParts);
    }

    private List<String> parseListContent(String listContent) {
        List<String> parts = new ArrayList<>();
        if (listContent == null || listContent.isEmpty()) {
            return parts;
        }

        String content = listContent;
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }

        if (!content.trim().isEmpty()) {
            for (String part : content.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    parts.add(trimmed);
                }
            }
        }

        return parts;
    }

    private List<String> extractIdentifiersFromChildren(JmmNode node) {
        List<String> identifiers = new ArrayList<>();

        for (JmmNode child : node.getChildren()) {
            if ("ID".equals(child.getKind())) {
                identifiers.add(child.toString());
            } else if (child.hasAttribute("name")) {
                identifiers.add(child.get("name"));
            } else {
                identifiers.addAll(extractIdentifiersFromChildren(child));
            }
        }

        return identifiers;
    }

    private JmmNode findFirstChildByKind(JmmNode parent, String... kinds) {
        Set<String> kindSet = new HashSet<>();
        for (String kind : kinds) {
            kindSet.add(kind);
        }

        for (JmmNode child : parent.getChildren()) {
            if (kindSet.contains(child.getKind())) {
                return child;
            }
        }

        return null;
    }

    private JmmNode findClassDeclaration(JmmNode root) {
        List<JmmNode> classes = root.getChildren("CLASS_DECL");
        if (classes.isEmpty()) {
            return null;
        }

        for (JmmNode classNode : classes) {
            if (classNode.hasAttribute("extendsId")) {
                return classNode;
            }
        }

        return classes.get(classes.size() - 1);
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();
        JmmNode classBody = findFirstChildByKind(classDecl, "CLASS_BODY");

        if (classBody == null) {
            return fields;
        }

        for (JmmNode child : classBody.getChildren()) {
            if ("VAR_DECL".equals(child.getKind())) {
                Symbol field = convertVarDeclToSymbol(child);
                if (field != null) {
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    private boolean isMethodDeclaration(JmmNode node) {
        String kind = node.getKind();
        return "METHOD_DECL".equals(kind) || "VARARGS_METHOD_DECL".equals(kind);
    }

    private void buildMethods(JmmNode classDecl) {
        JmmNode classBody = findFirstChildByKind(classDecl, "CLASS_BODY");

        if (classBody == null) {
            return;
        }

        for (JmmNode methodNode : classBody.getChildren()) {
            if (isMethodDeclaration(methodNode) && methodNode.hasAttribute("name")) {
                String methodName = methodNode.get("name");

                List<Symbol> methodParams = extractMethodParameters(methodNode);
                String signature = buildMethodSignature(methodName, methodParams);
                System.out.println("DEBUG: Generated signature for method: " + signature);
                methodSignaturesToNames.put(signature, methodName);
                methodSignaturesToParams.put(signature, methodParams);
                Type returnType = extractMethodReturnType(methodNode);
                methodSignaturesToReturnTypes.put(signature, returnType);

                System.out.println("DEBUG: Method signature " + signature +
                        " has return type " + returnType.getName() +
                        (returnType.isArray() ? "[]" : ""));
            }
        }
    }

    private String buildMethodSignature(String methodName, List<Symbol> params) {
        StringBuilder sb = new StringBuilder(methodName);
        sb.append('(');

        if (!params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                Symbol param = params.get(i);
                sb.append(param.getType().getName());
                if (param.getType().isArray()) {
                    sb.append("[]");
                }

                if (i < params.size() - 1) {
                    sb.append(',');
                }
            }
        }

        sb.append(')');
        return sb.toString();
    }

    private Type extractMethodReturnType(JmmNode methodNode) {
        JmmNode functionTypeNode = findFirstChildByKind(methodNode, "FUNCTION_TYPE");
        if (functionTypeNode != null) {
            return TypeUtils.convertType(functionTypeNode);
        }

        return new Type("void", false);
    }

    private List<Symbol> extractMethodParameters(JmmNode methodNode) {
        List<Symbol> parameters = new ArrayList<>();

        JmmNode paramListNode = findFirstChildByKind(methodNode, "ParamList");
        if (paramListNode != null) {
            for (JmmNode paramNode : paramListNode.getChildren("PARAM")) {
                Symbol param = extractParameterSymbol(paramNode);
                if (param != null) {
                    parameters.add(param);
                }
            }
        }

        if (parameters.isEmpty() && "main".equals(methodNode.get("name"))) {
            JmmNode mainParamNode = findFirstChildByKind(methodNode, "MAIN_PARAM");
            if (mainParamNode != null && mainParamNode.hasAttribute("name")) {
                parameters.add(new Symbol(new Type("String", true), mainParamNode.get("name")));
            }
        }

        return parameters;
    }

    private Symbol extractParameterSymbol(JmmNode paramNode) {

        String paramName = null;
        if (paramNode.hasAttribute("name")) {
            paramName = paramNode.get("name");
        } else if (paramNode.hasAttribute("ID")) {
            paramName = paramNode.get("ID");
        }

        if (paramName == null) {
            return null;
        }

        Type paramType = TypeUtils.newIntType();
        JmmNode typeNode = findFirstChildByKind(paramNode, "TYPE");
        if (typeNode != null) {
            paramType = TypeUtils.convertType(typeNode);
        }

        boolean isVarargs = paramNode.hasAttribute("ellipsis");
        if (isVarargs) {
            paramType = new Type(paramType.getName(), true);
        }

        if (paramType.isArray()) {
            int dimensions = countArrayDimensions(typeNode);
            if (dimensions > 0) {
                String baseTypeName = paramType.getName();
                paramType = new ArrayType(baseTypeName, dimensions);
                System.out.println("DEBUG: Parameter " + paramName + " is an array with " +
                        dimensions + " dimensions (recursive count)");
            }
        }

        return new Symbol(paramType, paramName);
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl, List<String> methods) {
        Map<String, List<Symbol>> localsMap = new HashMap<>();
        System.out.println("DEBUG buildLocals: Building local variables for class " +
                (classDecl.hasAttribute("name") ? classDecl.get("name") : "unknown"));

        for (String method : methods) {
            localsMap.put(method, new ArrayList<>());
        }

        JmmNode classBody = findFirstChildByKind(classDecl, "CLASS_BODY");
        if (classBody == null) {
            System.out.println("  DEBUG No CLASS_BODY found");
            return localsMap;
        }

        System.out.println("  DEBUG Found CLASS_BODY, processing method declarations");

        for (JmmNode methodNode : classBody.getChildren()) {
            if (isMethodDeclaration(methodNode) && methodNode.hasAttribute("name")) {
                String methodName = methodNode.get("name");
                System.out.println("  DEBUG Processing method: " + methodName);

                List<Symbol> params = extractMethodParameters(methodNode);
                String signature = buildMethodSignature(methodName, params);

                List<Symbol> locals = new ArrayList<>();


                JmmNode methodBody = findFirstChildByKind(methodNode, "STMT");
                if (methodBody != null) {
                    collectAllVariableDeclarations(methodBody, locals);
                }

                System.out.println("  DEBUG Collected " + locals.size() +
                        " local variables for method " + signature);

                localsMap.put(methodName, locals);
                System.out.println("  DEBUG Added locals for method: " + methodName);
            }
        }

        return localsMap;
    }

    private void collectAllVariableDeclarations(JmmNode node, List<Symbol> vars) {

        if ("VarDeclarationStmt".equals(node.getKind())) {
            for (JmmNode child : node.getChildren()) {
                if ("VAR_DECL".equals(child.getKind())) {
                    Symbol symbol = convertVarDeclToSymbol(child);
                    if (symbol != null) {
                        vars.add(symbol);
                        System.out.println("    DEBUG Added local variable: " + symbol.getName() +
                                " : " + symbol.getType().getName() +
                                (symbol.getType().isArray() ? "[]" : ""));
                    }
                }
            }
        } else if ("VAR_DECL".equals(node.getKind())) {
            Symbol symbol = convertVarDeclToSymbol(node);
            if (symbol != null) {
                vars.add(symbol);
                System.out.println("    DEBUG Added local variable: " + symbol.getName() +
                        " : " + symbol.getType().getName() +
                        (symbol.getType().isArray() ? "[]" : ""));
            }
        }

        for (JmmNode child : node.getChildren()) {
            collectAllVariableDeclarations(child, vars);
        }
    }

    private Symbol convertVarDeclToSymbol(JmmNode varDeclNode) {
        if (!varDeclNode.hasAttribute("name")) {
            return null;
        }

        String varName = varDeclNode.get("name");
        System.out.println("DEBUG varDeclToSymbol: Processing VAR_DECL for variable '" + varName + "'");

        Type varType = TypeUtils.newIntType(); // Default to int
        JmmNode typeNode = findFirstChildByKind(varDeclNode, "TYPE");

        if (typeNode != null) {
            System.out.println("  DEBUG Type node: " + typeNode.getKind() +
                    (typeNode.hasAttribute("name") ? " name=" + typeNode.get("name") : ""));
            System.out.println("  DEBUG Type node children:");

            for (JmmNode child : typeNode.getChildren()) {
                System.out.println("    - " + child.getKind());
            }

            varType = TypeUtils.convertType(typeNode);

            if (varType.isArray()) {
                int dimensions = countArrayDimensions(typeNode);
                if (dimensions > 0) {
                    String baseTypeName = varType.getName();
                    varType = new ArrayType(baseTypeName, dimensions);
                    System.out.println("  DEBUG Variable " + varName + " is an array with " +
                            dimensions + " dimensions (recursive count)");
                }
            }

            System.out.println("  DEBUG Converted to type: " + varType.getName() +
                    (varType.isArray() ? "[]" : ""));
        } else {
            System.out.println("  DEBUG No TYPE node found, defaulting to int");
        }

        return new Symbol(varType, varName);
    }

    private int countArrayDimensions(JmmNode typeNode) {
        if (typeNode == null) {
            return 0;
        }

        for (JmmNode child : typeNode.getChildren()) {
            if ("ArraySuffix".equals(child.getKind())) {
                return countArraySuffixDepth(child);
            }
        }

        return 0;
    }

    private int countArraySuffixDepth(JmmNode arraySuffixNode) {
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

}