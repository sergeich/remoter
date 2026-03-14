package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for List type parameters
 */
internal class ListParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (paramType != ParamType.OUT) {
                if (isListOfStrings(param.asType())) {
                    methodBuilder.addStatement("$DATA.writeStringList(" + param.simpleName + ")")
                } else {
                    methodBuilder.addStatement("$DATA.writeList(" + param.simpleName + ")")
                }
            }
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (isListOfStrings(resultType)) {
                methodBuilder.addStatement("$REPLY.writeStringList($RESULT)")
            } else {
                methodBuilder.addStatement("$REPLY.writeList($RESULT)")
            }
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (isListOfStrings(param.asType())) {
            methodBuilder.addStatement("$REPLY.writeStringList($paramName)")
        } else {
            methodBuilder.addStatement("$REPLY.writeList($paramName)")
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultType = methodType.getReturnAsKotlinType()
        val resultKSType = methodType.getReturnAsKSType()
        if (resultKSType.isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (isListOfStrings(resultKSType)) {
                methodBuilder.addStatement("$RESULT = $REPLY.createStringArrayList() as %T ", resultType)
            } else {
                methodBuilder.addStatement("$RESULT = $REPLY.readArrayList(javaClass.getClassLoader()) as %T", resultType)
            }
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (paramType == ParamType.OUT) {
                methodBuilder.addStatement("$paramName = mutableListOf()")
            } else {
                val suffix = if (param.isNullable()) "" else "!!"
                if (isListOfStrings(param.asType())) {
                    methodBuilder.addStatement("$paramName = $DATA.createStringArrayList()$suffix")
                } else {
                    methodBuilder.addStatement("$paramName = $DATA.readArrayList(javaClass.getClassLoader())$suffix")
                }
            }
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
            }
            if (isListOfStrings(param.asType())) {
                methodBuilder.addStatement("$REPLY.readStringList(" + param.simpleName + ")")
            } else {
                methodBuilder.addStatement("$REPLY.readList(" + param.simpleName + ", javaClass.getClassLoader())")
            }
            if (param.isNullable()) {
                methodBuilder.endControlFlow()
            }
        }
    }

    private fun isListOfStrings(ksType: KSType): Boolean {
        val decl = ksType.declaration
        if (decl.qualifiedName?.asString() != "kotlin.collections.List") return false
        val argType = ksType.arguments.firstOrNull()?.type?.resolve() ?: return false
        return argType.declaration.qualifiedName?.asString() == "kotlin.String"
    }
}
