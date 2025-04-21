package pt.up.fe.comp2025.optimization;

import org.antlr.v4.runtime.misc.Pair;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;

import java.lang.annotation.ElementType;
import java.util.*;

public class DataFlowAnalysis {
    public void analyseMethod(Method method) {
        var in = new HashMap<>();
        var out = new HashMap<>();
        var defs = new HashMap<>();
        var uses = new HashMap<>();

        for(Instruction instruction: method.getInstructions()){
            defs.put(instruction, this.getDefs(instruction, method.getVarTable()));
            uses.put(instruction, this.getUses(instruction));
        }

    }

    private List<Operand> getDefs(Instruction instruction, HashMap<String, Descriptor> methodVarTable) {
        List<Operand> operandsFound = new ArrayList<>();

        if(!instruction.getInstType().equals(InstructionType.ASSIGN)){
            //Defs are only present in assigns
            return operandsFound;
        }

        var assignInstruction = (AssignInstruction) instruction;
        Element operand = assignInstruction.getDest();
        /*
        Descriptor descriptor = methodVarTable.get(((Operand) operand).getName());
        if(!descriptor.getScope().equals(VarScope.LOCAL)){
            //only locals are assign in
            return operandsFound;
        }*/
        operandsFound.add((Operand) operand);

        return operandsFound;
    }

    private List<Operand> getUses(Instruction instruction){
        List<Operand> operandsFound = new ArrayList<>();
        switch(instruction.getInstType()){
            case ASSIGN:
                operandsFound = this.assignUses((AssignInstruction) instruction);
                break;
            case CALL:
                break;
            case GOTO:
                //no usages in go to expressions
                return null;
            case NOPER:
                break;
            case BRANCH:
                break;
            case RETURN:
                break;
            case GETFIELD:
                break;
            case PUTFIELD:
                break;
            case UNARYOPER:
                break;
            case BINARYOPER:
                break;
        }
        return operandsFound;
    }

    private List<Operand> assignUses(AssignInstruction instruction){
        //in assigns, we want the right side of the instruction . Ex: In a = b + c we want the b and c
        return this.getUses(instruction.getRhs());
    }

    private List<Operand> binaryOperUses(BinaryOpInstruction instruction){
        List<Operand> operandsFound = new ArrayList<>();
        for(var operand : instruction.getOperands()){
            if(!(operand instanceof Operand)) continue;
            operandsFound.add((Operand) operand);
        }
        return operandsFound;
    }

    private List<Operand> unaryOperUses(UnaryOpInstruction instruction){
        List<Operand> operandsFound = new ArrayList<>();
        for(var operand : instruction.getOperands()){
            if(!(operand instanceof Operand)) continue;
            operandsFound.add((Operand) operand);
        }
        return operandsFound;
    }

    private List<Operand> nOperUses(SingleOpInstruction instruction){
        Element operand = instruction.getSingleOperand();
        if(!(operand instanceof Operand)) return List.of();
        return List.of((Operand) operand);
    }

    private List<Operand>  branchUses(CondBranchInstruction instruction){
        List<Operand> operandsFound = new ArrayList<>();

        for(var operand : instruction.getOperands()){
            if(!(operand instanceof Operand)) continue;
            operandsFound.add((Operand) operand);
        }
        return operandsFound;
    }

    private List<Operand> returnUses(ReturnInstruction instruction){
        List<Operand> operandsFound = new ArrayList<>();
        Optional<Element> operand = instruction.getOperand();
        //check if the return instruction has an operator (non-void) and if is operand
        if(operand.isEmpty() || (operand.get() instanceof Operand)) return operandsFound;

        operandsFound.add((Operand) operand.get());
        return operandsFound;
    }



}
