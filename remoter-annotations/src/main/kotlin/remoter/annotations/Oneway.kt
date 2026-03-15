package remoter.annotations

/**
 * Marks a method call as asynchronous one way call
 *
 * Only applies to methods with void return, and will be ignored for others.
 *
 * @see Remoter
 * @see ParamOut
 * @see ParamIn
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class Oneway
