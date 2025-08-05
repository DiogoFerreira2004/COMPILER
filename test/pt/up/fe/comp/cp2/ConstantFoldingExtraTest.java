package pt.up.fe.comp.cp2;

import org.junit.Test;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;

/**
 * Casos extra de constant folding (simples e com controlo de fluxo).
 * Coloca os .jmm correspondentes em
 *   src/test/resources/pt/up/fe/comp/cp2/optimizations/extra/
 */
public class ConstantFoldingExtraTest {

    // ---------------- utilitários herdados --------------------------------

    private static OllirResult getOllirResult(String filename) {
        return OptimizationsTest.getOllirResult(filename);
    }

    private static OllirResult getOllirResultOpt(String filename) {
        return OptimizationsTest.getOllirResultOpt(filename);
    }

    // ---------------- testes simples --------------------------------------

    @Test
    public void constFoldAdd() {
        String file = "extra/FoldAdd.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("5", method, optimized);   // 2+3 → 5
    }

    @Test
    public void constFoldMul() {
        String file = "extra/FoldMul.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("12", method, optimized);  // 4*3 → 12
    }

    /**
     * (1 + 2) * (3 + 4)
     * Esperamos ver:
     *   tmp0.i32 :=.i32 3.i32 *.i32 7.i32;
     * Logo, devem aparecer literais 3 e 7 mas não 21.
     */
    @Test
    public void constFoldNested() {
        String file = "extra/FoldNested.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("21", method, optimized);  // (1+2)*(3+4) → 21
    }

    // ---------------- testes com while ------------------------------------

    @Test
    public void constFoldWhileAdd() {
        String file = "extra/FoldWhileAdd.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("5", method, optimized);   // 2+3 → 5
    }

    @Test
    public void constFoldWhileConstCond() {
        String file = "extra/FoldWhileConstCond.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("2", method, optimized);   // 1+1 → 2
    }

    // ---------------- testes com if ---------------------------------------

    @Test
    public void constFoldIfMul() {
        String file = "extra/FoldIfMul.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertFindLiteral("30", method, optimized);  // 5*6 → 30
    }

    @Test
    public void constFoldIfElse() {
        String file = "extra/FoldIfElse.jmm";
        var original  = getOllirResult(file);
        var optimized = getOllirResultOpt(file);

        CpUtils.assertTrue("Código devia mudar com -o flag",
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "bar");

        // ter 3 e 4 é suficiente
        CpUtils.assertFindLiteral("3", method, optimized);   // 1+2 → 3
        CpUtils.assertFindLiteral("4", method, optimized);   // 2+2 → 4

    }
}
