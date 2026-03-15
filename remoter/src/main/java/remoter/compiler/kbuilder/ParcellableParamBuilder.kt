package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for Parcellable type parameters
 */
internal class ParcellableParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                writeArrayOutParamsToProxy(param, methodBuilder)
            } else {
                methodBuilder.addStatement("$DATA.writeTypedArray(" + param.simpleName + ", 0)")
            }
        } else {
            if (paramType != ParamType.OUT) {
                if (param.isNullable()) {
                    methodBuilder.beginControlFlow("if (" + param.simpleName + " != null)")
                    methodBuilder.addStatement("$DATA.writeInt(1)")
                    methodBuilder.addStatement(param.simpleName + ".writeToParcel($DATA, 0)")
                    methodBuilder.endControlFlow()
                    methodBuilder.beginControlFlow("else")
                    methodBuilder.addStatement("$DATA.writeInt(0)")
                    methodBuilder.endControlFlow()
                } else {
                    methodBuilder.addStatement("$DATA.writeInt(1)")
                    methodBuilder.addStatement(param.simpleName + ".writeToParcel($DATA, 0)")
                }
            }
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeTypedArray($RESULT, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
        } else {
            if (methodElement.isNullable()) {
                methodBuilder.beginControlFlow("if ($RESULT != null)")
                methodBuilder.addStatement("$REPLY.writeInt(1)")
                methodBuilder.addStatement("$RESULT.writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
                methodBuilder.endControlFlow()
                methodBuilder.beginControlFlow("else")
                methodBuilder.addStatement("$REPLY.writeInt(0)")
                methodBuilder.endControlFlow()
            } else {
                methodBuilder.addStatement("$REPLY.writeInt(1)")
                methodBuilder.addStatement("$RESULT.writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
            }
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            methodBuilder.addStatement("$REPLY.writeTypedArray($paramName, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
        } else {
            if (param.asKotlinType().isNullable) {
                methodBuilder.beginControlFlow("if ($paramName != null)")
                methodBuilder.addStatement("$REPLY.writeInt(1)")
                methodBuilder.addStatement("$paramName!!.writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
                methodBuilder.endControlFlow()
                methodBuilder.beginControlFlow("else")
                methodBuilder.addStatement("$REPLY.writeInt(0)")
                methodBuilder.endControlFlow()
            } else {
                methodBuilder.addStatement("$REPLY.writeInt(1)")
                methodBuilder.addStatement("$paramName.writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
            }
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultType = methodType.getReturnAsKotlinType()
        val resultKSType = methodType.getReturnAsKSType()
        if (resultKSType.isArrayType()) {
            methodBuilder.addStatement("$RESULT = $REPLY.createTypedArray(" + getParcelableClassName(resultKSType) + ".CREATOR) as %T", resultType)
        } else {
            methodBuilder.beginControlFlow("if ($REPLY.readInt() != 0)")
            if (resultKSType.arguments.isNotEmpty()) {
                methodBuilder.addStatement("$RESULT = (" + getParcelableClassName(resultKSType) + ".CREATOR.createFromParcel($REPLY) as %T)", resultType)
            } else {
                methodBuilder.addStatement("$RESULT = " + getParcelableClassName(resultKSType) + ".CREATOR.createFromParcel($REPLY)")
            }
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            if (resultType.isNullable) {
                methodBuilder.addStatement("$RESULT = null")
            } else {
                methodBuilder.addStatement("throw %T(\"Unexpected null result\")", NullPointerException::class)
            }
            methodBuilder.endControlFlow()
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        if (param.asType().isArrayType()) {
            if (paramType == ParamType.OUT) {
                _writeOutParamsToStub(param, paramType, paramName, methodBuilder)
            } else {
                methodBuilder.addStatement(paramName + " =  $DATA.createTypedArray(" + getParcelableClassName(param.asType()) + ".CREATOR) as " + param.asKotlinType())
            }
        } else {
            if (paramType == ParamType.OUT) {
                methodBuilder.addStatement(paramName + " = " + getParcelableClassName(param.asType()) + "()")
            } else {
                methodBuilder.beginControlFlow("if ( $DATA.readInt() != 0)")
                val cast = if (param.asType().arguments.isNotEmpty()) " as " + param.asKotlinType() else ""
                methodBuilder.addStatement(paramName + " = " + getParcelableClassName(param.asType()) + ".CREATOR.createFromParcel($DATA)" + cast)
                methodBuilder.endControlFlow()
                methodBuilder.beginControlFlow("else")
                if (param.isNullable()) {
                    methodBuilder.addStatement("$paramName = null")
                } else {
                    methodBuilder.addStatement("throw %T(\"Unexpected null result\")", NullPointerException::class)
                }
                methodBuilder.endControlFlow()
            }
        }
    }

    private fun _writeOutParamsToStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            methodBuilder.addStatement("val " + paramName + "_length = $DATA.readInt()")
            methodBuilder.beginControlFlow("if (" + paramName + "_length < 0 )")
            if (param.isNullable()) {
                methodBuilder.addStatement("$paramName = null")
            } else {
                methodBuilder.beginControlFlow(paramName + " = " + param.asKotlinType()
                        + "(0)")
                methodBuilder.addStatement("%T()", param.asType().componentType().asKotlinType().copy(false))
                methodBuilder.endControlFlow()
            }
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.beginControlFlow(paramName + " = " + param.asKotlinType().copy(false)
                    + "(" + paramName + "_length)")
            methodBuilder.addStatement("%T()", param.asType().componentType().asKotlinType().copy(false))
            methodBuilder.endControlFlow()
            methodBuilder.endControlFlow()
        }
    }

    private fun getParcelableClassName(ksType: KSType): String {
        return if (!ksType.isArrayType()) {
            ksType.declaration.qualifiedName?.asString() ?: ksType.toString()
        } else {
            getParcelableClassName(ksType.componentType())
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            if (param.asType().isArrayType()) {
                if (param.isNullable()) {
                    methodBuilder.beginControlFlow("if (${param.simpleName} != null)")
                }
                methodBuilder.addStatement("$REPLY.readTypedArray(" + param.simpleName + ", " + getParcelableClassName(param.asType()) + ".CREATOR)")
                if (param.isNullable()) {
                    methodBuilder.endControlFlow()
                }
            } else {
                methodBuilder.beginControlFlow("if ($REPLY.readInt() != 0)")
                if (param.isNullable()) {
                    methodBuilder.addStatement(param.simpleName + "?.readFromParcel($REPLY)")
                } else {
                    methodBuilder.addStatement(param.simpleName + ".readFromParcel($REPLY)")
                }
                methodBuilder.endControlFlow()
            }
        }
    }
}
