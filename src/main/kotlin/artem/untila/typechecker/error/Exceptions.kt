package artem.untila.typechecker.error

sealed class TypeCheckError(name: String) : RuntimeException() {

//    abstract val description: String

    override val message: String = "ERROR_$name"
}

// Language core
class MissingMain : TypeCheckError("MISSING_MAIN")
class UndefinedVariable : TypeCheckError("UNDEFINED_VARIABLE")
class UnexpectedTypeForExpression : TypeCheckError("UNEXPECTED_TYPE_FOR_EXPRESSION")
class NotAFunction : TypeCheckError("NOT_A_FUNCTION")
class UnexpectedLambda : TypeCheckError("UNEXPECTED_LAMBDA")
class UnexpectedTypeForParameter : TypeCheckError("UNEXPECTED_TYPE_FOR_PARAMETER")

// #tuples
class NotATuple : TypeCheckError("NOT_A_TUPLE")
class UnexpectedTuple : TypeCheckError("UNEXPECTED_TUPLE")
class TupleIndexOutOfBounds : TypeCheckError("TUPLE_INDEX_OUT_OF_BOUNDS")
class UnexpectedTupleLength : TypeCheckError("UNEXPECTED_TUPLE_LENGTH")

// #records
class NotARecord : TypeCheckError("NOT_A_RECORD")
class UnexpectedRecord : TypeCheckError("UNEXPECTED_RECORD")
class MissingRecordFields : TypeCheckError("MISSING_RECORD_FIELDS")
class UnexpectedRecordFields : TypeCheckError("UNEXPECTED_RECORD_FIELDS")
class UnexpectedFieldAccess : TypeCheckError("UNEXPECTED_FIELD_ACCESS")

// #lists
class NotAList : TypeCheckError("NOT_A_LIST")
class UnexpectedList : TypeCheckError("UNEXPECTED_LIST")
class AmbiguousList : TypeCheckError("AMBIGUOUS_LIST")

// #sum-types
class UnexpectedInjection : TypeCheckError("UNEXPECTED_INJECTION")
class AmbiguousSumType : TypeCheckError("AMBIGUOUS_SUM_TYPE")

// #variants
class UnexpectedVariant : TypeCheckError("UNEXPECTED_VARIANT")
class UnexpectedVariantLabel : TypeCheckError("UNEXPECTED_VARIANT_LABEL")
class AmbiguousVariantType : TypeCheckError("AMBIGUOUS_VARIANT_TYPE")

// Pattern-matching
class IllegalEmptyMatching : TypeCheckError("ILLEGAL_EMPTY_MATCHING")
class NonexhaustiveMatchPatterns : TypeCheckError("NONEXHAUSTIVE_MATCH_PATTERNS")
class UnexpectedPatternForType : TypeCheckError("UNEXPECTED_PATTERN_FOR_TYPE")
