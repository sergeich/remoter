package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for short type parameters
 */
internal class ShortParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            logError("short[] not supported, use int[]" + param.simpleName)
        } else {
            methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".toInt())")
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            logError("short[] not supported, use int[]")
        } else {
            methodBuilder.addStatement("$REPLY.writeInt($RESULT.toInt())")
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        if (resultKSType.isArrayType()) {
            logError("short[] is not supported, use int[]")
            if (methodType.isNullable()) {
                methodBuilder.addStatement("$RESULT = null")
            } else {
                methodBuilder.addStatement("$RESULT = ShortArray(0)")
            }
        } else {
            methodBuilder.addStatement("$RESULT = $REPLY.readInt().toShort()")
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            logError("short[] not supported, use int[]" + param.simpleName)
            methodBuilder.addStatement("$paramName = ShortArray(0)")
        } else {
            methodBuilder.addStatement("$paramName = $DATA.readInt().toShort()")
        }
    }
}
