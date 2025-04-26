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

import java.util.List;

public class ArgumentCheck extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_CALL, this::visitArgumentsCheck);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        //special handling for the main function
        if(currentMethod.equals("main")){
            this.handleMain(method, table);
            return null;
        }

        // check for multiple varargs in method parameters
        List<Symbol> parameters = table.getParameters(currentMethod);

        boolean foundVararg = false;

        for (int i = 0; i < parameters.size(); i++) {
            Symbol param = parameters.get(i);
            boolean isVararg = param.getType().getName().endsWith(" vararg");

            if (isVararg) {
                if (foundVararg) {
                    // Found second vararg - error
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            method.getLine(),
                            method.getColumn(),
                            "Method '" + currentMethod + "' has multiple varargs parameters",
                            null
                    ));
                    break;
                }
                foundVararg = true;

                // Check if it's not the last parameter
                if (i != parameters.size() - 1) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            method.getLine(),
                            method.getColumn(),
                            "Varargs parameter must be the last parameter in method '" + currentMethod + "'",
                            null
                    ));
                    break;
                }
            }
        }

        return null;
    }

    private Void handleMain(JmmNode method, SymbolTable table){
        List<Symbol> parameters = table.getParameters(currentMethod);
        if(parameters.size() != 1){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    "Invalid main method: invalid number of parameters",
                    null
            ));
            return null;
        }

        Symbol param = parameters.getFirst();
        if(!param.getType().getName().equals("String") || !param.getType().isArray()){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    method.getLine(),
                    method.getColumn(),
                    "Invalid main method: invalid parameter type",
                    null
            ));
            return null;
        }
        return null;
    }

    private Void visitArgumentsCheck(JmmNode node, SymbolTable table) {
        TypeUtils utils = new TypeUtils(table);
        utils.setCurrentMethod(currentMethod);

        var imps = table.getImports();

        // First check if this is a method call on an object from an imported class
        if (node.getNumChildren() > 0) {
            JmmNode firstChild = node.getChild(0);
            Type firstChildType = utils.getExprType(firstChild);



            // If the object we're calling the method on is an imported type or
            // is itself an import identifier, we assume the method exists
            if (firstChildType != null) {
                String typeName = firstChildType.getName();
                if (imps.contains(typeName) ||
                    imps.stream().anyMatch(imp -> imp.endsWith("." + typeName))) {
                    return null; // Assume methods on imported types are valid
                }
            }

            // If the caller is a variable reference that matches an import name
            if (firstChild.getKind().equals(Kind.VAR_REF_EXPR.getNodeName())) {
                String varName = firstChild.get("name");
                if (imps.contains(varName) ||
                    imps.stream().anyMatch(imp -> imp.endsWith("." + varName))) {
                    return null; // Direct import reference like io.print()
                }
            }

            // Handle this.method() calls
            if (firstChild.getKind().equals(Kind.THIS_EXPR.getNodeName())) {
                // If we're calling a method on 'this', check if the method exists in this class
                if (table.getMethods().contains(node.get("name"))) {
                    // Method exists in this class, validate arguments
                    validateMethodArguments(node, table, utils);
                    return null;
                }
                // If method doesn't exist in this class, check if it might be in superclass
                if (!table.getSuper().isEmpty()) {
                    return null; // Assume it's defined in superclass
                }
            }

            //assume that the method belongs to the super class
            if (!table.getSuper().isEmpty() &&
                (firstChildType != null && firstChildType.getName().equals(table.getClassName())) &&
                (!table.getMethods().contains(node.get("name")))) {
                return null;
            }
        }

        // If we got here, it's a method call on this class, so validate it
        validateMethodArguments(node, table, utils);
        return null;
    }

    private void validateMethodArguments(JmmNode node, SymbolTable table, TypeUtils utils) {
        var arguments = table.getParameters(node.get("name"));

        if (arguments == null) {
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    "Method " + node.get("name") + " does not exist",
                    null
            );
            addReport(report);
            return;
        }

        // Check number of arguments (unless last parameter is a vararg)
        if ((arguments.size() != node.getChildren().size() - 1) &&
            (arguments.isEmpty() || !arguments.getLast().getType().getName().equals("int vararg"))) {
            String message = "Wrong number of arguments: " + (node.getChildren().size() - 1) + ". Expected: " + arguments.size();
            Report report = Report.newError(
                    Stage.SEMANTIC,
                    node.getLine(),
                    node.getColumn(),
                    message,
                    null
            );
            addReport(report);
            return;
        }

        // Validate argument types
        for (int i = 1; i < node.getChildren().size(); i++) {
            var child = node.getChild(i);
            Type childType = utils.getExprType(child);
            if (childType == null) continue; // Skip if we can't determine type

            Symbol argType;
            //check if I is bigger than the argument list (only useful in varargs)
            if (arguments.size() <= i) {
                // For varargs, use the last parameter
                argType = arguments.getLast();
            }
            else {
                argType = arguments.get(i - 1);
            }

            // Special case for int[] passed to int vararg
            if (childType.getName().equals("int") && childType.isArray() &&
                    argType.getType().getName().equals("int vararg")) {
                continue;
            }

            //ignore if the parameter is vararg and the arguments passed are int
            if (childType.getName().equals("int") && !childType.isArray() &&
                argType.getType().getName().equals("int vararg")) {
                continue;
            }

            if (!childType.getName().equals(argType.getType().getName()) ||
                childType.isArray() != argType.getType().isArray()) {
                String message = String.format("Invalid argument type: %s and %s",
                                               childType.getName(), argType.getType().getName());
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
    }
}
