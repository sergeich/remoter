package util.remoter.service

import remoter.annotations.Remoter


/**
 * A sample interface in kotlin that uses
 */
@Remoter
interface ITimeService {

    /**
     * Returns current time
     */
    suspend fun getTime(): Long

    /**
     * Simulate a service side crash
     */
    suspend fun simulateServiceCrash(): Int

}
