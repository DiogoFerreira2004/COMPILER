package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.specs.util.SpecsStrings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum Kind {
    PROGRAM,
    IMPORT_DECLARATION,

    CLASS_DECL,
    CLASS_BODY,

    VAR_DECL,

    TYPE,
    FUNCTION_TYPE,

    METHOD_DECL,
    MAIN_PARAM,
    PARAM,

    STMT,
    VarDeclarationStmt,
    IfStmt,
    WhileStmt,
    ASSIGN_STMT,
    RETURN_STMT,
    ExpressionStmt,
    BlockStmt,

    IdentifierLValue,
    ArrayAccessLValue,

    IntLiteral,
    INTEGER_LITERAL,
    TrueLiteral,
    FalseLiteral,
    TrueLiteralExpr,
    FalseLiteralExpr,
    ThisExpression,
    VAR_REF_EXPR,
    ParenExpr,

    NewIntArrayExpr,
    NewObjectExpr,
    ArrayInitializerExpr,

    DIRECT_METHOD_CALL,
    MethodCallExpr,

    ArrayAccessExpr,
    ArrayLengthExpr,
    ArrayAccessPostfix,
    ArrayLengthPostfix,

    SignExpr,
    NotExpr,
    UNARY_EXPR,

    MULTIPLICATIVE_EXPR,
    ADDITIVE_EXPR,
    RELATIONAL_EXPR,
    LogicalAndExpr,
    LogicalOrExpr,
    BINARY_EXPR,
    EQUALITY_EXPR,
    LOGICAL_AND_EXPR,
    LOGICAL_OR_EXPR,

    EXPR,
    BOOLEAN_LITERAL,
    STRING,
    POSTFIX_AS_UNARY,
    POSTFIX_EXPR,
    MethodCallPostfix;

    private final String name;

    private Kind(String name) {
        this.name = name;
    }

    private Kind() {
        this.name = SpecsStrings.toCamelCase(name(), "_", true);
    }

    public static Kind fromString(String kind) {
        for (Kind k : Kind.values()) {
            if (k.getNodeName().equals(kind)) {
                return k;
            }
        }
        throw new RuntimeException("Could not convert string '" + kind + "' to a Kind");
    }

    public static List<String> toNodeName(Kind firstKind, Kind... otherKinds) {
        var nodeNames = new ArrayList<String>();
        nodeNames.add(firstKind.getNodeName());

        for(Kind kind : otherKinds) {
            nodeNames.add(kind.getNodeName());
        }

        return nodeNames;
    }

    public String getNodeName() {
        return name;
    }

    @Override
    public String toString() {
        return getNodeName();
    }

    /**
     * Tests if the given JmmNode has the same kind as this type.
     *
     * @param node
     * @return
     */
    public boolean check(JmmNode node) {
        return node.isInstance(this);
    }

    /**
     * Performs a check and throws if the test fails. Otherwise, does nothing.
     *
     * @param node
     */
    public void checkOrThrow(JmmNode node) {
        if (!check(node)) {
            throw new RuntimeException("Node '" + node + "' is not a '" + getNodeName() + "'");
        }
    }

    /**
     * Performs a check on all kinds to test and returns false if none matches. Otherwise, returns true.
     *
     * @param node
     * @param kindsToTest
     * @return
     */
    public static boolean check(JmmNode node, Kind... kindsToTest) {
        for (Kind k : kindsToTest) {
            // if any matches, return successfully
            if (k.check(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs a check an all kinds to test and throws if none matches. Otherwise, does nothing.
     *
     * @param node
     * @param kindsToTest
     */
    public static void checkOrThrow(JmmNode node, Kind... kindsToTest) {
        if (!check(node, kindsToTest)) {
            // throw if none matches
            throw new RuntimeException("Node '" + node + "' is not any of " + Arrays.asList(kindsToTest));
        }
    }
}