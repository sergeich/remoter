package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for generic type parameters
 */
internal class GenericParamBuilder(remoterInterfaceElement: KSClassDeclaration, bindingManager: KBindingManager) : ParamBuilder(remoterInterfaceElement, bindingManager) {
    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        methodBuilder.addStatement("val pClass" + param.simpleName + " = getParcelerClass(" + param.simpleName + ")")
        methodBuilder.beginControlFlow("if (pClass" + param.simpleName + " != null)")
        methodBuilder.addStatement("$DATA.writeInt(1)")
        methodBuilder.addStatement("$DATA.writeString(pClass" + param.simpleName + ".getName())")
        methodBuilder.addStatement("org.parceler.Parcels.wrap(pClass" + param.simpleName + ", " + param.simpleName + ").writeToParcel($DATA, 0)")
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("$DATA.writeInt(2)")
        methodBuilder.addStatement("$DATA.writeValue(" + param.simpleName + " )")
        methodBuilder.endControlFlow()
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        methodBuilder.addStatement("val pClassResult = getParcelerClass($RESULT)")
        methodBuilder.beginControlFlow("if (pClassResult != null)")
        methodBuilder.addStatement("$REPLY.writeInt(1)")
        methodBuilder.addStatement("$REPLY.writeString(pClassResult.getName())")
        methodBuilder.addStatement("org.parceler.Parcels.wrap(pClassResult, $RESULT).writeToParcel($REPLY, 0)")
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("$REPLY.writeInt(2)")
        methodBuilder.addStatement("$REPLY.writeValue($RESULT)")
        methodBuilder.endControlFlow()
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultType = methodType.getReturnAsKotlinType()
        methodBuilder.beginControlFlow("if ($REPLY.readInt() == 1)")
        methodBuilder.addStatement("$RESULT = getParcelerObject($REPLY.readString(), $REPLY) as %T", resultType)
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("$RESULT = $REPLY.readValue(javaClass.getClassLoader()) as %T", resultType)
        methodBuilder.endControlFlow()
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        super.writeParamsToStub(methodType, param, paramType, paramName, methodBuilder)
        methodBuilder.beginControlFlow("if ($DATA.readInt() == 1)")
        methodBuilder.addStatement("$paramName = getParcelerObject($DATA.readString(), $DATA) as %T", param.asKotlinType())
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("$paramName = $DATA.readValue(javaClass.getClassLoader()) as %T", param.asKotlinType())
        methodBuilder.endControlFlow()
    }
}
