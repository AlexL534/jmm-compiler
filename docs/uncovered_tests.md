# Uncovered Semantic Analysis Tests

## Class and Method Related Tests
1. **classNotImported** - No checks for classes being imported before use
2. **callToUndeclaredMethod** - No method existence validation
3. **callToMethodAssumedInExtends** - No inheritance-based method lookup
4. **callToMethodAssumedInImport** - No support for methods in imported classes
5. **incompatibleArguments** - No checking of method argument types
6. **incompatibleReturn** - No validation that returned values match method return type

## Array Operation Tests
7. **arrayAccessOnInt** - No validation that array access is performed on array types
8. **arrayIndexNotInt** - No checking that array indices are integers
9. **arrayInit** - No validation of array initialization
10. **arrayInitWrong1** - No checking for array element type compatibility
11. **arrayInitWrong2** - No checking for array element type compatibility

## Object Assignment Tests
12. **objectAssignmentFail** - Current implementation always returns false for subtype checking
13. **objectAssignmentPassExtends** - No class hierarchy analysis for assignment compatibility
14. **objectAssignmentPassImports** - No import relationship checking for assignments

## Control Flow Tests
15. **intInIfCondition** - No validation that if conditions are boolean expressions
16. **arrayInWhileCondition** - No validation that while conditions are boolean expressions

## Varargs Tests
17. **varargs** - No support for variable argument parameters
18. **varargsWrong** - No validation of varargs parameter usage

## Import Tests
19. **assumeArguments** - No checking or assumptions about imported method signatures
