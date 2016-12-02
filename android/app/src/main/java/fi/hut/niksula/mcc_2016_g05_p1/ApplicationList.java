package fi.hut.niksula.mcc_2016_g05_p1;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import android.support.design.widget.Snackbar;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;
import static android.support.design.widget.Snackbar.LENGTH_INDEFINITE;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
import static com.google.android.gms.location.Geofence.NEVER_EXPIRE;
import static com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE;
import static com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES;
import static com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS;
import static com.google.android.gms.location.GeofencingRequest.INITIAL_TRIGGER_ENTER;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_RFB_HOST;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_RFB_PASSWORD;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_RFB_PORT;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_SESSION_VMS;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_VMNAME;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_HOST;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_PORT;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_RESOURCE_LOGOUT;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_RESOURCE_STARTVM;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_RESOURCE_STOPVM;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.VNC_PASSWORD;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.VNC_PORT;

public class ApplicationList extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status> {

    protected static final String LOG_TAG = "MCC-2016-G05-P1-AL";
    protected static final String INTENT_GEOFENCE_TRANSITIONS ="GeofenceTransition";

    protected static final float GEOFENCE_RADIUS_IN_METERS = 75;
    protected static final LatLng TBLDG_LATLANG = new LatLng(60.186965, 24.821457);
    protected static final String TBLDG_ID = "T-Building";

    protected GoogleApiClient mGoogleApiClient;

