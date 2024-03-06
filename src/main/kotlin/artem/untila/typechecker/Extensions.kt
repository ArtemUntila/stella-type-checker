package artem.untila.typechecker

import StellaParser.ExprContext
import artem.untila.typechecker.types.StellaType
import kotlin.properties.Delegates

var ExprContext.variableContext: VariableContext by Delegates.notNull()
var ExprContext.expectedType: StellaType by Delegates.notNull()