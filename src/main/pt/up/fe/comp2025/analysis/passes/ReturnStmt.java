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

import static pt.up.fe.comp2025.ast.Kind.BINARY_EXPR;
import static pt.up.fe.comp2025.ast.Kind.RETURN_STMT;

public class ReturnStmt extends AnalysisVisitor {
    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Type getReturnType(JmmNode returnStmt, SymbolTable table){
        String type = "int";
        boolean isArray = false;

        if(Kind.INTEGER_LITERAL.check(returnStmt)){
            type = "int";
        }
        else if(Kind.BOOLEAN_LITERAL.check(returnStmt)){
            type = "boolean";
        }
        else if(Kind.VAR_REF_EXPR.check(returnStmt)) {
            //is a field
            for (Symbol field : table.getFields()) {
                if (field.getName().equals(returnStmt.get("name"))) {
                    type = field.getType().getName();
                    isArray = field.getType().isArray();
                }
            }
            var parameters = table.getParameters(currentMethod);
            // Var is a parameter
            if (parameters.stream().anyMatch(param -> param.getName().equals(returnStmt.get("name")))) {
                for (Symbol param : parameters) {
                    if (param.getName().equals(returnStmt.get("name"))) {
                        type = param.getType().getName();
                        isArray = param.getType().isArray();

                    }
                }
            }
            // Var is a declared variable
            var locals = table.getLocalVariables(currentMethod);
            if (locals.stream()
                    .anyMatch(varDecl -> varDecl.getName().equals(returnStmt.get("name")))) {
                for (Symbol varDecl : locals) {
                    if (varDecl.getName().equals(returnStmt.get("name"))) {
                        type = varDecl.getType().getName();
                        isArray = varDecl.getType().isArray();
                    }
                }

            }

        }
        else if(Kind.METHOD_CALL.check(returnStmt)){
            var methodType = table.getReturnType(returnStmt.get("name"));
            if(methodType == null){
                return null;
            }
            type = table.getReturnType(returnStmt.get("name")).getName();
            isArray = table.getReturnType(returnStmt.get("name")).isArray();
        }
        else if(Kind.ARRAY_LENGTH.check(returnStmt)){
            type = "int";
            isArray = false;
        }
        else if(Kind.ARRAY_CREATION.check(returnStmt)){
            type = "int";
            isArray = true;
        }
        else if(Kind.ARRAY_SUBSCRIPT.check(returnStmt)){
            type = "int";
            isArray = false;
        }
        else if(Kind.ARRAY_LITERAL.check(returnStmt)){
            type = "int";
            isArray = true;
        }
        else{
            return this.getReturnType(returnStmt.getChild(0), table);
        }
        return new Type(type, isArray);

    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        //TypeUtils aux = new TypeUtils(table);
        Type exprType = this.getReturnType(returnStmt.getChild(0), table);

        if(exprType == null){
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    "Could not evaluate the return expression",
                    null
            );

            return null;
        }

        Type methodType = table.getReturnType(currentMethod);
        if((!methodType.getName().equals(exprType.getName())) || (methodType.isArray() != exprType.isArray())){
            String message = String.format("Invalid return type: %s and %s", exprType.getName(), methodType.getName());
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    message,
                    null
            );

            addReport(report);
        }



        return null;
    }
}
