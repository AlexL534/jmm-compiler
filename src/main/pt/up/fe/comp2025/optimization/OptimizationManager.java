package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;

/**
 * Manager class that applies optimizations to the AST.
 * Applies constant propagation and constant folding until a fixed point is reached.
 */
public class OptimizationManager {
    
    private final ConstantPropagationVisitor propagationVisitor;
    private final ConstantFoldingVisitor foldingVisitor;
    
    public OptimizationManager() {
        this.propagationVisitor = new ConstantPropagationVisitor();
        this.foldingVisitor = new ConstantFoldingVisitor();
    }
    
    /**
     * Apply optimizations to the AST.
     * @param node The root node of the AST.
     * @param optimizationFlag Whether optimizations are enabled.
     * @return True if any optimizations were applied, false otherwise.
     */
    public boolean optimize(JmmNode node, boolean optimizationFlag) {
        if (!optimizationFlag) {
            return false;
        }
        
        boolean changed = false;
        boolean currentIterationChanged;
        
        // Keep applying optimizations until no more changes are made
        do {
            currentIterationChanged = false;
            
            // Apply constant propagation
            propagationVisitor.reset(); // Reset change tracking
            propagationVisitor.visit(node);
            if (propagationVisitor.hasChanged()) {
                currentIterationChanged = true;
                changed = true;
            }
            
            // Apply constant folding
            foldingVisitor.reset(); // Reset change tracking
            foldingVisitor.visit(node, true);
            if (foldingVisitor.hasChanged()) {
                currentIterationChanged = true;
                changed = true;
            }
        } while (currentIterationChanged);
        
        return changed;
    }
}