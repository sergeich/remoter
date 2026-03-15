package remoter.annotations

/**
 * Marks a parameter as an output only type.
 *
 * Only applies to primitive array types (e.g. int[], long[], String[], etc.), [java.util.List], [java.util.Map],
 * or Parcelable types. By default, these types are treated as input and output, unless
 * they are marked otherwise using either [@ParamOut][ParamOut] or [ParamIn]
 *
 * Annotating on any unsupported types will be ignored
 *
 * @see Remoter
 * @see ParamIn
 * @see Oneway
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ParamOut
