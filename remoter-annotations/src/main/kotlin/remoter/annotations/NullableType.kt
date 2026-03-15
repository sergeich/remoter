package remoter.annotations

/**
 * Marks which of the types in a type parameter are nullable
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class NullableType(
    /**
     * Array of indexes (0 based) of the type that are nullable.
     *
     * Default is [0]. If the type is single type param, then this can be omitted.
     */
    val nullableIndexes: IntArray = [0]
)
