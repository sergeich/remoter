package remoter.compiler.kbuilder

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Base class for all KSP-based Remoter code generators.
 */
open class KRemoterBuilder(
    val remoterInterfaceElement: KSClassDeclaration,
    val bindingManager: KBindingManager
) {
    val remoterInterfacePackageName: String =
        remoterInterfaceElement.packageName.asString()
    val remoterInterfaceClassName: String =
        remoterInterfaceElement.simpleName.asString()

    private val logger: KSPLogger = bindingManager.getMessager()

    /**
     * Visitor called for each interface method during code generation.
     */
    interface ElementVisitor {
        fun visitElement(
            classBuilder: TypeSpec.Builder,
            member: KSFunctionDeclaration,
            methodIndex: Int,
            methodBuilder: FunSpec.Builder?
        )
    }

    open fun logError(message: String?) {
        logger.error(message ?: "")
    }

    open fun logWarning(message: String?) {
        logger.warn(message ?: "")
    }

    open fun logInfo(message: String?) {
        logger.info(message ?: "")
    }

    protected open fun processRemoterElements(
        classBuilder: TypeSpec.Builder,
        elementVisitor: ElementVisitor,
        methodBuilder: FunSpec.Builder?
    ) {
        processRemoterElements(
            classBuilder,
            remoterInterfaceElement,
            0,
            elementVisitor,
            methodBuilder
        )
    }

    internal open fun processRemoterElements(
        classBuilder: TypeSpec.Builder,
        element: KSClassDeclaration,
        methodIndex: Int,
        elementVisitor: ElementVisitor,
        methodBuilder: FunSpec.Builder?
    ): Int {
        var index = methodIndex

        for (superTypeRef in element.superTypes) {
            val superDecl = superTypeRef.resolve().declaration
            if (superDecl is KSClassDeclaration && superDecl.classKind == ClassKind.INTERFACE) {
                index = processRemoterElements(
                    classBuilder, superDecl, index, elementVisitor, methodBuilder
                )
            }
        }

        for (member in element.getDeclaredFunctions()) {
            elementVisitor.visitElement(classBuilder, member, index, methodBuilder)
            index++
        }

        return index
    }
}
