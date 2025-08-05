package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.ArrayType;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;

public class JmmSymbolTable extends AJmmSymbolTable {

    private final String className;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final List<Symbol> fields;
    private final String superClass;
    private final List<String> imports;
    private String currentMethod;
    private final Map<String, String> methodSignatures;
    private final Map<String, Type> signatureToReturnType;
    private final Map<String, List<Symbol>> signatureToParams;
    private final Map<String, Integer> fieldArrayDimensions = new HashMap<>();
    private final Map<String, Map<String, Integer>> localArrayDimensions = new HashMap<>();
    private final Map<String, Integer> methodReturnArrayDimensions = new HashMap<>();
    private final Map<String, Integer> allArrayDimensions = new HashMap<>();

    public JmmSymbolTable(String className,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          String superClass,
                          List<String> imports,
                          List<Symbol> fields) {

        this.className = className;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.imports = imports;
        this.fields = fields;
        this.superClass = superClass;
        this.methodSignatures = new HashMap<>();
        this.signatureToReturnType = new HashMap<>();
        this.signatureToParams = new HashMap<>();

        buildMethodSignatures();
    }

    public void registerArrayDimensions(String methodName, String symbolName, int dimensions) {

        if (symbolName == null || symbolName.isEmpty()) {
            return;
        }

        if (dimensions <= 0) {
            return;
        }

        System.out.println("DEBUG: Registrando símbolo " + symbolName +
                " como array com " + dimensions + " dimensões" +
                (methodName != null ? " no método " + methodName : " como campo"));

        if (methodName == null) {
            fieldArrayDimensions.put(symbolName, dimensions);
        } else {
            localArrayDimensions
                    .computeIfAbsent(methodName, k -> new HashMap<>())
                    .put(symbolName, dimensions);
        }

        allArrayDimensions.put(getFullyQualifiedName(methodName, symbolName), dimensions);

        Symbol symbol = findSymbol(methodName, symbolName);
        if (symbol != null && symbol.getType().isArray()) {

        }
    }

    public void registerMethodReturnDimensions(String methodName, int dimensions) {
        if (methodName == null || methodName.isEmpty() || dimensions <= 0) {
            return;
        }

        methodReturnArrayDimensions.put(methodName, dimensions);

        Type returnType = getReturnType(methodName);
        if (returnType != null && !returnType.isArray()) {
            System.out.println("WARN: Tentativa de registrar dimensões para método que não retorna array: " + methodName);
        }
    }

    public int getArrayDimensions(String methodName, String symbolName) {

        String fullName = getFullyQualifiedName(methodName, symbolName);
        Integer cachedDims = allArrayDimensions.get(fullName);
        if (cachedDims != null) {
            return cachedDims;
        }

        Integer fieldDims = fieldArrayDimensions.get(symbolName);
        if (fieldDims != null && fieldDims > 0) {
            return fieldDims;
        }

        if (methodName != null) {
            Map<String, Integer> localDims = localArrayDimensions.get(methodName);
            if (localDims != null) {
                Integer dims = localDims.get(symbolName);
                if (dims != null && dims > 0) {
                    return dims;
                }
            }
        }

        if (methodName == null) {
            for (Map.Entry<String, Map<String, Integer>> entry : localArrayDimensions.entrySet()) {
                Integer dims = entry.getValue().get(symbolName);
                if (dims != null && dims > 0) {
                    return dims;
                }
            }
        }

        Symbol symbol = findSymbol(methodName, symbolName);
        if (symbol != null && symbol.getType().isArray()) {
            return 1;
        }

        Integer returnDims = methodReturnArrayDimensions.get(symbolName);
        if (returnDims != null && returnDims > 0) {
            return returnDims;
        }

        return 0;
    }

    public int getMethodReturnDimensions(String methodName) {

        Integer dimensions = methodReturnArrayDimensions.get(methodName);
        if (dimensions != null && dimensions > 0) {
            return dimensions;
        }

        Type returnType = getReturnType(methodName);
        if (returnType != null && returnType.isArray()) {
            if (returnType instanceof ArrayType) {
                int dims = ((ArrayType) returnType).getDimensions();
                methodReturnArrayDimensions.put(methodName, dims);
                return dims;
            }

            return 1;
        }

        return 0;
    }

