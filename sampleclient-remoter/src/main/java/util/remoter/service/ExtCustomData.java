package util.remoter.service;

import org.parceler.Parcel;
import org.parceler.ParcelConstructor;

/**
 * For testing @Parcel (parceler)
 */
@Parcel
public class ExtCustomData extends CustomData {

    int i;
    IExtE remoteInterface;

    @ParcelConstructor
    public ExtCustomData(IExtE remoteInterface) {
        this.remoteInterface = remoteInterface;
    }

    public IExtE getRemoteInterface() {
        return remoteInterface;
    }


}
