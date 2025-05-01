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
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        int configMaxRegs = 1;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        // Get methods for analysis
        var originalMethod = CpUtils.getMethod(original, "soManyRegisters");
        var optimizedMethod = CpUtils.getMethod(optimized, "soManyRegisters");

        int originalNumReg = CpUtils.countRegisters(originalMethod);
        int actualNumReg = CpUtils.countRegisters(optimizedMethod);

        CpUtils.assertNotEquals(
            "Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
            originalNumReg, actualNumReg,
            optimized
        );

        // Get variable table and register assignments
        var varTable = optimizedMethod.getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var bReg = varTable.get("b").getVirtualReg();
        var cReg = varTable.get("c").getVirtualReg();
        var dReg = varTable.get("d").getVirtualReg();
        
        // Verify all variables share the same register in a copy chain
        CpUtils.assertEquals("Expected registers of variables 'a' and 'b' to be the same", aReg, bReg, optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'c' to be the same", aReg, cReg, optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'd' to be the same", aReg, dReg, optimized);
        
        // Count unique registers used by local variables
        Set<Integer> localRegisters = new HashSet<>();
        localRegisters.add(aReg);
        localRegisters.add(bReg);
        localRegisters.add(cReg);
        localRegisters.add(dReg);
        int uniqueLocalRegs = localRegisters.size();
        
        int expectedUniqueLocalRegs = 1; // All locals should share the same register
        CpUtils.assertEquals(
            "Expected local variables to use exactly " + expectedUniqueLocalRegs + " register, found " + uniqueLocalRegs,
            expectedUniqueLocalRegs, uniqueLocalRegs, 
            optimized
        );

        // Verify total register count including parameters
        int expectedTotalReg = 3; // locals + this + arg parameter
        CpUtils.assertTrue(
            "Expected total number of registers in 'soManyRegisters' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
            actualNumReg == expectedTotalReg,
            optimized
        );
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
        int configMaxRegs = 0; // Minimize registers

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        // Get the method from original and optimized results
        var originalMethod = CpUtils.getMethod(original, "minimize");
        var optimizedMethod = CpUtils.getMethod(optimized, "minimize");

        // Verify that variables a and c share the same register through the variable table
        var varTable = optimizedMethod.getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var bReg = varTable.get("b").getVirtualReg();
        var cReg = varTable.get("c").getVirtualReg();

        // Verify a and c share the same register
        CpUtils.assertEquals(
            "Expected registers of variables 'a' and 'c' to be the same in minimize mode", 
            aReg, cReg, 
            optimized
        );

        // Verify a and b use different registers
        CpUtils.assertNotEquals(
            "Expected registers of variables 'a' and 'b' to be different", 
            aReg, bReg, 
            optimized
        );
        
        // Specifically count how many unique registers are used by local variables a, b, and c
        Set<Integer> localRegisters = new HashSet<>();
        localRegisters.add(aReg);
        localRegisters.add(bReg);
        localRegisters.add(cReg);
        int uniqueLocalRegs = localRegisters.size();
        
        // We expect 2 unique registers: one for a/c and one for b
        int expectedUniqueLocalRegs = 2;
        CpUtils.assertEquals(
            "Expected local variables to use exactly " + expectedUniqueLocalRegs + " registers, found " + uniqueLocalRegs,
            expectedUniqueLocalRegs, uniqueLocalRegs, 
            optimized
        );
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
        int expectedTotalReg = 4; // 2 parameters + this + 2 locals (the locals can share one register!)
        int configMaxRegs = 1;

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
        int expectedTotalReg = 3; // Including parameters
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

        System.out.println("RegA: " + aReg);
        System.out.println("RegB: " + bReg);
        System.out.println("RegC: " + cReg);

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

    @Test
    public void regAllocArrays() {
        String filename = "reg_alloc/regalloc_arrays.jmm";
        int expectedTotalReg = 6; // arr, i, sum, temp, j, size parameter
        int configMaxRegs = 3;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "arrayAccess"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "arrayAccess"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'arrayAccess' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        var varTable = CpUtils.getMethod(optimized, "arrayAccess").getVarTable();
        var iReg = varTable.get("i").getVirtualReg();
        var jReg = varTable.get("j").getVirtualReg();
        
        // i and j should ideally share the same register since their lifetimes don't overlap
        CpUtils.assertEquals("Expected registers of variables 'i' and 'j' to be the same since their lifetimes don't overlap", 
                iReg, jReg, optimized);
    }

    @Test
    public void regAllocConditional() {
        String filename = "reg_alloc/regalloc_conditional.jmm";
        int expectedTotalReg = 7; // arg, a, b, c, d, result, this parameter
        int configMaxRegs = 4; 

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "conditionalControl"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "conditionalControl"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'conditionalControl' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        // Variables with non-overlapping lifetimes across different branches should ideally share registers
        var varTable = CpUtils.getMethod(optimized, "conditionalControl").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var resultReg = varTable.get("result").getVirtualReg();
        
        // In a well-implemented register allocator, 'a' and 'result' could share a register
        // as 'a' is only used before the conditional branches and 'result' only after
        CpUtils.assertEquals("Expected variables with non-overlapping lifetimes to share registers", 
                aReg, resultReg, optimized);
    }

    @Test
    public void regAllocMethodCalls() {
        String filename = "reg_alloc/regalloc_method_calls.jmm";
        int expectedTotalReg = 5; // a, b, c, result (the result can share a register with one of the other local variables), arg parameter, this parameter
        int configMaxRegs = 3;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "methodWithCalls"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "methodWithCalls"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'methodWithCalls' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        // Method calls should preserve values across calls when they are needed later
        var varTable = CpUtils.getMethod(optimized, "methodWithCalls").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        var bReg = varTable.get("b").getVirtualReg();
        var cReg = varTable.get("c").getVirtualReg();
        
        // 'a' and 'b' values are both needed after method calls, they should have different registers
        CpUtils.assertNotEquals("Expected variables 'a' and 'b' to have different registers as both are needed after method calls",
                aReg, bReg, optimized);

        // 'a' and 'c' values are both needed after method calls, they should have different registers
        CpUtils.assertNotEquals("Expected variables 'a' and 'b' to have different registers as both are needed after method calls",
                bReg, cReg, optimized);
    }

    @Test
    public void regAllocSpill() {
        String filename = "reg_alloc/regalloc_spill.jmm";
        int expectedTotalReg = 11; // a to h, result, arg parameter, this parameter
        int configMaxRegs = 4; // Force spilling by limiting available physical registers

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "manyLiveVars"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "manyLiveVars"));

        CpUtils.assertTrue("Expected number of locals in 'manyLiveVars' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);

        // When multiple variables are live simultaneously but we don't have enough registers,
        // the allocator must handle spilling correctly to preserve values
        var method = CpUtils.getMethod(optimized, "manyLiveVars");
        
        // The complex expression should be present and able to return a result
        CpUtils.assertReturnExists(method, optimized);
    }
}
