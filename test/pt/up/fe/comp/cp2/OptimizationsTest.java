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

package pt.up.fe.comp.cp2;

import org.junit.Test;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OptimizationsTest {
    private static final String BASE_PATH = "pt/up/fe/comp/cp2/optimizations/";

    static OllirResult getOllirResult(String filename) {
        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), Collections.emptyMap(), false);
    }

    static OllirResult getOllirResultOpt(String filename) {
        Map<String, String> config = new HashMap<>();
        config.put(ConfigOptions.getOptimize(), "true");

        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), config, true);
    }

    static OllirResult getOllirResultRegalloc(String filename, int maxRegs) {
        Map<String, String> config = new HashMap<>();
        config.put(ConfigOptions.getRegister(), Integer.toString(maxRegs));


        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), config, true);
    }

    @Test
    public void regAllocSimple() {

        String filename = "reg_alloc/regalloc_no_change.jmm";
        int expectedTotalReg = 4;
        int configMaxRegs = 2;

        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        // Number of registers might change depending on what temporaries are generated, no use comparing with original

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertNotEquals("Expected registers of variables 'a' and 'b' to be different", aReg, varTable.get("b").getVirtualReg(), optimized);
    }


    @Test
    public void regAllocSequence() {

        String filename = "reg_alloc/regalloc.jmm";
        int expectedTotalReg = 3;
        int configMaxRegs = 1;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "soManyRegisters"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        System.out.println("Var table:");
        System.out.println(varTable);
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertEquals("Expected registers of variables 'a' and 'b' to be the same", aReg, varTable.get("b").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'c' to be the same", aReg, varTable.get("c").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'd' to be the same", aReg, varTable.get("d").getVirtualReg(), optimized);

    }


    @Test
    public void constPropSimple() {

        String filename = "const_prop_fold/PropSimple.jmm";

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        CpUtils.assertNotEquals("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode(), optimized.getOllirCode(),
                optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertLiteralReturn("1", method, optimized);
    }

    @Test
    public void constPropWithLoop() {

        String filename = "const_prop_fold/PropWithLoop.jmm";

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        CpUtils.assertNotEquals("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode(), optimized.getOllirCode(),
                optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertLiteralCount("3", method, optimized, 3);
    }

    @Test
    public void constFoldSimple() {

        String filename = "const_prop_fold/FoldSimple.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("30", method, optimized);
    }

    @Test
    public void constFoldSequence() {

        String filename = "const_prop_fold/FoldSequence.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("14", method, optimized);
    }

    @Test
    public void constPropAnFoldSimple() {

        String filename = "const_prop_fold/PropAndFoldingSimple.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("15", method, optimized);
    }

    @Test
    public void regAllocCtor() {
        OllirResult res = getOllirResultRegalloc("extra/PropArrayIndex.jmm", 1);

        var ctor   = CpUtils.getMethod(res, "PropArrayIndex");
        int nCtor  = CpUtils.countRegisters(ctor);
        CpUtils.assertEquals("Construtor devia usar 1 registo", 1, nCtor, res);

        var ctorTab = ctor.getVarTable();
        CpUtils.assertEquals("'this' devia estar no registo 0",
                0, ctorTab.get("this").getVirtualReg(), res);
    }

    /* ---------------- método fun ---------------- */
    @Test
    public void regAllocFun() {
        OllirResult res = getOllirResultRegalloc("extra/PropArrayIndex.jmm", 1);

        var fun   = CpUtils.getMethod(res, "fun");
        int nFun  = CpUtils.countRegisters(fun);
        CpUtils.assertEquals("Método 'fun' devia precisar de 2 registos", 2, nFun, res);

        var tab = fun.getVarTable();
        int regThis = tab.get("this").getVirtualReg();
        int regArr  = tab.get("arr").getVirtualReg();

        // todos (arr,len,tmp0,tmp1) partilham o mesmo registo (#1)
        CpUtils.assertTrue("arr e len deviam partilhar registo",
                regArr == tab.get("len").getVirtualReg(), res);
        CpUtils.assertTrue("arr e tmp0 deviam partilhar registo",
                regArr == tab.get("tmp0").getVirtualReg(), res);
        CpUtils.assertTrue("arr e tmp1 deviam partilhar registo",
                regArr == tab.get("tmp1").getVirtualReg(), res);

        // … e esse não é o mesmo registo do 'this'
        CpUtils.assertNotEquals("'this' não deve partilhar registo com variáveis locais",
                regThis, regArr, res);
    }

    /* ---------------- método main ---------------- */
    @Test
    public void regAllocMain() {
        OllirResult res = getOllirResultRegalloc("extra/PropArrayIndex.jmm", 1);

        var main   = CpUtils.getMethod(res, "main");
        int nMain  = CpUtils.countRegisters(main);
        CpUtils.assertEquals("Método 'main' devia precisar de 1 registo", 1, nMain, res);

        var tab = main.getVarTable();
        CpUtils.assertEquals("'args' devia estar no registo 0",
                0, tab.get("args").getVirtualReg(), res);
    }

    /* *********************************************************************
     *  Register allocation – RegAllocComplex.jmm
     * ********************************************************************/

    @Test
    public void regAllocComplexCtor() {

        String file = "reg_alloc/RegAllocComplex.jmm";
        int maxRegs = 5;                       // -r 5

        OllirResult res = getOllirResultRegalloc(file, maxRegs);

        var ctor  = CpUtils.getMethod(res, "RegAlloc");
        int nCtor = CpUtils.countRegisters(ctor);
        CpUtils.assertEquals("Construtor devia usar 1 registo", 1, nCtor, res);

        CpUtils.assertEquals("'this' devia estar no registo #0",
                0, ctor.getVarTable().get("this").getVirtualReg(), res);
    }



    @Test
    public void regAllocComplexMain() {

        String file = "reg_alloc/RegAllocComplex.jmm";
        int maxRegs = 5;

        OllirResult res = getOllirResultRegalloc(file, maxRegs);

        var main = CpUtils.getMethod(res, "main");
        CpUtils.assertEquals("'main' devia precisar de 1 registo",
                1, CpUtils.countRegisters(main), res);

        CpUtils.assertEquals("'args' devia estar no registo #0",
                0, main.getVarTable().get("args").getVirtualReg(), res);
    }

    @Test
    public void constFoldMixArithmetic() {

        String filename = "extra/ExprMixArithmetic.jmm"; // coloca o .jmm nesta pasta
        OllirResult original  = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        // 1) o código tem de mudar com a flag -o
        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n"
                        + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        // 2) depois do constant‑folding, o literal 9 deve aparecer no método compute
        var method = CpUtils.getMethod(optimized, "compute");
        CpUtils.assertFindLiteral("9", method, optimized);
    }

    /* *********************************************************************
     *  Register allocation – PropWithLoop.jmm
     * ********************************************************************/

    @Test
    public void regAllocLoopCtor() {

        String file = "const_prop_fold/PropWithLoop.jmm";
        int maxRegs = 4;                      // liberdade suficiente

        OllirResult res = getOllirResultRegalloc(file, maxRegs);

        var ctor = CpUtils.getMethod(res, "PropWithLoop");
        CpUtils.assertEquals("Construtor devia usar 1 registo",
                1, CpUtils.countRegisters(ctor), res);

        CpUtils.assertEquals("'this' devia estar no registo #0",
                0, ctor.getVarTable().get("this").getVirtualReg(), res);
    }

    @Test
    public void regAllocLoopMain() {

        String file = "const_prop_fold/PropWithLoop.jmm";
        int maxRegs = 4;

        OllirResult res = getOllirResultRegalloc(file, maxRegs);

        var main = CpUtils.getMethod(res, "main");
        CpUtils.assertEquals("'main' devia precisar de 1 registo",
                1, CpUtils.countRegisters(main), res);

        CpUtils.assertEquals("'args' devia estar no registo #0",
                0, main.getVarTable().get("args").getVirtualReg(), res);
    }

}
