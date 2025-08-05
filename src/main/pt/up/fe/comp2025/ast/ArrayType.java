package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class ArrayType extends Type {
    private final int dimensions;
    private final JmmNode sourceNode;

    public ArrayType(String baseType, int dimensions) {
        super(baseType, true); // isArray = true sempre
        this.dimensions = Math.max(1, dimensions); // Garantir pelo menos uma dimensão
        this.sourceNode = null;
    }

    public ArrayType(String baseType, int dimensions, JmmNode sourceNode) {
        super(baseType, true);
        this.dimensions = Math.max(1, dimensions);
        this.sourceNode = sourceNode;
    }

    public int getDimensions() {
        return dimensions;
    }

    public JmmNode getSourceNode() {
        return sourceNode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getName());
        for (int i = 0; i < dimensions; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public boolean isCompatibleWith(Type other) {
        if (!other.isArray()) {
            return false;
        }

        if (!other.getName().equals(this.getName())) {
            return false;
        }

        int otherDimensions = extractDimensions(other);
        return dimensions == otherDimensions;
    }

    private int extractDimensions(Type type) {

        if (type instanceof ArrayType) {
            return ((ArrayType) type).getDimensions();
        }

        String typeName = type.toString();
        int countBrackets = 0;
        int index = typeName.indexOf("[]");
        while (index != -1) {
            countBrackets++;
            index = typeName.indexOf("[]", index + 2);
        }

        return Math.max(1, countBrackets);
    }
}