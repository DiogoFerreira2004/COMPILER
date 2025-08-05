package pt.up.fe.comp2025.optimization.util;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;

import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods for optimization.
 * Follows the Single Responsibility Principle by focusing on providing
 * utilities specifically for the optimization phase.
 */
public class OptUtils {

    private static AccumulatorMap<String> temporaries = new AccumulatorMap<>();
    private final TypeUtils types;
    private int tmpCounter = 0;

    /**
     * Constructs a new OptUtils instance.
     *
     * @param types The TypeUtils instance for type conversions
     */
    public OptUtils(TypeUtils types) {
        this.types = types;
    }

    /**
     * Creates a new temporary variable name with the default prefix.
     *
     * @return A unique temporary variable name
     */
    public String nextTemp() {
        return "tmp" + (tmpCounter++);
    }
    /**
     * Creates a new temporary variable name with the specified prefix.
     *
     * @param prefix The prefix for the temporary variable
     * @return A unique temporary variable name
     */
    public String nextTemp(String prefix) {
        var nextTempNum = temporaries.add(prefix) - 1;
        return prefix + nextTempNum;
    }


    public void resetTemporaries() {
        tmpCounter = 0;
        System.out.println("DEBUG: Contadores de temporários resetados");
    }

    /**
     * Converts a JmmNode type to its OLLIR type representation.
     *
     * @param typeNode The JmmNode representing a type
     * @return The OLLIR type string
     */
    public String toOllirType(JmmNode typeNode) {
        return toOllirType(types.convertType(typeNode));
    }

    /**
     * Gets the next temporary number without incrementing the counter.
     * Useful for previewing the next counter value.
     *
     * @param prefix The prefix for the temporary variable
     * @return The next number that would be assigned
     */
    public int peekNextTempNum(String prefix) {
        return temporaries.getCount(prefix);
    }


    /**
     * Converts a Type to its OLLIR type representation.
     *
     * @param type The Type object
     * @return The OLLIR type string
     */
    public String toOllirType(Type type) {
        return toOllirType(type.getName(), type.isArray());
    }


    /**
     * Converts a type name to its OLLIR type representation.
     *
     * @param typeName The name of the type
     * @return The OLLIR type string
     */
    private String toOllirType(String typeName) {
        return toOllirType(typeName, false);
    }

    /**
     * Converts a type name and array status to its OLLIR type representation.
     * Implementação generalizada que suporta todos os tipos primitivos e objetos.
     *
     * @param typeName The name of the type
     * @param isArray Whether the type is an array
     * @return The OLLIR type string
     */
    private String toOllirType(String typeName, boolean isArray) {
        // Prefixo para arrays
        String arrayPrefix = isArray ? "array." : "";

        // Mapear tipos básicos para tipos OLLIR
        String baseType = switch (typeName.toLowerCase()) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "float" -> "float";
            case "double" -> "double";
            case "char" -> "char";
            case "byte" -> "byte";
            case "short" -> "short";
            case "long" -> "long";
            // Caso especial: String já é tratada como um array de caracteres
            case "string" -> {
                // Como String já é tratada como array, não adicionar prefixo duplicado
                arrayPrefix = "";
                yield "array.String";
            }
            // Para outros tipos (classes), usar o nome original
            default -> typeName;
        };

        // Montar a string final do tipo OLLIR
        if (typeName.equalsIgnoreCase("string")) {
            return "." + baseType;
        }

        return "." + arrayPrefix + baseType;
    }


}