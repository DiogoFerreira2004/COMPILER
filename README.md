# FEUP - COMPILERS - 2024/2025
> Curricular Unit: COMP - [Compiladores](https://sigarra.up.pt/feup/pt/ucurr_geral.ficha_uc_view?pv_ocorrencia_id=541891)

## 3rd Year - 2nd Semester Project

### Brief description:

This compiler project implements a complete compilation pipeline featuring advanced optimization techniques at multiple levels. Built as part of the Compilers course, it transforms source code through Abstract Syntax Tree (AST) optimizations, OLLIR (Optimized Low-Level Intermediate Representation) generation, register allocation, and finally produces efficient JVM bytecode using Jasmin.

The compiler includes sophisticated optimization strategies such as constant propagation and folding, algebraic identity recognition, dead code elimination, and loop-aware optimizations. It features an intelligent register allocation system using graph coloring with the DSatur algorithm, supporting variable spilling when registers are insufficient. The system also implements live variable analysis, interference graph construction, and generates optimized JVM instructions with specialized handling for method invocations, array operations, and control flow structures.

Key features include support for varargs methods, efficient constant loading, increment optimization detection, and automatic calculation of stack and local variable limits. The project demonstrates practical application of compiler theory concepts including data flow analysis, register allocation algorithms, and code generation optimization techniques.

We hope you find it useful!

## Developed by

- **Álvaro Luís Dias Amaral Alvim Torres** - up202208954@up.pt  
- **Diogo Miguel Fernandes Ferreira** - up202205295@up.pt  
- **Tomás Ferreira de Oliveira** - up202208415@up.pt

*All members contributed equally to the entirety of the project development.*

---

## Table of Contents

- [CP2 - AST Level Optimizations](#cp2---ast-level-optimizations)
- [OLLIR Optimizations](#ollir-optimizations)
- [Live Variable Analysis and Interference Graph](#live-variable-analysis-and-interference-graph)
- [Register Allocation](#register-allocation)
- [Special Case Optimizations](#special-case-optimizations)
- [CP3 - Jasmin Code Generation](#cp3---jasmin-code-generation)
- [JVM Instruction Optimizations](#jvm-instruction-optimizations)
- [Stack and Local Variable Analysis](#stack-and-local-variable-analysis)
- [Type Management and Descriptors](#type-management-and-descriptors)
- [Control Flow and Labels](#control-flow-and-labels)
- [Special Case Handling](#special-case-handling)

---

## CP2 - AST Level Optimizations

### Constant Propagation and Folding

- **Constant Propagation**: Identifies variables with statically known values and replaces their occurrences with effective constants
- **Constant Folding**: Evaluates expressions with constant values at compile time and replaces them with their results
- **Loop-Aware Optimization**: Special treatment for variables modified within loops to preserve correct semantics
- **Multi-Assignment Variable Tracking**: Detects variables assigned in different execution paths (e.g., if-else branches)

### Expression Optimization

- **Algebraic Identities**: Recognizes and optimizes patterns like multiplication by 0/1, addition of 0, subtraction of 0, among others
- **Relational Expression Evaluation**: Resolves constant comparisons (like 5 < 10) during compilation
- **Boolean Expression Simplification**: Optimizes expressions like AND/OR when one operand is constant
- **Dead Code Elimination**: Removes code that will never be executed (e.g., after always-false conditions)

### Varargs and Special Handling

- **Varargs Treatment**: Transforms varargs parameter syntax into appropriate array operations throughout the AST

---

## OLLIR Optimizations

### Code Structure and Formatting

- **Import Format Correction**: Fixes common syntax issues in import declarations
- **Element Order Application**: Ensures fields, constructors, and methods appear in correct order
- **Method Invocation Correction**: Adds missing method invocation instructions when necessary
- **Method Body Indentation**: Improves readability of generated OLLIR code

### Control Flow and Efficiency

- **Sequential Label Management**: Uses global sequential counter to ensure unique labels
- **Expression Result Caching**: Avoids recalculations during code generation
- **Consistent Control Structure Formatting**: Applies specific rules for if-then-else and loops

---

## Live Variable Analysis and Interference Graph

### Data Flow Analysis

- **DEF/USE Set Calculation**: Identifies where variables are defined and used
- **In/Out Iteration to Fixed Point**: Data flow analysis algorithm to determine variable lifetime
- **Live Interval Construction**: Maps instruction points where each variable is live

### Interference Management

- **Interference Graph Construction**: Identifies which variables cannot share registers
- **Implicit Interference Handling**: Identification of interferences not obvious in source code
- **Graph Validation and Correction**: Ensures symmetry and consistency in interference graph

---

## Register Allocation

### Graph Coloring Algorithm

- **DSatur Graph Coloring**: Implements DSatur algorithm for efficient register allocation
- **Copy Coalescing**: Merges variables in copy instructions when their live ranges don't overlap

### Allocation Modes

- **Mode `-r=-1`**: Uses default number of registers (no optimization)
- **Mode `-r=0`**: Optimizes to use minimum number of registers
- **Mode `-r=n` (n > 0)**: Uses at most n registers with variable spilling when necessary

### Advanced Features

- **Variable Spilling Management**: When insufficient registers, some variables share registers
- **Variable Prioritization**: Assigns registers first to variables with highest interference count
- **Unused Variable Handling**: Allows unused variables to share registers

---

## Special Case Optimizations

### Parameter and Method Handling

- **Special Parameter Treatment**: Ensures `this` is in register 0 and other parameters have dedicated registers
- **Temporary Variable Optimization**: Prioritizes register allocation for regular variables over temporary ones
- **Switch Pattern Detection**: Optimized treatment of cascading if-else structures
- **Main Method Optimization**: Special register allocation for main method

### Advanced Features

- **Emergency Allocation**: Safety system to guarantee allocation when normal methods fail
- **Label Normalization**: Standardization of label naming for control structures
- **Short-Circuit for Logical Operators**: Efficient evaluation implementation for AND/OR
- **Multi-Assigned Variable Return Management**: Preserves original variables in return instructions

---

## CP3 - Jasmin Code Generation

### Class Structure Generation

- **Class Declarations**: Generates class, superclass, and default constructor declarations
- **Fields and Methods**: Creates complete field and method declarations with appropriate modifiers
- **Method Signatures**: Generates correct signatures including parameters and return types
- **Varargs Support**: Automatically detects and handles methods with variable parameters

### Method Invocation and Operations

- **Method Invocations**: Implements static, virtual, and special calls with proper stack management
- **Array Operations**: Complete support for array creation, access, assignment, and length operations
- **Object Management**: Object creation and field access/assignment with type validation

---

## JVM Instruction Optimizations

### Specialized Instructions

- **Optimized Loading Instructions**: Uses specialized forms like `iload_0`, `aload_1`, `istore_2` when possible
- **Efficient Constant Loading**: Automatically selects between `iconst_0`, `bipush`, `sipush`, and `ldc` based on value
- **Increment Optimization**: Detects `i = i + 1` patterns and replaces with more efficient `iinc` instruction
- **Zero Comparisons**: Uses direct comparison instructions (`iflt`, `ifne`) instead of two-operand comparisons

### Cross-Instruction Analysis

- **Cross-Instruction Optimization**: Identifies and optimizes patterns spanning multiple OLLIR instructions
- **Intelligent Constant Management**: Special handling for boolean values and numeric constants

---

## Stack and Local Variable Analysis

### Limit Calculations

- **`.limit stack` Calculation**: Determines maximum stack size needed for each method
- **`.limit locals` Calculation**: Calculates exact number of local variables required
- **Method Call Analysis**: Considers arguments and return types in stack calculation
- **Array Operations Analysis**: Specific analysis for operations requiring multiple stack values

### Special Method Handling

- **Main Method Optimization**: Special treatment for main methods with single return instruction
- **Limit Validation**: Ensures calculated values are within valid JVM limits

---

## Type Management and Descriptors

### Type System

- **JVM Type Descriptors**: Automatic conversion from OLLIR types to Java bytecode descriptors
- **String Array Support**: Specialized handling for string arrays (`[Ljava/lang/String;`)
- **Primitive Type Identification**: Recognition and conversion of void, integer, and boolean types
- **Class Name Formatting**: Conversion from dot notation to slash format (/)
- **Type Validation**: Consistency checking between OLLIR types and generated descriptors

---

## Control Flow and Labels

### Flow Control

- **Automatic Label Management**: Sequential generation of unique labels for control structures
- **Optimized Comparisons**: Efficient implementation of relational operations with label generation
- **Conditional and Unconditional Jumps**: Complete support for if-else, loops, and goto instructions
- **Label Positioning**: Correct label placement in generated code to maintain semantics
- **Unary Operations**: Implementation of logical and arithmetic negation

---

## Special Case Handling

### Advanced Features

- **Array Length**: Automatic detection and code generation for `array.length` operations
- **Reflection Introspection**: Uses Java reflection to analyze method properties when necessary
- **Varargs Treatment**: Varargs method identification through multiple strategies
- **Generated Code Validation**: Verifies Jasmin code contains essential elements (class, methods, etc.)
- **Error Recovery**: Fallback mechanisms to ensure code generation even with partial problems
- **Arithmetic Operation Optimization**: Specialized handling for operations like AND, OR, ADD, SUB, MUL, DIV

---

## Project Structure

The compiler is organized into several key phases:

1. **Frontend**: Lexical and syntactic analysis
2. **AST Optimization**: High-level optimizations on the abstract syntax tree
3. **OLLIR Generation**: Intermediate representation creation with optimizations
4. **Register Allocation**: Efficient register assignment using graph coloring
5. **Code Generation**: Final Jasmin bytecode generation with JVM optimizations

Each phase includes comprehensive error handling and optimization strategies to produce efficient and correct bytecode output.

---

## Usage

The compiler supports various optimization levels and register allocation strategies through command-line parameters. Key options include:

- `-r=-1`: Default register allocation
- `-r=0`: Minimum register optimization
- `-r=n`: Constrained register allocation with spilling

The system automatically applies all AST and OLLIR optimizations, with intelligent fallback mechanisms to ensure compilation success even in edge cases.
