package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

/**
 * Register allocator for the J-- compiler.
 * Implements register allocation using graph coloring.
 */
public class RegisterAllocator {

    /**
     * Exception thrown when register allocation fails because not enough registers are available.
     */
    public static class RegisterAllocationException extends RuntimeException {
        private final int minRegistersNeeded;

        public RegisterAllocationException(int minRegistersNeeded) {
            super("Need at least " + minRegistersNeeded + " registers for allocation");
            this.minRegistersNeeded = minRegistersNeeded;
        }

        public int getMinRegistersNeeded() {
            return minRegistersNeeded;
        }
    }

    /**
     * Performs register allocation on all methods in the class.
     * 
     * @param ollirResult The OLLIR representation of the class.
     * @param maxRegisters The maximum number of registers to use, or 0 for minimization, or -1 for no allocation.
     * @return A list of reports, including errors if any.
     */
    public List<Report> allocateRegisters(OllirResult ollirResult, int maxRegisters) {
        List<Report> reports = new ArrayList<>();
        ClassUnit classUnit = ollirResult.getOllirClass();
        
        // Skip register allocation if maxRegisters is -1 (default)
        if (maxRegisters == -1) {
            System.out.println("Skipping register allocation (using default register allocation)");
            return reports;
        }
        
        System.out.println("Register allocation with " + (maxRegisters == 0 ? "minimized" : maxRegisters) + " registers");
        
        // Process each method
        for (Method method : classUnit.getMethods()) {
            if (method.isConstructMethod()) {
                continue; // Skip constructor method
            }
            
            try {
                if (maxRegisters == 0) {
                    // Optimization: Use as few registers as possible
                    minimizeRegisters(method);
                    reports.add(Report.newLog(Stage.OPTIMIZATION, 0, 0, 
                        "Register allocation (minimized) for method " + method.getMethodName() + 
                        ": " + generateRegisterMappingReport(method), null));
                } else {
                    // Limitation: Use at most maxRegisters registers
                    limitRegisters(method, maxRegisters);
                    reports.add(Report.newLog(Stage.OPTIMIZATION, 0, 0,
                        "Register allocation (limited to " + maxRegisters + ") for method " + 
                        method.getMethodName() + ": " + generateRegisterMappingReport(method), null));
                }
            } catch (RegisterAllocationException e) {
                reports.add(Report.newError(Stage.OPTIMIZATION, 0, 0,
                    "Register allocation failed for method " + method.getMethodName() + 
                    ": " + e.getMessage() + ". Minimum registers required: " + e.getMinRegistersNeeded(), null));
            } catch (Exception e) {
                reports.add(Report.newError(Stage.OPTIMIZATION, 0, 0,
                    "Register allocation failed for method " + method.getMethodName() + 
                    ": " + e.getMessage(), e));
            }
        }
        
        return reports;
    }
    
    /**
     * Minimizes the number of registers used by the method.
     */
    private void minimizeRegisters(Method method) {
        // Analyze method to get liveness information
        Map<Instruction, Set<String>> liveIn = new HashMap<>();
        Map<Instruction, Set<String>> liveOut = new HashMap<>();
        performLivenessAnalysis(method, liveIn, liveOut);
        
        // Build the interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method, liveIn, liveOut);
        
        // Apply optimizations for copy chains
        optimizeForCopyChains(method, interferenceGraph);
        
        // Color the graph using a greedy algorithm (minimizing colors)
        Map<String, Integer> colorAssignment = colorGraph(interferenceGraph);
        
        // Update the variable table with the new register assignments
        updateVarTable(method, colorAssignment);
    }
    
    /**
     * Limits the number of registers used by the method.
     */
    private void limitRegisters(Method method, int maxRegisters) {
        // Analyze method to get liveness information
        Map<Instruction, Set<String>> liveIn = new HashMap<>();
        Map<Instruction, Set<String>> liveOut = new HashMap<>();
        performLivenessAnalysis(method, liveIn, liveOut);
        
        // Build the interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method, liveIn, liveOut);
        
        // Apply optimizations for copy chains
        optimizeForCopyChains(method, interferenceGraph);
        
        try {
            // Color the graph using a greedy algorithm, limiting to maxRegisters
            Map<String, Integer> colorAssignment = colorGraphLimited(interferenceGraph, maxRegisters);
            
            // Update the variable table with the new register assignments
            updateVarTable(method, colorAssignment);
        } catch (RegisterAllocationException e) {
            // Find the minimum required number of registers and throw an exception
            int minRequired = findMinimumRequiredRegisters(interferenceGraph);
            throw new RegisterAllocationException(minRequired);
        }
    }
    
