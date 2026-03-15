package remoter

/**
 * Retrieve any global properties send along with any remote call.
 * Properties can be sent using [RemoterProxy.setRemoterGlobalProperties]
 */
object RemoterGlobalProperties {

    private val globalProperties = ThreadLocal<MutableMap<String, Any?>>()
    private val proxyUidMap = HashMap<Int, Int>()

    /**
     * Get value of the given global property if any. Returns null otherwise.
     */
    @JvmStatic
    fun get(key: String): Any? = globalProperties.get()?.get(key)

    /**
     * Internally used
     */
    @JvmStatic
    fun set(properties: Map<*, *>?) {
        reset()
        if (properties != null) {
            val prop = HashMap<String, Any?>()
            for (key in properties.keys) {
                prop[key.toString()] = properties[key]
            }
            globalProperties.set(prop)
        }
    }

    /**
     * Internally used to reset
     */
    @JvmStatic
    fun reset() {
        globalProperties.remove()
    }

    @JvmStatic
    fun mapProxyUid(from: Int, to: Int) {
        proxyUidMap[from] = to
    }

    @JvmStatic
    fun getMappedUid(from: Int): Int {
        return try {
            if (proxyUidMap.containsKey(from)) proxyUidMap[from]!! else from
        } catch (_: Exception) {
            from
        }
    }
}
