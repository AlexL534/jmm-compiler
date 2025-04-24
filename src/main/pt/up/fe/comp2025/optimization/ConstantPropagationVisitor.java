package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp2025.ast.Kind;

/**
 * Visitor that performs constant propagation.
 * Tracks variable values and replaces variable references with constants when possible.
 */
public class ConstantPropagationVisitor extends AJmmVisitor<Void, Boolean> {
    
    private boolean modified = false;
    
    // Map of variable name to its constant value (if known)
    // The key is the variable name, the value is the JmmNode representing the constant
    private final Map<String, JmmNode> constantVariables = new HashMap<>();
    
    // Map to keep track of variables that are modified in loops, if-branches, etc.
    // These variables can't be fully propagated
    private final Map<String, Boolean> variableModifiedInBranch = new HashMap<>();
    
    // Stack of method scopes to handle variable scoping
    private String currentMethod = null;
    private final Map<String, Map<String, JmmNode>> methodScopeVariables = new HashMap<>();
    
    /**
     * Reset the visitor state for a new optimization pass
     */
    public void reset() {
        modified = false;
        // Note: We don't reset the other data structures as they maintain 
        // important context between optimization passes
    }
    
    public boolean hasChanged() {
        return modified;
    }
    
    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL.getNodeName(), this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT.getNodeName(), this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR.getNodeName(), this::visitVarRefExpr);
        addVisit(Kind.WHILE_STMT.getNodeName(), this::visitWhileStmt);
        addVisit(Kind.IF_STMT.getNodeName(), this::visitIfStmt);
        setDefaultVisit(this::defaultVisit);
    }
    
    private Boolean visitMethodDecl(JmmNode node, Void unused) {
        // Set current method scope
        String methodName = node.get("name");
        currentMethod = methodName;
        
        // Initialize method scope variables
        methodScopeVariables.put(methodName, new HashMap<>());
        
        // Visit method body
        for (JmmNode child : node.getChildren()) {
            visit(child);
        }
        
        // Clear method scope when exiting
        currentMethod = null;
        return false;
    }
    
    private Boolean visitAssignStmt(JmmNode node, Void unused) {
        // Process the right-hand side first
        for (JmmNode child : node.getChildren()) {
            visit(child);
        }
        
        String varName = node.get("varName");
        JmmNode rhs = node.getChild(0);
        
        if (isWhileLoopContext(node) || isIfStatementContext(node)) {
            // Mark the variable as modified in a branch
            variableModifiedInBranch.put(varName, true);
            // Remove it from known constants since its value is not statically determinable
            if (currentMethod != null) {
                methodScopeVariables.get(currentMethod).remove(varName);
            }
            return false;
        }
        
        // If the RHS is a constant, remember this assignment
        if (isConstant(rhs)) {
            if (currentMethod != null) {
                methodScopeVariables.get(currentMethod).put(varName, rhs);
            }
        } else {
            // If not constant, remove from known constants
            if (currentMethod != null) {
                methodScopeVariables.get(currentMethod).remove(varName);
            }
        }
        
        return false;
    }
    
    private Boolean visitVarRefExpr(JmmNode node, Void unused) {
        String varName = node.get("name");
        
        if (currentMethod != null && methodScopeVariables.get(currentMethod).containsKey(varName) && 
            !variableModifiedInBranch.containsKey(varName)) {
            
            JmmNode constantNode = methodScopeVariables.get(currentMethod).get(varName);
            
            // Create a copy of the constant node
            JmmNode newNode;
            if (constantNode.getKind().equals(Kind.INTEGER_LITERAL.getNodeName())) {
                newNode = new JmmNodeImpl(Kind.toNodeName(Kind.INTEGER_LITERAL));
                newNode.put("value", constantNode.get("value"));
            } else if (constantNode.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName())) {
                newNode = new JmmNodeImpl(Kind.toNodeName(Kind.BOOLEAN_LITERAL));
                newNode.put("value", constantNode.get("value"));
            } else {
                return false;
            }
            
            // Replace the variable reference with the constant
            node.replace(newNode);
            modified = true;
            return true;
        }
        
        return false;
    }
    
    private Boolean visitWhileStmt(JmmNode node, Void unused) {
        // Visit condition first and apply constant propagation to it
        if (node.getNumChildren() > 0) {
            JmmNode condition = node.getChild(0);
            // First pass to replace variables with constants without evaluating the condition
            propagateConstantsInCondition(condition);
            
            // Then visit the condition to apply any other optimizations
            visit(condition);
        }
        
        // Variables modified inside loop body cannot be propagated
        if (node.getNumChildren() > 1) {
            markVarModificationsInBranch(node.getChild(1));
            visit(node.getChild(1));
        }
        
        return false;
    }
    
    /**
     * Propagate constants in a condition expression without evaluating the condition
     */
    private void propagateConstantsInCondition(JmmNode conditionNode) {
        if (conditionNode == null) return;
        
        // If this is a variable reference, try to replace it with its constant value
        if (conditionNode.getKind().equals(Kind.VAR_REF_EXPR.getNodeName())) {
            String varName = conditionNode.get("name");
            if (currentMethod != null && methodScopeVariables.get(currentMethod).containsKey(varName) && 
                !variableModifiedInBranch.containsKey(varName)) {
                
                JmmNode constantNode = methodScopeVariables.get(currentMethod).get(varName);
                if (isConstant(constantNode)) {
                    // Create a copy of the constant node
                    JmmNode newNode;
                    if (constantNode.getKind().equals(Kind.INTEGER_LITERAL.getNodeName())) {
                        newNode = new JmmNodeImpl(Kind.toNodeName(Kind.INTEGER_LITERAL));
                        newNode.put("value", constantNode.get("value"));
                        conditionNode.replace(newNode);
                        modified = true;
                    } else if (constantNode.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName())) {
                        newNode = new JmmNodeImpl(Kind.toNodeName(Kind.BOOLEAN_LITERAL));
                        newNode.put("value", constantNode.get("value"));
                        conditionNode.replace(newNode);
                        modified = true;
                    }
                }
            }
        } 
        // If it's a binary operation or other node, visit its children
        else {
            // Make a copy of children list to avoid concurrent modification issues
            for (int i = 0; i < conditionNode.getNumChildren(); i++) {
                propagateConstantsInCondition(conditionNode.getChild(i));
            }
        }
    }
    
    private Boolean visitIfStmt(JmmNode node, Void unused) {
        // Visit condition first
        if (node.getNumChildren() > 0) {
            visit(node.getChild(0));
        }
        
        // Variables modified inside either branch cannot be fully propagated
        if (node.getNumChildren() > 1) {
            markVarModificationsInBranch(node.getChild(1));
            visit(node.getChild(1));
        }
        
        if (node.getNumChildren() > 2) {
            markVarModificationsInBranch(node.getChild(2));
            visit(node.getChild(2));
        }
        
        return false;
    }
    
    private void markVarModificationsInBranch(JmmNode node) {
        if (node.getKind().equals(Kind.ASSIGN_STMT.getNodeName())) {
            String varName = node.get("varName");
            variableModifiedInBranch.put(varName, true);
            if (currentMethod != null) {
                methodScopeVariables.get(currentMethod).remove(varName);
            }
        }
        
        for (JmmNode child : node.getChildren()) {
            markVarModificationsInBranch(child);
        }
    }
    
    private boolean isWhileLoopContext(JmmNode node) {
        JmmNode parent = node.getParent();
        while (parent != null) {
            if (parent.getKind().equals(Kind.WHILE_STMT.getNodeName())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    private boolean isIfStatementContext(JmmNode node) {
        JmmNode parent = node.getParent();
        while (parent != null) {
            if (parent.getKind().equals(Kind.IF_STMT.getNodeName())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    private boolean isConstant(JmmNode node) {
        return node.getKind().equals(Kind.INTEGER_LITERAL.getNodeName()) || 
               node.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName());
    }
    
    private Boolean defaultVisit(JmmNode node, Void unused) {
        for (JmmNode child : node.getChildren()) {
            visit(child);
        }
        return false;
    }
}