    /**
     * Finds the minimum number of registers required for this interference graph.
     */
    private int findMinimumRequiredRegisters(Map<String, Set<String>> interferenceGraph) {
        try {
            Map<String, Integer> colors = colorGraph(interferenceGraph);
            int maxColor = -1;
            for (Integer color : colors.values()) {
                if (color > maxColor) {
                    maxColor = color;
                }
            }
            return maxColor + 1;
        } catch (Exception e) {
            // If something goes wrong, make a conservative estimate
            return interferenceGraph.size();
        }
    }
    
    /**
     * Performs liveness analysis on a method to determine live in/out sets for each instruction.
     */
    private void performLivenessAnalysis(Method method, 
                                         Map<Instruction, Set<String>> liveIn, 
                                         Map<Instruction, Set<String>> liveOut) {
        List<Instruction> instructions = method.getInstructions();
        Map<Integer, List<Integer>> successors = new HashMap<>();
        Map<Instruction, Set<String>> defineVars = new HashMap<>();
        Map<Instruction, Set<String>> useVars = new HashMap<>();
        
        // Initialize data structures
        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            liveIn.put(inst, new HashSet<>());
            liveOut.put(inst, new HashSet<>());
            defineVars.put(inst, new HashSet<>());
            useVars.put(inst, new HashSet<>());
            
            // Find variables defined and used by this instruction
            findDefinitionsAndUses(inst, defineVars.get(inst), useVars.get(inst));
            
            // Compute control flow successors
            successors.put(i, findSuccessors(method, i));
        }
        
