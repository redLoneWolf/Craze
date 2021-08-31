//
// This file is auto-generated. Please don't modify it!
//
package org.opencv.tracking;

// C++: class TrackerTLD

/**
 * the TLD (Tracking, learning and detection) tracker
 * <p>
 * TLD is a novel tracking framework that explicitly decomposes the long-term tracking task into
 * tracking, learning and detection.
 * <p>
 * The tracker follows the object from frame to frame. The detector localizes all appearances that
 * have been observed so far and corrects the tracker if necessary. The learning estimates detector's
 * errors and updates it to avoid these errors in the future. The implementation is based on CITE: TLD .
 * <p>
 * The Median Flow algorithm (see cv::TrackerMedianFlow) was chosen as a tracking component in this
 * implementation, following authors. The tracker is supposed to be able to handle rapid motions, partial
 * occlusions, object absence etc.
 */
public class legacy_TrackerTLD extends legacy_Tracker {

    protected legacy_TrackerTLD(long addr) {
        super(addr);
    }

    // internal usage only
    public static legacy_TrackerTLD __fromPtr__(long addr) {
        return new legacy_TrackerTLD(addr);
    }

    //
    // C++: static Ptr_legacy_TrackerTLD cv::legacy::TrackerTLD::create()
    //

    /**
     * Constructor
     *
     * @return automatically generated
     */
    public static legacy_TrackerTLD create() {
        return legacy_TrackerTLD.__fromPtr__(create_0());
    }


    @Override
    protected void finalize() throws Throwable {
        delete(nativeObj);
    }


    // C++: static Ptr_legacy_TrackerTLD cv::legacy::TrackerTLD::create()
    private static native long create_0();

    // native support for java finalize()
    private static native void delete(long nativeObj);

}
