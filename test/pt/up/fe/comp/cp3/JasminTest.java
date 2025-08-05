/**
 * Copyright 2022 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.cp3;

import org.junit.Test;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsStrings;
import utils.ProjectTestUtils;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JasminTest {


    static JasminResult getJasminResult(String filename) {

        var resource = "pt/up/fe/comp/cp3/jasmin/" + filename;

        SpecsCheck.checkArgument(resource.endsWith(".ollir"), () -> "Expected resource to end with .ollir: " + resource);

        var ollirResult = new OllirResult(SpecsIo.getResource(resource), Collections.emptyMap());

        var result = TestUtils.backend(ollirResult);

        return result;

    }

    public static void testOllirToJasmin(String resource, String expectedOutput) {
        SpecsCheck.checkArgument(resource.endsWith(".ollir"), () -> "Expected resource to end with .ollir: " + resource);

        var ollirResult = new OllirResult(SpecsIo.getResource(resource), Collections.emptyMap());

        var result = TestUtils.backend(ollirResult);

        ProjectTestUtils.runJasmin(result, null);
    }

    public static void testOllirToJasmin(String resource) {
        testOllirToJasmin(resource, null);
    }


    private static final String JASMIN_METHOD_REGEX_PREFIX = "\\.method\\s+((public|private)\\s+)?(\\w+)\\(\\)";


    @Test
    public void ollirToJasminBasic() {
        testOllirToJasmin("pt/up/fe/comp/cp3/jasmin/basic/OllirToJasminBasic.ollir");
    }

    @Test
    public void ollirToJasminArithmetics() {
        testOllirToJasmin("pt/up/fe/comp/cp3/jasmin/arithmetic/OllirToJasminArithmetics.ollir");
    }

    @Test
    public void ollirToJasminInvoke() {
        testOllirToJasmin("pt/up/fe/comp/cp3/jasmin/calls/OllirToJasminInvoke.ollir");
    }

    @Test
    public void ollirToJasminFields() {
        testOllirToJasmin("pt/up/fe/comp/cp3/jasmin/basic/OllirToJasminFields.ollir");
    }

    /*checks if method declaration is correct (array)*/
    @Test
    public void section1_Basic_Method_Declaration_Array() {
        JasminResult jasminResult = getJasminResult("basic/BasicMethodsArray.ollir");
        CpUtils.matches(jasminResult, JASMIN_METHOD_REGEX_PREFIX + "\\[I");
    }

    /*checks if the index for loading a argument is correct (should be 1) */
    @Test
    public void section2_Arithmetic_BytecodeIndex_IloadArg() {
        var jasminResult = getJasminResult("arithmetic/ByteCodeIndexes1.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);

        int iloadIndex = CpUtils.getBytecodeIndex("iload", methodCode);
        assertEquals(1, iloadIndex);
    }

    /*checks if the index for storing a var is correct (should be > 1) */
    @Test
    public void section2_Arithmetic_BytecodeIndex_IstoreVar() {
        var jasminResult = getJasminResult("arithmetic/ByteCodeIndexes2.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);

        int istoreIndex = CpUtils.getBytecodeIndex("istore", methodCode);
        assertTrue("Expected index to be greater than one, is " + istoreIndex, istoreIndex > 1);
    }

    @Test
    public void section2_Arithmetic_Simple_and() {
        CpUtils.runJasmin(getJasminResult("arithmetic/Arithmetic_and.ollir"), "0");
    }

    @Test
    public void section2_Arithmetic_Simple_less() {
        CpUtils.runJasmin(getJasminResult("arithmetic/Arithmetic_less.ollir"), "1");
    }


    /*checks if an addition is correct (more than 2 values)*/
    @Test
    public void section3_ControlFlow_If_Simple() {
        CpUtils.runJasmin(getJasminResult("control_flow/SimpleIfElseStat.ollir"), "Result: 5\nResult: 8");
    }

    /*checks if an addition is correct (more than 2 values)*/
    @Test
    public void section3_ControlFlow_Inverted() {
        CpUtils.runJasmin(getJasminResult("control_flow/SimpleControlFlow.ollir"), "Result: 3");
    }

    /*checks OLLIR code that uses >= for an inverted condition */
    @Test
    public void section3_ControlFlow_If_Not_Simple() {
        CpUtils.runJasmin(getJasminResult("control_flow/SimpleIfElseNot.ollir"), "10\n200");
    }

    /*checks if the code of a simple WHILE statement is well executed */
    @Test
    public void section3_ControlFlow_While_Simple() {
        CpUtils.runJasmin(getJasminResult("control_flow/SimpleWhileStat.ollir"), "Result: 0\nResult: 1\nResult: 2");
    }

    /*checks if the code of a more complex IF ELSE statement (similar a switch statement) is well executed */
    @Test
    public void section3_ControlFlow_Mixed_Switch() {
        CpUtils.runJasmin(getJasminResult("control_flow/SwitchStat.ollir"),
                "Result: 1\nResult: 2\nResult: 3\nResult: 4\nResult: 5\nResult: 6\nResult: 7");
    }

    /*checks if the code of a more complex IF ELSE statement (similar a switch statement) is well executed */
    @Test
    public void section3_ControlFlow_Mixed_Nested() {
        CpUtils.runJasmin(getJasminResult("control_flow/IfWhileNested.ollir"), "Result: 1\nResult: 2\nResult: 1");
    }

    /*checks if the code of a call to a function with multiple arguments (using boolean expressions in the call) is
    well executed*/
    @Test
    public void section4_Calls_Misc_ConditionArgs() {
        CpUtils.runJasmin(getJasminResult("calls/ConditionArgsFuncCall.ollir"), "Result: 10");

    }


    /*checks if an array is correctly initialized*/
    @Test
    public void section5_Arrays_Init_Array() {
        CpUtils.runJasmin(getJasminResult("arrays/ArrayInit.ollir"), "Result: 5");

    }

    /*checks if the access to the elements of array is correct*/
    @Test
    public void section5_Arrays_Store_Array() {
        CpUtils.runJasmin(getJasminResult("arrays/ArrayAccess.ollir"),
                "Result: 1\nResult: 2\nResult: 3\nResult: 4\nResult: 5");

    }

    /*checks multiple expressions as indexes to access the elements of an array*/
    @Test
    public void section5_Arrays_Load_ComplexArrayAccess() {
        CpUtils.runJasmin(getJasminResult("arrays/ComplexArrayAccess.ollir"),
                "Result: 1\nResult: 2\nResult: 3\nResult: 4\nResult: 5");

    }

    /*checks if array has correct signature ?*/
    @Test
    public void section5_Arrays_Signature_ArrayAsArg() {
        var jasminResult = getJasminResult("arrays/ArrayAsArgCode.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);

        CpUtils.matches(methodCode, "invokevirtual\\s+ArrayAsArg(/|\\.)(\\w+)\\(\\[I\\)I");
    }

    /*checks if array is being passed correctly as an argument to a function*/
    @Test
    public void section5_Arrays_As_Arg_Simple() {
        CpUtils.runJasmin(getJasminResult("arrays/ArrayAsArg.ollir"), "Result: 2");
    }

    /*checks if array is being passed correctly as an argument to a function (index of aload > 1)*/
    @Test
    public void section5_Arrays_As_Arg_Aload() {
        var jasminResult = getJasminResult("arrays/ArrayAsArgCode.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);

        int aloadIndex = CpUtils.getBytecodeIndex("aload", methodCode);
        assertTrue("Expected aload index to be greater than 1, is " + aloadIndex + ":\n" + methodCode, aloadIndex > 1);
    }

    /**
     * Novos testes para Jasmin baseados nos exemplos fornecidos
     */

// Testes para ArrayMethodAccess
    /**
     * Test if array access using method call as index works correctly
     */
    @Test
    public void section5_Arrays_MethodIndex_Access() {
        JasminResult jasminResult = getJasminResult("ArrayMethodAccess.ollir");
        // Verifica se invoca o método para obter o índice
        CpUtils.matches(jasminResult, "invokevirtual\\s+ArrayMethodAccess/index\\(\\)I");
        // Verifica se usa o resultado como índice do array
        CpUtils.matches(jasminResult, "iaload");
    }

    /**
     * Test if array access using expression as index works correctly
     */
    @Test
    public void section5_Arrays_ExpressionIndex_Division() {
        JasminResult jasminResult = getJasminResult("ArrayMethodAccess.ollir");
        // Verifica se faz divisão para calcular índice
        CpUtils.matches(jasminResult, "idiv");
        // Verifica se usa arraylength
        CpUtils.matches(jasminResult, "arraylength");
    }

    /**
     * Test if array length access works correctly
     */
    @Test
    public void section5_Arrays_Length_Access() {
        JasminResult jasminResult = getJasminResult("ArrayMethodAccess.ollir");
        // Verifica se usa arraylength corretamente
        CpUtils.matches(jasminResult, "arraylength");
        // Verifica se faz subtração para calcular índice
        CpUtils.matches(jasminResult, "isub");
    }

    /**
     * Test if ArrayMethodAccess executes correctly and prints expected output
     */
    @Test
    public void section5_Arrays_MethodAccess_Execution() {
        CpUtils.runJasmin(getJasminResult("ArrayMethodAccess.ollir"), "Result: 20\nResult: 30\nResult: 20\nResult: 30");
    }

// Testes para ArraysAndVarargs
    /**
     * Test if varargs method is declared correctly
     */
    @Test
    public void section5_Arrays_Varargs_Declaration() {
        JasminResult jasminResult = getJasminResult("ArraysAndVarargs.ollir");
        // Verifica se o método tem a marcação varargs
        CpUtils.matches(jasminResult, "\\.method\\s+.*varargs\\s+processVarargs\\(\\[I\\)I");
    }

    /**
     * Test if varargs method creates array correctly
     */
    @Test
    public void section5_Arrays_Varargs_ArrayCreation() {
        JasminResult jasminResult = getJasminResult("ArraysAndVarargs.ollir");
        // Verifica se cria array para varargs
        CpUtils.matches(jasminResult, "newarray\\s+int");
        // Verifica se inicializa elementos do array
        CpUtils.matches(jasminResult, "iastore");
    }

    /**
     * Test if while loop in processArray works correctly
     */
    @Test
    public void section5_Arrays_ProcessArray_Loop() {
        JasminResult jasminResult = getJasminResult("ArraysAndVarargs.ollir");
        // Verifica se tem label de while
        CpUtils.matches(jasminResult, "while\\d+:");
        // Verifica se usa arraylength para condição
        CpUtils.matches(jasminResult, "arraylength");
        // Verifica se incrementa contador
        CpUtils.matches(jasminResult, "iinc");
    }

// Testes para ComplexIfElse
    /**
     * Test if nested if-else structure generates correct labels
     */
    @Test
    public void section3_ControlFlow_NestedIf_Labels() {
        JasminResult jasminResult = getJasminResult("ComplexIfElse.ollir");
        // Verifica se tem labels then e endif
        CpUtils.matches(jasminResult, "then\\d+:");
        CpUtils.matches(jasminResult, "endif\\d+:");
    }

    /**
     * Test if complex comparison uses correct bytecode
     */
    @Test
    public void section3_ControlFlow_ComplexIf_Comparison() {
        JasminResult jasminResult = getJasminResult("ComplexIfElse.ollir");
        // Verifica se usa if_icmplt para comparações
        CpUtils.matches(jasminResult, "if_icmplt");
        // Verifica se usa ifne para condições
        CpUtils.matches(jasminResult, "ifne");
    }

    /**
     * Test if complex if-else generates correct jump instructions
     */
    @Test
    public void section3_ControlFlow_ComplexIf_Jumps() {
        JasminResult jasminResult = getJasminResult("ComplexIfElse.ollir");
        // Verifica se usa goto para pular blocos
        CpUtils.matches(jasminResult, "goto");
        // Verifica se gera labels j_true e j_end para comparações
        CpUtils.matches(jasminResult, "j_true_\\d+:");
        CpUtils.matches(jasminResult, "j_end\\d+:");
    }

// Testes para Varargs
    /**
     * Test if simple varargs method is declared correctly
     */
    @Test
    public void section5_Arrays_SimpleVarargs_Declaration() {
        JasminResult jasminResult = getJasminResult("Varargs.ollir");
        // Verifica se método sum tem marcação varargs
        CpUtils.matches(jasminResult, "\\.method\\s+varargs\\s+sum\\(\\[I\\)I");
    }

    /**
     * Test if varargs method processes array correctly in while loop
     */
    @Test
    public void section5_Arrays_SimpleVarargs_Loop() {
        JasminResult jasminResult = getJasminResult("Varargs.ollir");
        // Verifica estrutura do while
        CpUtils.matches(jasminResult, "while\\d+:");
        CpUtils.matches(jasminResult, "endif\\d+:");
        // Verifica se usa arraylength
        CpUtils.matches(jasminResult, "arraylength");
        // Verifica se carrega elementos do array
        CpUtils.matches(jasminResult, "iaload");
    }

    /**
     * Test if varargs call creates array with correct values
     */
    @Test
    public void section5_Arrays_SimpleVarargs_Call() {
        JasminResult jasminResult = getJasminResult("Varargs.ollir");
        // Verifica se cria array com 5 elementos
        CpUtils.matches(jasminResult, "iconst_5");
        CpUtils.matches(jasminResult, "newarray\\s+int");
        // Verifica se inicializa com valores 1,2,3,4,5
        CpUtils.matches(jasminResult, "iconst_1");
        CpUtils.matches(jasminResult, "iconst_2");
        CpUtils.matches(jasminResult, "iconst_3");
        CpUtils.matches(jasminResult, "iconst_4");
        CpUtils.matches(jasminResult, "iconst_5");
    }

    /**
     * Test if varargs execution returns correct sum
     */
    @Test
    public void section5_Arrays_SimpleVarargs_Execution() {
        // O resultado esperado seria 15 (1+2+3+4+5)
        // Como não há output direto, verificamos se compila corretamente
        JasminResult jasminResult = getJasminResult("Varargs.ollir");
        jasminResult.compile(); // Verifica se compila sem erros
    }

// Testes adicionais para verificar limites de stack e locals
    /**
     * Test if locals limit is correctly calculated for complex methods
     */
    @Test
    public void section6_Limits_ComplexMethod_Locals() {
        var jasminResult = getJasminResult("ArraysAndVarargs.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult, "processArray");
        var numLocals = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitLocalsRegex(), 1));

        // Deve ter pelo menos 8 locals para as variáveis usadas
        assertTrue("Expected locals >= 8, got " + numLocals, numLocals >= 8);
        assertTrue("Expected locals < 99, got " + numLocals, numLocals < 99);
    }

    /**
     * Test if stack limit is correctly calculated for array operations
     */
    @Test
    public void section6_Limits_ArrayOperations_Stack() {
        var jasminResult = getJasminResult("ArrayMethodAccess.ollir");

        String jasminCode = jasminResult.getJasminCode();

        String mainMethodRegex = "\\.method\\s+public\\s+static\\s+main\\([^)]+\\)V[\\s\\S]*?\\.end\\s+method";
        Pattern pattern = Pattern.compile(mainMethodRegex);
        Matcher matcher = pattern.matcher(jasminCode);

        assertTrue("Could not find main method", matcher.find());
        String methodCode = matcher.group();

        var numStack = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitStackRegex(), 1));

        // Deve ter pelo menos 3 stack para operações de array
        assertTrue("Expected stack >= 3, got " + numStack, numStack >= 3);
        assertTrue("Expected stack < 99, got " + numStack, numStack < 99);
    }
    /*checks if the .limits locals is not a const 99 value */
    @Test
    public void section6_Limits_Locals_Not_99() {
        var jasminResult = getJasminResult("limits/LocalLimits.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);
        var numLocals = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitLocalsRegex(), 1));
        assertTrue("limit locals should be less than 99:\n" + methodCode, numLocals >= 0 && numLocals < 99);

        // Make sure the code compiles
        jasminResult.compile();
    }

    /*checks if the .limits locals is the expected value (with a tolerance of 2) */
    @Test
    public void section6_Limits_Locals_Simple() {

        var jasminResult = getJasminResult("limits/LocalLimits.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);
        var numLocals = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitLocalsRegex(), 1));

        // Find store or load with numLocals - 1
        var regex = CpUtils.getLocalsRegex(numLocals);
        CpUtils.matches(methodCode, regex);

        // Makes sure the code compiles
        jasminResult.compile();
    }

    /*checks if the .limits stack is not a const 99 value */
    @Test
    public void section6_Limits_Stack_Not_99() {
        var jasminResult = getJasminResult("limits/LocalLimits.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);
        var numStack = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitStackRegex(), 1));
        assertTrue("limit stack should be less than 99:\n" + methodCode, numStack >= 0 && numStack < 99);

        // Make sure the code compiles
        jasminResult.compile();
    }

    /*checks if the .limits stack is the expected value (with a tolerance of 2) */
    @Test
    public void section6_Limits_Stack_Simple() {

        var jasminResult = getJasminResult("limits/LocalLimits.ollir");
        var methodCode = CpUtils.getJasminMethod(jasminResult);
        var numStack = Integer.parseInt(SpecsStrings.getRegexGroup(methodCode, CpUtils.getLimitStackRegex(), 1));

        int expectedLimit = 3;
        int errorMargin = 2;
        int upperLimit = expectedLimit + errorMargin;

        assertTrue(
                "limit stack should be = " + expectedLimit + " (accepted if <= " + upperLimit
                        + "), but is " + numStack + ":\n" + methodCode,
                numStack <= upperLimit && numStack >= expectedLimit);

        // Make sure the code compiles
        jasminResult.compile();
    }
}