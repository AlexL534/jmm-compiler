package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;
import java.util.stream.Collectors;

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
                throw e;
            } catch (Exception e) {
                reports.add(Report.newError(Stage.OPTIMIZATION, 0, 0,
                    "Register allocation failed for method " + method.getMethodName() +
                    ": " + e.getMessage(), e));
                throw new RuntimeException(e);
            }
        }

        return reports;
    }
    
    /**
     * Minimizes the number of registers used by the method.
     */
    private void minimizeRegisters(Method method) {
        Map<Instruction, Set<String>> liveIn = new HashMap<>();
        Map<Instruction, Set<String>> liveOut = new HashMap<>();
        
        // Perform liveness analysis
        performLivenessAnalysis(method, liveIn, liveOut);

        // Ensure CFG is built
        method.buildCFG();

        // Build interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method, liveIn, liveOut);

        // Optimize for copy instructions to allow register sharing
        Map<String, Set<String>> copyRelations = findCopyRelations(method);
        
        // Find transitive copy chains to maximize register sharing
        Map<String, Set<String>> copyChains = buildTransitiveCopyChains(copyRelations);

        // Color graph prioritizing register sharing between variables in copy chains
        Map<String, Integer> colorAssignment = colorGraphMinimized(interferenceGraph, copyChains);

        // Update the variable table
        updateVarTable(method, colorAssignment);
    }
    
    /**
     * Colors the interference graph to minimize the number of colors used.
     * Specifically optimized for the case where maxRegisters = 0.
     */
    private Map<String, Integer> colorGraphMinimized(Map<String, Set<String>> interferenceGraph, 
                                                   Map<String, Set<String>> copyChains) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Group variables that can potentially share the same register
        // based on copy chains and non-interference
        List<Set<String>> registerGroups = new ArrayList<>();
        Set<String> processedVars = new HashSet<>();
        
        // Process variables in order of their copy chain size (largest first)
        // to maximize register sharing
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> Integer.compare(copyChains.get(v2).size(), copyChains.get(v1).size()));
        
        for (String var : sortedVars) {
            if (processedVars.contains(var)) continue;
            
            // Create a new register group starting with this variable
            Set<String> group = new HashSet<>();
            group.add(var);
            processedVars.add(var);
            
            // Try to add all variables from its copy chain that don't interfere
            for (String related : copyChains.get(var)) {
                if (!related.equals(var) && !processedVars.contains(related)) {
                    boolean canAdd = true;
                    
                    // Check if this variable interferes with any variable already in the group
                    for (String groupVar : group) {
                        if (interferenceGraph.get(groupVar).contains(related)) {
                            canAdd = false;
                            break;
                        }
                    }
                    
                    if (canAdd) {
                        group.add(related);
                        processedVars.add(related);
                    }
                }
            }
            
            registerGroups.add(group);
        }
        
        // Now color each group with a unique color
        for (int i = 0; i < registerGroups.size(); i++) {
            for (String var : registerGroups.get(i)) {
                colorAssignment.put(var, i);
            }
        }
        
        return colorAssignment;
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
        
        // Find copy relations and optimize for copy chains
        Map<String, Set<String>> copyRelations = findCopyRelations(method);
        optimizeForCopyChains(interferenceGraph, copyRelations);
        
        try {
            // Color the graph using a greedy algorithm, limiting to maxRegisters
            Map<String, Integer> colorAssignment = colorGraphLimited(interferenceGraph, copyRelations, maxRegisters);
            
            // Update the variable table with the new register assignments
            updateVarTable(method, colorAssignment);
        } catch (RegisterAllocationException e) {
            // Find the minimum required number of registers and throw an exception
            int minRequired = findMinimumRequiredRegisters(interferenceGraph, copyRelations);
            throw new RegisterAllocationException(minRequired);
        }
    }
    
    /**
     * Finds the minimum number of registers required for this interference graph.
     */
    private int findMinimumRequiredRegisters(Map<String, Set<String>> interferenceGraph, Map<String, Set<String>> copyRelations) {
        try {
            Map<String, Integer> colors = colorGraph(interferenceGraph, copyRelations);
            int maxColor = -1;
            for (Integer color : colors.values()) {
                if (color > maxColor) {
                    maxColor = color;
                }
            }
            return maxColor + 1;
        } catch (Exception e) {
            // If something goes wrong, make a conservative estimate based on graph degree
            int maxDegree = 0;
            for (Set<String> neighbors : interferenceGraph.values()) {
                maxDegree = Math.max(maxDegree, neighbors.size());
            }
            return maxDegree + 1;
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
            
            // Traverse instructions in reverse order (for backward data flow analysis)
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instruction inst = instructions.get(i);
                
                // Save old values to check for changes
                Set<String> oldLiveIn = new HashSet<>(liveIn.get(inst));
                Set<String> oldLiveOut = new HashSet<>(liveOut.get(inst));
                
                // LiveOut = union of LiveIn of all successors
                for (int succIndex : successors.get(i)) {
                    if (succIndex >= 0 && succIndex < instructions.size()) {
                        liveOut.get(inst).addAll(liveIn.get(instructions.get(succIndex)));
                    }
                }
                
                // LiveIn = use ∪ (liveOut - def)
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
        List<Instruction> instructions = method.getInstructions();
        int instructionCount = instructions.size();
        List<Integer> successors = new ArrayList<>();
        Instruction inst = instructions.get(instructionIndex);

        if (inst instanceof ReturnInstruction) {
            // Return: no successors
            return successors;
        }
        
        // Handle control flow instructions
        if (inst instanceof GotoInstruction gotoInst) {
            // Goto: try to find the actual target, fall back to next if exists
            String targetLabel = gotoInst.getLabel();
            Integer targetIndex = findLabelTarget(method, targetLabel);
            if (targetIndex != null) {
                successors.add(targetIndex);
                return successors;
            }
            
            if (instructionIndex + 1 < instructionCount) {
                successors.add(instructionIndex + 1);
            }
            return successors;
        }
        
        if (inst instanceof CondBranchInstruction condBranch) {
            // Conditional branch: check for label and add fall-through
            String targetLabel = condBranch.getLabel();
            Integer targetIndex = findLabelTarget(method, targetLabel);
            if (targetIndex != null) {
                successors.add(targetIndex);
            }
            
            // Next instruction is also a successor for condition branches
            if (instructionIndex + 1 < instructionCount) {
                successors.add(instructionIndex + 1);
            }
            
            return successors;
        }
        
        // For normal instructions, just fall through to next instruction
        if (instructionIndex + 1 < instructionCount) {
            successors.add(instructionIndex + 1);
        }
        
        return successors;
    }
    
    /**
     * Find the instruction index for a given label.
     */
    private Integer findLabelTarget(Method method, String label) {
        if (label == null) {
            return null;
        }
        
        // Get the label map from the method's CFG
        method.buildCFG();
        HashMap<String, Instruction> labels = method.getLabels();
        
        if (labels != null && labels.containsKey(label)) {
            // Find the index of the instruction with this label
            List<Instruction> instructions = method.getInstructions();
            Instruction targetInst = labels.get(label);
            
            for (int i = 0; i < instructions.size(); i++) {
                if (instructions.get(i) == targetInst) {
                    return i;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Builds an interference graph for the variables in a method.
     * Two variables interfere if one is live when the other is defined,
     * with special handling for copy instructions.
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
        
        // Build interference edges
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction assign) {
                Element dest = assign.getDest();
                
                if (!(dest instanceof Operand)) continue;
                
                String destVar = ((Operand) dest).getName();
                if (destVar.equals("this")) continue;
                
                // Find the source variable (if this is a copy instruction)
                String srcVar = null;
                boolean isCopy = false;
                
                if (assign.getRhs() instanceof SingleOpInstruction sop &&
                    sop.getSingleOperand() instanceof Operand) {
                    srcVar = ((Operand) sop.getSingleOperand()).getName();
                    isCopy = true;
                }
                
                // Variables live out of this instruction interfere with destVar
                for (String liveVar : liveOut.get(inst)) {
                    if (!liveVar.equals(destVar) && !liveVar.equals("this")) {
                        // For copy instructions, the source variable doesn't interfere with the destination
                        if (!(isCopy && liveVar.equals(srcVar))) {
                            addInterference(interferenceGraph, destVar, liveVar);
                        }
                    }
                }
            }
        }
        
        return interferenceGraph;
    }
    
    /**
     * Helper method to add an interference edge between two variables
     */
    private void addInterference(Map<String, Set<String>> interferenceGraph, String var1, String var2) {
        if (!interferenceGraph.containsKey(var1) || !interferenceGraph.containsKey(var2)) {
            return;
        }
        interferenceGraph.get(var1).add(var2);
        interferenceGraph.get(var2).add(var1);
    }
    
    /**
     * Find all definitions and uses of variables in an instruction.
     */
    private void findDefinitionsAndUses(Instruction inst, Set<String> defined, Set<String> used) {
        // Handle different instruction types
        if (inst instanceof AssignInstruction assign) {
            // The LHS is defined
            if (assign.getDest() instanceof Operand) {
                String varName = ((Operand) assign.getDest()).getName();
                if (!varName.equals("this")) {
                    defined.add(varName);
                }
            } else if (assign.getDest() instanceof ArrayOperand arrayOp) {
                // For array assignments like a[i] = x, both a and i are used
                used.add(arrayOp.getName());
                for (Element indexElement : arrayOp.getIndexOperands()) {
                    collectUsedVarsFromElement(indexElement, used);
                }
            }
            
            // Extract variables used in the RHS
            collectUsedVarsFromInstruction(assign.getRhs(), used);
        } else if (inst instanceof CallInstruction call) {
            // Method calls use their arguments
            // If instance method, the caller is used
            if (call.getCaller() != null) {
                collectUsedVarsFromElement(call.getCaller(), used);
            }
            
            // All arguments are used
            for (Element arg : call.getArguments()) {
                collectUsedVarsFromElement(arg, used);
            }
        } else if (inst instanceof ReturnInstruction ret) {
            // Return statements use their operand (if any)
            if (ret.hasReturnValue()) {
                collectUsedVarsFromElement(ret.getOperand().get(), used);
            }
        } else if (inst instanceof CondBranchInstruction branch) {
            // Conditional branches use their condition operands
            for (Element operand : branch.getOperands()) {
                collectUsedVarsFromElement(operand, used);
            }
        } else if (inst instanceof PutFieldInstruction put) {
            // PutField uses the object and the value
            for (Element operand : put.getOperands()) {
                collectUsedVarsFromElement(operand, used);
            }
        } else if (inst instanceof GetFieldInstruction get) {
            // GetField uses the object
            if (!get.getOperands().isEmpty()) {
                collectUsedVarsFromElement(get.getOperands().getFirst(), used);
            }
        } else if (inst instanceof UnaryOpInstruction unary) {
            // Use the operand
            collectUsedVarsFromElement(unary.getOperand(), used);
        } else if (inst instanceof BinaryOpInstruction binary) {
            // Use both operands
            collectUsedVarsFromElement(binary.getLeftOperand(), used);
            collectUsedVarsFromElement(binary.getRightOperand(), used);
        } else if (inst instanceof SingleOpInstruction single) {
            // Use the operand
            collectUsedVarsFromElement(single.getSingleOperand(), used);
        }
        
        // Remove 'this' from tracking
        defined.remove("this");
        used.remove("this");
    }
    
    /**
     * Collect variables used in an instruction.
     */
    private void collectUsedVarsFromInstruction(Instruction inst, Set<String> used) {
        if (inst instanceof SingleOpInstruction sop) {
            collectUsedVarsFromElement(sop.getSingleOperand(), used);
        } else if (inst instanceof BinaryOpInstruction bop) {
            collectUsedVarsFromElement(bop.getLeftOperand(), used);
            collectUsedVarsFromElement(bop.getRightOperand(), used);
        } else if (inst instanceof UnaryOpInstruction uop) {
            collectUsedVarsFromElement(uop.getOperand(), used);
        } else if (inst instanceof CallInstruction call) {
            if (call.getCaller() != null) {
                collectUsedVarsFromElement(call.getCaller(), used);
            }
            for (Element arg : call.getArguments()) {
                collectUsedVarsFromElement(arg, used);
            }
        } else if (inst instanceof GetFieldInstruction get) {
            for (Element op : get.getOperands()) {
                collectUsedVarsFromElement(op, used);
            }
        } else if (inst instanceof PutFieldInstruction put) {
            for (Element op : put.getOperands()) {
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
        } else if (element instanceof ArrayOperand arrayOp) {
            // Array accesses use both the array and the index
            used.add(arrayOp.getName());
            
            for (Element indexElement : arrayOp.getIndexOperands()) {
                collectUsedVarsFromElement(indexElement, used);
            }
        } else if (element instanceof LiteralElement) {
            // Literals don't use variables
        }
    }

    /**
     * Finds copy relations between variables (a = b).
     * These relations are important because variables in copy relations
     * can potentially share registers if they don't interfere.
     */
    private Map<String, Set<String>> findCopyRelations(Method method) {
        Map<String, Set<String>> copyRelations = new HashMap<>();
        
        // Initialize copy relations for all variables
        for (String varName : method.getVarTable().keySet()) {
            copyRelations.put(varName, new HashSet<>());
        }
        
        // Scan instructions for copy operations
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction assign) {
                Element dest = assign.getDest();
                
                // Skip if destination is not a simple variable (e.g. array access)
                if (!(dest instanceof Operand) || dest instanceof ArrayOperand) {
                    continue;
                }
                
                String destVar = ((Operand) dest).getName();
                
                // Skip if destination is 'this' or a field
                if (destVar.equals("this")) {
                    continue;
                }
                
                // Check if this is a simple copy instruction: a = b
                if (assign.getRhs() instanceof SingleOpInstruction sop) {
                    Element srcElement = sop.getSingleOperand();
                    
                    // Skip if source is not a simple variable
                    if (!(srcElement instanceof Operand) || srcElement instanceof ArrayOperand) {
                        continue;
                    }
                    
                    String srcVar = ((Operand) srcElement).getName();
                    
                    // Skip if source is 'this'
                    if (srcVar.equals("this")) {
                        continue;
                    }
                    
                    // Add copy relation
                    copyRelations.get(destVar).add(srcVar);
                    copyRelations.get(srcVar).add(destVar);
                }
            }
        }
        
        return copyRelations;
    }
    
    /**
     * Optimizes the interference graph based on copy relations.
     * Variables in copy relations that don't interfere can potentially share registers.
     */
    private void optimizeForCopyChains(Map<String, Set<String>> interferenceGraph, Map<String, Set<String>> copyRelations) {
        // For each pair of variables in a copy relation
        for (String var : copyRelations.keySet()) {
            for (String copyVar : copyRelations.get(var)) {
                // If they don't interfere, we prefer them to be allocated to the same register
                // No need to modify the interference graph, as the graph coloring algorithm
                // will handle this preference during coloring
                
                // We could potentially add a "preference" edge in a separate graph or adjust
                // the coloring heuristics, but for this implementation we'll rely on the 
                // ordering of variables in the coloring algorithm
            }
        }
    }
    
    /**
     * Colors the interference graph using a greedy algorithm, minimizing colors.
     * Optimized for copy chains to improve register sharing.
     */
    private Map<String, Integer> colorGraph(Map<String, Set<String>> interferenceGraph, 
                                            Map<String, Set<String>> copyRelations) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Build transitive copy chains for better coloring decisions
        Map<String, Set<String>> copyChains = buildTransitiveCopyChains(copyRelations);
        
        // Identify copy chain leaders - variables that have many copy relationships
        Map<String, Integer> copyChainSize = new HashMap<>();
        for (String var : interferenceGraph.keySet()) {
            copyChainSize.put(var, copyChains.get(var).size());
        }
        
        // Sort by chain size (descending) and then by graph degree (also descending)
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> {
            int chainSizeDiff = Integer.compare(copyChainSize.get(v2), copyChainSize.get(v1));
            if (chainSizeDiff != 0) return chainSizeDiff;
            return Integer.compare(interferenceGraph.get(v2).size(), interferenceGraph.get(v1).size());
        });
        
        // First pass: color variables in order of importance
        for (String var : sortedVars) {
            // Skip already colored variables
            if (colorAssignment.containsKey(var)) continue;
            
            // Color this variable with the smallest available color
            int color = findSmallestAvailableColor(interferenceGraph, colorAssignment, var);
            colorAssignment.put(var, color);
            
            // Try to assign the same color to all variables in this copy chain
            for (String related : copyChains.get(var)) {
                if (related.equals(var) || colorAssignment.containsKey(related)) continue;
                
                // Only consider variables that can safely use this color
                if (canShareColor(interferenceGraph, colorAssignment, related, color)) {
                    colorAssignment.put(related, color);
                }
            }
        }
        
        return colorAssignment;
    }
    
    /**
     * Colors the interference graph with a maximum number of colors.
     * Uses the graph coloring algorithm with a limit on colors.
     */
    private Map<String, Integer> colorGraphLimited(Map<String, Set<String>> interferenceGraph, 
                                                  Map<String, Set<String>> copyRelations,
                                                  int maxColors) {
        // Build transitive copy chains for better coloring decisions
        Map<String, Set<String>> copyChains = buildTransitiveCopyChains(copyRelations);
        
        // Create a working copy of the graph for the simplification phase
        Map<String, Set<String>> workGraph = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : interferenceGraph.entrySet()) {
            workGraph.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        
        // Initialize result map and removal stack
        Map<String, Integer> colorAssignment = new HashMap<>();
        Stack<String> removalStack = new Stack<>();
        List<String> nodes = new ArrayList<>(workGraph.keySet());
        
        // Group variables by copy chains to try to remove them together
        Map<String, Set<String>> simplificationGroups = new HashMap<>();
        for (String var : nodes) {
            simplificationGroups.put(var, new HashSet<>());
            simplificationGroups.get(var).add(var);
            
            // Find other variables that can potentially share the same register
            for (String other : copyChains.get(var)) {
                if (!other.equals(var) && canShareColor(interferenceGraph, colorAssignment, other, 0)) {
                    simplificationGroups.get(var).add(other);
                }
            }
        }
        
        // Simplification phase: try to remove variables that can share registers as groups first
        while (!nodes.isEmpty()) {
            // Find a node or group with combined degree < maxColors
            String nodeToRemove = null;
            for (String node : nodes) {
                if (simplificationGroups.get(node).size() > 1) {
                    // This node is part of a potential register sharing group
                    // Check if the combined neighborhood size is small enough
                    Set<String> combinedNeighbors = new HashSet<>();
                    for (String groupMember : simplificationGroups.get(node)) {
                        if (nodes.contains(groupMember)) {
                            combinedNeighbors.addAll(workGraph.get(groupMember));
                        }
                    }
                    
                    if (combinedNeighbors.size() < maxColors) {
                        nodeToRemove = node;
                        break;
                    }
                }
                
                // Otherwise, fall back to checking individual nodes
                if (workGraph.get(node).size() < maxColors) {
                    nodeToRemove = node;
                    break;
                }
            }
            
            // If no suitable node found, allocation is impossible with maxColors
            if (nodeToRemove == null) {
                throw new RegisterAllocationException(findMinimumRequiredRegisters(interferenceGraph, copyRelations));
            }
            
            // Remove the node and update the graph
            removalStack.push(nodeToRemove);
            nodes.remove(nodeToRemove);
            
            // Also remove any other nodes that can share a register with this one
            for (String shareNode : new ArrayList<>(simplificationGroups.get(nodeToRemove))) {
                if (nodes.contains(shareNode)) {
                    removalStack.push(shareNode);
                    nodes.remove(shareNode);
                }
            }
            
            // Update the working graph
            for (String neighbor : interferenceGraph.get(nodeToRemove)) {
                if (workGraph.containsKey(neighbor)) {
                    workGraph.get(neighbor).remove(nodeToRemove);
                }
            }
        }
        
        // Coloring phase: assign colors in reverse removal order
        Set<String> processed = new HashSet<>();
        while (!removalStack.isEmpty()) {
            String node = removalStack.pop();
            if (processed.contains(node)) continue;
            processed.add(node);
            
            // Find available color (lowest not used by neighbors)
            Set<Integer> usedColors = new HashSet<>();
            for (String neighbor : interferenceGraph.get(node)) {
                Integer neighborColor = colorAssignment.get(neighbor);
                if (neighborColor != null) {
                    usedColors.add(neighborColor);
                }
            }
            
            // Find the smallest available color
            int color = 0;
            while (usedColors.contains(color) && color < maxColors) {
                color++;
            }
            
            // If no color available, we need more registers
            if (color >= maxColors) {
                throw new RegisterAllocationException(maxColors + 1);
            }
            
            colorAssignment.put(node, color);
            
            // Assign the same color to all variables in the copy chain if possible
            for (String related : copyChains.get(node)) {
                if (!related.equals(node) && !colorAssignment.containsKey(related) && 
                    canShareColor(interferenceGraph, colorAssignment, related, color)) {
                    colorAssignment.put(related, color);
                    processed.add(related);
                }
            }
        }
        
        return colorAssignment;
    }

    /**
     * Check if a variable can use a specific color without creating conflicts.
     */
    private boolean canShareColor(Map<String, Set<String>> interferenceGraph, 
                                 Map<String, Integer> colorAssignment, 
                                 String var, 
                                 int color) {
        // Check all neighbors to see if any use this color
        for (String neighbor : interferenceGraph.get(var)) {
            Integer neighborColor = colorAssignment.get(neighbor);
            if (neighborColor != null && neighborColor == color) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Find the smallest color that can be assigned to a variable.
     */
    private int findSmallestAvailableColor(Map<String, Set<String>> interferenceGraph, 
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
     * Updates the variable table with the new register assignments.
     * Ensures parameters get the correct register numbers based on JVM calling convention.
     */
    private void updateVarTable(Method method, Map<String, Integer> colorAssignment) {
        // Get all parameters
        List<Element> params = method.getParams();
        Set<String> paramNames = params.stream()
                .filter(Operand.class::isInstance)
                .map(param -> ((Operand) param).getName())
                .collect(Collectors.toSet());
        
        int nextParamReg = 0;
        
        // Assign register 0 to 'this' for instance methods
        if (!method.isStaticMethod() && method.getVarTable().containsKey("this")) {
            method.getVarTable().get("this").setVirtualReg(nextParamReg++);
        }
        
        // Assign registers to parameters in order
        for (Element param : params) {
            if (param instanceof Operand paramOp) {
                String paramName = paramOp.getName();
                if (!paramName.equals("this")) {
                    method.getVarTable().get(paramName).setVirtualReg(nextParamReg++);
                }
            }
        }
        
        // The first available register for local variables
        int localOffset = nextParamReg;
        
        // Assign registers to local variables using the coloring
        for (Map.Entry<String, Integer> entry : colorAssignment.entrySet()) {
            String varName = entry.getKey();
            if (!paramNames.contains(varName) && !varName.equals("this")) {
                method.getVarTable().get(varName).setVirtualReg(localOffset + entry.getValue());
            }
        }
    }

    /**
     * Generates a report of the register mapping for a method.
     */
    private String generateRegisterMappingReport(Method method) {
        StringBuilder report = new StringBuilder();
        
        Map<String, Descriptor> varTable = method.getVarTable();
        List<String> sortedVars = new ArrayList<>(varTable.keySet());
        sortedVars.sort(Comparator.naturalOrder());
        
        for (String varName : sortedVars) {
            report.append(varName)
                  .append(" -> ")
                  .append(varTable.get(varName).getVirtualReg())
                  .append(", ");
        }
        
        // Remove the trailing comma and space
        if (report.length() > 2) {
            report.setLength(report.length() - 2);
        }
        
        return report.toString();
    }

    /**
     * Builds transitive copy chains from direct copy relations.
     * This helps identify groups of variables that can potentially share registers.
     */
    private Map<String, Set<String>> buildTransitiveCopyChains(Map<String, Set<String>> copyRelations) {
        Map<String, Set<String>> copyChains = new HashMap<>();
        
        // Start with direct copy relations
        for (String var : copyRelations.keySet()) {
            copyChains.put(var, new HashSet<>());
            copyChains.get(var).add(var);  // Include self in the chain
            copyChains.get(var).addAll(copyRelations.get(var));
        }
        
        // Repeatedly extend chains until no more changes
        boolean changed;
        do {
            changed = false;
            
            // For each variable's chain
            for (String var : copyChains.keySet()) {
                int sizeBefore = copyChains.get(var).size();
                
                // For each direct copy relation
                for (String related : new HashSet<>(copyChains.get(var))) {
                    // Add all of related's copy chain to var's chain
                    if (copyChains.containsKey(related)) {
                        copyChains.get(var).addAll(copyChains.get(related));
                    }
                }
                
                if (copyChains.get(var).size() > sizeBefore) {
                    changed = true;
                }
            }
        } while (changed);
        
        return copyChains;
    }
}