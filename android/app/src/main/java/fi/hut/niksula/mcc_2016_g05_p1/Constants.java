package fi.hut.niksula.mcc_2016_g05_p1;

public class Constants {
    private Constants() {

    }

    public static final String SERVER_HOST = BuildConfig.BACKEND_URI;

    public static final int SERVER_PORT = BuildConfig.BACKEND_PORT;

    public static final String VNC_PASSWORD = "mcc-2016";

    public static final int VNC_PORT = 5901;

    public static final String SERVER_RESOURCE_LOGIN = "login";

    public static final String SERVER_RESOURCE_LOGOUT = "logout";

    public static final String SERVER_RESOURCE_STARTVM = "startvm";

    public static final String SERVER_RESOURCE_STOPVM = "stopvm";

    public static final String EXTRA_VMNAME = "fi.hut.niksula.mcc_2016_g05_p1.VMNAME";

    public static final String EXTRA_RFB_HOST = "fi.hut.niksula.mcc_2016_g05_p1.RFB_HOST";

    public static final String EXTRA_RFB_PORT = "fi.hut.niksula.mcc_2016_g05_p1.RFB_PORT";

    public static final String EXTRA_RFB_PASSWORD = "fi.hut.niksula.mcc_2016_g05_p1.RFB_PASSWORD";

    public static final String EXTRA_SESSION_VMS = "fi.hut.niksula.mcc_2016_g05_p1.SESSION_VMS";
}
