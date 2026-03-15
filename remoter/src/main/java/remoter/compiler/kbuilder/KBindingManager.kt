package remoter.compiler.kbuilder

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.writeTo
import remoter.annotations.Remoter

/**
 * Manages Kotlin file generation using KSP.
 */
open class KBindingManager(
    private val env: SymbolProcessorEnvironment,
    val resolver: Resolver
) {
    companion object {
        const val PARCELER_ANNOTATION = "org.parceler.Parcel"
    }

    private var typeBuilderMap: MutableMap<KSType, ParamBuilder> = mutableMapOf()

    // Lazily resolved reference types used for isAssignableFrom checks
    private val stringClass by lazy { resolver.getClassDeclarationByName("kotlin.String")!! }
    private val charSequenceClass by lazy { resolver.getClassDeclarationByName("kotlin.CharSequence")!! }
    private val listClass by lazy { resolver.getClassDeclarationByName("kotlin.collections.List")!! }
    private val mapClass by lazy { resolver.getClassDeclarationByName("kotlin.collections.Map")!! }
    private val parcellableClass by lazy {
        resolver.getClassDeclarationByName("android.os.Parcelable")
    }

    private val remoterBuilderAvailable: Boolean =
        resolver.getClassDeclarationByName("remoter.builder.ServiceConnector") != null

    // ── File generation ───────────────────────────────────────────────────

    fun generateProxy(classDecl: KSClassDeclaration) {
        KClassBuilder(classDecl, this).generateProxy()
            .writeTo(env.codeGenerator, Dependencies(false, classDecl.containingFile!!))
    }

    fun generateStub(classDecl: KSClassDeclaration) {
        KClassBuilder(classDecl, this).generateStub()
            .writeTo(env.codeGenerator, Dependencies(false, classDecl.containingFile!!))
    }

    // ── Accessors used by builders ────────────────────────────────────────

    fun getMessager() = env.logger

    fun hasRemoterBuilder() = remoterBuilderAvailable

    internal fun getFieldBuilder(classDecl: KSClassDeclaration) = KFieldBuilder(classDecl, this)
    fun getFunctionBuilder(classDecl: KSClassDeclaration) = KMethodBuilder(classDecl, this)

    /**
     * Look up a class declaration by its fully-qualified name.
     * Strips generic parameters if present.
     */
    fun getClassDeclaration(className: String): KSClassDeclaration {
        var cName = className
        val templateStart = className.indexOf('<')
        if (templateStart != -1) {
            cName = className.substring(0, templateStart).trim()
        }
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString(cName))
            ?: error("Could not find class: $cName")
    }

    // ── Param builder dispatch ────────────────────────────────────────────

    /**
     * Returns the [ParamBuilder] responsible for marshalling the given KSP type.
     */
    internal open fun getBuilderForParam(
        remoteElement: KSClassDeclaration,
        type: KSType
    ): ParamBuilder {
        var builder = typeBuilderMap[type]
        if (builder != null) return builder

        val qn = type.declaration.qualifiedName?.asString() ?: ""

        builder = when {
            // Primitives and boxed equivalents
            qn == "kotlin.Int" || qn == "java.lang.Integer" ->
                IntParamBuilder(remoteElement, this)
            qn == "kotlin.Boolean" || qn == "java.lang.Boolean" ->
                BooleanParamBuilder(remoteElement, this)
            qn == "kotlin.Byte" || qn == "java.lang.Byte" ->
                ByteParamBuilder(remoteElement, this)
            qn == "kotlin.Char" || qn == "java.lang.Character" ->
                CharParamBuilder(remoteElement, this)
            qn == "kotlin.Double" || qn == "java.lang.Double" ->
                DoubleParamBuilder(remoteElement, this)
            qn == "kotlin.Float" || qn == "java.lang.Float" ->
                FloatParamBuilder(remoteElement, this)
            qn == "kotlin.Long" || qn == "java.lang.Long" ->
                LongParamBuilder(remoteElement, this)
            qn == "kotlin.Short" || qn == "java.lang.Short" ->
                ShortParamBuilder(remoteElement, this)

            // Primitive arrays
            qn == "kotlin.IntArray" -> IntParamBuilder(remoteElement, this)
            qn == "kotlin.BooleanArray" -> BooleanParamBuilder(remoteElement, this)
            qn == "kotlin.ByteArray" -> ByteParamBuilder(remoteElement, this)
            qn == "kotlin.CharArray" -> CharParamBuilder(remoteElement, this)
            qn == "kotlin.DoubleArray" -> DoubleParamBuilder(remoteElement, this)
            qn == "kotlin.FloatArray" -> FloatParamBuilder(remoteElement, this)
            qn == "kotlin.LongArray" -> LongParamBuilder(remoteElement, this)
            qn == "kotlin.ShortArray" -> ShortParamBuilder(remoteElement, this)

            // Object array: dispatch on the element type
            qn == "kotlin.Array" -> {
                val componentType = type.componentType()
                getBuilderForParam(remoteElement, componentType)
            }

            // String (before CharSequence, since String implements CharSequence)
            qn == "kotlin.String" || qn == "java.lang.String" ->
                StringParamBuilder(remoteElement, this)

            else -> buildDeclaredTypeBuilder(remoteElement, type)
        }

        typeBuilderMap[type] = builder
        return builder
    }

    private fun buildDeclaredTypeBuilder(
        remoteElement: KSClassDeclaration,
        type: KSType
    ): ParamBuilder {
        val decl = type.declaration as? KSClassDeclaration

        if (isAssignableTo(type, charSequenceClass)) {
            return CharSequenceParamBuilder(remoteElement, this)
        }

        if (isAssignableTo(type, listClass)) {
            val parcelerGenericType = getGenericParcelerListType(type)
            return if (parcelerGenericType != null) {
                ListOfParcelerParamBuilder(parcelerGenericType, remoteElement, this)
            } else {
                ListParamBuilder(remoteElement, this)
            }
        }

        if (isAssignableTo(type, mapClass)) {
            return MapParamBuilder(remoteElement, this)
        }

        if (parcellableClass != null && isAssignableTo(type, parcellableClass!!)) {
            return ParcellableParamBuilder(remoteElement, this)
        }

        if (decl != null) {
            if (decl.classKind == ClassKind.INTERFACE &&
                decl.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            Remoter::class.qualifiedName
                }
            ) {
                return BinderParamBuilder(remoteElement, this)
            }

            if (decl.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == PARCELER_ANNOTATION
                }
            ) {
                return ParcelerParamBuilder(remoteElement, this)
            }
        }

        return GenericParamBuilder(remoteElement, this)
    }

    /** True when [type] is a subtype of the star-projection of [superClass]. */
    private fun isAssignableTo(type: KSType, superClass: KSClassDeclaration): Boolean =
        superClass.asStarProjectedType().isAssignableFrom(type.makeNotNullable())

    /**
     * If [listType] is a List whose type argument carries @Parcel, returns that class declaration.
     */
    private fun getGenericParcelerListType(listType: KSType): KSClassDeclaration? {
        for (arg in listType.arguments) {
            val argType = arg.type?.resolve() ?: continue
            val argDecl = argType.declaration as? KSClassDeclaration ?: continue
            if (argDecl.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == PARCELER_ANNOTATION
                }
            ) {
                return argDecl
            }
        }
        return null
    }
}
