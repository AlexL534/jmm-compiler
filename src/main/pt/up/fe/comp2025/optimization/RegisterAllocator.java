package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

/**
 * Register allocator for the J-- compiler.
 * Implements a simplified register allocation algorithm for J--.
 */
public class RegisterAllocator {

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
            return reports;
        }
        
        System.out.println("Register allocation with " + maxRegisters + " registers");
        
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
     * Uses a simple graph coloring algorithm.
     * 
     * @param method The method to process.
     */
    private void minimizeRegisters(Method method) {
        // Create an interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method);
        
        // Color the graph using a greedy algorithm
        Map<String, Integer> colorAssignment = colorGraph(interferenceGraph);
        
        // Update the variable table with the new register assignments
        updateVarTable(method, colorAssignment);
    }
    
    /**
     * Limits the number of registers used by the method.
     * 
     * @param method The method to process.
     * @param maxRegisters The maximum number of registers to use.
     * @throws RuntimeException if more registers are needed than specified.
     */
    private void limitRegisters(Method method, int maxRegisters) {
        // Create an interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method);
        
        // Color the graph using a greedy algorithm, limiting to maxRegisters
        Map<String, Integer> colorAssignment = colorGraphLimited(interferenceGraph, maxRegisters);
        
        // Update the variable table with the new register assignments
        updateVarTable(method, colorAssignment);
    }
    
    /**
     * Builds a simplified interference graph based on variable liveness.
     * 
     * @param method The method to analyze.
     * @return A map of variable names to the set of variables they interfere with.
     */
    private Map<String, Set<String>> buildInterferenceGraph(Method method) {
        Map<String, Set<String>> interferenceGraph = new HashMap<>();
        
        // Initialize the graph with all variables
        for (String varName : method.getVarTable().keySet()) {
            if (!varName.equals("this")) { // Skip 'this'
                interferenceGraph.put(varName, new HashSet<>());
            }
        }
        
        // For each instruction, find all variables used and create interference edges
        for (var instruction : method.getInstructions()) {
            Set<String> varsInInstruction = getVarsUsedInInstruction(instruction);
            
            // Create interference edges between all pairs of variables in this instruction
            for (String var1 : varsInInstruction) {
                for (String var2 : varsInInstruction) {
                    if (!var1.equals(var2) && interferenceGraph.containsKey(var1) && 
                        interferenceGraph.containsKey(var2)) {
                        interferenceGraph.get(var1).add(var2);
                        interferenceGraph.get(var2).add(var1);
                    }
                }
            }
        }
        
        return interferenceGraph;
    }
    
    /**
     * Gets all variables used in an instruction.
     * 
     * @param instruction The instruction to analyze.
     * @return A set of variable names used in the instruction.
     */
    private Set<String> getVarsUsedInInstruction(Object instruction) {
        Set<String> vars = new HashSet<>();
        
        // Extract variables based on instruction type
        if (instruction instanceof org.specs.comp.ollir.inst.AssignInstruction) {
            org.specs.comp.ollir.inst.AssignInstruction assignInst = 
                (org.specs.comp.ollir.inst.AssignInstruction) instruction;
            
            // Add variables from destination and source
            extractVarsFromElement(assignInst.getDest(), vars);
            
            // For right-hand side, handle it carefully as it might not be an Element
            Object rhs = assignInst.getRhs();
            if (rhs instanceof Element) {
                extractVarsFromElement((Element) rhs, vars);
            }
        }
        // Add support for other instruction types as needed
        
        return vars;
    }
    
    /**
     * Extracts variables from an OLLIR Element recursively.
     * 
     * @param element The element to analyze.
     * @param vars The set to add variable names to.
     */
    private void extractVarsFromElement(Element element, Set<String> vars) {
        if (element instanceof Operand) {
            Operand operand = (Operand) element;
            vars.add(operand.getName());
        } 
        else if (element instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) element;
            vars.add(arrayOp.getName());
        } 
        // Handle other element types as needed
    }
    
    /**
     * Colors the interference graph using a greedy algorithm.
     * Minimizes the number of colors (registers) used.
     * 
     * @param interferenceGraph The interference graph.
     * @return A map of variable names to register numbers.
     */
    private Map<String, Integer> colorGraph(Map<String, Set<String>> interferenceGraph) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Sort variables by degree (number of interferences)
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> 
            Integer.compare(interferenceGraph.get(v2).size(), interferenceGraph.get(v1).size()));
        
        // Assign colors (registers)
        for (String var : sortedVars) {
            // Find the lowest color not used by neighbors
            Set<Integer> neighborColors = new HashSet<>();
            for (String neighbor : interferenceGraph.get(var)) {
                if (colorAssignment.containsKey(neighbor)) {
                    neighborColors.add(colorAssignment.get(neighbor));
                }
            }
            
            int color = 0;
            while (neighborColors.contains(color)) {
                color++;
            }
            
            colorAssignment.put(var, color);
        }
        
        return colorAssignment;
    }
    
    /**
     * Colors the interference graph using a greedy algorithm with a maximum number of colors.
     * 
     * @param interferenceGraph The interference graph.
     * @param maxColors The maximum number of colors to use.
     * @return A map of variable names to register numbers.
     * @throws RuntimeException if more colors are needed than specified.
     */
    private Map<String, Integer> colorGraphLimited(
            Map<String, Set<String>> interferenceGraph, int maxColors) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        
        // Sort variables by degree (number of interferences)
        List<String> sortedVars = new ArrayList<>(interferenceGraph.keySet());
        sortedVars.sort((v1, v2) -> 
            Integer.compare(interferenceGraph.get(v2).size(), interferenceGraph.get(v1).size()));
        
        // Assign colors (registers)
        for (String var : sortedVars) {
            // Find the lowest color not used by neighbors
            Set<Integer> neighborColors = new HashSet<>();
            for (String neighbor : interferenceGraph.get(var)) {
                if (colorAssignment.containsKey(neighbor)) {
                    neighborColors.add(colorAssignment.get(neighbor));
                }
            }
            
            int color = 0;
            while (neighborColors.contains(color)) {
                color++;
            }
            
            if (color >= maxColors) {
                throw new RuntimeException("Need at least " + (color + 1) + 
                    " registers, but limited to " + maxColors);
            }
            
            colorAssignment.put(var, color);
        }
        
        return colorAssignment;
    }
    
    /**
     * Updates the variable table with the new register assignments.
     * 
     * @param method The method being processed.
     * @param colorAssignment The register assignment for each variable.
     */
    private void updateVarTable(Method method, Map<String, Integer> colorAssignment) {
        // Count parameters to preserve their ordering
        int paramCount = method.getParams().size();
        
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
     * 
     * @param method The method to generate the report for.
     * @return A string representing the register mapping.
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