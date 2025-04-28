package pt.up.fe.comp.cp1;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class SemanticAnalysisTest {

    @Test
    public void symbolTable() {

        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/SymbolTable.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void varNotDeclared() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VarNotDeclared.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void classNotImported() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ClassNotImported.jmm"));

        // Debugging output to verify reports are being generated
        System.out.println("Reports: " + result.getReports());

        TestUtils.mustFail(result);
    }

    @Test
    public void intPlusObject() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/IntPlusObject.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void boolTimesInt() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/BoolTimesInt.jmm"));

        // Debugging output to verify reports are being generated
        System.out.println("Reports: " + result.getReports());


        TestUtils.mustFail(result);
    }

    @Test
    public void arrayPlusInt() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayPlusInt.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void arrayAccessOnInt() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayAccessOnInt.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void arrayIndexNotInt() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayIndexNotInt.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void assignIntToBool() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/AssignIntToBool.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void objectAssignmentFail() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ObjectAssignmentFail.jmm"));
        //System.out.println(result.getReports());
        TestUtils.mustFail(result);
    }

    @Test
    public void objectAssignmentPassExtends() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ObjectAssignmentPassExtends.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void objectAssignmentPassImports() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ObjectAssignmentPassImports.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void intInIfCondition() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/IntInIfCondition.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void arrayInWhileCondition() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayInWhileCondition.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void callToUndeclaredMethod() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/CallToUndeclaredMethod.jmm"));
        TestUtils.mustFail(result);
    }

    @Test
    public void callToMethodAssumedInExtends() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/CallToMethodAssumedInExtends.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void callToMethodAssumedInImport() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/CallToMethodAssumedInImport.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void incompatibleArguments() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/IncompatibleArguments.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void incompatibleReturn() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/IncompatibleReturn.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void assumeArguments() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/AssumeArguments.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void varargs() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/Varargs.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void varargsWrong() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VarargsWrong.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void arrayInit() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayInit.jmm"));
        TestUtils.noErrors(result);
    }

    @Test
    public void arrayInitWrong1() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayInitWrong1.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void arrayInitWrong2() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ArrayInitWrong2.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void thisInStatic() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ThisInsideStatic.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void thisIsObject() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ThisIsObject.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void thisIsObjectExtends() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ThisIsObjectExtends.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void returnTypeCannotBeVarargs() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ReturnTypeCannotBeVarargs.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void fieldDeclarationCannotBeVarargs() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/FieldDeclarationCannotBeVarargs.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void variableDeclarationCannotBeVarargs() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VariableDeclarationCannotBeVarargs.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void multipleVarargs() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MultipleVarargs.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void varargNotLast() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VarargNotLast.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void varargValidLast() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VarargValidLast.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void varargsCompatibility() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/VarargsCompatibility.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void repeatedMethods() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/RepeatedMethods.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void repeatedLocals() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/RepeatedLocals.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void repeatedParams() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/RepeatedParams.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void repeatedFields() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/RepeatedFields.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void fieldNamedMain() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/FieldNamedMain.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainWithoutParams() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainWithoutParams.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainWithExtraParam() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainWithExtraParam.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void validMain() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ValidMain.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainWrongParamType1() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainWrongParamType1.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainWrongParamType2() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainWrongParamType2.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainWrongParamType3() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainWrongParamType3.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void mainDifferentParamName() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MainDifferentParamName.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void intArrayTest() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/IntArrayTest.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void invalidIntArrayTest() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/InvalidIntArrayTest.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void duplicateImports() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/DuplicateImports.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void validIntReturn() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ValidIntReturn.jmm"));
        TestUtils.noErrors(result);
        System.out.println(result.getReports());
    }

    @Test
    public void missingIntReturn() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MissingIntReturn.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void conditionalMissingIntReturn() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/ConditionalMissingReturn.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void wrongReturnType() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/WrongReturnType.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void missingImport() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/MissingImport.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void duplicateClassImportDifferentPackages() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/DuplicateClassImportDifferentPackages.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }

    @Test
    public void emptyReturn() {
        var result = TestUtils.analyse(SpecsIo.getResource("pt/up/fe/comp/cp1/semanticanalysis/EmptyReturn.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }
}
