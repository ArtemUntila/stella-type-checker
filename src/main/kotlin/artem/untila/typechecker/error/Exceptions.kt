package artem.untila.typechecker.error

import java.io.PrintStream

sealed class TypeCheckError(
    tag: String,
    description: String = "",
) : RuntimeException() {

    val errorTag = "ERROR_$tag"

    override val message: String = errorTag

    val report: String = arrayOf(
        "An error occurred during typechecking!",
        "Type Error Tag: [$errorTag]",
        description
    ).joinToString("\n")

    fun report(out: PrintStream) = out.print(report)
}

// Language core
class MissingMain : TypeCheckError(
    "MISSING_MAIN",
    "main function is not found in the program"
)

class UndefinedVariable(name: String) : TypeCheckError(
    "UNDEFINED_VARIABLE", formatted(
        "undefined variable" to name
    )
)

class UnexpectedTypeForExpression(expected: String, actual: String, expr: String) : TypeCheckError(
    "UNEXPECTED_TYPE_FOR_EXPRESSION", formatted(
        "expected type" to expected,
        "but got" to actual,
        "for expression" to expr
    )
)

class NotAFunction(actual: String, expr: String, ctx: String) : TypeCheckError(
    "NOT_A_FUNCTION", formatted(
        "expected a function type but got" to actual,
        "for the expression" to expr,
        "in the function call" to ctx
    )
)

class UnexpectedLambda(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_LAMBDA", formatted(
        "expected an expression of a non-function type" to expected,
        "but got an anonymous function" to expr
    )
)

class UnexpectedTypeForParameter(expected: String, actual: String, param: String, expr: String) : TypeCheckError(
    "UNEXPECTED_TYPE_FOR_PARAMETER", formatted(
        "expected type" to expected,
        "but got" to actual,
        "for the param" to param,
        "in function" to expr
    )
)

// #tuples
class NotATuple(expr: String, actual: String, ctx: String) : TypeCheckError(
    "NOT_A_TUPLE", formatted(
        "expected an expression of tuple type, but got expression" to expr,
        "of type" to actual,
        "in expression" to ctx
    )
)

class UnexpectedTuple(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_TUPLE", formatted(
        "expected an expression of a non-tuple type" to expected,
        "but got a tuple" to expr
    )
)

class TupleIndexOutOfBounds(n: Int, expr: String, len: Int, expected: String) : TypeCheckError(
    "TUPLE_INDEX_OUT_OF_BOUNDS", formatted(
        "unexpected access to component number" to "$n",
        "in a tuple" to expr,
        "of length $len of type" to expected
    )
)

class UnexpectedTupleLength(expLen: Int, expected: String, actLen: Int, expr: String) : TypeCheckError(
    "UNEXPECTED_TUPLE_LENGTH", formatted(
        "expected $expLen components for a tuple of type" to expected,
        "but got $actLen in tuple" to expr
    )
)

// #records
class UnexpectedRecord(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_RECORD", formatted(
        "expected an expression of a non-record type" to expected,
        "but got a record" to expr
    )
)

class DuplicateRecordFields(fields: Collection<String>, expr: String) : TypeCheckError(
    "DUPLICATE_RECORD_FIELDS", formatted(
        "duplicate record fields" to fields.joinToString(", "),
        "in record" to expr
    )
)

class UnexpectedRecordFields(fields: Collection<String>, expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_RECORD_FIELDS", formatted(
        "unexpected fields" to fields.joinToString(", "),
        "for an expected record of type" to expected,
        "in the record" to expr
    )
)

class MissingRecordFields(fields: Collection<String>, expected: String, expr: String) : TypeCheckError(
    "MISSING_RECORD_FIELDS", formatted(
        "missing fields" to fields.joinToString(", "),
        "for an expected record of type" to expected,
        "in the record" to expr
    )
)

class NotARecord(actual: String, expr: String, ctx: String) : TypeCheckError(
    "NOT_A_RECORD", formatted(
        "expected a record type but got" to actual,
        "for the expression" to expr,
        "in the expression" to ctx
    )
)

