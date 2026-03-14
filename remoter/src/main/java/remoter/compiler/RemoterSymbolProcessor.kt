package remoter.compiler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import remoter.annotations.Remoter
import remoter.compiler.kbuilder.KBindingManager

class RemoterSymbolProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val bindingManager = KBindingManager(env, resolver)
        val symbols = resolver.getSymbolsWithAnnotation(Remoter::class.qualifiedName!!)

        symbols.filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .forEach { classDecl ->
                val classesToWrap = classDecl.getClassesToWrap(resolver)
                if (classesToWrap.isNotEmpty()) {
                    classesToWrap.forEach { generateClassesFor(it, bindingManager) }
                } else {
                    generateClassesFor(classDecl, bindingManager)
                }
            }

        return emptyList()
    }

    private fun generateClassesFor(classDecl: KSClassDeclaration, bindingManager: KBindingManager) {
        if (classDecl.classKind == ClassKind.INTERFACE) {
            bindingManager.generateProxy(classDecl)
            bindingManager.generateStub(classDecl)
        } else {
            env.logger.warn("@Remoter is expected only for interface. Ignoring ${classDecl.simpleName.asString()}")
        }
    }

    /**
     * Reads the classesToWrap attribute of the @Remoter annotation on a marker interface.
     */
    private fun KSClassDeclaration.getClassesToWrap(resolver: Resolver): List<KSClassDeclaration> {
        val remoterAnnotation = annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == Remoter::class.qualifiedName
        } ?: return emptyList()

        val classesArg = remoterAnnotation.arguments.find { it.name?.asString() == "classesToWrap" }
            ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val values = classesArg.value as? List<*> ?: return emptyList()

        return values.filterIsInstance<com.google.devtools.ksp.symbol.KSType>().mapNotNull { ksType ->
            val decl = ksType.declaration as? KSClassDeclaration
            decl?.takeIf { it.classKind == ClassKind.INTERFACE }
        }
    }
}
