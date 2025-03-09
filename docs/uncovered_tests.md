# Uncovered Semantic Analysis Tests

This document lists semantic analysis tests that are currently not implemented or not passing in our compiler.

## Class-Related Tests

1. **classNotImported** - Missing validation to ensure classes are imported before use
2. **objectAssignmentFail** - Incomplete subtype checking implementation (currently always returns false)
3. **objectAssignmentPassExtends** - Missing class hierarchy analysis for assignment compatibility
4. **objectAssignmentPassImports** - Missing import relationship checking for assignments

## Method-Related Tests

5. **callToUndeclaredMethod** - Missing method existence validation
6. **callToMethodAssumedInExtends** - Missing inheritance-based method lookup
7. **callToMethodAssumedInImport** - Missing support for methods in imported classes
8. **incompatibleArguments** - Missing validation of method argument types
9. **incompatibleReturn** - Missing validation that returned values match method return type
10. **assumeArguments** - Missing verification of imported method signatures

## Array Operation Tests

11. **arrayAccessOnInt** - Missing validation that array access operations are performed only on array types
12. **arrayIndexNotInt** - Missing validation that array indices are integer expressions
13. **arrayInit** - Missing validation of array initialization syntax
14. **arrayInitWrong1** - Missing type compatibility checks for array elements
15. **arrayInitWrong2** - Missing type compatibility checks for array elements in complex cases

## Varargs Tests

16. **varargs** - Missing support for variable argument parameters
17. **varargsWrong** - Missing validation of varargs parameter usage rules
