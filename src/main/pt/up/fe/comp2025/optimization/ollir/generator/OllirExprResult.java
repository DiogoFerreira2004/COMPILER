package pt.up.fe.comp2025.optimization.ollir.generator;

/**
 * Represents the result of generating OLLIR code for an expression.
 * Follows the Value Object pattern and immutability principle.
 */
public class OllirExprResult {

    /**
     * Singleton instance for empty result.
     */
    public static final OllirExprResult EMPTY = new OllirExprResult("", "");

    private final String code;
    private final String computation;

    /**
     * Constructs a result with both code and computation.
     *
     * @param code The OLLIR code that represents the expression result
     * @param computation The OLLIR code needed to compute the expression
     */
    public OllirExprResult(String code, String computation) {
        this.code = code;
        this.computation = computation;
    }

    /**
     * Constructs a result with only code (no computation needed).
     *
     * @param code The OLLIR code that represents the expression result
     */
    public OllirExprResult(String code) {
        this(code, "");
    }

    /**
     * Constructs a result with a StringBuilder for computation.
     *
     * @param code The OLLIR code that represents the expression result
     * @param computation The StringBuilder containing computation code
     */
    public OllirExprResult(String code, StringBuilder computation) {
        this(code, computation.toString());
    }

    /**
     * @return The computation code needed to evaluate this expression
     */
    public String getComputation() {
        return computation;
    }

    /**
     * @return The OLLIR code representing the result of this expression
     */
    public String getCode() {
        return code;
    }

    /**
     * Creates a new OllirExprResult by combining this result with a suffix.
     *
     * @param suffix The suffix to append to the code
     * @return A new OllirExprResult with the updated code
     */
    public OllirExprResult withSuffix(String suffix) {
        return new OllirExprResult(code + suffix, computation);
    }

    @Override
    public String toString() {
        return "OllirExprResult{" +
                "code='" + code + '\'' +
                ", computation='" + computation + '\'' +
                '}';
    }
}