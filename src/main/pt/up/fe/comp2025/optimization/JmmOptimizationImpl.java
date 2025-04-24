package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JmmOptimizationImpl implements JmmOptimization {

    private final OptimizationManager optimizationManager;
    private final RegisterAllocator registerAllocator;
    
    public JmmOptimizationImpl() {
        this.optimizationManager = new OptimizationManager();
        this.registerAllocator = new RegisterAllocator();
    }
    
    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        // Run optimizations if the -o flag is enabled
        boolean optimize = semanticsResult.getConfig().containsKey("optimize") || 
                          semanticsResult.getConfig().containsKey("-o");
        
        if (optimize) {
            // Apply optimizations before generating OLLIR code
            optimize(semanticsResult);
        }

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        boolean optimize = semanticsResult.getConfig().containsKey("optimize") || 
                          semanticsResult.getConfig().containsKey("-o");
        
        if (optimize) {
            JmmNode rootNode = semanticsResult.getRootNode();
            System.out.println("Applying optimizations...");
            
            boolean changed = optimizationManager.optimize(rootNode, true);
            
            if (changed) {
                System.out.println("Optimizations applied successfully!");
            } else {
                System.out.println("No optimizations were applied.");
            }
        }

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        List<Report> reports = new ArrayList<>(ollirResult.getReports());
        
        // Check if register allocation is requested
        int registerAllocation = ConfigOptions.getRegisterAllocation(ollirResult.getConfig());
        
        if (registerAllocation != -1) {
            System.out.println("Performing register allocation with " + 
                (registerAllocation == 0 ? "minimized" : registerAllocation) + " registers...");
            
            // Perform register allocation
            List<Report> regAllocReports = registerAllocator.allocateRegisters(ollirResult, registerAllocation);
            reports.addAll(regAllocReports);
            
            if (regAllocReports.isEmpty()) {
                System.out.println("Register allocation completed successfully!");
            } else {
                System.out.println("Register allocation encountered issues. See reports for details.");
            }
        }
        
        // Register allocation modifies the ClassUnit object within ollirResult directly
        // So we just need to return the original result with updated reports

        return ollirResult;
    }
}
