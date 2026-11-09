package com.limelight.vr;

/**
 * Physical parameters of a phone VR viewer (the headset the phone sits in).
 *
 * <p>Values follow the Google Cardboard viewer-profile conventions: distances are in meters and the lens
 * distortion is the polynomial {@code r_eye = r_screen * (1 + k1 * r_screen^2 + k2 * r_screen^4)}: the angle at which
 * the eye sees a point on the phone screen. Both radii are tangents of the angle from the lens axis.</p>
 */
public final class ViewerProfile {
    /** Distance between the two lens centers. Also used as the distance between the virtual eyes. */
    public final float interLensDistance;
    /** Distance from the phone screen to the lenses. */
    public final float screenToLensDistance;
    /** Radial distortion coefficients of the lenses. */
    public final float k1, k2;
    /** Half field of view through each lens, in degrees. Pixels beyond it are faded out. */
    public final float halfFovDegrees;

    public ViewerProfile(float interLensDistance, float screenToLensDistance, float k1, float k2, float halfFovDegrees) {
        this.interLensDistance = interLensDistance;
        this.screenToLensDistance = screenToLensDistance;
        this.k1 = k1;
        this.k2 = k2;
        this.halfFovDegrees = halfFovDegrees;
    }

    /** Google Cardboard (2015) and most current plastic viewers. */
    public static final ViewerProfile CARDBOARD_V2 = new ViewerProfile(0.064f, 0.039f, 0.34f, 0.55f, 60f);
    /** The original 2014 Google Cardboard. */
    public static final ViewerProfile CARDBOARD_V1 = new ViewerProfile(0.060f, 0.042f, 0.441f, 0.156f, 40f);
    /** Side-by-side without lens correction, for viewers whose lenses barely distort or for checking the image without a viewer. */
    public static final ViewerProfile NO_LENS = new ViewerProfile(0.064f, 0.039f, 0f, 0f, 60f);
    /**
     * cardoO VR Go (model CEGVRH04), from cardoo.co/vr-specs: 40 mm aspherical resin lenses, 90-100° field of view,
     * IPD dial 60-70 mm, focus dial 37.5-46.5 mm screen to lens.
     *
     * <ul>
     *   <li>Lens distance 64 mm: the IPD dial is turned until the two eyes' images merge, which puts the lenses where
     *   the app draws them.</li>
     *   <li>Screen to lens 42 mm: the middle of the focus dial's range.</li>
     *   <li>Half FOV 50°: from the 90-100° field of view.</li>
     *   <li>k1, k2: cardoO doesn't publish them. The lens is the same class as Cardboard v2's (large aspherical
     *   magnifier, about 100° field of view), so it uses those. Inside a 70° virtual screen, Cardboard v1's and v2's
     *   coefficients differ by about 1% at the screen's edge.</li>
     * </ul>
     */
    public static final ViewerProfile CARDOO_VR_GO = new ViewerProfile(0.064f, 0.042f, 0.34f, 0.55f, 50f);

    /** Maps the value stored by the "VR viewer" setting to a profile. Unknown values fall back to Cardboard v2. */
    public static ViewerProfile fromPreference(String value) {
        if ("cardboard_v1".equals(value)) {
            return CARDBOARD_V1;
        }
        if ("cardoo_vr_go".equals(value)) {
            return CARDOO_VR_GO;
        }
        if ("none".equals(value)) {
            return NO_LENS;
        }
        return CARDBOARD_V2;
    }
}
