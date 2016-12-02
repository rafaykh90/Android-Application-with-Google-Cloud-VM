package fi.hut.niksula.mcc_2016_g05_p1;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static fi.hut.niksula.mcc_2016_g05_p1.Constants.EXTRA_SESSION_VMS;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_HOST;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_PORT;
import static fi.hut.niksula.mcc_2016_g05_p1.Constants.SERVER_RESOURCE_LOGIN;

public class LoginScreen extends AppCompatActivity {
    protected static final String LOG_TAG = "MCC-2016-G05-P1-LS";

    protected static CookieManager cookieManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_screen);

        cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((Button) findViewById(R.id.buttonLogin)).setEnabled(true);
    }

    protected String readStream(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];

        reader.read(buffer);
        return new String(buffer);
    }

    protected String httpLogin(String username, String password) throws IOException {
        InputStream is = null;

        try {
            URL url = new URL("http", SERVER_HOST, SERVER_PORT, SERVER_RESOURCE_LOGIN);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.connect();

            OutputStreamWriter os = new OutputStreamWriter(conn.getOutputStream());
            os.write("username=" + username + "&password=" + password);
            os.flush();
            os.close();

            Log.i(LOG_TAG, "Login HTTP response code: " + conn.getResponseCode());

            is = conn.getInputStream();
            int len = Integer.parseInt(conn.getHeaderField("Content-Length"));

            return readStream(is, len);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    protected class LoginTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                return httpLogin(params[0], params[1]);
            } catch (IOException e) {
                return "{\"error\":true}";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i(LOG_TAG, "Backend response: " + result);

            try {
                JSONObject jsonObject = new JSONObject(result);

                if (jsonObject.has("error")) {
                    throw new Exception();
                }

                if (jsonObject.getBoolean("success")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("availableVMs");
                    ApplicationList.VMDescriptor sessionVMS[] = new ApplicationList.VMDescriptor[jsonArray.length()];

                    for (int i=0; i < jsonArray.length(); i++) {
                        JSONObject descriptor = jsonArray.getJSONObject(i);

                        sessionVMS[i] = ApplicationList.VMDescriptor.createInstance(
                                descriptor.getString("vmName"),
                                descriptor.getString("vmDisplayName")
                        );
                    }

                    Intent intent = new Intent(LoginScreen.this, ApplicationList.class);
                    intent.putExtra(EXTRA_SESSION_VMS, sessionVMS);
                    startActivity(intent);
                } else {
                    String msg = getResources().getString(R.string.activity_login_screen_error_credentials);
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();

                    ((Button) findViewById(R.id.buttonLogin)).setEnabled(true);
                }
            } catch (Exception e) {
                String msg = getResources().getString(R.string.generic_error_server);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();

                ((Button) findViewById(R.id.buttonLogin)).setEnabled(true);
            }
        }
    }

    protected String md5(String in) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            digest.reset();
            digest.update(in.getBytes());
            byte[] a = digest.digest();
            int len = a.length;
            StringBuilder sb = new StringBuilder(len << 1);
            for (int i = 0; i < len; i++) {
                sb.append(Character.forDigit((a[i] & 0xf0) >> 4, 16));
                sb.append(Character.forDigit(a[i] & 0x0f, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public void login(View view) {
        String username = ((EditText) findViewById(R.id.editTextUsername)).getText().toString();
        String password = ((EditText) findViewById(R.id.editTextPassword)).getText().toString();

        ((Button) findViewById(R.id.buttonLogin)).setEnabled(false);
        new LoginTask().execute(username, md5(password));
    }
}
