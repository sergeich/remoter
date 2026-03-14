package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for long type parameters
 */
internal class LongParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeArrayOutParamsToProxy(param, methodBuilder)
            } else {
                methodBuilder.addStatement("$DATA.writeLongArray(" + param.simpleName + ")")
            }
        } else {
            methodBuilder.addStatement("$DATA.writeLong(" + param.simpleName + ")")
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeLongArray($RESULT)")
        } else {
            methodBuilder.addStatement("$REPLY.writeLong($RESULT)")
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        val resultType = methodType.getReturnAsKotlinType()
        if (resultKSType.isArrayType()) {
            val suffix = if (resultType.isNullable) "" else "!!"
            methodBuilder.addStatement("$RESULT = $REPLY.createLongArray()$suffix")
        } else {
            methodBuilder.addStatement("$RESULT = $REPLY.readLong()")
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeLongArray($paramName)")
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeOutParamsToStub(param, paramType, paramName, methodBuilder)
            } else {
                val suffix = if (param.isNullable()) "" else "!!"
                methodBuilder.addStatement("$paramName = $DATA.createLongArray()$suffix")
            }
        } else {
            methodBuilder.addStatement("$paramName = $DATA.readLong()")
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType() && paramType != ParamType.IN) {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
            }
            methodBuilder.addStatement("$REPLY.readLongArray(" + param.simpleName + ")")
            if (param.isNullable()) {
                methodBuilder.endControlFlow()
            }
        }
    }
}
