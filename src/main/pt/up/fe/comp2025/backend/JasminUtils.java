package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    private int currentTempLabels = 0;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public int getCurrentTempLabel() {
        currentTempLabels++;
        return currentTempLabels;
    }

    public String ollirToJasminType(Type type) {
        if (type == null) {
            return "V";
        }
        
        var typeStr = type.toString();
        
        switch (typeStr){
            case "INT32":
            case "I32":
                return "I";
            case "INT32[]":
                return "[I";
            case "BOOLEAN":
                return "Z";
            case "VOID":
                return "V";
            case "STRING":
                return "Ljava/lang/String;";
            default:
                // If it's a class type
                if (type instanceof ClassType) {
                    String className = ((ClassType) type).getName();
                    return "L" + className + ";";
                }
                // Default to int if unsure
                return "I";
        }
    }

    /**
     * Check if a register number can use the _X instruction shorthand
     */
    public boolean canUseShorthand(int register) {
        return register >= 0 && register <= 3;
    }
    
    /**
     * Convert constant integers to the most appropriate Jasmin instruction
     */
    public String getIntegerLoadInstruction(int value) {
        if (value >= -1 && value <= 5) {
            return "iconst_" + (value == -1 ? "m1" : value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return "bipush " + value;
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return "sipush " + value;
        } else {
            return "ldc " + value;
        }
    }
    
    /**
     * Get the optimized load instruction for a specific register
     */
    public String getOptimizedLoad(String typePrefix, int register) {
        if (canUseShorthand(register)) {
            return typePrefix + "load_" + register;
        } else {
            return typePrefix + "load " + register;
        }
    }
    
    /**
     * Get the optimized store instruction for a specific register
     */
    public String getOptimizedStore(String typePrefix, int register) {
        if (canUseShorthand(register)) {
            return typePrefix + "store_" + register;
        } else {
            return typePrefix + "store " + register;
        }
    }
    
    /**
     * Calculate the appropriate type prefix for Jasmin instructions based on OLLIR type
     */
    public String getTypePrefix(Type type) {
        if (type == null) {
            return "i"; // Default to int
        }
        
        var typeStr = type.toString();
        
        if (typeStr.equals("INT32") || typeStr.equals("I32") || typeStr.equals("BOOLEAN")) {
            return "i";
        } else if (typeStr.equals("INT32[]") || type instanceof ClassType) {
            return "a";
        } else if (typeStr.equals("VOID")) {
            return "v";
        }
        
        // Default to int if unsure
        return "i";
    }
}
