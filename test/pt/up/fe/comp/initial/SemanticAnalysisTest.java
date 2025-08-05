package pt.up.fe.comp.initial;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.specs.util.SpecsIo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class SemanticAnalysisTest {

    @Test
    public void undeclaredVariable() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/UndeclaredVariable.jmm"));
        TestUtils.mustFail(result);
        assertEquals(1, result.getReports(ReportType.ERROR).size());
        System.out.println(result.getReports());
    }

    @Test
    public void thisInStaticMethod() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ThisInStaticMethod.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void thisAssignment() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ThisAssignment.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void arrayOperations() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ArrayOperations.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void complexCondition() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ComplexCondition.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void multipleVarargs() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/MultipleVarargs.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void arrayInitializer() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ArrayInitializer.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void methodOverriding() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/MethodOverriding.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void importMethodUse() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ImportMethodUse.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void advancedMethodCalls() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/AdvancedMethodCalls.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void objectAssignment() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ObjectAssignment.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void complexExpressions() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ComplexExpressions.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void nestedScopes() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/NestedScopes.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void validArrayInitializer() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidArrayInitializer.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void validVarargs() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidVarargs.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void validThisUsage() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidThisUsage.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void validComplexExpressions() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidComplexExpressions.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void comprehensiveTest() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ComprehensiveTest.jmm"));
        // Depending on if you've implemented all features, this might pass or fail
        // If it fails, you should see specific errors for the problematic parts
        System.out.println(result.getReports());
    }

    // 3.3.1 Types and Declarations Verification Tests

    @Test
    public void testIdentifierVerification() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/IdentifierVerification.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar variável não declarada",
                reports.stream().anyMatch(r -> r.getMessage().contains("undeclaredVariable")));
        System.out.println(reports);
    }

    @Test
    public void testOperationTypeCompatibility() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/OperationTypeCompatibility.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar operandos incompatíveis",
                reports.stream().anyMatch(r -> r.getMessage().toLowerCase().contains("incompatible") ||
                        r.getMessage().toLowerCase().contains("type")));
        System.out.println(reports);
    }

    @Test
    public void testArrayAccessVerification() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ArrayAccessVerification.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar acesso a não-array ou índice não inteiro",
                reports.stream().anyMatch(r -> r.getMessage().toLowerCase().contains("array") ||
                        r.getMessage().toLowerCase().contains("index")));
        System.out.println(reports);
    }

    @Test
    public void testConditionBooleanCheck() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ConditionBooleanCheck.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar condições não booleanas",
                reports.stream().anyMatch(r -> r.getMessage().toLowerCase().contains("boolean") ||
                        r.getMessage().toLowerCase().contains("condition")));
        System.out.println(reports);
    }

    // 3.3.2 Method Verification Tests

    @Test
    public void testMethodCallArgumentTypes() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/MethodCallArgumentTypes.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar argumentos incompatíveis",
                reports.stream().anyMatch(r -> r.getMessage().toLowerCase().contains("argument") ||
                        r.getMessage().toLowerCase().contains("parameter")));
        System.out.println(reports);
    }

    @Test
    public void testInheritedMethodCall() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/InheritedMethodCall.jmm"));
        TestUtils.noErrors(result);
    }

    // Testes Adicionais

    @Test
    public void testComplexNestedExpressions() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ComplexNestedExpressions.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void testArrayLengthAccess() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ArrayLengthAccess.jmm"));
        TestUtils.mustFail(result);
        var reports = result.getReports(ReportType.ERROR);
        assertTrue("Deve detectar acesso a length em não-array",
                reports.stream().anyMatch(r -> r.getMessage().toLowerCase().contains("length") &&
                        r.getMessage().toLowerCase().contains("array")));
        System.out.println(reports);
    }

    // Testes Positivos

    @Test
    public void testValidOperations() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidOperations.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void testValidScopesAndDeclarations() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/ValidScopesAndDeclarations.jmm"));
        TestUtils.noErrors(result);
    }

}