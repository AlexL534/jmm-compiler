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
    public void regAllocMinimize() {
        String filename = "reg_alloc/regalloc_minimize.jmm";
        int expectedTotalReg = 2;
        int configMaxRegs = 0; // Minimize registers

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "minimize"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "minimize"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag (minimize case)\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'minimize' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        var varTable = CpUtils.getMethod(optimized, "minimize").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertEquals("Expected registers of variables 'a' and 'c' to be the same in minimize mode", 
                aReg, varTable.get("c").getVirtualReg(), optimized);
    }

    @Test
    public void regAllocInterference() {
        String filename = "reg_alloc/regalloc_interference.jmm";
        int expectedTotalReg = 4;
        int configMaxRegs = 2;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "interference"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "interference"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'interference' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        var varTable = CpUtils.getMethod(optimized, "interference").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var bReg = varTable.get("b").getVirtualReg();
        CpUtils.assertNotEquals("Expected registers of variables 'a' and 'b' to be different as they interfere", 
                aReg, bReg, optimized);
    }

    @Test
    public void regAllocParams() {
        String filename = "reg_alloc/regalloc_params.jmm";
        int expectedTotalReg = 5; // 2 parameters + this + 2 locals
        int configMaxRegs = 2;

        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "withParams"));

        CpUtils.assertTrue("Expected number of locals in 'withParams' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        var varTable = CpUtils.getMethod(optimized, "withParams").getVarTable();
        var thisReg = varTable.get("this").getVirtualReg();
        var param1Reg = varTable.get("p1").getVirtualReg();
        var param2Reg = varTable.get("p2").getVirtualReg();
        var localReg = varTable.get("local").getVirtualReg();

        CpUtils.assertEquals("Expected 'this' to be in register 0", 0, thisReg, optimized);
        CpUtils.assertEquals("Expected param 'p1' to be in register 1", 1, param1Reg, optimized);
        CpUtils.assertEquals("Expected param 'p2' to be in register 2", 2, param2Reg, optimized);
        CpUtils.assertTrue("Expected local variable to be in register greater than parameters", 
                localReg > param2Reg, optimized);
    }

    @Test
    public void regAllocCopyChains() {
        String filename = "reg_alloc/regalloc_copy_chains.jmm";
        int expectedTotalReg = 4; // Including parameters
        int configMaxRegs = 1;

        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "copyChain"));

        CpUtils.assertTrue("Expected number of locals in 'copyChain' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        var varTable = CpUtils.getMethod(optimized, "copyChain").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var bReg = varTable.get("b").getVirtualReg();
        var cReg = varTable.get("c").getVirtualReg();

        CpUtils.assertEquals("Expected registers of variables in a copy chain to be the same", 
                aReg, bReg, optimized);
        CpUtils.assertEquals("Expected registers of variables in a copy chain to be the same", 
                bReg, cReg, optimized);
    }

    @Test(expected = RuntimeException.class)
    public void regAllocFailure() {
        String filename = "reg_alloc/regalloc_failure.jmm";
        int configMaxRegs = 1; // Deliberately too small to succeed

        // This should throw an exception because it's impossible to use only 1 register
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);
    }
}
