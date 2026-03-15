package remoter

/**
 * Listener to get notified about changes in a [RemoterProxy]
 */
interface RemoterProxyListener {

    /**
     * Called when the remote proxy connection is lost
     */
    fun onProxyDead()
}
