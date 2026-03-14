package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec

/**
 * A [ParamBuilder] for [CharSequence] type parameters
 */
internal class CharSequenceParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            logError("CharSequence[] cannot be marshalled")
        } else {
            if (param.isNullable()) {
                methodBuilder.beginControlFlow("if(" + param.simpleName + " != null)")
                methodBuilder.addStatement("$DATA.writeInt(1)")
                methodBuilder.addStatement("android.text.TextUtils.writeToParcel(" + param.simpleName + ", $DATA, 0)")
                methodBuilder.endControlFlow()
                methodBuilder.beginControlFlow("else")
                methodBuilder.addStatement("$DATA.writeInt(0)")
                methodBuilder.endControlFlow()
            } else {
                methodBuilder.addStatement("$DATA.writeInt(1)")
                methodBuilder.addStatement("android.text.TextUtils.writeToParcel(" + param.simpleName + ", $DATA, 0)")
            }
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            logError("CharSequence[] cannot be marshalled")
        } else {
            methodBuilder.beginControlFlow("if($RESULT!= null)")
            methodBuilder.addStatement("$REPLY.writeInt(1)")
            methodBuilder.addStatement("android.text.TextUtils.writeToParcel($RESULT, $REPLY, 0)")
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.addStatement("$REPLY.writeInt(0)")
            methodBuilder.endControlFlow()
        }
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        val resultType = methodType.getReturnAsKotlinType()
        if (resultKSType.isArrayType()) {
            logError("CharSequence[] cannot be marshalled")
        } else {
            methodBuilder.beginControlFlow("if($REPLY.readInt() != 0)")
            methodBuilder.addStatement("$RESULT = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel($REPLY)")
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
            logError("CharSequence[] cannot be marshalled")
        } else {
            methodBuilder.beginControlFlow("if($DATA.readInt() != 0)")
            methodBuilder.addStatement("$paramName = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel($DATA)")
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            if (param.isNullable()) {
                methodBuilder.addStatement("$paramName = null")
            } else {
                methodBuilder.addStatement("throw %T(\"Not expecting null\")", NullPointerException::class.java)
            }
            methodBuilder.endControlFlow()
        }
    }
}
