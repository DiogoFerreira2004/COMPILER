package pt.up.fe.comp.cp2;

import org.junit.Test;
import org.specs.comp.ollir.ArrayOperand;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.OperationType;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.BuiltinKind;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;

public class OllirTest {
    private static final String BASE_PATH = "pt/up/fe/comp/cp2/ollir/";

    static OllirResult getOllirResult(String filename) {
        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), Collections.emptyMap(), false);
    }

    public void compileBasic(ClassUnit classUnit) {
        // Test name of the class and super
        assertEquals("Class name not what was expected", "CompileBasic", classUnit.getClassName());
        assertEquals("Super class name not what was expected", "Quicksort", classUnit.getSuperClass());

        // Test method 1
        Method method1 = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals("method1"))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method1", method1);

        var retInst1 = method1.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method1", retInst1.isPresent());

        // Test method 2
        Method method2 = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals("method2"))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method2'", method2);

        var retInst2 = method2.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method2", retInst2.isPresent());
    }

    public void compileBasicWithFields(OllirResult ollirResult) {

        ClassUnit classUnit = ollirResult.getOllirClass();

        // Test name of the class and super
        assertEquals("Class name not what was expected", "CompileBasic", classUnit.getClassName());
        assertEquals("Super class name not what was expected", "Quicksort", classUnit.getSuperClass());

        // Test fields
        assertEquals("Class should have two fields", 2, classUnit.getNumFields());
        var fieldNames = new HashSet<>(Arrays.asList("intField", "boolField"));
        assertThat(fieldNames, hasItem(classUnit.getField(0).getFieldName()));
        assertThat(fieldNames, hasItem(classUnit.getField(1).getFieldName()));

        // Test method 1
        Method method1 = CpUtils.getMethod(ollirResult, "method1");
        assertNotNull("Could not find method1", method1);

        var method1GetField = CpUtils.getInstructions(GetFieldInstruction.class, method1);
        assertTrue("Expected 1 getfield instruction in method1, found " + method1GetField.size(), method1GetField.size() == 1);


        // Test method 2
        var method2 = CpUtils.getMethod(ollirResult, "method2");
        assertNotNull("Could not find method2'", method2);

        var method2GetField = CpUtils.getInstructions(GetFieldInstruction.class, method2);
        assertTrue("Expected 0 getfield instruction in method2, found " + method2GetField.size(), method2GetField.isEmpty());

        var method2PutField = CpUtils.getInstructions(PutFieldInstruction.class, method2);
        assertTrue("Expected 0 putfield instruction in method2, found " + method2PutField.size(), method2PutField.isEmpty());

        // Test method 3
        var method3 = CpUtils.getMethod(ollirResult, "method3");
        assertNotNull("Could not find method3'", method3);

        var method3PutField = CpUtils.getInstructions(PutFieldInstruction.class, method3);
        assertTrue("Expected 1 putfield instruction in method3, found " + method3PutField.size(), method3PutField.size() == 1);
    }

    public void compileArithmetic(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileArithmetic", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null); 

        assertNotNull("Could not find method " + methodName, methodFoo);

        var binOpInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof AssignInstruction)
                .map(instr -> (AssignInstruction) instr)
                .filter(assign -> assign.getRhs() instanceof BinaryOpInstruction)
                .findFirst();

        assertTrue("Could not find a binary op instruction in method " + methodName, binOpInst.isPresent());

        var retInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method " + methodName, retInst.isPresent());
    }

    public void compileMethodInvocation(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileMethodInvocation", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var callInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof CallInstruction)
                .map(CallInstruction.class::cast)
                .findFirst();
        assertTrue("Could not find a call instruction in method " + methodName, callInst.isPresent());

        assertEquals("Invocation type not what was expected", InvokeStaticInstruction.class,
                callInst.get().getClass());
    }

    public void compileAssignment(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileAssignment", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var assignInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof AssignInstruction)
                .map(AssignInstruction.class::cast)
                .findFirst();
        assertTrue("Could not find an assign instruction in method " + methodName, assignInst.isPresent());

        assertEquals("Assignment does not have the expected type", BuiltinKind.INT32, CpUtils.toBuiltinKind(assignInst.get().getTypeOfAssign()));
    }


    @Test
    public void basicClass() {
        var result = getOllirResult("basic/BasicClass.jmm");

        compileBasic(result.getOllirClass());
    }

    @Test
    public void basicClassWithFields() {
        var result = getOllirResult("basic/BasicClassWithFields.jmm");
        System.out.println(result.getOllirCode());

        compileBasicWithFields(result);
    }

    @Test
    public void basicAssignment() {
        var result = getOllirResult("basic/BasicAssignment.jmm");

        compileAssignment(result.getOllirClass());
    }

    @Test
    public void basicMethodInvocation() {
        var result = getOllirResult("basic/BasicMethodInvocation.jmm");

        compileMethodInvocation(result.getOllirClass());
    }


    /*checks if method declaration is correct (array)*/
    @Test
    public void basicMethodDeclarationArray() {
        var result = getOllirResult("basic/BasicMethodsArray.jmm");

        var method = CpUtils.getMethod(result, "func4");

        CpUtils.assertEquals("Method return type", "int[]", CpUtils.toString(method.getReturnType()), result);
    }

    @Test
    public void arithmeticSimpleAdd() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_add.jmm");

        compileArithmetic(ollirResult.getOllirClass());
        System.out.println(ollirResult.getOllirCode());
    }

    @Test
    public void arithmeticSimpleAnd() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_and.jmm");
        var method = CpUtils.getMethod(ollirResult, "main");
        var numBranches = CpUtils.getInstructions(CondBranchInstruction.class, method).size();

        System.out.println(ollirResult.getOllirCode());

        CpUtils.assertTrue("Expected at least 2 branches, found " + numBranches, numBranches >= 2, ollirResult);
    }

    @Test
    public void arithmeticSimpleLess() {
        var ollirResult = getOllirResult("arithmetic/Arithmetic_less.jmm");

        var method = CpUtils.getMethod(ollirResult, "main");

        CpUtils.assertHasOperation(OperationType.LTH, method, ollirResult);
        System.out.println(ollirResult.getOllirCode());

    }

    @Test
    public void controlFlowIfSimpleSingleGoTo() {

        var result = getOllirResult("control_flow/SimpleIfElseStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);
        CpUtils.assertEquals("Number of branches", 1, branches.size(), result);

        var gotos = CpUtils.assertInstExists(GotoInstruction.class, method, result);
        CpUtils.assertTrue("Has at least 1 goto", gotos.size() >= 1, result);
    }

    @Test
    public void controlFlowIfSwitch() {

        var result = getOllirResult("control_flow/SwitchStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);
        CpUtils.assertEquals("Number of branches", 6, branches.size(), result);

        var gotos = CpUtils.assertInstExists(GotoInstruction.class, method, result);
        CpUtils.assertTrue("Has at least 6 gotos", gotos.size() >= 6, result);
    }

    @Test
    public void controlFlowWhileSimple() {

        var result = getOllirResult("control_flow/SimpleWhileStat.jmm");

        var method = CpUtils.getMethod(result, "func");

        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, method, result);

        CpUtils.assertTrue("Number of branches between 1 and 2", branches.size() > 0 && branches.size() < 3, result);
    }


    /*checks if an array is correctly initialized*/
    @Test
    public void arraysInitArray() {
        var result = getOllirResult("arrays/ArrayInit.jmm");

        var method = CpUtils.getMethod(result, "main");

        var calls = CpUtils.assertInstExists(CallInstruction.class, method, result);

        CpUtils.assertEquals("Number of calls", 3, calls.size(), result);

        // Get new
        var newCalls = calls.stream().filter(call -> call instanceof NewInstruction)
                .collect(Collectors.toList());

        CpUtils.assertEquals("Number of 'new' calls", 1, newCalls.size(), result);

        // Get length
        var lengthCalls = calls.stream().filter(call -> call instanceof ArrayLengthInstruction)
                .collect(Collectors.toList());

        CpUtils.assertEquals("Number of 'arraylenght' calls", 1, lengthCalls.size(), result);
    }

    /*checks if the access to the elements of array is correct*/
    @Test
    public void arraysAccessArray() {
        var result = getOllirResult("arrays/ArrayAccess.jmm");

        var method = CpUtils.getMethod(result, "foo");

        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 5, numArrayStores, result);

        var numArrayReads = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .filter(element -> element instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array reads", 5, numArrayReads, result);
    }

    /*checks multiple expressions as indexes to access the elements of an array*/
    @Test
    public void arraysLoadComplexArrayAccess() {
        // Just parse
        var result = getOllirResult("arrays/ComplexArrayAccess.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var method = CpUtils.getMethod(result, "main");

        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 5, numArrayStores, result);

        var numArrayReads = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .filter(element -> element instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array reads", 6, numArrayReads, result);
    }
    @Test
    public void varargsTest() {
        var result = getOllirResult("extra/Varargs.jmm");
        System.out.println(result.getOllirCode());

        // Verify class name
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name not what was expected", "Varargs", classUnit.getClassName());

        // Test sum method - verify it has varargs parameter
        Method sumMethod = CpUtils.getMethod(result, "sum");
        assertNotNull("Could not find 'sum' method", sumMethod);

        // Check varargs parameter is represented as array
        var params = sumMethod.getParams();
        CpUtils.assertEquals("Number of parameters", 1, params.size(), result);

        // Verify parameter is an array by checking the toString representation
        String paramTypeStr = CpUtils.toString(params.get(0).getType());
        CpUtils.assertTrue("First parameter should be an array type", paramTypeStr.contains("[]") || paramTypeStr.contains("array"), result);

        // Test test method - verify it calls sum with array
        Method testMethod = CpUtils.getMethod(result, "test");
        assertNotNull("Could not find 'test' method", testMethod);

        // Verify method call
        var callInsts = CpUtils.getInstructions(CallInstruction.class, testMethod);
        CpUtils.assertTrue("Expected method call to 'sum'", callInsts.size() >= 1, result);
    }

    @Test
    public void complexIfElseTest() {
        var result = getOllirResult("extra/ComplexIfElse.jmm");
        System.out.println(result.getOllirCode());

        // Verify class name
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name not what was expected", "ComplexIfElse", classUnit.getClassName());

        // Test max method
        Method maxMethod = CpUtils.getMethod(result, "max");
        assertNotNull("Could not find 'max' method", maxMethod);

        // Verify complex if-else structure with conditional branches
        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, maxMethod, result);
        CpUtils.assertTrue("Expected at least 2 conditional branches", branches.size() >= 2, result);

        // Verify goto instructions for if-else implementation
        var gotos = CpUtils.assertInstExists(GotoInstruction.class, maxMethod, result);
        CpUtils.assertTrue("Expected multiple goto instructions for nested if-else", gotos.size() >= 2, result);

        // Verify return instruction
        var returnInst = CpUtils.assertInstExists(ReturnInstruction.class, maxMethod, result);
        CpUtils.assertEquals("Should have exactly 1 return instruction", 1, returnInst.size(), result);
    }

    @Test
    public void complexWhileTest() {
        var result = getOllirResult("extra/ComplexWhile.jmm");
        System.out.println(result.getOllirCode());

        // Verify class name
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name not what was expected", "ComplexWhile", classUnit.getClassName());

        // Test countEven method
        Method countMethod = CpUtils.getMethod(result, "countEven");
        assertNotNull("Could not find 'countEven' method", countMethod);

        // Verify while loop structure
        var branches = CpUtils.assertInstExists(CondBranchInstruction.class, countMethod, result);
        CpUtils.assertTrue("Expected at least 2 conditional branches (while condition + if condition)", branches.size() >= 2, result);

        // Verify goto instructions for while loop implementation
        var gotos = CpUtils.assertInstExists(GotoInstruction.class, countMethod, result);
        CpUtils.assertTrue("Expected at least 1 goto instruction for while loop", gotos.size() >= 1, result);

        // Verify array length operation
        var calls = CpUtils.assertInstExists(CallInstruction.class, countMethod, result);
        boolean hasArrayLength = calls.stream().anyMatch(call -> call instanceof ArrayLengthInstruction);
        CpUtils.assertTrue("Should use array length in while condition", hasArrayLength, result);

        // Verify arithmetic operations used in condition
        CpUtils.assertHasOperation(OperationType.DIV, countMethod, result);
        CpUtils.assertHasOperation(OperationType.MUL, countMethod, result);
    }

    @Test
    public void arraysAndVarargsTest() {
        var result = getOllirResult("extra/ArraysAndVarargs.jmm");
        System.out.println(result.getOllirCode());

        // Verify class name
        ClassUnit classUnit = result.getOllirClass();
        assertEquals("Class name not what was expected", "ArraysAndVarargs", classUnit.getClassName());

        // Test processArray method
        Method processArrayMethod = CpUtils.getMethod(result, "processArray");
        assertNotNull("Could not find 'processArray' method", processArrayMethod);

        // Test processVarargs method
        Method processVarargsMethod = CpUtils.getMethod(result, "processVarargs");
        assertNotNull("Could not find 'processVarargs' method", processVarargsMethod);

        // Verify varargs parameter is treated as array
        var params = processVarargsMethod.getParams();
        CpUtils.assertEquals("Number of parameters", 1, params.size(), result);

        // Verify parameter is an array by checking the toString representation
        String paramTypeStr = CpUtils.toString(params.get(0).getType());
        CpUtils.assertTrue("First parameter should be an array type", paramTypeStr.contains("[]") || paramTypeStr.contains("array"), result);

        // Test test method
        Method testMethod = CpUtils.getMethod(result, "test");
        assertNotNull("Could not find 'test' method", testMethod);

        // Verify array assignments
        var assigns = CpUtils.assertInstExists(AssignInstruction.class, testMethod, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 3, numArrayStores, result);

        // Verify method calls
        var callInsts = CpUtils.getInstructions(CallInstruction.class, testMethod);
        CpUtils.assertTrue("Expected method calls to processArray and processVarargs", callInsts.size() >= 2, result);
    }

    @Test
    public void arraysLoadArrayMethodAccess() {
        // Just parse
        var result = getOllirResult("extra/ArrayMethodAccess.jmm");

        System.out.println("---------------------- OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("---------------------- OLLIR ----------------------");

        var method = CpUtils.getMethod(result, "main");

        var assigns = CpUtils.assertInstExists(AssignInstruction.class, method, result);
        var numArrayStores = assigns.stream().filter(assign -> assign.getDest() instanceof ArrayOperand).count();
        CpUtils.assertEquals("Number of array stores", 4, numArrayStores, result);

        var numArrayReads = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .filter(element -> element instanceof ArrayOperand).count();
        // pegar os elementos e printar
        var elements = assigns.stream()
                .flatMap(assign -> CpUtils.getElements(assign.getRhs()).stream())
                .collect(Collectors.toList());
        for (var element : elements) {
            System.out.println("Element: " + element);
        }

        CpUtils.assertEquals("Number of array reads", 5, numArrayReads, result);

        var calls = CpUtils.assertInstExists(CallInstruction.class, method, result);
        var methodCalls = calls.stream()
                .filter(call -> call instanceof InvokeVirtualInstruction)
                .count();
        CpUtils.assertEquals("Number of virtual method calls", 1, methodCalls, result);

        var lengthCalls = calls.stream()
                .filter(call -> call instanceof ArrayLengthInstruction)
                .count();
        CpUtils.assertEquals("Number of array length operations", 1, lengthCalls, result);
    }

}
