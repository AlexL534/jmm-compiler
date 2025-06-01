package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import static pt.up.fe.comp2025.ast.Kind.BINARY_EXPR;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;
    private String currentMethod = "";

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public void setCurrentMethod(String currentMethod) {
        this.currentMethod = currentMethod;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newBooleanType() {return new Type("boolean", false);}

    public static Type newVoidType() {return new Type("void", false);}

    public static Type newStringArrayType() {return new Type("String", true);}

    public static Type convertType(JmmNode typeNode) {

        var name = typeNode.get("value");
        var isArray = typeNode.getKind().equals("ArrayType");

        if(!isArray) {
            isArray = typeNode.getKind().equals("VarArgType");

            if(isArray) {
                name += " vararg";
            }
        }

        return new Type(name, isArray);
    }

    public boolean isValidType(Type type) {
        var name = type.getName();
        var isarray = type.isArray();
        if(isarray) {
            return name.equals("int");
        }
        else{
            var imports = table.getImports();

            for(var imp : imports){
                var imps = imp.split("\\.");
                if(imps[imps.length - 1].strip().equals(name)) {
                    return true;
                }
            }

            return name.equals("int") ||
                    name.equals("boolean") || name.equals("String") ||
                    name.equals(table.getClassName()) || name.equals(table.getSuper());
        }
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {

        String type = "unknown";
        boolean isArray = false;

        if(Kind.INTEGER_LITERAL.check(expr)){
            type = "int";
        }
        else if(Kind.BOOLEAN_LITERAL.check(expr)){
            type = "boolean";
        }
        else if(Kind.VAR_REF_EXPR.check(expr) || Kind.VAR_DECL.check(expr)) {
            for (Symbol field : table.getFields()) {
                //The main method is static so it cannot access class fields
                if (field.getName().equals(expr.get("name")) && (currentMethod == null || !currentMethod.equals("main"))) {
                    type = field.getType().getName();
                    isArray = field.getType().isArray();
                }
            }
            var parameters = table.getParameters(currentMethod);
            if(parameters != null) {
                // Var is a parameter
                if (parameters.stream().anyMatch(param -> param.getName().equals(expr.get("name")))) {
                    for (Symbol param : parameters) {
                        if (param.getName().equals(expr.get("name"))) {
                            type = param.getType().getName();
                            isArray = param.getType().isArray();

                        }
                    }
                }
            }

            // Var is a declared variable
            var locals = table.getLocalVariables(currentMethod);
            if(locals != null) {
                if (locals.stream()
                        .anyMatch(varDecl -> varDecl.getName().equals(expr.get("name")))) {
                    for (Symbol varDecl : locals) {
                        if (varDecl.getName().equals(expr.get("name"))) {
                            type = varDecl.getType().getName();
                            isArray = varDecl.getType().isArray();
                        }
                    }

                }
            }

        }
        else if(Kind.METHOD_CALL.check(expr)){
            var methodType = table.getReturnType(expr.get("name"));
            if(methodType == null){
                return null;
            }
            type = table.getReturnType(expr.get("name")).getName();
            isArray = table.getReturnType(expr.get("name")).isArray();
        }
        else if(Kind.FIELD_ACCESS.check(expr)){
            type = "int";
            isArray = false;
        }
        else if(Kind.ARRAY_CREATION.check(expr)){
            type = "int";
            isArray = true;
        }
        else if(Kind.ARRAY_SUBSCRIPT.check(expr)){
            type = "int";
            isArray = false;
        }
        else if(Kind.ARRAY_LITERAL.check(expr)){
            type = "int";
            isArray = true;
        }
        else if(Kind.OBJECT_CREATION.check(expr)){
            type = expr.get("name");
            isArray = false;
        }
        else if(Kind.THIS_EXPR.check(expr)){
            type = table.getClassName();
            isArray = false;
        }
        // Handle binary expressions like comparisons
        else if (BINARY_EXPR.check(expr)) {
            String op = expr.get("op");
            // Logical and comparison operators return boolean
            if (op.matches("&&|\\|\\||==|!=|<|>|<=|>=")) {
                return new Type("boolean", false);
            } else {
                return new Type("int", false);  // Arithmetic operations
            }
        }
        else{
            return this.getExprType(expr.getChild(0));
        }



        return new Type(type, isArray);
    }
}
