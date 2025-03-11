package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp.jmm.analysis.table.Type;

public class ArgumentCheck extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL, this::visitArgumentsCheck);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArgumentsCheck(JmmNode node, SymbolTable table) {
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        var imps = table.getImports();

        //if the method called is from an import
        var firstChildType = utils.getExprType(node.getChild(0)).getName();
        if(imps.contains(firstChildType)) {
            return null;
        }
        //assume that the method belongs to the super class
        if(imps.contains(table.getSuper()) && (firstChildType.equals(table.getClassName())) && (!table.getMethods().contains(node.get("name")))) {
            return null;
        }

        Type childType = null;
        var arguments = table.getParameters(node.get("name"));

        if(arguments == null){
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Method" + node.get("name") + "does not exist",
                    null
            );
            addReport(report);
            return null;
        }
        if((arguments.size() != node.getChildren().size() - 1) && (!arguments.getLast().getType().getName().equals("int vararg"))) {
            String message = "Wrong number of arguments: " + (node.getChildren().size() - 1) + ". Expected:" + arguments.size();
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);

            return null;
        }

        for (int i = 1; i < node.getChildren().size(); i++) {
            var child = node.getChild(i);
            childType = utils.getExprType(child);
            Symbol argType = null;

            //check if i is bigger than the argument list (only useful in argvars)
            if(arguments.size() <= i) {
                argType = arguments.getLast();
            }
            else {
                argType = arguments.get(i - 1);
            }

            //ignore if the parameter is vararg and the arguments passed are int
            if(childType.getName().equals("int") && !childType.isArray() && argType.getType().getName().equals("int vararg")) {
                continue;
            }

            if(!childType.getName().equals(argType.getType().getName()) || childType.isArray() != argType.getType().isArray()) {
                String message = String.format("Invalid argument type: %s and %s", childType.getName(), argType.getType().getName());
                Report report = Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        message,
                        null
                );
                addReport(report);
            }
        }

        return null;
    }
}
