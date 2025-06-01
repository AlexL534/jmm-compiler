# Compiler Project

Contains a reference implementation for the compiler project.

## Team Contribution
Lucas Faria - 33%  
Gonçalo Magalhães - 33%  
Alexandre Lopes - 33%

## Optimizations

Our compiler implements several optimization techniques to improve the generated code's efficiency. The optimizations can be enabled using the `-o` or `optimize` flag.

### Code Optimization Techniques

#### 1. Constant Propagation
- Tracks variable values at compile time
- Replaces variable references with their constant values when possible
- Handles method scopes and avoids propagating variables modified in conditional branches or loops
- Implemented in `ConstantPropagationVisitor.java`

##### Examples of Constant Propagation
- **Simple Propagation**: In expressions like `a = 5; b = 20 - a;`, the value of `a` is propagated into the second statement, effectively transforming it to `b = 20 - 5;`
- **Loop-Aware Propagation**: In while loops, the propagation is done carefully to avoid incorrect optimizations (see `PropWithLoop.jmm` test)
- **Method Scoped**: Propagation respects method boundaries and ensures values are correctly tracked within their appropriate scopes

#### 2. Constant Folding
- Evaluates constant expressions at compile time
- Folds operations with constant operands (e.g., `10 + 20` becomes `30`)
- Supports arithmetic operations (+, -, *, /), comparison operations (<), and boolean operations (&&, ||)
- Preserves comparison structure in loop conditions for stability
- Implemented in `ConstantFoldingVisitor.java`

##### Examples of Constant Folding
- **Simple Arithmetic**: Expressions like `a = 10 + 20;` are folded into `a = 30;`
- **Boolean Operations**: Expressions like `b = 10 < 20;` are evaluated to `b = true;`
- **Complex Expressions**: Multi-operator expressions like `a = 20 - 10 * 3 / 5;` are folded following operator precedence rules
- **Combined with Propagation**: When used together with constant propagation, more complex optimizations are achieved

#### 3. Register Allocation
- Implements graph coloring algorithm for efficient register allocation
- Provides two modes:
  - Register minimization (`-r=0`): Uses the minimum possible number of registers
  - Register limitation (`-r=N`): Uses at most N registers, with spilling when necessary
- Detects copy chains to maximize register sharing opportunities 
- Special handling for method calls to ensure correct parameter handling
- Implemented in `RegisterAllocator.java`

### Register Allocation Features

#### Live Variable Analysis
- Tracks which variables are live (will be used in the future) at each program point
- Builds interference graphs based on variable liveness
- Handles complex control structures like if-else statements and loops
- Specially handles array access operations for correct register allocation

#### Copy Chain Detection
- Identifies sequences of assignments like `a = 1; b = a; c = b;`
- Allows variables in copy chains to share registers when they don't interfere
- Transitively builds copy relations to maximize sharing opportunities
- Example: In code like `a = arg; b = a; c = b;`, all three variables might use the same register

#### Method Call Handling
- Prevents register sharing between method parameters and results
- Ensures correct register allocation for method arguments
- Preserves live variables across method calls
- Special handling for method parameters to maintain correct parameter order and register assignment

#### Register Spilling and Limitations
- When register limit is too small, calculates minimum required registers
- Reports the minimum number of registers needed for allocation
- Implements strategies for register spilling when required registers exceed available ones
- Intelligently selects which variables to spill based on usage patterns
- Handles complex expressions with many simultaneously live variables (see `regalloc_spill.jmm` test)

#### Handling Conditional Control Flow
- Correctly analyzes variable liveness across if-else branches
- Ensures proper register allocation in both branches of conditional statements
- Carefully tracks which variables are live at branch merge points

#### Array Access Optimization
- Special handling for array access operations
- Optimizes register usage for array indexes and values
- Efficiently manages registers in array initialization and traversal loops

### Optimization Management

The optimization process is coordinated by `OptimizationManager.java`, which:
- Applies constant propagation and folding iteratively until a fixed point is reached
- Ensures all possible optimizations are applied
- Integrates with register allocation to produce optimized code
- Provides command-line options for enabling/disabling specific optimizations

## Command Line Options

Our compiler supports the following optimization-related command line options:

- `-o` or `--optimize`: Enable all optimizations
- `-r=N`: Limit register allocation to N registers (0 means use minimum required)
- `--no-constant-prop`: Disable constant propagation
- `--no-constant-fold`: Disable constant folding
- `--no-reg-alloc`: Disable register allocation

## Performance Impact

The implemented optimizations significantly improve code performance:

1. **Execution Speed**: Constant folding and propagation reduce runtime calculations
2. **Code Size**: Optimized code is more compact, reducing the size of generated binaries
3. **Register Usage**: Optimal register allocation reduces memory access operations
