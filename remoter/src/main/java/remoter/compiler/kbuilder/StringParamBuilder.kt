package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for String type parameters
 */
internal class StringParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.isVararg) {
            methodBuilder.addStatement("$DATA.writeInt(${param.simpleName}.size)")
            methodBuilder.beginControlFlow("for (___remoter_s in ${param.simpleName})")
            methodBuilder.addStatement("$DATA.writeString(___remoter_s)")
            methodBuilder.endControlFlow()
        } else if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeArrayOutParamsToProxy(param, methodBuilder)
            } else {
                methodBuilder.addStatement("$DATA.writeStringArray(" + param.simpleName + ")")
            }
        } else {
            methodBuilder.addStatement("$DATA.writeString(" + param.simpleName + ")")
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeStringArray($RESULT)")
        } else {
            methodBuilder.addStatement("$REPLY.writeString($RESULT)")
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        val resultType = methodType.getReturnAsKotlinType()
        if (resultKSType.isArrayType()) {
            methodBuilder.addStatement("$RESULT = $REPLY.createStringArray()")
        } else {
            if (resultType.isNullable) {
                methodBuilder.addStatement("$RESULT = $REPLY.readString()")
            } else {
                methodBuilder.addStatement("$RESULT = $REPLY.readString()!!")
            }
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeStringArray($paramName)")
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (param.isVararg) {
            methodBuilder.addStatement("val ${paramName}_size = $DATA.readInt()")
            methodBuilder.addStatement("var $paramName = Array(${paramName}_size) { $DATA.readString() }")
        } else {
            super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
            if (param.asType().isArrayType()) {
                if (paramType == ParamType.OUT) {
                    writeOutParamsToStub(param, paramType, paramName, methodBuilder)
                } else {
                    if (param.isNullable()) {
                        methodBuilder.addStatement("$paramName = $DATA.createStringArray()")
                    } else {
                        methodBuilder.addStatement("$paramName = $DATA.createStringArray()!!")
                    }
                }
            } else {
                if (param.isNullable()) {
                    methodBuilder.addStatement("$paramName = $DATA.readString()")
                } else {
                    methodBuilder.addStatement("$paramName = $DATA.readString()!!")
                }
            }
        }
    }

    /**
     * Called to generate code to write @[remoter.annotations.ParamOut] params for stub
     */
    override fun writeOutParamsToStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            methodBuilder.addStatement("val " + paramName + "_length = $DATA.readInt()")
            methodBuilder.beginControlFlow("if (" + paramName + "_length < 0 )")
            if (param.isNullable()) {
                methodBuilder.addStatement("$paramName = null")
            } else {
                methodBuilder.addStatement(paramName + " = " + param.asKotlinType()
                        + "(0){\"\"}")
            }
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.addStatement(paramName + " = " + param.asKotlinType().copy(false)
                    + "(" + paramName + "_length){\"\"}")
            methodBuilder.endControlFlow()
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType() && paramType != ParamType.IN) {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
            }
            methodBuilder.addStatement("$REPLY.readStringArray(" + param.simpleName + ")")
            if (param.isNullable()) {
                methodBuilder.endControlFlow()
            }
        }
    }
}