    private void buildMethodSignatures() {
        for (String methodName : methods) {
            List<Symbol> methodParams = params.getOrDefault(methodName, Collections.emptyList());
            Type returnType = returnTypes.get(methodName);

            String signature = buildSignature(methodName, methodParams);

            methodSignatures.put(signature, methodName);
            signatureToReturnType.put(signature, returnType);
            signatureToParams.put(signature, methodParams);
        }
    }

    private String buildSignature(String methodName, List<Symbol> methodParams) {
        StringBuilder sb = new StringBuilder(methodName);
        sb.append('(');

        if (!methodParams.isEmpty()) {
            for (int i = 0; i < methodParams.size(); i++) {
                Symbol param = methodParams.get(i);
                sb.append(param.getType().getName());
                if (param.getType().isArray()) {
                    sb.append("[]");
                }

                if (i < methodParams.size() - 1) {
                    sb.append(',');
                }
            }
        }

        sb.append(')');
        return sb.toString();
    }

    public String getMethodSignature(String methodName, List<Type> paramTypes) {
        StringBuilder sb = new StringBuilder(methodName);
        sb.append('(');

        if (paramTypes != null && !paramTypes.isEmpty()) {
            for (int i = 0; i < paramTypes.size(); i++) {
                Type type = paramTypes.get(i);
                sb.append(type.getName());
                if (type.isArray()) {
                    sb.append("[]");
                }

                if (i < paramTypes.size() - 1) {
                    sb.append(',');
                }
            }
        }

        sb.append(')');
        return sb.toString();
    }

    public boolean hasMethodSignature(String methodSignature) {
        return methodSignatures.containsKey(methodSignature);
    }

    public Type getReturnTypeBySignature(String methodName, List<Type> paramTypes) {
        String signature = getMethodSignature(methodName, paramTypes);
        Type returnType = signatureToReturnType.get(signature);

        if (returnType == null) {
            returnType = returnTypes.get(methodName);
        }

        return returnType;
    }

    public List<Symbol> getParametersBySignature(String methodName, List<Type> paramTypes) {
        String signature = getMethodSignature(methodName, paramTypes);
        List<Symbol> methodParams = signatureToParams.get(signature);

        if (methodParams == null) {
            methodParams = params.getOrDefault(methodName, Collections.emptyList());
        }

        return methodParams;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superClass;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        if (methodSignatures.containsKey(methodSignature)) {
            return signatureToReturnType.get(methodSignature);
        }

        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {

        if (methodSignatures.containsKey(methodSignature)) {
            return signatureToParams.get(methodSignature);
        }

        return params.getOrDefault(methodSignature, new ArrayList<>());
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return locals.getOrDefault(methodSignature, new ArrayList<>());
    }

    public String getCurrentMethod() {
        return currentMethod;
    }

    public void setCurrentMethod(String currentMethod) {
        this.currentMethod = currentMethod;
    }

    @Override
    public String toString() {
        return print();
    }

    private int inferDimensionsFromType(Type type) {
        if (!type.isArray()) {
            return 0;
        }

        if (type instanceof ArrayType) {
            return ((ArrayType) type).getDimensions();
        }

        String typeString = type.toString();
        int dimensions = 1;

        int startIdx = 0;
        while ((startIdx = typeString.indexOf("[]", startIdx)) != -1) {
            dimensions++;
            startIdx += 2;
        }

        return dimensions;
    }

    private Symbol findSymbol(String methodName, String symbolName) {
        if (symbolName == null) {
            return null;
        }

        if (methodName != null) {
            for (Symbol param : getParameters(methodName)) {
                if (param.getName().equals(symbolName)) {
                    return param;
                }
            }

            for (Symbol local : getLocalVariables(methodName)) {
                if (local.getName().equals(symbolName)) {
                    return local;
                }
            }
        }

        for (Symbol field : getFields()) {
            if (field.getName().equals(symbolName)) {
                return field;
            }
        }

        if (methodName == null) {
            for (String method : getMethods()) {
                Symbol found = findSymbol(method, symbolName);
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
}