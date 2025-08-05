package pt.up.fe.comp.initial;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.specs.util.SpecsIo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SymbolTableTest {

	static JmmSemanticsResult getSemanticsResult(String filename) {
		return TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/initial/symboltable/" + filename));
	}

	static JmmSemanticsResult test(String filename, boolean fail) {
		var semantics = getSemanticsResult(filename);
		if(fail) {
			TestUtils.mustFail(semantics.getReports());
		} else {
			TestUtils.noErrors(semantics.getReports());
		}
		return semantics;
	}

	@Test
	public void Class() {
		var semantics = test("Class.jmm", false);
		assertEquals("Class", semantics.getSymbolTable().getClassName());
	}

	@Test
	public void Methods() {
		var semantics = test("Methods.jmm", false);
		var st = semantics.getSymbolTable();
		var methods = st.getMethods();
		assertEquals(1, methods.size());

		var method = methods.get(0);
		var ret = st.getReturnType(method);
		assertEquals("Method with return type int", "int", ret.getName());

		var numParameters = st.getParameters(method).size();
		assertEquals("Method " + method + " parameters", 1, numParameters);
	}

	@Test
	public void Fields() {
		var semantics = test("Fields.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("Fields", st.getClassName());
		var fields = st.getFields();
		assertEquals(3, fields.size());
		assertEquals("int", fields.get(0).getType().getName());
		assertEquals("boolean", fields.get(1).getType().getName());
		assertEquals("Fields", fields.get(2).getType().getName());
	}

	@Test
	public void MultipleMethods() {
		var semantics = test("MultipleMethods.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("MultipleMethods", st.getClassName());
		var methods = st.getMethods();
		assertEquals(4, methods.size());

		// method1: sem parâmetros, retorna int
		assertEquals("int", st.getReturnType("method1").getName());
		assertEquals(0, st.getParameters("method1").size());

		// method2: 1 parâmetro boolean, retorna boolean
		assertEquals("boolean", st.getReturnType("method2").getName());
		assertEquals(1, st.getParameters("method2").size());
		assertEquals("boolean", st.getParameters("method2").get(0).getType().getName());

		// method3: 3 parâmetros (Methods, int, boolean), retorna Methods
		assertEquals("Methods", st.getReturnType("method3").getName());
		assertEquals(3, st.getParameters("method3").size());

		// method4: sem parâmetros, retorna void
		assertEquals("void", st.getReturnType("method4").getName());
		assertEquals(0, st.getParameters("method4").size());
	}

	@Test
	public void Inheritance() {
		var semantics = test("Inheritance.jmm", false);
		var st = semantics.getSymbolTable();
		// Nome da classe: Child; Superclasse: Parent
		assertEquals("Child", st.getClassName());
		assertEquals("Parent", st.getSuper());
	}

	@Test
	public void ArrayTypes() {
		var semantics = test("ArrayTypes.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ArrayTypes", st.getClassName());
		var ret = st.getReturnType("getArray");
		// Espera-se que o tipo seja "int" com flag array true
		assertEquals("int", ret.getName());
		assertEquals(true, ret.isArray());

		// Verifica o parâmetro do método sumArray: int[] com flag array true
		var params = st.getParameters("sumArray");
		assertEquals(1, params.size());
		assertEquals("int", params.get(0).getType().getName());
		assertEquals(true, params.get(0).getType().isArray());
	}

	// Testes para os novos arquivos:

	@Test
	public void LocalVariables() {
		var semantics = test("LocalVariables.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("LocalVariables", st.getClassName());
		// Verifica o campo declarado
		var fields = st.getFields();
		assertEquals(1, fields.size());
		assertEquals("int", fields.get(0).getType().getName());

		// Método "compute": retorna int e tem 2 parâmetros (int a, int b)
		assertEquals("int", st.getReturnType("compute").getName());
		var computeParams = st.getParameters("compute");
		assertEquals(2, computeParams.size());
		assertEquals("int", computeParams.get(0).getType().getName());
		assertEquals("int", computeParams.get(1).getType().getName());

		// Método "test": retorna void e não tem parâmetros
		assertEquals("void", st.getReturnType("test").getName());
		assertEquals(0, st.getParameters("test").size());
	}

	@Test
	public void NestedBlocks() {
		var semantics = test("NestedBlocks.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("NestedBlocks", st.getClassName());
		// Apenas o método "process": void sem parâmetros
		assertEquals("void", st.getReturnType("process").getName());
		assertEquals(0, st.getParameters("process").size());
	}

	@Test
	public void ArrayAccess() {
		var semantics = test("ArrayAccess.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ArrayAccess", st.getClassName());

		// Método "access": retorna int e tem 2 parâmetros: int[] e int
		assertEquals("int", st.getReturnType("access").getName());
		var accessParams = st.getParameters("access");
		assertEquals(2, accessParams.size());
		assertEquals("int", accessParams.get(0).getType().getName());
		assertEquals(true, accessParams.get(0).getType().isArray());
		assertEquals("int", accessParams.get(1).getType().getName());
		assertEquals(false, accessParams.get(1).getType().isArray());

		// Método "createArray": retorna int[] e tem 1 parâmetro: int
		var createRet = st.getReturnType("createArray");
		assertEquals("int", createRet.getName());
		assertEquals(true, createRet.isArray());
		var createParams = st.getParameters("createArray");
		assertEquals(1, createParams.size());
		assertEquals("int", createParams.get(0).getType().getName());
	}

	@Test
	public void BooleanLogic() {
		var semantics = test("BooleanLogic.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("BooleanLogic", st.getClassName());
		// Método "evaluate": retorna boolean e tem 2 parâmetros boolean
		assertEquals("boolean", st.getReturnType("evaluate").getName());
		var evalParams = st.getParameters("evaluate");
		assertEquals(2, evalParams.size());
		assertEquals("boolean", evalParams.get(0).getType().getName());
		assertEquals("boolean", evalParams.get(1).getType().getName());
	}

	@Test
	public void MainTest() {
		var semantics = test("MainTest.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("MainTest", st.getClassName());
		// Método "main": retorna void, com 1 parâmetro String[] (array de String)
		assertEquals("void", st.getReturnType("main").getName());
		var mainParams = st.getParameters("main");
		assertEquals(1, mainParams.size());
		assertEquals("String", mainParams.get(0).getType().getName());
		assertEquals(true, mainParams.get(0).getType().isArray());
	}

	@Test
	public void InheritanceChain() {
		var semantics = test("InheritanceChain.jmm", false);
		var st = semantics.getSymbolTable();
		// Supondo que a classe principal seja "Child" com superclasse "Parent"
		assertEquals("Child", st.getClassName());
		assertEquals("Parent", st.getSuper());
		// Método "getChildValue": retorna int
		assertEquals("int", st.getReturnType("getChildValue").getName());
		// (Opcional: verificar se o método herdado "getValue" está presente, conforme a implementação da tabela)
	}

	@Test
	public void CommentsTest() {
		var semantics = test("CommentsTest.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("CommentsTest", st.getClassName());
		// Método "compute": retorna int e não tem parâmetros
		assertEquals("int", st.getReturnType("compute").getName());
		assertEquals(0, st.getParameters("compute").size());
	}

	@Test
	public void EdgeCases() {
		var semantics = test("EdgeCases.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("EdgeCases", st.getClassName());
		// Método "testLiterals": retorna int e sem parâmetros
		assertEquals("int", st.getReturnType("testLiterals").getName());
		assertEquals(0, st.getParameters("testLiterals").size());
	}

	@Test
	public void NestedControl() {
		var semantics = test("NestedControl.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("NestedControl", st.getClassName());
		// Método "process": retorna void sem parâmetros
		assertEquals("void", st.getReturnType("process").getName());
		assertEquals(0, st.getParameters("process").size());
	}

	@Test
	public void ComplexMethods() {
		var semantics = test("ComplexMethods.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ComplexMethods", st.getClassName());
		// Método "transform": retorna int[] e tem 2 parâmetros: int[] e int
		var transformRet = st.getReturnType("transform");
		assertEquals("int", transformRet.getName());
		assertEquals(true, transformRet.isArray());
		var transformParams = st.getParameters("transform");
		assertEquals(2, transformParams.size());
		assertEquals("int", transformParams.get(0).getType().getName());
		assertEquals(true, transformParams.get(0).getType().isArray());
		assertEquals("int", transformParams.get(1).getType().getName());
		assertEquals(false, transformParams.get(1).getType().isArray());

		// Método "compare": retorna boolean e tem 2 parâmetros int
		assertEquals("boolean", st.getReturnType("compare").getName());
		var compareParams = st.getParameters("compare");
		assertEquals(2, compareParams.size());
		assertEquals("int", compareParams.get(0).getType().getName());
		assertEquals("int", compareParams.get(1).getType().getName());
	}

	@Test
	public void ComplexExpressions() {
		var semantics = test("ComplexExpressions.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ComplexExpressions", st.getClassName());
		// Método "calculate": retorna int e tem 3 parâmetros (int, int, int)
		assertEquals("int", st.getReturnType("calculate").getName());
		var calcParams = st.getParameters("calculate");
		assertEquals(3, calcParams.size());
		assertEquals("int", calcParams.get(0).getType().getName());
		assertEquals("int", calcParams.get(1).getType().getName());
		assertEquals("int", calcParams.get(2).getType().getName());
	}

	@Test
	public void ControlStructures() {
		var semantics = test("ControlStructures.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ControlStructures", st.getClassName());
		// Método "execute": retorna void sem parâmetros
		assertEquals("void", st.getReturnType("execute").getName());
		assertEquals(0, st.getParameters("execute").size());
	}

	@Test
	public void Recursion() {
		var semantics = test("Recursion.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("Recursion", st.getClassName());
		// Método "factorial": retorna int e tem 1 parâmetro int
		assertEquals("int", st.getReturnType("factorial").getName());
		var factorialParams = st.getParameters("factorial");
		assertEquals(1, factorialParams.size());
		assertEquals("int", factorialParams.get(0).getType().getName());
	}

	@Test
	public void UnaryOperators() {
		var semantics = test("UnaryOperators.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("UnaryOperators", st.getClassName());
		// Método "negate": retorna int e tem 1 parâmetro int
		assertEquals("int", st.getReturnType("negate").getName());
		var negateParams = st.getParameters("negate");
		assertEquals(1, negateParams.size());
		assertEquals("int", negateParams.get(0).getType().getName());

		// Método "not": retorna boolean e tem 1 parâmetro boolean
		assertEquals("boolean", st.getReturnType("not").getName());
		var notParams = st.getParameters("not");
		assertEquals(1, notParams.size());
		assertEquals("boolean", notParams.get(0).getType().getName());
	}
	@Test
	public void VarargsTest() {
		var semantics = test("VarargsTest.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("VarargsTest", st.getClassName());

		// Method "sum" with varargs parameter
		assertEquals("int", st.getReturnType("sum").getName());
		var sumParams = st.getParameters("sum");
		assertEquals(1, sumParams.size());
		assertEquals("int", sumParams.get(0).getType().getName());
		assertEquals(true, sumParams.get(0).getType().isArray());

		// Method "process" with normal parameters and varargs
		assertEquals("int", st.getReturnType("process").getName());
		var processParams = st.getParameters("process");
		assertEquals(3, processParams.size());
		assertEquals("int", processParams.get(0).getType().getName());
		assertEquals("boolean", processParams.get(1).getType().getName());
		assertEquals("int", processParams.get(2).getType().getName());
		assertEquals(true, processParams.get(2).getType().isArray());
	}

	@Test
	public void ArrayInitializerTest() {
		var semantics = test("ArrayInitializer.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ArrayInitializer", st.getClassName());

		// Method "getValues" returning int[]
		var getValuesRet = st.getReturnType("getValues");
		assertEquals("int", getValuesRet.getName());
		assertEquals(true, getValuesRet.isArray());

		// Method "combine" with array parameters and return
		var combineRet = st.getReturnType("combine");
		assertEquals("int", combineRet.getName());
		assertEquals(true, combineRet.isArray());

		var combineParams = st.getParameters("combine");
		assertEquals(2, combineParams.size());
		assertEquals("int", combineParams.get(0).getType().getName());
		assertEquals(true, combineParams.get(0).getType().isArray());
		assertEquals("int", combineParams.get(1).getType().getName());
		assertEquals(true, combineParams.get(1).getType().isArray());
	}

	@Test
	public void ImportUsageTest() {
		var semantics = test("ImportUsage.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ImportUsage", st.getClassName());

		// Check imports
		var imports = st.getImports();
		assertEquals(3, imports.size());
		assertEquals("java.util.List", imports.get(0));
		assertEquals("java.io.File", imports.get(1));
		assertEquals("MyCustomClass", imports.get(2));
	}

	@Test
	public void VariableScopesTest() {
		var semantics = test("VariableScopes.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("VariableScopes", st.getClassName());

		// Check field 'x'
		var fields = st.getFields();
		assertEquals(1, fields.size());
		assertEquals("int", fields.get(0).getType().getName());

		// Methods
		assertEquals("int", st.getReturnType("testScopes").getName());
		assertEquals("int", st.getReturnType("anotherMethod").getName());

		var anotherMethodParams = st.getParameters("anotherMethod");
		assertEquals(1, anotherMethodParams.size());
		assertEquals("int", anotherMethodParams.get(0).getType().getName());
	}

	@Test
	public void MethodOverloadingTest() {
		var semantics = test("MethodOverloading.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("MethodOverloading", st.getClassName());

		// Note: The actual behavior depends on how method overloading is handled
		// Most basic compiler might just have the last method definition win
		var methods = st.getMethods();
		assertTrue(methods.contains("calculate"));
	}

	@Test
	public void CombinedVarArgsTest() {
		var semantics = test("CombinedVarArgs.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("CombinedVarArgs", st.getClassName());

		// Method calculateSum with varargs
		assertEquals("int", st.getReturnType("calculateSum").getName());
		var calcSumParams = st.getParameters("calculateSum");
		assertEquals(1, calcSumParams.size());
		assertEquals("int", calcSumParams.get(0).getType().getName());
		assertEquals(true, calcSumParams.get(0).getType().isArray());

		// Method getSequence with regular param and varargs
		var getSeqRet = st.getReturnType("getSequence");
		assertEquals("int", getSeqRet.getName());
		assertEquals(true, getSeqRet.isArray());

		var getSeqParams = st.getParameters("getSequence");
		assertEquals(2, getSeqParams.size());
		assertEquals("int", getSeqParams.get(0).getType().getName());
		assertEquals(false, getSeqParams.get(0).getType().isArray());
		assertEquals("int", getSeqParams.get(1).getType().getName());
		assertEquals(true, getSeqParams.get(1).getType().isArray());
	}

	@Test
	public void InheritedMethodsTest() {
		var semantics = test("InheritedMethods.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("InheritedMethods", st.getClassName());
		assertEquals("BaseClass", st.getSuper());

		// Check methods
		var methods = st.getMethods();
		assertTrue(methods.contains("calculate"));
		assertTrue(methods.contains("overrideMethod"));

		// Check return types
		assertEquals("int", st.getReturnType("calculate").getName());
		assertEquals("int", st.getReturnType("overrideMethod").getName());

		// Check parameters
		var overrideParams = st.getParameters("overrideMethod");
		assertEquals(1, overrideParams.size());
		assertEquals("int", overrideParams.get(0).getType().getName());
	}

	@Test
	public void ComplexProgramTest() {
		var semantics = test("ComplexProgram.jmm", false);
		var st = semantics.getSymbolTable();
		assertEquals("ComplexProgram", st.getClassName());

		// Check imports
		var imports = st.getImports();
		assertEquals(1, imports.size());
		assertEquals("java.util.Arrays", imports.get(0));

		// Check fields
		var fields = st.getFields();
		assertEquals(2, fields.size());

		// Check methods
		var methods = st.getMethods();
		assertTrue(methods.contains("main"));
		assertTrue(methods.contains("processData"));
		assertTrue(methods.contains("run"));

		// Check main method
		assertEquals("void", st.getReturnType("main").getName());
		var mainParams = st.getParameters("main");
		assertEquals(1, mainParams.size());
		assertEquals("String", mainParams.get(0).getType().getName());
		assertEquals(true, mainParams.get(0).getType().isArray());

		// Check processData method
		var processDataRet = st.getReturnType("processData");
		assertEquals("int", processDataRet.getName());
		assertEquals(true, processDataRet.isArray());

		var processDataParams = st.getParameters("processData");
		assertEquals(3, processDataParams.size());
		assertEquals("int", processDataParams.get(0).getType().getName());
		assertEquals(true, processDataParams.get(0).getType().isArray());
		assertEquals("int", processDataParams.get(1).getType().getName());
		assertEquals("boolean", processDataParams.get(2).getType().getName());

		// Check run method
		assertEquals("void", st.getReturnType("run").getName());
		var runParams = st.getParameters("run");
		assertEquals(3, runParams.size());
		assertEquals("String", runParams.get(0).getType().getName());
		assertEquals(true, runParams.get(0).getType().isArray());
		assertEquals("int", runParams.get(1).getType().getName());
		assertEquals("boolean", runParams.get(2).getType().getName());
	}

	/*

	class Arrays {
    public void arrayOperations() {
        int[] myArray;
        int size;
        int element;


        myArray = new int[10];  // Criação do array
        myArray[0] = 5;         // Atribuição a um elemento
        element = myArray[2];     // Acesso a um elemento
        size = myArray.length;   // Obtenção do

        int x;
        x = myArray[1+1]; //array access com expressao
    }

    public static void main(String[] args) {}
}
	 */


}