    protected BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG, "Handling geofence transition");

            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

            if (geofencingEvent.hasError()) {
                logGeofenceError(geofencingEvent.getErrorCode());
                return;
            }

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            int geofenceTransition = geofencingEvent.getGeofenceTransition();

            for (Geofence geofence : triggeringGeofences) {
                if (geofenceTransition == GEOFENCE_TRANSITION_ENTER) {
                    Log.i(LOG_TAG, "Entered " + geofence.getRequestId());
                    recreateLayout("openoffice");
                } else if (geofenceTransition == GEOFENCE_TRANSITION_EXIT) {
                    Log.i(LOG_TAG, "Exited " + geofence.getRequestId());
                    recreateLayout(null);
                }
            }
        }
    };

    // Static because this is MVP
    public static class VMDescriptor implements Serializable {
        protected String name;
        protected String displayName;

        public static VMDescriptor createInstance (String name, String displayName){
            // Helper function to work around java (lack of) features. In many cases, you cannot use
            // "new VMDescriptor(){{ name=foo display=bar }} since the outer class "this" is
            // implicitly captured. (and it might not be serializable or it might be Serializable
            // but implemented as screw-you-and-your-type-system-I'll-just-throw-NotSupported).
            VMDescriptor vmd = new VMDescriptor();
            vmd.name = name;
            vmd.displayName = displayName;
            return vmd;
        }
    }
    private static class VMState {
        protected String vmName;
        protected String vmDisplayName;
        protected boolean isRunning = false;
        protected int controlId = 0;
        protected String rfbHost;
        protected int rfbPort = VNC_PORT;
        protected String rfbPass = VNC_PASSWORD;
    }
    static VMState s_vms[] = {};
    /* package */ static ArrayList<RemoteFramebuffer> s_activeFramebuffers = new ArrayList<>();

    boolean sessionLogoutStarted = false;
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_application_list);

        CookieHandler.setDefault(LoginScreen.cookieManager);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        final VMDescriptor descriptors[] = (VMDescriptor[])getIntent().getSerializableExtra(EXTRA_SESSION_VMS);
        s_vms = new VMState[descriptors.length];
        for (int ndx = 0; ndx < descriptors.length; ++ndx)
        {
            s_vms[ndx] = new VMState();
            s_vms[ndx].vmName = descriptors[ndx].name;
            s_vms[ndx].vmDisplayName = descriptors[ndx].displayName;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

        IntentFilter filter = new IntentFilter(INTENT_GEOFENCE_TRANSITIONS);
        registerReceiver(mBroadcastReceiver, filter);
        recreateLayout(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(LOG_TAG, "GoogleApiClient connection started");

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            requestLocationPermission();
        } else {
            setupLocationServices();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(LOG_TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(LOG_TAG, "GoogleApiClient connection failed:" + connectionResult.getErrorMessage());
    }

    @Override
    public void onResult(@NonNull Status status) {
        if (status.isSuccess()) {
            Log.i(LOG_TAG, "Successfully added geofence");
        } else {
            int errorCode = status.getStatusCode();
            logGeofenceError(errorCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (grantResults.length == 1 && grantResults[0] == PERMISSION_GRANTED) {
            setupLocationServices();
        }
    }

    protected void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_FINE_LOCATION)) {
            Snackbar.make(findViewById(R.id.activity_application_list_logout_btn), R.string.permission_location_rationale, LENGTH_INDEFINITE)
                    .setAction(R.string.generic_ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(ApplicationList.this, new String[]{ACCESS_FINE_LOCATION}, 0);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, 0);
        }
    }

    protected void setupLocationServices() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            Geofence geofence = new Geofence.Builder()
                    .setRequestId(TBLDG_ID)
                    .setCircularRegion(TBLDG_LATLANG.latitude, TBLDG_LATLANG.longitude, GEOFENCE_RADIUS_IN_METERS)
                    .setExpirationDuration(NEVER_EXPIRE)
                    .setTransitionTypes(GEOFENCE_TRANSITION_ENTER | GEOFENCE_TRANSITION_EXIT)
                    .build();

            GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                    .setInitialTrigger(INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build();

            Intent intent = new Intent(INTENT_GEOFENCE_TRANSITIONS);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, FLAG_UPDATE_CURRENT);

            LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, geofencingRequest, pendingIntent)
                    .setResultCallback(this);
        }
    }

    protected void logGeofenceError(int errorCode) {
        switch (errorCode) {
            case GEOFENCE_NOT_AVAILABLE:
                Log.e(LOG_TAG, "Error: GEOFENCE_NOT_AVAILABLE");
                return;
            case GEOFENCE_TOO_MANY_GEOFENCES:
                Log.e(LOG_TAG, "Error: GEOFENCE_TOO_MANY_GEOFENCES");
                return;
            case GEOFENCE_TOO_MANY_PENDING_INTENTS:
                Log.e(LOG_TAG, "Error: GEOFENCE_TOO_MANY_PENDING_INTENTS");
                return;
            default:
                Log.e(LOG_TAG, "Error:" + errorCode);
                return;
        }
    }

    protected void recreateLayout (String priorityVMName) {
        LinearLayout linearLayoutMain = (LinearLayout) findViewById(R.id.linearLayoutMain);
        View logoutButton = linearLayoutMain.findViewById(R.id.activity_application_list_logout_btn);

        LayoutInflater layoutInflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        linearLayoutMain.removeAllViews();
        for (VMState vm : s_vms) {
            View vmView = layoutInflater.inflate(R.layout.app_list_app, linearLayoutMain, false);
            vmView.setTag(vm);

            // Replace placeholder id
            vm.controlId = View.generateViewId();
            vmView.findViewById(R.id.app_list_app_placeholder_control_id).setId(vm.controlId);

            ((TextView)vmView.findViewById(R.id.app_list_app_text)).setText(vm.vmDisplayName);

            if (vm.vmName.equals(priorityVMName))
                linearLayoutMain.addView(vmView, 0);
            else
                linearLayoutMain.addView(vmView);
        }
        linearLayoutMain.addView(logoutButton);

        updateControlUI();
    }

    protected String readStream(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];

        reader.read(buffer);
        return new String(buffer);
    }

    protected String httpGet(String serverResource, String resourceParam) throws IOException {
        InputStream is = null;

        try {
            URL url = new URL("http", SERVER_HOST, SERVER_PORT, serverResource + resourceParam);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(120000);
            conn.setConnectTimeout(120000); // 2 mins
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();

            Log.i(LOG_TAG, "HTTP resource: " + serverResource + " parameter: " + resourceParam +
                    " response code: " + conn.getResponseCode());

            is = conn.getInputStream();
            int len = Integer.parseInt(conn.getHeaderField("Content-Length"));

            return readStream(is, len);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private interface IHTTPJSONResponseHandler {
        void onResponse (JSONObject jsonOrNull);
    }
    protected class HttpTask extends AsyncTask<String, Void, String> {
        IHTTPJSONResponseHandler cb;

        HttpTask(IHTTPJSONResponseHandler cb_) {
            cb = cb_;
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                if (params.length > 1) {
                    return httpGet(params[0], "/" + params[1]);
                } else {
                    return httpGet(params[0], "");
                }
            } catch (IOException e) {
                return "{\"error\":true}";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i(LOG_TAG, "Backend response: " + result);

            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }

            try {
                JSONObject jsonObject = new JSONObject(result);

                if (jsonObject.has("error")) {
                    throw new Exception();
                }

                if (cb != null)
                    cb.onResponse(jsonObject);
            } catch (Exception e) {
                String msg = getResources().getString(R.string.generic_error_server);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                if (cb != null)
                    cb.onResponse(null);
            }
        }
    }

    private VMState getControllerVM (View view) {
        if (view == null) {
            return null;
        }
        if (view.getTag() != null) {
            return (VMState)view.getTag();
        }
        for (VMState vm : s_vms) {
            if (vm.controlId == view.getId()) {
                return vm;
            }
        }
        return null;
    }

    private void switchToVM (VMState vm) {
        // \todo: use proper values
        Intent intent = new Intent(this, RemoteFramebuffer.class);
        intent.putExtra(EXTRA_RFB_HOST, vm.rfbHost);
        intent.putExtra(EXTRA_RFB_PORT, vm.rfbPort);
        intent.putExtra(EXTRA_RFB_PASSWORD, vm.rfbPass);
        intent.putExtra(EXTRA_VMNAME, vm.vmName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.setData(Uri.fromParts("content", vm.vmName, null));
        startActivity(intent);
    }

    private void beginStartVM (final VMState vm) {
        String title = getResources().getString(R.string.activity_application_list_loading_title);
        String msg = getResources().getString(R.string.activity_application_list_loading_message);
        progressDialog = ProgressDialog.show(ApplicationList.this, title, msg, true);

        IHTTPJSONResponseHandler onDoneCB = new IHTTPJSONResponseHandler(){
            @Override
            public void onResponse (JSONObject response) {
                if (response == null)
                    return;

                vm.isRunning = true;
                updateControlUI();

                try {
                    vm.rfbHost = response.getString("externalIP");
                } catch (JSONException e) {
                    return;
                }

                switchToVM(vm);
            }
        };
        new HttpTask(onDoneCB).execute(SERVER_RESOURCE_STARTVM, vm.vmName);
    }

    private void beginKillVM (final VMState vm) {
        String title = getResources().getString(R.string.activity_application_list_loading_title);
        String msg = getResources().getString(R.string.activity_application_list_loading_message);
        progressDialog = ProgressDialog.show(ApplicationList.this, title, msg, true);

        IHTTPJSONResponseHandler onDoneCB = new IHTTPJSONResponseHandler() {
            @Override
            public void onResponse(JSONObject response) {
                if (response == null)
                    return;

                vm.isRunning = false;
                updateControlUI();
            }
        };
        new HttpTask(onDoneCB).execute(SERVER_RESOURCE_STOPVM, vm.vmName);

        // Kill activities
        ArrayList<RemoteFramebuffer> obsoleteRfbs = new ArrayList<>();
        for (RemoteFramebuffer rfb : s_activeFramebuffers) {
            if (vm.vmName.equals(rfb.getIntent().getStringExtra(EXTRA_VMNAME))) {
                obsoleteRfbs.add(rfb);
            }
        }
        for (RemoteFramebuffer rfb : obsoleteRfbs) {
            rfb.finish();
        }
    }

    private void beginSessionLogout () {
        if (sessionLogoutStarted)
            return;
        sessionLogoutStarted = true;

        killAllRemoteFramebufferActivities();

        new HttpTask(null).execute(SERVER_RESOURCE_LOGOUT);
    }

    private void updateControlUI (){
        // Update button UI based on VM state
        for (VMState vm : s_vms) {
            ImageButton btn = ((ImageButton) findViewById(vm.controlId));
            if (btn == null)
                continue;
            btn.setImageResource(vm.isRunning ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_media_play);
        }
    }

    public void clickHandler(View view) {
        final VMState vm = getControllerVM(view);
        if (vm == null)
            return;

        if (progressDialog != null)
            return;

        if (vm.isRunning) {
            switchToVM(vm);
        } else {
            beginStartVM(vm);
        }
    }

    public void controlClickHandler (View view) {
        final VMState vm = getControllerVM(view);
        if (vm == null)
            return;

        if (progressDialog != null)
            return;

        if (vm.isRunning) {
            beginKillVM(vm);
        } else {
            beginStartVM(vm);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        beginSessionLogout();
    }
    @Override
    protected void onDestroy () {
        super.onDestroy();
        beginSessionLogout();
    }
    @Override
    protected void onResume () {
        super.onResume();
        updateControlUI();
    }

    public void doLogout (View view) {
        beginSessionLogout();

        Intent intent = new Intent(this, LoginScreen.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /* package */ static void registerRemoteFramebufferActivity (RemoteFramebuffer rfb) {
        s_activeFramebuffers.add(rfb);
    }
    /* package */ static void unregisterRemoteFramebufferActivity (RemoteFramebuffer rfb) {
        s_activeFramebuffers.remove(rfb);

        // Returning from RemoteFramebuffer implicitly kills the VM
        for (VMState vm : s_vms) {
            if (vm.vmName.equals(rfb.getIntent().getStringExtra(EXTRA_VMNAME))) {
                vm.isRunning = false;
                // updateControlUI is called onResume
            }
        }
    }
    private static void killAllRemoteFramebufferActivities () {
        ArrayList<RemoteFramebuffer> rfbs = s_activeFramebuffers;
        s_activeFramebuffers = new ArrayList<>();

        for (RemoteFramebuffer rfb : rfbs) {
            rfb.finish();
        }
    }
}
