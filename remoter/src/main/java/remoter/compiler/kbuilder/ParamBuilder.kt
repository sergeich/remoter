package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [KRemoterBuilder] for a specific type of a parameter
 */
internal abstract class ParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : KRemoterBuilder(remoterInterfaceElement, bindingManager) {

    companion object {
        internal val DATA = "___remoter_data"
        internal val REPLY = "___remoter_reply"
        internal val RESULT = "___remoter_result"
    }

    /**
     * Called to generate code to write the params to proxy
     */
    open fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {}

    /**
     * Called to generate code that reads results from proxy
     */
    open fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {}

    /**
     * Called to generate code that reads results from proxy for @[remoter.annotations.ParamOut] parameters
     */
    open fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {}

    /**
     * Called to generate code to write params for stub
     */
    open fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        methodBuilder.addStatement("var $paramName:%T", param.asKotlinType())
    }

    /**
     * Called to generate code to write @[remoter.annotations.ParamOut] params for stub
     */
    open fun writeOutParamsToStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            methodBuilder.addStatement("val " + paramName + "_length = $DATA.readInt()")
            methodBuilder.beginControlFlow("if (" + paramName + "_length < 0 )")
            if (param.isNullable()) {
                methodBuilder.addStatement("$paramName = null")
            } else {
                methodBuilder.addStatement(paramName + " = " + param.asKotlinType()
                        + "(0)")
            }
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.addStatement(paramName + " = " + param.asKotlinType().copy(false)
                    + "(" + paramName + "_length)")
            methodBuilder.endControlFlow()
        }
    }

    /**
     * Called to generate code that reads results from stub
     */
    open fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {}

    /**
     * Called to generate code that reads [remoter.annotations.ParamOut] results from stub
     */
    open fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {}

    /**
     * Called to generate code that writes the out params for array type
     */
    protected open fun writeArrayOutParamsToProxy(param: KSValueParameter, methodBuilder: FunSpec.Builder) {
        if (param.isNullable()) {
            methodBuilder.beginControlFlow("if (" + param.simpleName + " == null)")
            methodBuilder.addStatement("$DATA.writeInt(-1)")
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".size)")
            methodBuilder.endControlFlow()
        } else {
            methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".size)")
        }
    }

    /**
     * Represents the type of parameter
     */
    enum class ParamType {
        IN, OUT, IN_OUT
    }
}
