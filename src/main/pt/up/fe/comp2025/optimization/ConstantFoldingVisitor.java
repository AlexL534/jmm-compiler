package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.List;

import pt.up.fe.comp2025.ast.Kind;

/**
 * Visitor that performs constant folding.
 * Folds expressions with constant operands into a single constant value.
 */
public class ConstantFoldingVisitor extends AJmmVisitor<Boolean, Boolean> {
    
    private boolean modified = false;
    
    public boolean hasChanged() {
        return modified;
    }
    
    /**
     * Reset the visitor state for a new optimization pass
     */
    public void reset() {
        modified = false;
    }
    
    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR.getNodeName(), this::visitBinaryExpr);
        setDefaultVisit(this::defaultVisit);
    }
    
    private Boolean visitBinaryExpr(JmmNode node, Boolean preserveComparisonStructure) {
        // First visit children to fold nested expressions
        for (JmmNode child : node.getChildren()) {
            visit(child, preserveComparisonStructure);
        }
        
        // Check if both operands are constants after potential folding
        List<JmmNode> children = node.getChildren();
        if (children.size() != 2) {
            return false;
        }
        
        JmmNode leftChild = children.get(0);
        JmmNode rightChild = children.get(1);
        
        // Try to fold only if both operands are literals (integer or boolean)
        if (isConstant(leftChild) && isConstant(rightChild)) {
            String op = node.get("op");
            
            if (leftChild.getKind().equals(Kind.INTEGER_LITERAL.getNodeName()) && 
                rightChild.getKind().equals(Kind.INTEGER_LITERAL.getNodeName())) {
                // Integer operation
                int leftValue = Integer.parseInt(leftChild.get("value"));
                int rightValue = Integer.parseInt(rightChild.get("value"));
                int result;
                
                // Perform the operation
                switch (op) {
                    case "+":
                        result = leftValue + rightValue;
                        break;
                    case "-":
                        result = leftValue - rightValue;
                        break;
                    case "*":
                        result = leftValue * rightValue;
                        break;
                    case "/":
                        if (rightValue == 0) {
                            // Avoid division by zero
                            return false;
                        }
                        result = leftValue / rightValue;
                        break;
                    case "<":
                        // For comparison operators, check if we're inside a while loop context
                        // and should preserve the comparison structure for the test
                        if (preserveComparisonStructure && isInWhileLoopCondition(node)) {
                            // Don't fold this comparison, leave it as is
                            return false;
                        }
                        replaceBooleanExpr(node, leftValue < rightValue);
                        return true;
                    default:
                        return false;
                }
                
                // Replace the binary expression with the result literal
                replaceIntExpr(node, result);
                return true;
            } else if (leftChild.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName()) && 
                      rightChild.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName())) {
                // Boolean operation
                boolean leftValue = Boolean.parseBoolean(leftChild.get("value"));
                boolean rightValue = Boolean.parseBoolean(rightChild.get("value"));
                boolean result;
                
                // Perform the operation
                switch (op) {
                    case "&&":
                        result = leftValue && rightValue;
                        break;
                    case "||":
                        result = leftValue || rightValue;
                        break;
                    default:
                        return false;
                }
                
                // Replace the binary expression with the result literal
                replaceBooleanExpr(node, result);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if this node is part of a while loop condition
     */
    private boolean isInWhileLoopCondition(JmmNode node) {
        JmmNode parent = node;
        while (parent != null && !parent.getKind().equals(Kind.WHILE_STMT.getNodeName())) {
            parent = parent.getParent();
        }
        
        // If this node is in a while loop and is the first child (condition)
        if (parent != null && parent.getNumChildren() > 0) {
            JmmNode conditionNode = parent.getChild(0);
            return conditionNode == node || isDescendant(conditionNode, node);
        }
        
        return false;
    }
    
    /**
     * Check if potentialChild is a descendant of node
     */
    private boolean isDescendant(JmmNode node, JmmNode potentialChild) {
        for (JmmNode child : node.getChildren()) {
            if (child == potentialChild || isDescendant(child, potentialChild)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isConstant(JmmNode node) {
        return node.getKind().equals(Kind.INTEGER_LITERAL.getNodeName()) || 
               node.getKind().equals(Kind.BOOLEAN_LITERAL.getNodeName());
    }
    
    private void replaceIntExpr(JmmNode node, int value) {
        // Create a new integer literal node
        JmmNode newNode = new JmmNodeImpl(Kind.toNodeName(Kind.INTEGER_LITERAL));
        newNode.put("value", String.valueOf(value));
        
        // Replace the node in the parent
        node.replace(newNode);
        modified = true;
    }
    
    private void replaceBooleanExpr(JmmNode node, boolean value) {
        // Create a new boolean literal node
        JmmNode newNode = new JmmNodeImpl(Kind.toNodeName(Kind.BOOLEAN_LITERAL));
        newNode.put("value", String.valueOf(value));
        
        // Replace the node in the parent
        node.replace(newNode);
        modified = true;
    }
    
    private Boolean defaultVisit(JmmNode node, Boolean dummy) {
        for (JmmNode child : node.getChildren()) {
            visit(child, dummy);
        }
        return false;
    }
}