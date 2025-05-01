package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.RETURN_STMT;

public class AssignmentCheck extends AnalysisVisitor {
    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssign(JmmNode node, SymbolTable table){
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        //First get the Type of the variable that is being assigned
        var varName = node.get("varName");
        String typeVar = "unknown";
        boolean isArrayVar = false;

        for (Symbol field : table.getFields()) {
            //the main method is static so it cannot use class fields
            if (field.getName().equals(varName) && !currentMethod.equals("main")) {
                typeVar = field.getType().getName();
                isArrayVar = field.getType().isArray();
            }
        }
        var parameters = table.getParameters(currentMethod);
        // Var is a parameter
        if(parameters != null) {
            if (parameters.stream().anyMatch(param -> param.getName().equals(varName))) {
                for (Symbol param : parameters) {
                    if (param.getName().equals(varName)) {
                        typeVar = param.getType().getName();
                        isArrayVar = param.getType().isArray();

                    }
                }
            }
        }
        // Var is a declared variable
        var locals = table.getLocalVariables(currentMethod);
        if(locals != null) {
            if (locals.stream()
                    .anyMatch(varDecl -> varDecl.getName().equals(varName))) {
                for (Symbol varDecl : locals) {
                    if (varDecl.getName().equals(varName)) {
                        typeVar = varDecl.getType().getName();
                        isArrayVar = varDecl.getType().isArray();
                    }
                }
            }
        }

        if(!utils.isValidType(new Type(typeVar, isArrayVar))){
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Invalid Type: " + typeVar,
                    null
            );
            addReport(report);
        }

        //get The expression type
        JmmNode expr = node.getChild(0);
        Type exprType = utils.getExprType(expr);

        if(exprType.getName().equals("unknown")){
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Could not evaluate the expressions type of the assignment",
                    null
            );
            addReport(report);
        }

        //check if any of the types is imported (imported classes don't need type checking)
        for(String imp : table.getImports() ){
            if(imp.equals(exprType.getName()) ){
                return null;
            }
        }

        //Extended classes to imports are ignored in type check
        if(table.getImports().contains(table.getSuper()) && (table.getClassName().equals(exprType.getName()))){
            return null;
        }

        if((!exprType.getName().equals(typeVar)) || (isArrayVar != exprType.isArray())){
            String message = String.format("Invalid return assignment: %s and %s", exprType.getName(), typeVar);
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );

            addReport(report);
        }

        return null;
    }


}
