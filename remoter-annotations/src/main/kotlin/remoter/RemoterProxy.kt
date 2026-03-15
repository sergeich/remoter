package remoter

/**
 * Represents a remote proxy. This will be implemented by the Remoter generated Proxy classes.
 */
interface RemoterProxy {

    /**
     * Register a [RemoterProxyListener]
     */
    fun registerProxyListener(listener: RemoterProxyListener)

    /**
     * Un register a [RemoterProxyListener]
     */
    fun unRegisterProxyListener(listener: RemoterProxyListener?)

    /**
     * Checks whether the remote side is still alive
     *
     * @see registerProxyListener
     */
    fun isRemoteAlive(): Boolean

    /**
     * Destroys any stub created while sending the given object through this proxy.
     *
     * @see destroyProxy
     */
    fun destroyStub(`object`: Any?)

    /**
     * Call to destroy the proxy. Proxy should not be used after this. This also clears any stubs
     * that are send using this proxy
     */
    fun destroyProxy()

    /**
     * Set any global properties to be sent with all remote calls.
     * At the service side this can be obtained using [RemoterGlobalProperties.get]
     */
    fun setRemoterGlobalProperties(properties: MutableMap<String, Any>?)
}
