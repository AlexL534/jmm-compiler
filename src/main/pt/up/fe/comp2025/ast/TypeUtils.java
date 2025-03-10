package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.Objects;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newBooleanType() {return new Type("boolean", false);}

    public static Type newVoidType() {return new Type("void", false);}

    public static Type convertType(JmmNode typeNode) {

        // TODO: When you support new types, this must be updated
        var name = typeNode.get("value");
        var isArray = typeNode.getKind().equals("ArrayType");

        return new Type(name, isArray);
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {

        // TODO: Update when there are new types
        String type = "int";
        boolean isArray = false;

        if(Kind.INTEGER_LITERAL.check(expr)){
            type = "int";
        }
        else if(Kind.BOOLEAN_LITERAL.check(expr)){
            type = "boolean";
        }
        else if(Kind.VAR_REF_EXPR.check(expr)) {
            for (Symbol field : table.getFields()) {
                if (field.getName().equals(expr.get("name"))) {
                    type = field.getType().getName();
                    isArray = field.getType().isArray();
                }
            }

        }



        return new Type(type, isArray);
    }





}
