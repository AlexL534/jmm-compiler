package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    private final OptimizationManager optimizationManager;
    
    public JmmOptimizationImpl() {
        this.optimizationManager = new OptimizationManager();
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
        // Currently we're doing AST-based optimizations only
        // OLLIR-based optimizations would be implemented here

        var config = ollirResult.getConfig();
        if(!config.containsKey("registerAllocation")){
            return ollirResult;
        }
        var registers = config.get("registerAllocation");
        if(registers.equals("-1")){
            //use the same ollir. No optimizations needed
            return ollirResult;
        }
        var ollirClass =  ollirResult.getOllirClass();

        //build the cfg
        ollirClass.buildCFGs();

        //TODO: liveness analysis
        var dataFlow = new DataFlowAnalysis();
        for(var method : ollirClass.getMethods()){
            dataFlow.analyseMethod(method);
        }

        //TODO: Interference Graph
        //TODO: Graph Coloring

        return ollirResult;
    }
}
