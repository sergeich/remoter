package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.FunSpec


/**
 * A [ParamBuilder] for List of Parceler type parameters
 */
internal class ListOfParcelerParamBuilder(
    private val genericType: KSClassDeclaration,
    remoterInterfaceElement: KSClassDeclaration,
    bindingManager: KBindingManager
) : ParamBuilder(remoterInterfaceElement, bindingManager) {

    override fun writeParamsToProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (param.asType().isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (paramType != ParamType.OUT) {
                if (param.isNullable()) {
                    methodBuilder.beginControlFlow("if (" + param.simpleName + " != null)")
                    methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".size)")
                    methodBuilder.beginControlFlow("for(__r_item in " + param.simpleName + " )")
                    methodBuilder.addStatement("val pClass = getParcelerClass(__r_item)")
                    methodBuilder.addStatement("$DATA.writeString(pClass?.getName())")
                    methodBuilder.addStatement("org.parceler.Parcels.wrap(pClass, __r_item).writeToParcel($DATA, 0)")
                    methodBuilder.endControlFlow()
                    methodBuilder.endControlFlow()
                    methodBuilder.beginControlFlow("else")
                    methodBuilder.addStatement("$DATA.writeInt(-1)")
                    methodBuilder.endControlFlow()
                } else {
                    methodBuilder.addStatement("$DATA.writeInt(" + param.simpleName + ".size)")
                    methodBuilder.beginControlFlow("for(__r_item in " + param.simpleName + " )")
                    methodBuilder.addStatement("val pClass = getParcelerClass(__r_item)")
                    methodBuilder.addStatement("$DATA.writeString(pClass?.getName())")
                    methodBuilder.addStatement("org.parceler.Parcels.wrap(pClass, __r_item).writeToParcel($DATA, 0)")
                    methodBuilder.endControlFlow()
                }
            }
        }
    }

    override fun readResultsFromStub(methodElement: KSFunctionDeclaration, resultType: KSType, methodBuilder: FunSpec.Builder) {
        if (resultType.isArrayType()) {
            logError("List[] is not supported")
        } else {
            methodBuilder.beginControlFlow("if ($RESULT != null)")
            methodBuilder.addStatement("$REPLY.writeInt($RESULT.size)")
            methodBuilder.beginControlFlow("for(item in $RESULT )")
            methodBuilder.addStatement("val pClass = getParcelerClass(item)")
            methodBuilder.addStatement("$REPLY.writeString(pClass!!.getName())")
            methodBuilder.addStatement("org.parceler.Parcels.wrap(pClass, item).writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
            methodBuilder.endControlFlow()
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            methodBuilder.addStatement("$REPLY.writeInt(-1)")
            methodBuilder.endControlFlow()
        }
    }

    override fun readOutResultsFromStub(param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        methodBuilder.beginControlFlow("if ($paramName != null)")
        methodBuilder.addStatement("$REPLY.writeInt($paramName.size)")
        methodBuilder.beginControlFlow("for(item in $paramName )")
        methodBuilder.addStatement("val pClass = getParcelerClass(item)")
        methodBuilder.addStatement("$REPLY.writeString(pClass!!.getName())")
        methodBuilder.addStatement("org.parceler.Parcels.wrap(pClass, item).writeToParcel($REPLY, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)")
        methodBuilder.endControlFlow()
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("$REPLY.writeInt(-1)")
        methodBuilder.endControlFlow()
    }

    override fun readResultsFromProxy(methodType: KSFunctionDeclaration, methodBuilder: FunSpec.Builder) {
        val resultKSType = methodType.getReturnAsKSType()
        val resultType = methodType.getReturnAsKotlinType()
        if (resultKSType.isArrayType()) {
            logError("List[] is not supported")
        } else {
            methodBuilder.addStatement("val _size_result = $REPLY.readInt()")
            methodBuilder.beginControlFlow("if(_size_result >= 0)")
            // Build into a concrete MutableList<T> to avoid out-projection preventing add()
            methodBuilder.addStatement("val __tmp_result = mutableListOf<%T>()", genericType.asKotlinType())
            methodBuilder.beginControlFlow("for(i in 0 until _size_result) ")
            methodBuilder.addStatement("__tmp_result.add(getParcelerObject($REPLY.readString(), $REPLY) as %T)", genericType.asKotlinType())
            methodBuilder.endControlFlow()
            methodBuilder.addStatement("$RESULT = __tmp_result")
            methodBuilder.endControlFlow()
            methodBuilder.beginControlFlow("else")
            if (resultType.isNullable) {
                methodBuilder.addStatement("$RESULT = null")
            } else {
                methodBuilder.addStatement("$RESULT = mutableListOf()")
            }
            methodBuilder.endControlFlow()
        }
    }

    override fun writeParamsToStub(methodType: KSFunctionDeclaration, param: KSValueParameter, paramType: ParamType, paramName: String, methodBuilder: FunSpec.Builder) {
        // Declare as MutableList<T> (not out-projected) so add() is callable
        methodBuilder.addStatement("var $paramName: MutableList<%T> = mutableListOf()", genericType.asKotlinType())
        if (param.asType().isArrayType()) {
            logError("List[] is not supported")
        } else {
            if (paramType != ParamType.OUT) {
                methodBuilder.addStatement("val size_$paramName = $DATA.readInt()")
                methodBuilder.beginControlFlow("if (size_$paramName >=0)")
                methodBuilder.addStatement("$paramName = mutableListOf()")
                methodBuilder.beginControlFlow("for(i in 0 until size_$paramName)")
                methodBuilder.addStatement("$paramName.add(getParcelerObject($DATA.readString(), $DATA) as %T )", genericType.asKotlinType())
                methodBuilder.endControlFlow()
                methodBuilder.endControlFlow()
                methodBuilder.beginControlFlow("else")
                if (!param.isNullable()) {
                    methodBuilder.addStatement("$paramName = mutableListOf()")
                }
                methodBuilder.endControlFlow()
            }
        }
    }

    override fun readOutParamsFromProxy(param: KSValueParameter, paramType: ParamType, methodBuilder: FunSpec.Builder) {
        if (paramType != ParamType.IN) {
            val n = param.simpleName
            methodBuilder.addStatement("val _size_$n = $REPLY.readInt()")
            methodBuilder.beginControlFlow("if(_size_$n >= 0)")
            // Cast to MutableList<T> to avoid out-projection preventing add()
            methodBuilder.addStatement("val __typed_$n = $n as? MutableList<%T>", genericType.asKotlinType())
            methodBuilder.addStatement("__typed_$n?.clear()")
            methodBuilder.beginControlFlow("for(i in 0 until _size_$n)")
            methodBuilder.addStatement("__typed_$n?.add(getParcelerObject($REPLY.readString(), $REPLY) as %T)", genericType.asKotlinType())
            methodBuilder.endControlFlow()
            methodBuilder.endControlFlow()
        }
    }
}