        // Iterative data flow analysis until reaching a fixed point
        boolean changed;
        do {
            changed = false;
            
            // Traverse instructions in reverse order (better convergence)
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instruction inst = instructions.get(i);
                
                // Save old values to check for changes
                Set<String> oldLiveIn = new HashSet<>(liveIn.get(inst));
                Set<String> oldLiveOut = new HashSet<>(liveOut.get(inst));
                
                // LiveOut = union of LiveIn of all successors
                for (Integer succIndex : successors.get(i)) {
                    if (succIndex >= 0 && succIndex < instructions.size()) {
                        liveOut.get(inst).addAll(liveIn.get(instructions.get(succIndex)));
                    }
                }
                
                // LiveIn = use U (liveOut - def)
                Set<String> newLiveIn = new HashSet<>(useVars.get(inst));
                Set<String> liveOutMinusDef = new HashSet<>(liveOut.get(inst));
                liveOutMinusDef.removeAll(defineVars.get(inst));
                newLiveIn.addAll(liveOutMinusDef);
                liveIn.put(inst, newLiveIn);
                
                // Check if anything changed
                if (!oldLiveIn.equals(liveIn.get(inst)) || !oldLiveOut.equals(liveOut.get(inst))) {
                    changed = true;
                }
            }
        } while (changed);
    }
    
    /**
     * Finds the successor instruction indices for a given instruction index.
     */
    private List<Integer> findSuccessors(Method method, int instructionIndex) {
        List<Integer> successors = new ArrayList<>();
        List<Instruction> instructions = method.getInstructions();
        Instruction inst = instructions.get(instructionIndex);
        
        // Default successor is the next instruction
        if (instructionIndex < instructions.size() - 1) {
            successors.add(instructionIndex + 1);
        }
        
        // Handle control flow instructions
        if (inst instanceof GotoInstruction) {
            // For unconditional jumps, remove default successor
            successors.clear();
            // Add next instruction as fallback if we can't determine jump target
            if (instructionIndex < instructions.size() - 1) {
                successors.add(instructionIndex + 1);
            }
        } else if (inst instanceof CondBranchInstruction) {
            // For conditional branches, keep default successor
            // Note: We can't easily determine the branch target in this simplified model
        } else if (inst instanceof ReturnInstruction) {
            // Return has no successors
            successors.clear();
        }
        
        return successors;
    }
    
    /**
     * Builds an interference graph for the variables in a method.
     */
    private Map<String, Set<String>> buildInterferenceGraph(Method method, 
                                                          Map<Instruction, Set<String>> liveIn,
                                                          Map<Instruction, Set<String>> liveOut) {
        Map<String, Set<String>> interferenceGraph = new HashMap<>();
        
        // Initialize the graph with all variables except 'this'
        for (String varName : method.getVarTable().keySet()) {
            if (!varName.equals("this")) {
                interferenceGraph.put(varName, new HashSet<>());
            }
        }
        
        // Build edges between variables that interfere
        List<Instruction> instructions = method.getInstructions();
        for (Instruction inst : instructions) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                Element dest = assign.getDest();
                
                if (!(dest instanceof Operand)) continue;
                
                String destVar = ((Operand) dest).getName();
                if (destVar.equals("this")) continue;
                
                // Variables live out of this instruction interfere with destVar
                for (String liveVar : liveOut.get(inst)) {
                    if (!liveVar.equals(destVar) && !liveVar.equals("this")) {
                        // Create interference unless this is a copy instruction and liveVar is the source
                        boolean isCopy = false;
                        String srcVar = null;
                        
                        if (assign.getRhs() instanceof SingleOpInstruction) {
                            SingleOpInstruction sop = (SingleOpInstruction) assign.getRhs();
                            if (sop.getSingleOperand() instanceof Operand) {
                                srcVar = ((Operand) sop.getSingleOperand()).getName();
                                isCopy = true;
                            }
                        }
                        
                        if (!(isCopy && liveVar.equals(srcVar))) {
                            interferenceGraph.get(destVar).add(liveVar);
                            interferenceGraph.get(liveVar).add(destVar);
                        }
                    }
                }
            }
        }
        
        return interferenceGraph;
    }
    
    /**
     * Find all definitions and uses of variables in an instruction.
     */
    private void findDefinitionsAndUses(Instruction inst, Set<String> defined, Set<String> used) {
        // Handle different instruction types
        if (inst instanceof AssignInstruction) {
            AssignInstruction assign = (AssignInstruction) inst;
            
            // The LHS is defined
            if (assign.getDest() instanceof Operand) {
                String varName = ((Operand) assign.getDest()).getName();
                if (!varName.equals("this")) {
                    defined.add(varName);
                }
            }
            
            // Extract variables used in the RHS
            collectUsedVarsFromInstruction(assign.getRhs(), used);
        } else if (inst instanceof CallInstruction) {
            // Method calls use their arguments
            CallInstruction call = (CallInstruction) inst;
            
            // If instance method, the caller is used
            if (call.getCaller() != null) {
                collectUsedVarsFromElement(call.getCaller(), used);
            }
            
            // All arguments are used
            for (Element arg : call.getArguments()) {
                collectUsedVarsFromElement(arg, used);
            }
        } else if (inst instanceof ReturnInstruction) {
            // Return statements use their operand (if any)
            ReturnInstruction ret = (ReturnInstruction) inst;
            if (ret.hasReturnValue()) {
                collectUsedVarsFromElement(ret.getOperand().get(), used);
            }
        } else if (inst instanceof CondBranchInstruction) {
            // Conditional branches use their condition operands
            CondBranchInstruction branch = (CondBranchInstruction) inst;
            for (Element operand : branch.getOperands()) {
                collectUsedVarsFromElement(operand, used);
            }
        } else if (inst instanceof PutFieldInstruction) {
            // PutField uses the object and the value
            PutFieldInstruction put = (PutFieldInstruction) inst;
            for (Element operand : put.getOperands()) {
                collectUsedVarsFromElement(operand, used);
            }
        } else if (inst instanceof GetFieldInstruction) {
            // GetField uses the object
            GetFieldInstruction get = (GetFieldInstruction) inst;
            if (!get.getOperands().isEmpty()) {
                collectUsedVarsFromElement(get.getOperands().get(0), used);
            }
        }
        
        // Remove 'this' from tracking
        defined.remove("this");
        used.remove("this");
    }
    
    /**
     * Collect variables used in an instruction.
     */
    private void collectUsedVarsFromInstruction(Instruction inst, Set<String> used) {
        if (inst instanceof SingleOpInstruction) {
            SingleOpInstruction sop = (SingleOpInstruction) inst;
            collectUsedVarsFromElement(sop.getSingleOperand(), used);
        } else if (inst instanceof BinaryOpInstruction) {
            BinaryOpInstruction bop = (BinaryOpInstruction) inst;
            collectUsedVarsFromElement(bop.getLeftOperand(), used);
            collectUsedVarsFromElement(bop.getRightOperand(), used);
        } else if (inst instanceof UnaryOpInstruction) {
            UnaryOpInstruction uop = (UnaryOpInstruction) inst;
            collectUsedVarsFromElement(uop.getOperand(), used);
        } else if (inst instanceof CallInstruction) {
            CallInstruction call = (CallInstruction) inst;
            if (call.getCaller() != null) {
                collectUsedVarsFromElement(call.getCaller(), used);
            }
            for (Element arg : call.getArguments()) {
                collectUsedVarsFromElement(arg, used);
            }
        } else if (inst instanceof GetFieldInstruction) {
            GetFieldInstruction get = (GetFieldInstruction) inst;
            for (Element op : get.getOperands()) {
                collectUsedVarsFromElement(op, used);
            }
        }
    }
    
    /**
     * Collect variables used in an element.
     */
    private void collectUsedVarsFromElement(Element element, Set<String> used) {
        if (element instanceof Operand) {
            String varName = ((Operand) element).getName();
            if (!varName.equals("this")) {
                used.add(varName);
            }
        } else if (element instanceof ArrayOperand) {
            // Array accesses use both the array and the index
            ArrayOperand arrayOp = (ArrayOperand) element;
            used.add(arrayOp.getName());
            
            for (Element indexElement : arrayOp.getIndexOperands()) {
                collectUsedVarsFromElement(indexElement, used);
            }
        } else if (element instanceof LiteralElement) {
            // Literals don't use variables
        }
    }
    
    /**
     * Apply optimizations for copy chains like a = b, b = c.
     * This can help reduce unnecessary interferences in the graph.
     */
    private void optimizeForCopyChains(Method method, Map<String, Set<String>> interferenceGraph) {
        // Identify all copy instructions
        Map<String, Set<String>> copyRelations = new HashMap<>();
        
        // Initialize copy relations tracking
        for (String var : interferenceGraph.keySet()) {
            copyRelations.put(var, new HashSet<>());
        }
        
        // Find all simple copy operations (a = b)
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                
                if (assign.getDest() instanceof Operand &&
                    assign.getRhs() instanceof SingleOpInstruction) {
                    
                    SingleOpInstruction sop = (SingleOpInstruction) assign.getRhs();
                    if (sop.getSingleOperand() instanceof Operand) {
                        String destVar = ((Operand) assign.getDest()).getName();
                        String srcVar = ((Operand) sop.getSingleOperand()).getName();
                        
                        // Skip 'this' and check both variables are in our graph
                        if (!destVar.equals("this") && !srcVar.equals("this") &&
                            interferenceGraph.containsKey(destVar) && interferenceGraph.containsKey(srcVar)) {
                            
                            // Record that these variables are related by a copy
                            copyRelations.get(destVar).add(srcVar);
                            copyRelations.get(srcVar).add(destVar);
                        }
                    }
                }
            }
        }
        
        // Find and optimize for copy chains
        for (String var : interferenceGraph.keySet()) {
            Set<String> relatedVars = copyRelations.get(var);
            
            // For each pair of variables related to this one by copies
            for (String var1 : relatedVars) {
                for (String var2 : relatedVars) {
                    if (!var1.equals(var2) && interferenceGraph.get(var1).contains(var2)) {
                        // Found two related variables that currently interfere
                        // For variables in a copy chain, they sometimes don't need to interfere
                        // if their live ranges don't truly overlap
                        
                        // Conservatively check if they're both only involved in copy operations
                        boolean var1OnlyInCopies = isUsedOnlyInCopies(method, var1);
                        boolean var2OnlyInCopies = isUsedOnlyInCopies(method, var2);
                        
                        // If both are only used in copies, we may be able to optimize
                        if (var1OnlyInCopies && var2OnlyInCopies) {
                            // Remove the interference edge (allows sharing registers)
                            interferenceGraph.get(var1).remove(var2);
                            interferenceGraph.get(var2).remove(var1);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Check if a variable is only used in copy operations.
     */
    private boolean isUsedOnlyInCopies(Method method, String varName) {
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                
                // Check usage as destination (LHS)
                if (assign.getDest() instanceof Operand && 
                    ((Operand) assign.getDest()).getName().equals(varName)) {
                    
                    // If it's assigned something other than a simple variable, it's not only in copies
                    if (!(assign.getRhs() instanceof SingleOpInstruction)) {
                        return false;
                    }
                    
                    SingleOpInstruction sop = (SingleOpInstruction) assign.getRhs();
                    if (!(sop.getSingleOperand() instanceof Operand)) {
                        return false;
                    }
                }
                
                // Check usage on RHS
                if (assign.getRhs() instanceof SingleOpInstruction) {
                    SingleOpInstruction sop = (SingleOpInstruction) assign.getRhs();
                    
                    if (sop.getSingleOperand() instanceof Operand && 
                        ((Operand) sop.getSingleOperand()).getName().equals(varName)) {
                        // Used as source in a copy - check that LHS is a simple variable
                        if (!(assign.getDest() instanceof Operand)) {
                            return false;
                        }
                    }
                } else {
                    // Check if it's used in a non-copy RHS expression
                    Set<String> usedVars = new HashSet<>();
                    collectUsedVarsFromInstruction(assign.getRhs(), usedVars);
                    
                    if (usedVars.contains(varName)) {
                        return false;  // Used in a complex expression
                    }
                }
            } else {
                // Check if used in non-assignment instructions
                Set<String> usedVars = new HashSet<>();
                Set<String> definedVars = new HashSet<>();
                findDefinitionsAndUses(inst, definedVars, usedVars);
                
                if (usedVars.contains(varName)) {
                    return false;  // Used in non-assignment
                }
            }
        }
        
        return true;  // Only used in copy operations
    }
    
    /**
     * Colors the interference graph using a greedy algorithm, minimizing colors.
     * Sorts vertices by degree (most constrained first) for better results.
     */
    private Map<String, Integer> colorGraph(Map<String, Set<String>> interferenceGraph) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Sort variables by degree (number of interferences) in descending order
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> 
            Integer.compare(interferenceGraph.get(v2).size(), interferenceGraph.get(v1).size()));
        
        // First pass: handle variables in copy chains specially to improve register sharing
        Map<String, Set<String>> copyChains = findCopyChains(sortedVars, interferenceGraph);
        for (Map.Entry<String, Set<String>> entry : copyChains.entrySet()) {
            String var = entry.getKey();
            if (colorAssignment.containsKey(var)) continue;
            
            // Find a color for this variable
            int color = findSmallestAvailableColor(interferenceGraph, colorAssignment, var);
            colorAssignment.put(var, color);
            
            // Try to assign the same color to variables in its copy chain when possible
            for (String relatedVar : entry.getValue()) {
                if (colorAssignment.containsKey(relatedVar)) continue;
                
                // Check if we can use the same color
                boolean canUseColor = true;
                for (String neighbor : interferenceGraph.get(relatedVar)) {
                    if (colorAssignment.containsKey(neighbor) && colorAssignment.get(neighbor) == color) {
                        canUseColor = false;
                        break;
                    }
                }
                
                if (canUseColor) {
                    colorAssignment.put(relatedVar, color);
                }
            }
        }
        
        // Second pass: color remaining variables
        for (String var : sortedVars) {
            if (colorAssignment.containsKey(var)) continue;
            
            int color = findSmallestAvailableColor(interferenceGraph, colorAssignment, var);
            colorAssignment.put(var, color);
        }
        
        return colorAssignment;
    }
    
    /**
     * Colors the interference graph with a maximum number of colors.
     */
    private Map<String, Integer> colorGraphLimited(
            Map<String, Set<String>> interferenceGraph, int maxColors) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Sort variables by degree (number of interferences) in descending order
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> 
            Integer.compare(interferenceGraph.get(v2).size(), interferenceGraph.get(v1).size()));
        
        // First pass: handle variables in copy chains specially
        Map<String, Set<String>> copyChains = findCopyChains(sortedVars, interferenceGraph);
        for (Map.Entry<String, Set<String>> entry : copyChains.entrySet()) {
            String var = entry.getKey();
            if (colorAssignment.containsKey(var)) continue;
            
            // Find a color for this variable
            int color = findSmallestAvailableColor(interferenceGraph, colorAssignment, var);
            if (color >= maxColors) {
                throw new RegisterAllocationException(color + 1);
            }
            colorAssignment.put(var, color);
            
            // Try to assign the same color to variables in its copy chain
            for (String relatedVar : entry.getValue()) {
                if (colorAssignment.containsKey(relatedVar)) continue;
                
                // Check if we can use the same color
                boolean canUseColor = true;
                for (String neighbor : interferenceGraph.get(relatedVar)) {
                    if (colorAssignment.containsKey(neighbor) && colorAssignment.get(neighbor) == color) {
                        canUseColor = false;
                        break;
                    }
                }
                
                if (canUseColor) {
                    colorAssignment.put(relatedVar, color);
                }
            }
        }
        
        // Second pass: color remaining variables
        for (String var : sortedVars) {
            if (colorAssignment.containsKey(var)) continue;
            
            int color = findSmallestAvailableColor(interferenceGraph, colorAssignment, var);
            if (color >= maxColors) {
                throw new RegisterAllocationException(color + 1);
            }
            colorAssignment.put(var, color);
        }
        
        return colorAssignment;
    }
    
    /**
     * Find the smallest color that can be assigned to a variable.
     */
    private int findSmallestAvailableColor(
            Map<String, Set<String>> interferenceGraph, 
            Map<String, Integer> colorAssignment,
            String var) {
        Set<Integer> usedColors = new HashSet<>();
        
        // Check what colors are used by neighbors
        for (String neighbor : interferenceGraph.get(var)) {
            if (colorAssignment.containsKey(neighbor)) {
                usedColors.add(colorAssignment.get(neighbor));
            }
        }
        
        // Find the smallest available color
        int color = 0;
        while (usedColors.contains(color)) {
            color++;
        }
        
        return color;
    }
    
    /**
     * Find copy chains in the graph to optimize allocation.
     */
    private Map<String, Set<String>> findCopyChains(List<String> variables, Map<String, Set<String>> interferenceGraph) {
        Map<String, Set<String>> copyChains = new HashMap<>();
        
        // Initialize with no chains
        for (String var : variables) {
            copyChains.put(var, new HashSet<>());
        }
        
        // Check all pairs of variables for potential copy relationships
        for (String var1 : variables) {
            for (String var2 : variables) {
                if (var1.equals(var2)) continue;
                
                // If they don't interfere, they might be in a copy chain
                if (!interferenceGraph.get(var1).contains(var2)) {
                    copyChains.get(var1).add(var2);
                    copyChains.get(var2).add(var1);
                }
            }
        }
        
        return copyChains;
    }
    
    /**
     * Updates the variable table with the new register assignments.
     */
    private void updateVarTable(Method method, Map<String, Integer> colorAssignment) {
        // Make sure 'this' is at register 0 if it exists
        int nextParamReg = 0;
        if (method.getVarTable().containsKey("this")) {
            method.getVarTable().get("this").setVirtualReg(nextParamReg++);
        }
        
        // Assign registers to parameters first (they need to be at specific slots)
        for (Element param : method.getParams()) {
            if (param instanceof Operand) {
                String paramName = ((Operand) param).getName();
                if (!paramName.equals("this")) { // Skip 'this', already handled
                    method.getVarTable().get(paramName).setVirtualReg(nextParamReg++);
                }
            }
        }
        
        // Base offset for local variables
        int localOffset = nextParamReg;
        
        // Now assign registers to local variables based on coloring
        for (Map.Entry<String, Integer> entry : colorAssignment.entrySet()) {
            String varName = entry.getKey();
            int color = entry.getValue();
            
            // Only update local variables (not parameters)
            boolean isParameter = false;
            for (Element param : method.getParams()) {
                if (param instanceof Operand && ((Operand) param).getName().equals(varName)) {
                    isParameter = true;
                    break;
                }
            }
            
            if (!isParameter && !varName.equals("this")) {
                method.getVarTable().get(varName).setVirtualReg(localOffset + color);
            }
        }
    }
    
    /**
     * Generates a report of the register mapping for a method.
     */
    private String generateRegisterMappingReport(Method method) {
        StringBuilder report = new StringBuilder();
        
        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            report.append(entry.getKey())
                  .append(" -> ")
                  .append(entry.getValue().getVirtualReg())
                  .append(", ");
        }
        
        // Remove the trailing comma and space
        if (report.length() > 2) {
            report.setLength(report.length() - 2);
        }
        
        return report.toString();
    }
}