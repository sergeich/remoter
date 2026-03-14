package remoter.compiler.kbuilder

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import remoter.annotations.NullableType

// ─── KSType helpers ──────────────────────────────────────────────────────────

/** Convert a KSP type to a KotlinPoet TypeName. */
internal fun KSType.asKotlinType(resolver: TypeParameterResolver = TypeParameterResolver.EMPTY): TypeName = toTypeName(resolver)

/**
 * Returns true if this type is any array type:
 * kotlin.Array<T>, or a primitive array (IntArray, BooleanArray, …).
 */
fun KSType.isArrayType(): Boolean {
    val qn = declaration.qualifiedName?.asString() ?: return false
    return qn == "kotlin.Array" ||
            qn == "kotlin.IntArray" ||
            qn == "kotlin.BooleanArray" ||
            qn == "kotlin.ByteArray" ||
            qn == "kotlin.CharArray" ||
            qn == "kotlin.DoubleArray" ||
            qn == "kotlin.FloatArray" ||
            qn == "kotlin.LongArray" ||
            qn == "kotlin.ShortArray"
}

/**
 * Returns the element type of an object array (kotlin.Array<T>).
 * Only call this after confirming isArrayType() == true and
 * the type is NOT a primitive array.
 */
fun KSType.componentType(): KSType = arguments.first().type!!.resolve()

/** True when this type represents kotlin.Unit (void equivalent). */
fun KSType.isVoidType(): Boolean =
    declaration.qualifiedName?.asString() == "kotlin.Unit"

// ─── KSValueParameter helpers ─────────────────────────────────────────────

/** Convenience: resolve the declared type of this parameter. */
internal fun KSValueParameter.asType(): KSType = type.resolve()

/** Parameter name as a plain String. */
internal val KSValueParameter.simpleName: String get() = name!!.asString()

/** True when the parameter type is marked nullable (has `?`). */
internal fun KSValueParameter.isNullable(): Boolean = type.resolve().isMarkedNullable

/** The KotlinPoet TypeName for this parameter, respecting nullability. */
internal fun KSValueParameter.asKotlinType(resolver: TypeParameterResolver = paramDeclaringClassTypeParamResolver()): TypeName = type.resolve().asKotlinType(resolver)

private fun KSValueParameter.paramDeclaringClassTypeParamResolver(): TypeParameterResolver =
    ((parent as? KSFunctionDeclaration)?.parentDeclaration as? KSClassDeclaration)
        ?.typeParameters?.toTypeParameterResolver()
        ?: TypeParameterResolver.EMPTY

/** True when the NullableType annotation specifies that typeIndex is nullable. */
internal fun KSValueParameter.isNullableType(typeIndex: Int): Boolean =
    annotations.any {
        it.shortName.asString() == "NullableType" &&
                (it.arguments.firstOrNull()?.value as? List<*>)?.contains(typeIndex) == true
    }

// ─── KSFunctionDeclaration helpers ───────────────────────────────────────

/** True when the function is declared `suspend`. */
internal fun KSFunctionDeclaration.isSuspendFunction() =
    modifiers.contains(Modifier.SUSPEND)

/**
 * True when the suspend function's return is annotated @NullableType,
 * meaning the caller must treat it as nullable.
 */
@OptIn(KspExperimental::class)
internal fun KSFunctionDeclaration.isSuspendReturningNullable() =
    getAnnotationsByType(NullableType::class).firstOrNull() != null

/**
 * For a suspend function, returns the actual Kotlin return type
 * (KSP reports it directly; there is no synthetic Continuation parameter).
 */
internal fun KSFunctionDeclaration.getReturnTypeOfSuspend(): KSType =
    returnType!!.resolve()

/** Returns the resolved return type (works for both suspend and non-suspend). */
internal fun KSFunctionDeclaration.getReturnAsKSType(): KSType =
    returnType!!.resolve()

/** Returns the KotlinPoet TypeName for the return type. */
internal fun KSFunctionDeclaration.getReturnAsKotlinType(resolver: TypeParameterResolver = declaringClassTypeParamResolver()): TypeName {
    return if (isSuspendFunction()) {
        returnType!!.resolve().asKotlinType(resolver).copy(isSuspendReturningNullable())
    } else {
        returnType!!.resolve().asKotlinType(resolver)
    }
}

private fun KSFunctionDeclaration.declaringClassTypeParamResolver(): TypeParameterResolver =
    (parentDeclaration as? KSClassDeclaration)?.typeParameters?.toTypeParameterResolver()
        ?: TypeParameterResolver.EMPTY

/** True when the function's return type is marked nullable. */
internal fun KSFunctionDeclaration.isNullable(): Boolean =
    returnType?.resolve()?.isMarkedNullable == true

// ─── KSClassDeclaration helpers ──────────────────────────────────────────

/** The KotlinPoet TypeName for the star-projected form of this class. */
internal fun KSClassDeclaration.asKotlinType(): TypeName =
    asStarProjectedType().asKotlinType()

/**
 * Checks whether this interface (or any interface it extends) has at least
 * one suspend function — used to decide which builder path to use.
 */
fun KSClassDeclaration.hasSuspendFunction(): Boolean {
    if (superTypes.any {
            val superDecl = it.resolve().declaration
            superDecl is KSClassDeclaration && superDecl.hasSuspendFunction()
        }) return true
    return getDeclaredFunctions().any { it.isSuspendFunction() }
}

// kept for backward compatibility with usages via the old Common.kt API
internal fun getNameForClassNameSearch(name: String): String {
    val result = name.split(' ').last()
    return javaToKotlinMap[result] ?: result
}
