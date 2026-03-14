package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MAP


/**
 * A [ParamBuilder] for Map type parameters
 */
internal class MapParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            logError("Map[] is not supported")
        } else {
            if (paramType != ParamType.OUT) {
                methodBuilder.addStatement("$DATA.writeMap(" + param.simpleName + " as %T<*, *>?)", MAP)
            }
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            logError("Map[] is not supported")
        } else {
            methodBuilder.addStatement("$REPLY.writeMap($RESULT as %T<*, *>?)", MAP)
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        methodBuilder.addStatement("$REPLY.writeMap($paramName as %T<*, *>?)", MAP)
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultType = methodType.getReturnAsKotlinType()
        val resultKSType = methodType.getReturnAsKSType()
        if (resultKSType.isArrayType()) {
            logError("Map[] is not supported")
        } else {
            methodBuilder.addStatement("$RESULT = $REPLY.readHashMap(javaClass.getClassLoader()) as %T", resultType)
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            logError("Map[] is not supported")
        } else {
            if (paramType == ParamType.OUT) {
                methodBuilder.addStatement("$paramName = mutableMapOf()")
            } else {
                methodBuilder.addStatement("$paramName = $DATA.readHashMap(javaClass.getClassLoader()) as %T", param.asKotlinType())
            }
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
            }
            methodBuilder.addStatement("${param.simpleName}.clear()")
            methodBuilder.addStatement("$REPLY.readMap(" + param.simpleName + " as %T<*, *>, javaClass.getClassLoader())", MAP)
            if (param.isNullable()) {
                methodBuilder.endControlFlow()
            }
        }
    }
}
