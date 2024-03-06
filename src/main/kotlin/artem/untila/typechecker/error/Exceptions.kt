package artem.untila.typechecker.error

sealed class TypeCheckError(name: String) : RuntimeException() {

//    abstract val description: String

    override val message: String = "ERROR_$name"
}

// Core
/* 1*/ class MissingMain : TypeCheckError("MISSING_MAIN")
/* 2*/ class UndefinedVariable : TypeCheckError("UNDEFINED_VARIABLE")
/* 3*/ class UnexpectedTypeForExpression : TypeCheckError("UNEXPECTED_TYPE_FOR_EXPRESSION")
/* 4*/ class NotAFunction : TypeCheckError("NOT_A_FUNCTION")
/* 8*/ class UnexpectedLambda : TypeCheckError("UNEXPECTED_LAMBDA")
/* 9*/ class UnexpectedTypeForParameter : TypeCheckError("UNEXPECTED_TYPE_FOR_PARAMETER")

// Tuples
/* 5*/ class NotATuple : TypeCheckError("NOT_A_TUPLE")
/*10*/ class UnexpectedTuple : TypeCheckError("UNEXPECTED_TUPLE")
/*17*/ class TupleIndexOutOfBounds : TypeCheckError("TUPLE_INDEX_OUT_OF_BOUNDS")
/*18*/ class UnexpectedTupleLength : TypeCheckError("UNEXPECTED_TUPLE_LENGTH")

// Records
/* 6*/ class NotARecord : TypeCheckError("NOT_A_RECORD")
/*11*/ class UnexpectedRecord : TypeCheckError("UNEXPECTED_RECORD")
/*14*/ class MissingRecordFields : TypeCheckError("MISSING_RECORD_FIELDS")
/*15*/ class UnexpectedRecordFields : TypeCheckError("UNEXPECTED_RECORD_FIELDS")
/*16*/ class UnexpectedFieldAccess : TypeCheckError("UNEXPECTED_FIELD_ACCESS")

// Lists
/* 7*/ class NotAList : TypeCheckError("NOT_A_LIST")
/*12*/ class UnexpectedList : TypeCheckError("UNEXPECTED_LIST")
/*20*/ class AmbiguousList : TypeCheckError("AMBIGUOUS_LIST")

// Sum types
/*13*/ class UnexpectedInjection : TypeCheckError("UNEXPECTED_INJECTION")
/*19*/ class AmbiguousSumType : TypeCheckError("AMBIGUOUS_SUM_TYPE")

// Pattern-matching
/*21*/ class IllegalEmptyMatching : TypeCheckError("ILLEGAL_EMPTY_MATCHING")
/*22*/ class NonexhaustiveMatchPatterns : TypeCheckError("NONEXHAUSTIVE_MATCH_PATTERNS")
/*23*/ class UnexpectedPatternForType : TypeCheckError("UNEXPECTED_PATTERN_FOR_TYPE")
