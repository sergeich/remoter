package remoter.compiler.kbuilder

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.jvm.throws
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import remoter.RemoterGlobalProperties
import remoter.RemoterProxyListener
import remoter.RemoterStub
import remoter.compiler.kbuilder.KClassBuilder.Companion.PROXY_SUFFIX

class KMethodBuilder(
    remoterInterfaceElement: KSClassDeclaration,
    bindingManager: KBindingManager
) : KRemoterBuilder(remoterInterfaceElement, bindingManager) {

    fun addProxyMethods(classBuilder: TypeSpec.Builder) {
        processRemoterElements(classBuilder, object : ElementVisitor {
            override fun visitElement(classBuilder: TypeSpec.Builder, member: KSFunctionDeclaration, methodIndex: Int, methodBuilder: FunSpec.Builder?) {
                addProxyMethods(classBuilder, member, methodIndex)
            }
        }, null)
        addProxyExtras(classBuilder)
        addCommonExtras(classBuilder)
    }

    fun addStubMethods(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("onTransact")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Boolean::class)
            .throws(ClassName("android.os", "RemoteException"))
            .addParameter("code", Int::class)
            .addParameter(ParamBuilder.DATA, ClassName("android.os", "Parcel"))
            .addParameter(ParamBuilder.REPLY, ClassName("android.os", "Parcel").copy(true))
            .addParameter("flags", Int::class)

        methodBuilder.beginControlFlow("try")
        methodBuilder.beginControlFlow("when (mapTransactionCode(code))")

        methodBuilder.beginControlFlow("INTERFACE_TRANSACTION -> ")
        methodBuilder.addStatement("${ParamBuilder.REPLY}?.writeString(DESCRIPTOR)")
        methodBuilder.addStatement("return true")
        methodBuilder.endControlFlow()

        methodBuilder.beginControlFlow("TRANSACTION__getStubID -> ")
        methodBuilder.addStatement("${ParamBuilder.DATA}.enforceInterface(DESCRIPTOR)")
        methodBuilder.addStatement("${ParamBuilder.REPLY}?.writeNoException()")
        methodBuilder.addStatement("${ParamBuilder.REPLY}?.writeInt(serviceImpl.hashCode())")
        methodBuilder.addStatement("return true")
        methodBuilder.endControlFlow()

        methodBuilder.beginControlFlow("TRANSACTION__getStubProcessID -> ")
        methodBuilder.addStatement("${ParamBuilder.DATA}.enforceInterface(DESCRIPTOR)")
        methodBuilder.addStatement("${ParamBuilder.REPLY}?.writeNoException()")
        methodBuilder.addStatement("${ParamBuilder.REPLY}?.writeInt(android.os.Process.myPid())")
        methodBuilder.addStatement("return true")
        methodBuilder.endControlFlow()

        processRemoterElements(classBuilder, object : ElementVisitor {
            override fun visitElement(classBuilder: TypeSpec.Builder, member: KSFunctionDeclaration, methodIndex: Int, methodBuilder: FunSpec.Builder?) {
                addStubMethods(member, methodIndex, methodBuilder!!)
            }
        }, methodBuilder)

        methodBuilder.endControlFlow() // end when
        methodBuilder.endControlFlow() // end try

        methodBuilder.beginControlFlow("catch (re:%T)", Throwable::class)
        methodBuilder.beginControlFlow("if ( (flags and FLAG_ONEWAY) == 0)")
        methodBuilder.beginControlFlow("if ( ${ParamBuilder.REPLY} != null )")
        methodBuilder.addStatement("${ParamBuilder.REPLY}.setDataPosition(0)")
        methodBuilder.addStatement("${ParamBuilder.REPLY}.writeInt(REMOTER_EXCEPTION_CODE)")
        methodBuilder.addStatement("${ParamBuilder.REPLY}.writeString(re.message)")
        methodBuilder.addStatement("${ParamBuilder.REPLY}.writeSerializable(re)")
        methodBuilder.endControlFlow()
        methodBuilder.addStatement("return true")
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("else")
        methodBuilder.addStatement("%T.w(\"StubCall: serviceImpl?.toString()\", \"Binder call failed.\", re)", ClassName("android.util", "Log"))
        methodBuilder.addStatement("throw %T(re)", RuntimeException::class)
        methodBuilder.endControlFlow()
        methodBuilder.endControlFlow()

        methodBuilder.addStatement("return super.onTransact(code, ${ParamBuilder.DATA}, ${ParamBuilder.REPLY}, flags)")
        classBuilder.addFunction(methodBuilder.build())

        addCommonExtras(classBuilder)
        addStubExtras(classBuilder)
    }

    private fun addStubMethods(
        member: KSFunctionDeclaration,
        methodIndex: Int,
        methodBuilder: FunSpec.Builder
    ) {
        val methodName = member.simpleName.asString()
        val isSuspendFunction = member.isSuspendFunction()
        val isSuspendUnit = isSuspendFunction && member.getReturnTypeOfSuspend().isVoidType()
        val isOneWay = member.getReturnAsKSType().isVoidType() &&
                member.annotations.any { it.shortName.asString() == "Oneway" }

        methodBuilder.beginControlFlow("TRANSACTION_${methodName}_$methodIndex -> ")
        methodBuilder.addStatement("${ParamBuilder.DATA}.enforceInterface(DESCRIPTOR)")
        methodBuilder.addStatement("onDispatchTransaction(code)")

        val paramNames = mutableListOf<String>()
        val outParams = mutableListOf<KSValueParameter>()
        val outParamNames = mutableListOf<String>()

        if (isSuspendFunction) {
            methodBuilder.beginControlFlow("kotlinx.coroutines.runBlocking")
        }

        for ((paramIndex, param) in member.parameters.withIndex()) {
            val paramType = when {
                param.annotations.any { it.shortName.asString() == "ParamIn" } -> ParamBuilder.ParamType.IN
                param.annotations.any { it.shortName.asString() == "ParamOut" } -> ParamBuilder.ParamType.OUT
                else -> ParamBuilder.ParamType.IN_OUT
            }
            val paramBuilderForParam = bindingManager.getBuilderForParam(remoterInterfaceElement, param.asType())
            val paramName = "arg_stb_$paramIndex"
            paramNames.add(paramName)
            paramBuilderForParam.writeParamsToStub(member, param, paramType, paramName, methodBuilder)
            if (paramType != ParamBuilder.ParamType.IN) {
                outParams.add(param)
                outParamNames.add(paramName)
            }
        }

        var methodCall = "serviceImpl!!.$methodName("
        for ((i, name) in paramNames.withIndex()) {
            val isLast = i == paramNames.size - 1
            if (isLast && member.parameters.lastOrNull()?.isVararg == true) methodCall += "*"
            methodCall += name
            if (!isLast) methodCall += ", "
        }
        methodCall += ")"

        methodBuilder.addStatement("val __gp_bundle = ${ParamBuilder.DATA}.readBundle(javaClass.getClassLoader())")
        methodBuilder.addStatement("%T.set(__gp_bundle?.keySet()?.associateWith { __gp_bundle.getString(it) })", RemoterGlobalProperties::class.java)

        if (isSuspendFunction) {
            if (!isSuspendUnit) {
                methodBuilder.addStatement("val ${ParamBuilder.RESULT} = $methodCall")
            } else {
                methodBuilder.addStatement(methodCall)
            }
        } else {
            if (!member.getReturnAsKSType().isVoidType()) {
                methodBuilder.addStatement("val ${ParamBuilder.RESULT} = $methodCall")
            } else {
                methodBuilder.addStatement(methodCall)
            }
        }
        methodBuilder.addStatement("RemoterGlobalProperties.reset()")

        if (!isOneWay) {
            methodBuilder.beginControlFlow("if (${ParamBuilder.REPLY} != null)")
            methodBuilder.addStatement("${ParamBuilder.REPLY}.writeNoException()")

            if (isSuspendFunction) {
                if (!isSuspendUnit) {
                    val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, member.getReturnTypeOfSuspend())
                    pb.readResultsFromStub(member, member.getReturnTypeOfSuspend(), methodBuilder)
                }
            } else {
                val retType = member.getReturnAsKSType()
                if (!retType.isVoidType()) {
                    val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, retType)
                    pb.readResultsFromStub(member, retType, methodBuilder)
                }
            }

            for ((pIndex, param) in outParams.withIndex()) {
                val paramType = when {
                    param.annotations.any { it.shortName.asString() == "ParamIn" } -> ParamBuilder.ParamType.IN
                    param.annotations.any { it.shortName.asString() == "ParamOut" } -> ParamBuilder.ParamType.OUT
                    else -> ParamBuilder.ParamType.IN_OUT
                }
                val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, param.asType())
                pb.readOutResultsFromStub(param, paramType, outParamNames[pIndex], methodBuilder)
            }

            methodBuilder.endControlFlow()
        }

        if (isSuspendFunction) {
            methodBuilder.endControlFlow() // end runBlocking
        }

        methodBuilder.addStatement("return true")
        methodBuilder.endControlFlow() // end transaction case
    }

    private fun addProxyMethods(classBuilder: TypeSpec.Builder, member: KSFunctionDeclaration, methodIndex: Int) {
        val methodName = member.simpleName.asString()
        val isOnewayAnnotated = member.annotations.any { it.shortName.asString() == "Oneway" }
        val isOneWay = member.getReturnAsKSType().isVoidType() && isOnewayAnnotated
        if (!isOneWay && isOnewayAnnotated) {
            logWarning("@Oneway is expected only for methods with void return. Ignoring it for $methodName")
        }

        val isSuspendFunction = member.isSuspendFunction()
        val isSuspendReturnNullable = if (isSuspendFunction) member.isSuspendReturningNullable() else false
        val isSuspendUnit = isSuspendFunction && member.getReturnTypeOfSuspend().isVoidType()

        val typeParamResolver = (member.parentDeclaration as? KSClassDeclaration)
            ?.typeParameters?.toTypeParameterResolver()
            ?: TypeParameterResolver.EMPTY

        val methodBuilder = FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)

        if (isSuspendFunction) {
            methodBuilder.addModifiers(KModifier.SUSPEND)
            methodBuilder.returns(member.getReturnTypeOfSuspend().asKotlinType(typeParamResolver).copy(isSuspendReturnNullable))
        } else {
            val retType = member.getReturnAsKotlinType(typeParamResolver)
            methodBuilder.returns(if (member.isReturnPotentiallyNullable()) retType.copy(nullable = true) else retType)
        }

        // @Throws annotation
        val throwsAnnotation = member.annotations.find { it.shortName.asString() == "Throws" }
        if (throwsAnnotation != null) {
            try {
                val types = throwsAnnotation.arguments.flatMap { arg ->
                    when (val v = arg.value) {
                        is List<*> -> v.filterIsInstance<com.google.devtools.ksp.symbol.KSType>()
                            .map { it.asKotlinType() }
                        is com.google.devtools.ksp.symbol.KSType -> listOf(v.asKotlinType())
                        else -> emptyList()
                    }
                }
                if (types.isNotEmpty()) methodBuilder.throws(types)
            } catch (_: Exception) { /* ignore */ }
        }

        val paramsSize = member.parameters.size
        for ((paramIndex, param) in member.parameters.withIndex()) {
            val isLastParam = paramIndex == paramsSize - 1
            if (isLastParam && param.isVararg) {
                val componentType = param.asType()
                methodBuilder.addParameter(
                    ParameterSpec.builder(param.simpleName, componentType.asKotlinType(typeParamResolver))
                        .addModifiers(KModifier.VARARG).build()
                )
            } else {
                methodBuilder.addParameter(
                    ParameterSpec.builder(param.simpleName, param.asKotlinType(typeParamResolver)).build()
                )
            }
        }

        if (isSuspendFunction) {
            methodBuilder.beginControlFlow("return kotlinx.coroutines.withContext(%T.IO)", ClassName.bestGuess("kotlinx.coroutines.Dispatchers"))
        }

        methodBuilder.addStatement("val ${ParamBuilder.DATA} = %T.obtain()", ClassName.bestGuess("android.os.Parcel"))
        if (!isOneWay) {
            methodBuilder.addStatement("val ${ParamBuilder.REPLY} = %T.obtain()", ClassName.bestGuess("android.os.Parcel"))
        }

        if (isSuspendFunction) {
            if (!isSuspendUnit) {
                methodBuilder.addStatement("var ${ParamBuilder.RESULT}: %T",
                    member.getReturnTypeOfSuspend().asKotlinType(typeParamResolver).copy(isSuspendReturnNullable))
            }
        } else {
            if (!member.getReturnAsKSType().isVoidType()) {
                val resultType = member.getReturnAsKotlinType(typeParamResolver)
                methodBuilder.addStatement("var ${ParamBuilder.RESULT}: %T",
                    if (member.isReturnPotentiallyNullable()) resultType.copy(nullable = true) else resultType)
            }
        }

        methodBuilder.beginControlFlow("try")
        methodBuilder.addStatement("${ParamBuilder.DATA}.writeInterfaceToken(DESCRIPTOR)")

        val outParams = mutableListOf<KSValueParameter>()
        for (param in member.parameters) {
            val paramType = when {
                param.annotations.any { it.shortName.asString() == "ParamIn" } -> ParamBuilder.ParamType.IN
                param.annotations.any { it.shortName.asString() == "ParamOut" } -> ParamBuilder.ParamType.OUT
                else -> ParamBuilder.ParamType.IN_OUT
            }
            if (paramType != ParamBuilder.ParamType.IN) outParams.add(param)
            val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, param.asType())
            pb.writeParamsToProxy(param, paramType, methodBuilder)
        }

        var remoterCall = "_getRemoteServiceBinder().transact"
        if (bindingManager.hasRemoterBuilder() && isSuspendFunction) {
            remoterCall = "_getRemoteServiceBinderSuspended().transact"
        }

        methodBuilder.addStatement("val __gp_bundle = %T()", ClassName("android.os", "Bundle"))
        methodBuilder.addStatement("__global_properties?.forEach { (k, v) -> __gp_bundle.putString(k, v.toString()) }")
        methodBuilder.addStatement("${ParamBuilder.DATA}.writeBundle(if (__global_properties != null) __gp_bundle else null)")

        if (isOneWay) {
            methodBuilder.addStatement("$remoterCall(TRANSACTION_${methodName}_$methodIndex, ${ParamBuilder.DATA}, null, android.os.IBinder.FLAG_ONEWAY)")
        } else {
            methodBuilder.addStatement("$remoterCall(TRANSACTION_${methodName}_$methodIndex, ${ParamBuilder.DATA}, ${ParamBuilder.REPLY}, 0)")
            methodBuilder.addStatement("val __exception = checkException(${ParamBuilder.REPLY})")
            methodBuilder.beginControlFlow("if(__exception != null)")
            methodBuilder.addStatement("throw __exception ")
            methodBuilder.endControlFlow()

            if (isSuspendFunction) {
                if (!isSuspendUnit) {
                    val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, member.getReturnTypeOfSuspend())
                    pb.readResultsFromProxy(member, methodBuilder)
                }
            } else {
                if (!member.getReturnAsKSType().isVoidType()) {
                    val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, member.getReturnAsKSType())
                    pb.readResultsFromProxy(member, methodBuilder)
                }
            }

            for (param in outParams) {
                val paramType = when {
                    param.annotations.any { it.shortName.asString() == "ParamIn" } -> ParamBuilder.ParamType.IN
                    param.annotations.any { it.shortName.asString() == "ParamOut" } -> ParamBuilder.ParamType.OUT
                    else -> ParamBuilder.ParamType.IN_OUT
                }
                val pb = bindingManager.getBuilderForParam(remoterInterfaceElement, param.asType())
                pb.readOutParamsFromProxy(param, paramType, methodBuilder)
            }
        }

        methodBuilder.endControlFlow() // end try
        methodBuilder.beginControlFlow("catch (re:%T)", ClassName("android.os", "RemoteException"))
        methodBuilder.addStatement("throw %T(re)", RuntimeException::class)
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("finally")
        if (!isOneWay) methodBuilder.addStatement("${ParamBuilder.REPLY}.recycle()")
        methodBuilder.addStatement("${ParamBuilder.DATA}.recycle()")
        methodBuilder.endControlFlow()

        if (isSuspendFunction) {
            if (!isSuspendUnit) methodBuilder.addStatement(ParamBuilder.RESULT)
            methodBuilder.endControlFlow() // end withContext
        } else {
            if (!member.getReturnAsKSType().isVoidType()) {
                methodBuilder.addStatement("return ${ParamBuilder.RESULT}")
            }
        }

        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addCommonExtras(classBuilder: TypeSpec.Builder) {
        addGetParcelClass(classBuilder)
        addGetParcelObject(classBuilder)
    }

    private fun addGetParcelClass(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("getParcelerClass")
            .addModifiers(KModifier.PRIVATE)
            .returns(Class::class.asTypeName().parameterizedBy(STAR).copy(true))
            .addParameter("pObject", Any::class.asTypeName().copy(true))
            .beginControlFlow("if (pObject != null)")
            .addStatement("var objClass: Class<*>? = pObject.javaClass")
            .addStatement("var found = false")
            .beginControlFlow("while (!found && objClass != null)")
            .beginControlFlow("try")
            .addStatement("Class.forName(objClass.name + \"\\\$\\\$Parcelable\")")
            .addStatement("found = true")
            .endControlFlow()
            .beginControlFlow("catch (ignored: ClassNotFoundException) ")
            .addStatement("objClass = objClass.superclass")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return objClass")
            .endControlFlow()
            .addStatement("return null")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addGetParcelObject(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("getParcelerObject")
            .addModifiers(KModifier.PRIVATE)
            .returns(Any::class.asTypeName().copy(true))
            .addParameter("pClassName", String::class.asTypeName().copy(true))
            .addParameter("data", ClassName("android.os", "Parcel"))
            .beginControlFlow("return try")
            .beginControlFlow("if (pClassName != null)")
            .addStatement("val creator = Class.forName(\"\$pClassName\$\\\$Parcelable\").getField(\"CREATOR\")[null] as %T.Creator<*>", ClassName("android.os", "Parcelable"))
            .addStatement("val pWrapper = creator.createFromParcel(data)")
            .addStatement("pWrapper.javaClass.getMethod(\"getParcel\").invoke(pWrapper)")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("null")
            .endControlFlow()
            .endControlFlow()
            .beginControlFlow("catch (ignored: Exception)")
            .addStatement("null")
            .endControlFlow()
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyExtras(classBuilder: TypeSpec.Builder) {
        addRemoterProxyMethods(classBuilder)
        addProxyDeathMethod(classBuilder, "linkToDeath", "Register a {@link android.os.IBinder.DeathRecipient} to know of binder connection lose\n")
        addProxyDeathMethod(classBuilder, "unlinkToDeath", "UnRegisters a {@link android.os.IBinder.DeathRecipient}\n")
        addProxyRemoteAlive(classBuilder)
        addProxyCheckException(classBuilder)
        addGetId(classBuilder)
        addHashCode(classBuilder)
        addEquals(classBuilder)
        addProxyToString(classBuilder)
        addProxyDestroyMethods(classBuilder)
        addProxyGetServiceSuspended(classBuilder)
    }

    private fun addProxyDestroyMethods(classBuilder: TypeSpec.Builder) {
        var methodBuilder = FunSpec.builder("destroyStub")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("`object`", Any::class.asTypeName().copy(true))
            .returns(Unit::class)
            .beginControlFlow("if(`object` != null)")
            .beginControlFlow("synchronized (stubMap)")
            .addStatement("val binder = stubMap[`object`]")
            .beginControlFlow("if (binder != null)")
            .addStatement("(binder as %T).destroyStub()", RemoterStub::class)
            .addStatement("stubMap.remove(`object`)")
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
        classBuilder.addFunction(methodBuilder.build())

        methodBuilder = FunSpec.builder("destroyProxy")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Unit::class)
        if (bindingManager.hasRemoterBuilder()) {
            methodBuilder.addStatement("_proxyScope.%M()", MemberName("kotlinx.coroutines", "cancel"))
        }
        methodBuilder.addStatement("this.remoteBinder = null")
            .addStatement("unRegisterProxyListener(null)")
            .beginControlFlow("synchronized (stubMap)")
            .beginControlFlow("stubMap.values.forEach")
            .addStatement("(it as %T).destroyStub()", RemoterStub::class)
            .endControlFlow()
            .addStatement("stubMap.clear()")
            .endControlFlow()
        if (bindingManager.hasRemoterBuilder()) {
            methodBuilder.addStatement("_remoterServiceConnector?.disconnect()")
        }
        classBuilder.addFunction(methodBuilder.build())

        methodBuilder = FunSpec.builder("setRemoterGlobalProperties")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("properties", ClassName("kotlin.collections", "MutableMap")
                .parameterizedBy(String::class.asTypeName(), Any::class.asTypeName()).copy(true))
            .returns(Unit::class)
            .addStatement("this.__global_properties = properties")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addRemoterProxyMethods(classBuilder: TypeSpec.Builder) {
        var methodBuilder = FunSpec.builder("registerProxyListener")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Unit::class)
            .addParameter("listener", RemoterProxyListener::class)
            .addStatement("unRegisterProxyListener(null)")
            .addStatement("val pListener = DeathRecipient(listener)")
            .addStatement("linkToDeath(pListener)")
            .addStatement("proxyListener = pListener")
        classBuilder.addFunction(methodBuilder.build())

        methodBuilder = FunSpec.builder("unRegisterProxyListener")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Unit::class)
            .addParameter("listener", RemoterProxyListener::class.asTypeName().copy(true))
            .beginControlFlow("proxyListener?.let ")
            .addStatement("unlinkToDeath(it)")
            .addStatement("it.unregister()")
            .endControlFlow()
            .addStatement("proxyListener = null")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyDeathMethod(classBuilder: TypeSpec.Builder, deathMethod: String, doc: String) {
        val methodBuilder = FunSpec.builder(deathMethod)
            .addModifiers(KModifier.PUBLIC)
            .returns(Unit::class)
            .addParameter("deathRecipient", ClassName("android.os", "IBinder.DeathRecipient"))
            .beginControlFlow("try")
            .addStatement("_getRemoteServiceBinder().$deathMethod(deathRecipient, 0)")
            .endControlFlow()
            .beginControlFlow("catch (ignored: %T)", Exception::class)
            .endControlFlow()
            .addKdoc(doc)
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyRemoteAlive(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("isRemoteAlive")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Boolean::class)
            .addStatement("var alive = false")
            .beginControlFlow("try")
            .addStatement("alive = _getRemoteServiceBinder().isBinderAlive() == true")
            .endControlFlow()
            .beginControlFlow("catch (ignored:%T)", Exception::class)
            .endControlFlow()
            .addStatement("return alive")
            .addKdoc("Checks whether the remote process is alive\n")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyCheckException(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("checkException")
            .addModifiers(KModifier.PRIVATE)
            .returns(Throwable::class.asTypeName().copy(true))
            .addParameter("reply", ClassName("android.os", "Parcel"))
            .addStatement("val code = reply.readInt()")
            .addStatement("var exception: Throwable? = null")
            .beginControlFlow("if (code != 0)")
            .addStatement("val msg = reply.readString()")
            .beginControlFlow("exception = if (code == REMOTER_EXCEPTION_CODE) ")
            .addStatement("reply.readSerializable() as Throwable")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("RuntimeException(msg)")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return exception")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addHashCode(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("hashCode")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(Int::class)
            .addStatement("return _binderID")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addEquals(classBuilder: TypeSpec.Builder) {
        val totalTypesArguments = remoterInterfaceElement.typeParameters.size
        var typeAddition = ""
        if (totalTypesArguments > 0) {
            typeAddition = "<*"
            (1 until totalTypesArguments).forEach {
                typeAddition += ",*"
            }
            typeAddition += ">"
        }
        val methodBuilder = FunSpec.builder("equals")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("other", Any::class.asTypeName().copy(true))
            .returns(Boolean::class)
            .addStatement("return (other is $remoterInterfaceClassName$PROXY_SUFFIX$typeAddition) && (other.hashCode() == hashCode()) && (_stubProcess == other._stubProcess)")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyToString(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("toString")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .returns(String::class)
            .addStatement("return \"$remoterInterfaceClassName$PROXY_SUFFIX[ \"+ _stubProcess + \":\" + _binderID + \"]\"")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addProxyGetServiceSuspended(classBuilder: TypeSpec.Builder) {
        if (bindingManager.hasRemoterBuilder()) {
            val methodBuilder = FunSpec.builder("_getRemoteServiceBinderSuspended")
                .addModifiers(KModifier.PRIVATE, KModifier.SUSPEND)
                .addParameter(ParameterSpec.builder("waitForInit", Boolean::class).defaultValue("true").build())
                .returns(ClassName("android.os", "IBinder"))
                .addStatement("val sConnector = _remoterServiceConnector")
                .beginControlFlow("return if (sConnector != null)")
                .addStatement("val rBinder = sConnector.getService()")
                .beginControlFlow("if (waitForInit)")
                .addStatement("_serviceInitComplete.await()")
                .endControlFlow()
                .addStatement("rBinder")
                .endControlFlow()
                .beginControlFlow("else")
                .addStatement("remoteBinder?: throw %T(\"No remote binder or IServiceConnector set\")", RuntimeException::class)
                .endControlFlow()
            classBuilder.addFunction(methodBuilder.build())
        }

        val methodBuilderNonSuspended = FunSpec.builder("_getRemoteServiceBinder")
            .addModifiers(KModifier.PRIVATE)
            .returns(ClassName("android.os", "IBinder"))

        if (bindingManager.hasRemoterBuilder()) {
            methodBuilderNonSuspended
                .addParameter(ParameterSpec.builder("waitForInit", Boolean::class).defaultValue("true").build())
                .beginControlFlow("val result = if (remoteBinder != null)")
                .addStatement("remoteBinder")
                .endControlFlow()
                .beginControlFlow("else if (_remoterServiceConnector != null)")
                .beginControlFlow("kotlinx.coroutines.runBlocking")
                .addStatement("_remoterServiceConnector?.getService()")
                .endControlFlow()
                .endControlFlow()
                .beginControlFlow("else ")
                .addStatement("remoteBinder")
                .endControlFlow()
                .beginControlFlow("if (remoteBinder == null && waitForInit)")
                .addStatement("kotlinx.coroutines.runBlocking { _serviceInitComplete.await() }")
                .endControlFlow()
                .addStatement("return result?: throw %T(\"No remote binder or IServiceConnectot set\")", RuntimeException::class)
        } else {
            methodBuilderNonSuspended
                .addStatement("val result = remoteBinder")
                .addStatement("return result?: throw %T(\"No remote binder or IServiceConnectot set\")", RuntimeException::class)
        }
        classBuilder.addFunction(methodBuilderNonSuspended.build())
    }

    private fun addGetId(classBuilder: TypeSpec.Builder) {
        val getBinderCall = if (bindingManager.hasRemoterBuilder()) "_getRemoteServiceBinder(false)" else "_getRemoteServiceBinder()"
        addGetIdMethod(classBuilder, "__remoter_getStubID", "TRANSACTION__getStubID", getBinderCall)
        addGetIdMethod(classBuilder, "__remoter_getStubProcessID", "TRANSACTION__getStubProcessID", getBinderCall)
        if (bindingManager.hasRemoterBuilder()) {
            addGetIdMethod(classBuilder, "__remoter_getStubID_sus", "TRANSACTION__getStubID", "_getRemoteServiceBinderSuspended(false)", true)
            addGetIdMethod(classBuilder, "__remoter_getStubProcessID_sus", "TRANSACTION__getStubProcessID", "_getRemoteServiceBinderSuspended(false)", true)
        }
    }

    private fun addGetIdMethod(classBuilder: TypeSpec.Builder, methodName: String, descriptorName: String, getMethod: String, isSuspended: Boolean = false) {
        val methodBuilder = FunSpec.builder(methodName)
            .addModifiers(KModifier.PRIVATE)
            .returns(Int::class)
            .addStatement("val data = Parcel.obtain()")
            .addStatement("val reply = Parcel.obtain()")
        methodBuilder.beginControlFlow("val result = try ")
        methodBuilder.addStatement("data.writeInterfaceToken(DESCRIPTOR)")
        methodBuilder.addStatement("$getMethod.transact($descriptorName, data, reply, 0)")
        methodBuilder.addStatement("val exception = checkException(reply)")
        methodBuilder.beginControlFlow("if(exception != null)")
        methodBuilder.addStatement("throw (exception as %T)", RuntimeException::class)
        methodBuilder.endControlFlow()
        methodBuilder.addStatement("reply.readInt()")
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("catch (ignored: %T)", Exception::class)
        methodBuilder.addStatement("hashCode()")
        methodBuilder.endControlFlow()
        methodBuilder.beginControlFlow("finally")
        methodBuilder.addStatement("reply.recycle()")
        methodBuilder.addStatement("data.recycle()")
        methodBuilder.endControlFlow()
        methodBuilder.addStatement("return result")
        if (isSuspended) methodBuilder.addModifiers(KModifier.SUSPEND)
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addStubExtras(classBuilder: TypeSpec.Builder) {
        addStubDestroyMethods(classBuilder)
        addStubInterceptMethods(classBuilder)
        addStubMapTransaction(classBuilder)
    }

    private fun addStubDestroyMethods(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("destroyStub")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .beginControlFlow("try")
            .addStatement("this.attachInterface(null, DESCRIPTOR)")
            .addStatement("binderWrapper.binder = null")
            .endControlFlow()
            .beginControlFlow("catch (t:%T)", Throwable::class)
            .endControlFlow()
            .addStatement("serviceImpl = null")
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addStubInterceptMethods(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("onDispatchTransaction")
            .addModifiers(KModifier.PROTECTED, KModifier.OPEN)
            .throws(Exception::class.java)
            .addKdoc("Override to intercept before binder call for validation\n")
            .addParameter("code", Int::class)
        classBuilder.addFunction(methodBuilder.build())
    }

    private fun addStubMapTransaction(classBuilder: TypeSpec.Builder) {
        val methodBuilder = FunSpec.builder("mapTransactionCode")
            .addModifiers(KModifier.PRIVATE)
            .returns(Int::class)
            .addParameter("code", Int::class)
            .beginControlFlow("if (checkStubProxyMatch == false || code == INTERFACE_TRANSACTION)")
            .addStatement("return code")
            .endControlFlow()
            .addStatement("var mappedCode = code")
            .addStatement("val callingUid = %T.getMappedUid(Binder.getCallingUid())", RemoterGlobalProperties::class.java)
            .beginControlFlow("if (callingUid == 0)")
            .addStatement("return code")
            .endControlFlow()
            .addStatement("var __lastMethodIndexOfProxy = __processLastMethodMap.getOrDefault(callingUid, -1)")
            .beginControlFlow("if (__lastMethodIndexOfProxy == -1)")
            .addStatement("__lastMethodIndexOfProxy = code - 1")
            .addStatement("__processLastMethodMap[callingUid] = __lastMethodIndexOfProxy")
            .endControlFlow()
            .beginControlFlow("if (__lastMethodIndexOfProxy < __lastMethodIndex) ")
            .beginControlFlow("if (code > __lastMethodIndexOfProxy)")
            .addStatement("mappedCode = __lastMethodIndex + (code - __lastMethodIndexOfProxy)")
            .endControlFlow()
            .endControlFlow()
            .beginControlFlow("else if (__lastMethodIndexOfProxy > __lastMethodIndex)")
            .beginControlFlow("if (code > __lastMethodIndexOfProxy) ")
            .addStatement("mappedCode = __lastMethodIndex + (code - __lastMethodIndexOfProxy)")
            .endControlFlow()
            .beginControlFlow("else if (code > __lastMethodIndex)")
            .addStatement("throw RuntimeException(\"Interface mismatch between Proxy and Stub \" + code + \" [\" + __lastMethodIndex + \"]. Use same interface for both client and server\")")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return mappedCode")
        classBuilder.addFunction(methodBuilder.build())
    }
}
