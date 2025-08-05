package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
    }

    public String getDescriptor(Type type) {
        if (type == null) {
            return "V";
        }

        String typeString = type.toString().toLowerCase();

        if (typeString.contains("string") && (typeString.contains("array") || typeString.contains("[]"))) {
            return "[Ljava/lang/String;";
        }

        if (isVoidType(type)) {
            return "V";
        } else if (isIntegerType(type)) {
            return "I";
        } else if (isBooleanType(type)) {
            return "Z";
        } else if (isArrayType(type)) {
            Type elemType = getArrayElementType(type);
            if (elemType != null) {
                String elemTypeString = elemType.toString().toLowerCase();
                if (elemTypeString.contains("string")) {
                    return "[Ljava/lang/String;";
                }

                if (elemType instanceof ClassType) {
                    String className = ((ClassType) elemType).getName();
                    if ("String".equals(className) || "java.lang.String".equals(className) ||
                            className.toLowerCase().contains("string")) {
                        return "[Ljava/lang/String;";
                    }
                }

                if (isIntegerType(elemType) || isBooleanType(elemType)) {
                    return "[I";
                }
            }
            return "[" + getDescriptor(elemType);
        } else if (type instanceof ClassType) {
            String className = ((ClassType) type).getName();
            if ("String".equals(className) || "java.lang.String".equals(className) ||
                    className.toLowerCase().contains("string")) {
                return "Ljava/lang/String;";
            }
            return "L" + className.replace('.', '/') + ";";
        } else {
            String fullTypeString = type.getClass().getSimpleName().toLowerCase() + " " + type.toString().toLowerCase();
            if (fullTypeString.contains("string")) {
                if (fullTypeString.contains("array") || fullTypeString.contains("[]")) {
                    return "[Ljava/lang/String;";
                } else {
                    return "Ljava/lang/String;";
                }
            }
            return "Ljava/lang/Object;";
        }
    }

    public boolean isVoidType(Type type) {
        return type != null &&
                (type.getClass().getSimpleName().contains("Void") ||
                        (type instanceof BuiltinType && ((BuiltinType)type).getKind() == BuiltinKind.VOID));
    }

    public boolean isIntegerType(Type type) {
        return type != null &&
                (type.getClass().getSimpleName().contains("Integer") ||
                        (type instanceof BuiltinType && ((BuiltinType)type).getKind() == BuiltinKind.INT32));
    }

    public boolean isBooleanType(Type type) {
        return type != null &&
                (type.getClass().getSimpleName().contains("Boolean") ||
                        (type instanceof BuiltinType && ((BuiltinType)type).getKind() == BuiltinKind.BOOLEAN));
    }

    public boolean isArrayType(Type type) {
        return type != null && type instanceof ArrayType;
    }

    public Type getArrayElementType(Type type) {
        if (type instanceof ArrayType) {
            return ((ArrayType) type).getElementType();
        }
        return null;
    }

    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public String getLoadInstruction(Type type, int register) {
        if (isIntegerType(type) || isBooleanType(type)) {
            return getOptimizedILoad(register);
        } else {
            return getOptimizedALoad(register);
        }
    }

    public String getStoreInstruction(Type type, int register) {
        if (isIntegerType(type) || isBooleanType(type)) {
            return getOptimizedIStore(register);
        } else {
            return getOptimizedAStore(register);
        }
    }

    public String getReturnInstruction(Type type) {
        if (isVoidType(type)) {
            return "return";
        } else if (isIntegerType(type) || isBooleanType(type)) {
            return "ireturn";
        } else {
            return "areturn";
        }
    }

    public String getOptimizedILoad(int register) {
        if (register >= 0 && register <= 3) {
            return "iload_" + register;
        } else {
            return "iload " + register;
        }
    }

    public String getOptimizedIStore(int register) {
        if (register >= 0 && register <= 3) {
            return "istore_" + register;
        } else {
            return "istore " + register;
        }
    }

    public String getOptimizedALoad(int register) {
        if (register >= 0 && register <= 3) {
            return "aload_" + register;
        } else {
            return "aload " + register;
        }
    }

    public String getOptimizedAStore(int register) {
        if (register >= 0 && register <= 3) {
            return "astore_" + register;
        } else {
            return "astore " + register;
        }
    }

    public String getOptimizedConstant(int value) {
        if (value >= -1 && value <= 5) {
            return "iconst_" + (value == -1 ? "m1" : value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return "bipush " + value;
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return "sipush " + value;
        } else {
            return "ldc " + value;
        }
    }

    public String formatClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "java/lang/Object";
        }
        return className.replace('.', '/');
    }
}