package pt.up.fe.comp.initial;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;

public class GrammarTest {
    private static final String INSTANCE_METHOD = "methodDecl";
    private static final String STATEMENT = "stmt";
    private static final String EXPRESSION = "expr";

    @Test
    public void testClass() {
        TestUtils.parseVerbose("class Foo {}");
    }

    @Test
    public void testClassWithExtends() {
        TestUtils.parseVerbose("class Foo extends Bar {}");
    }

    @Test
    public void testImport() {
        TestUtils.parseVerbose("import java.io.File; class Foo {}");
    }

    @Test
    public void testMultipleImports() {
        TestUtils.parseVerbose("import java.io.File; import java.util.List; class Foo {}");
    }

    @Test
    public void testClassWithFields() {
        TestUtils.parseVerbose("class Foo { int x; boolean flag; Foo other; }");
    }

    @Test
    public void testInstanceMethodEmpty() {
        TestUtils.parseVerbose("int foo(int anInt) {return anInt;}", INSTANCE_METHOD);
    }

    @Test
    public void testVoidMethod() {
        TestUtils.parseVerbose("void foo() {return;}", INSTANCE_METHOD);
    }

    @Test
    public void testMethodWithMultipleParams() {
        TestUtils.parseVerbose("int foo(int a, boolean b, Foo c) {return a;}", INSTANCE_METHOD);
    }

    @Test
    public void testVarargMethod() {
        TestUtils.parseVerbose("int sum(int... nums) {return 0;}", INSTANCE_METHOD);
    }

    @Test
    public void testMainMethod() {
        TestUtils.parseVerbose("public static void main(String[] args) {}", INSTANCE_METHOD);
    }

    @Test
    public void testStmtAssign() {
        TestUtils.parseVerbose("a=b;", STATEMENT);
    }

    @Test
    public void testStmtArrayAssign() {
        TestUtils.parseVerbose("arr[i]=value;", STATEMENT);
    }

    @Test
    public void testStmtBlock() {
        TestUtils.parseVerbose("{ int a; a = 1; }", STATEMENT);
    }

    @Test
    public void testStmtIf() {
        TestUtils.parseVerbose("if (a < b) x = 1; else x = 2;", STATEMENT);
    }

    @Test
    public void testStmtWhile() {
        TestUtils.parseVerbose("while (a < 10) a = a + 1;", STATEMENT);
    }

    @Test
    public void testExprId() {
        TestUtils.parseVerbose("a", EXPRESSION);
    }

    @Test
    public void testExprIntLiteral() {
        TestUtils.parseVerbose("9", EXPRESSION);
    }

    @Test
    public void testExprBooleanLiteral() {
        TestUtils.parseVerbose("true", EXPRESSION);
    }

    @Test
    public void testExprThis() {
        TestUtils.parseVerbose("this", EXPRESSION);
    }

    @Test
    public void testExprMult() {
        TestUtils.parseVerbose("2 * 3", EXPRESSION);
    }

    @Test
    public void testExprMultAddChain() {
        TestUtils.parseVerbose("1 * 2 + 3 * 4", EXPRESSION);
    }

    @Test
    public void testExprAdd() {
        TestUtils.parseVerbose("2 + 3", EXPRESSION);
    }

    @Test
    public void testExprSub() {
        TestUtils.parseVerbose("5 - 3", EXPRESSION);
    }

    @Test
    public void testExprDiv() {
        TestUtils.parseVerbose("6 / 2", EXPRESSION);
    }

    @Test
    public void testExprRel() {
        TestUtils.parseVerbose("a < b", EXPRESSION);
    }

    @Test
    public void testExprLogical() {
        TestUtils.parseVerbose("a && b", EXPRESSION);
    }

    @Test
    public void testExprNot() {
        TestUtils.parseVerbose("!flag", EXPRESSION);
    }

    @Test
    public void testExprArrayAccess() {
        TestUtils.parseVerbose("arr[idx]", EXPRESSION);
    }

    @Test
    public void testExprArrayLength() {
        TestUtils.parseVerbose("arr.length", EXPRESSION);
    }

    @Test
    public void testExprMethodCall() {
        TestUtils.parseVerbose("obj.method(a, b)", EXPRESSION);
    }

    @Test
    public void testExprNewArray() {
        TestUtils.parseVerbose("new int[size]", EXPRESSION);
    }

    @Test
    public void testExprNewObject() {
        TestUtils.parseVerbose("new Foo()", EXPRESSION);
    }

    @Test
    public void testExprArrayInitializer() {
        TestUtils.parseVerbose("[1, 2, 3]", EXPRESSION);
    }

    @Test
    public void testComplexExpression() {
        TestUtils.parseVerbose("a + b * !c && (d < e.method())", EXPRESSION);
    }
}