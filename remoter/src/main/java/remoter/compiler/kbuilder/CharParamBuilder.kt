package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for char type parameters
 */
internal class CharParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeArrayOutParamsToProxy(param, methodBuilder)
            } else {
                methodBuilder.addStatement("$DATA.writeCharArray(" + param.simpleName + ")")
            }
        } else {
            methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".code)")
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeCharArray($RESULT)")
        } else {
            methodBuilder.addStatement("$REPLY.writeInt($RESULT.code)")
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        val resultType = methodType.getReturnAsKotlinType()
        if (resultKSType.isArrayType()) {
            val suffix = if (resultType.isNullable) "" else "!!"
            methodBuilder.addStatement("$RESULT = $REPLY.createCharArray()$suffix")
        } else {
            methodBuilder.addStatement("$RESULT = $REPLY.readInt().toChar()")
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeCharArray($paramName)")
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeOutParamsToStub(param, paramType, paramName, methodBuilder)
            } else {
                val suffix = if (param.isNullable()) "" else "!!"
                methodBuilder.addStatement("$paramName = $DATA.createCharArray()$suffix")
            }
        } else {
            methodBuilder.addStatement("$paramName = $DATA.readInt().toChar()")
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType() && paramType != ParamType.IN) {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
            }
            methodBuilder.addStatement("$REPLY.readCharArray(" + param.simpleName + ")")
            if (param.isNullable()) {
                methodBuilder.endControlFlow()
            }
        }
    }
}