class UnexpectedFieldAccess(field: String, expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_FIELD_ACCESS", formatted(
        "unexpected access to field" to field,
        "in a record of type" to expected,
        "in the expression" to expr
    )
)

// #lists
class NotAList(actual: String, expr: String, ctx: String) : TypeCheckError(
    "NOT_A_LIST", formatted(
        "expected a list type but got" to actual,
        "for the expression" to expr,
        "in the expression" to ctx
    )
)

class UnexpectedList(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_LIST", formatted(
        "expected an expression of a non-list type" to expected,
        "but got a list" to expr
    )
)

class AmbiguousListType : TypeCheckError(
    "AMBIGUOUS_LIST_TYPE",
    "type inference of empty lists is not supported (use type ascriptions)"
)

// #sum-types
class UnexpectedInjection(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_INJECTION", formatted(
        "expected an expression of a non-sum type" to expected,
        "but got an injection into a sum type" to expr
    )
)

class AmbiguousSumType : TypeCheckError(
    "AMBIGUOUS_SUM_TYPE",
    "type inference for sum types is not supported (use type ascriptions)"
)

// #variants
class UnexpectedVariant(expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_VARIANT", formatted(
        "expected an expression of a non-variant type" to expected,
        "but got a variant" to expr
    )
)

class UnexpectedVariantLabel(label: String, expected: String, expr: String) : TypeCheckError(
    "UNEXPECTED_VARIANT_LABEL", formatted(
        "unexpected label" to label,
        "for a variant type" to expected,
        "in variant expression" to expr
    )
)

class AmbiguousVariantType : TypeCheckError(
    "AMBIGUOUS_VARIANT_TYPE",
    "type inference for variants is not supported (use type ascriptions)"
)

// Pattern-matching
class IllegalEmptyMatching(expr: String) : TypeCheckError(
    "ILLEGAL_EMPTY_MATCHING", formatted (
        "illegal empty matching\nin expression" to expr
    )
)

class NonexhaustiveMatchPatterns(expr: String, patterns: Collection<String>) : TypeCheckError(
    "NONEXHAUSTIVE_MATCH_PATTERNS", formatted(
        "non-exhaustive pattern matches\nwhen matching on expression" to expr,
        "at least the following patterns are not matched:" to patterns.joinToString("\n")
    )
)

class UnexpectedPatternForType(pattern: String, expected: String) : TypeCheckError(
    "UNEXPECTED_PATTERN_FOR_TYPE", formatted(
        "unexpected pattern" to pattern,
        "when pattern matching is expected for type" to expected
    )
)

// #nullary- and #multiparameter-functions
class IncorrectArityOfMain : TypeCheckError(
    "INCORRECT_ARITY_OF_MAIN",
    "main function must have exactly one parameter"
)

class IncorrectNumberOfArguments(n: Int, expr: String, expected: String, m: Int, ctx: String) : TypeCheckError(
    "INCORRECT_NUMBER_OF_ARGUMENTS", formatted(
        "expected $n arguments for the function" to expr,
        "of type" to expected,
        "but got $m arguments in the function call" to ctx
    )
)

class UnexpectedNumberOfParametersInLambda(n: Int, expected: String, m: Int, expr: String) : TypeCheckError(
    "UNEXPECTED_NUMBER_OF_PARAMETERS_IN_LAMBDA", formatted(
        "expected $n parameters for a function type" to expected,
        "but got $m parameters in function" to expr
    )
)

// #universal-types
class NotAGenericFunction(actual: String, expr: String, ctx: String) : TypeCheckError(
    "NOT_A_GENERIC_FUNCTION", formatted(
        "expected a universal type but got" to actual,
        "for the expression" to expr,
        "in the type application" to ctx
    )
)

class IncorrectNumberOfTypeArguments(n: Int, expr: String, m: Int, ctx: String) : TypeCheckError(
    "INCORRECT_NUMBER_OF_TYPE_ARGUMENTS", formatted(
        "expected $n type arguments\nfor the expression" to expr,
        "but got $m type arguments\nin the application" to ctx
    )
)

class UndefinedTypeVariable(name: String) : TypeCheckError(
    "UNDEFINED_TYPE_VARIABLE",
    "undefined type variable $name"
)
