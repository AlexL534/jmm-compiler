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
import pt.up.fe.comp2025.CompilerConfig;
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
        int expectedNumReg = 4;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);

        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        // Number of registers might change depending on what temporaries are generated, no use comparing with original

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedNumReg + ", is " + actualNumReg,
                actualNumReg == expectedNumReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertNotEquals("Expected registers of variables 'a' and 'b' to be different", aReg, varTable.get("b").getVirtualReg(), optimized);
    }


    @Test
    public void regAllocSequence() {

        String filename = "reg_alloc/regalloc.jmm";
        int expectedNumReg = 3;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "soManyRegisters"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedNumReg + ", is " + actualNumReg,
                actualNumReg == expectedNumReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertEquals("Expected registers of variables 'a' and 'b' to be the same", aReg, varTable.get("b").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'c' to be the same", aReg, varTable.get("c").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'd' to be the same", aReg, varTable.get("d").getVirtualReg(), optimized);

    }

    @Test
    public void regAllocConditional() {
        String filename = "reg_alloc/regalloc_conditional.jmm";
        int expectedNumReg = 4; // This should be enough to handle the method

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);

        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "conditionalControl"));

        CpUtils.assertTrue("Expected number of locals in 'conditionalControl' to be less than or equal to " + expectedNumReg + ", is " + actualNumReg,
                actualNumReg <= expectedNumReg,
                optimized);

        // Check that results variable shares register with another variable 
        // since they're not live at the same time
        var varTable = CpUtils.getMethod(optimized, "conditionalControl").getVarTable();
        var resultReg = varTable.get("result").getVirtualReg();
        var dReg = varTable.get("d").getVirtualReg();
        CpUtils.assertEquals("Expected 'result' and 'd' to share the same register since they aren't simultaneously live", 
                resultReg, dReg, optimized);
    }

    @Test
    public void regAllocSpill() {
        String filename = "reg_alloc/regalloc_spill.jmm";
        
        // First try with limited registers - should fail or spill
        int tooFewReg = 4;
        
        OllirResult original = getOllirResult(filename);
        
        // Try with enough registers
        int enoughReg = 8;
        OllirResult optimized = getOllirResultRegalloc(filename, enoughReg);
        
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "manyLiveVars"));
        
        // Should be able to allocate with the required number of registers
        CpUtils.assertTrue("Expected successful allocation with " + enoughReg + " registers",
                actualNumReg <= enoughReg,
                optimized);
    }

    @Test
    public void regAllocMethodCalls() {
        String filename = "reg_alloc/regalloc_method_calls.jmm";
        int expectedNumReg = 4;

        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);

        // Check main method
        int methodRegs = CpUtils.countRegisters(CpUtils.getMethod(optimized, "methodWithCalls"));
        
        CpUtils.assertTrue("Expected number of registers in 'methodWithCalls' to be less than or equal to " + expectedNumReg,
                methodRegs <= expectedNumReg,
                optimized);
                
        // Also verify the helper method has proper register allocation
        int helperRegs = CpUtils.countRegisters(CpUtils.getMethod(optimized, "helper"));
        CpUtils.assertTrue("Expected number of registers in 'helper' to be minimal", 
                helperRegs <= 2, // 'this' + 1 parameter
                optimized);
    }

    @Test 
    public void regAllocArrays() {
        String filename = "reg_alloc/regalloc_arrays.jmm";
        int expectedNumReg = 5;

        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);
        
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "arrayAccess"));
        
        CpUtils.assertTrue("Expected number of registers in 'arrayAccess' to be less than or equal to " + expectedNumReg,
                actualNumReg <= expectedNumReg,
                optimized);
                
        // Check that i and j can share registers since they're used in different loops
        var varTable = CpUtils.getMethod(optimized, "arrayAccess").getVarTable();
        var iReg = varTable.get("i").getVirtualReg();
        var jReg = varTable.get("j").getVirtualReg();
        CpUtils.assertEquals("Expected loop counters 'i' and 'j' to share registers since they aren't simultaneously live",
                iReg, jReg,
                optimized);
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
    public void regAllocCopyChains() {
        String filename = "reg_alloc/regalloc_copy_chains.jmm";
        int expectedNumReg = 4; // We should need at most 4 registers due to copy chain optimization

        OllirResult optimized = getOllirResultRegalloc(filename, expectedNumReg);
        
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "copyChains"));
        
        // Should be able to allocate with the specified number of registers
        CpUtils.assertTrue("Expected number of registers in 'copyChains' to be less than or equal to " + expectedNumReg,
                actualNumReg <= expectedNumReg,
                optimized);
                
        // Check that a, b, c share the same register
        var varTable = CpUtils.getMethod(optimized, "copyChains").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertEquals("Expected variables 'a' and 'b' to share the same register due to copy chain",
                aReg, varTable.get("b").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected variables 'a' and 'c' to share the same register due to copy chain",
                aReg, varTable.get("c").getVirtualReg(), optimized);
                
        // Check that x, y, z share the same register
        var xReg = varTable.get("x").getVirtualReg();
        CpUtils.assertEquals("Expected variables 'x' and 'y' to share the same register due to copy chain",
                xReg, varTable.get("y").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected variables 'x' and 'z' to share the same register due to copy chain",
                xReg, varTable.get("z").getVirtualReg(), optimized);
                
        // Check that a and x do not share the same register (they need to be separate)
        CpUtils.assertNotEquals("Expected variables 'a' and 'x' to use different registers as they're both needed at the end",
                aReg, xReg, optimized);
    }

}
