package fi.hut.niksula.mcc_2016_g05_p1;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import fi.hut.niksula.librfbclient.RFBClient;


public class RemoteFramebuffer extends AppCompatActivity {
    private static final String LOG_TAG = "MCC-2016-G05-P1-RFB";
    private boolean m_closeRequested = false;
    private boolean m_activityRunning = false;
    private Thread m_workerThread = null;

    static class VNCTouchEvent {
        float relativeX;
        float relativeY;
        int button;
    }
    private ArrayList<VNCTouchEvent> m_touchQueue = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_framebuffer);

        ActionBar bar = getSupportActionBar();
        if (bar != null)
            bar.setDisplayHomeAsUpEnabled(true);

        // Touch handler
        final ImageView remoteFBImage = (ImageView)findViewById(R.id.remoteFBImage);
        remoteFBImage.setOnTouchListener(new View.OnTouchListener() {
            boolean isDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !isDown)  {
                    Drawable image = remoteFBImage.getDrawable();
                    if (image == null)
                        return false;
                    if (image.getBounds().width() == 0 || image.getBounds().height() == 0)
                        return false;

                    float contentAspectRatio = image.getBounds().height() / (float)image.getBounds().width();
                    float containerAspectRatio = remoteFBImage.getHeight() / (float)remoteFBImage.getWidth();
                    float relativeX;
                    float relativeY;

                    if (contentAspectRatio > containerAspectRatio) {
                        // Content is height limited
                        float Xpadding = (remoteFBImage.getWidth() - (remoteFBImage.getHeight() / contentAspectRatio)) / 2.0f;
                        relativeX = (event.getX() - Xpadding) / (remoteFBImage.getHeight() / contentAspectRatio);
                        relativeY = (event.getY()) / (float)remoteFBImage.getHeight();
                    } else {
                        float Ypadding = (remoteFBImage.getHeight() - (remoteFBImage.getWidth() * contentAspectRatio)) / 2.0f;
                        relativeX = event.getX() / (float)remoteFBImage.getWidth();
                        relativeY = (event.getY() - Ypadding) / (remoteFBImage.getWidth() * contentAspectRatio);
                    }
                    if (relativeX < 0 || relativeX > 1 || relativeY < 0 || relativeY > 1)
                        return false;

                    VNCTouchEvent vncEvent = new VNCTouchEvent();
                    vncEvent.button = 0; // primary
                    vncEvent.relativeX = relativeX;
                    vncEvent.relativeY = relativeY;

                    synchronized (m_touchQueue) {
                        // If we would buffer up too many events, just drop them
                        if (m_touchQueue.size() < 4)
                            m_touchQueue.add(vncEvent);
                    }
                    isDown = true;
                    return true;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    isDown = false;
                    return true;
                }
                return false;
            }
        });

        // Open connection
        final Intent intent = getIntent();
        final String rfbHost = intent.getStringExtra(Constants.EXTRA_RFB_HOST);
        final int rfbPort = intent.getIntExtra(Constants.EXTRA_RFB_PORT, -1);
        final String rfbPass = intent.getStringExtra(Constants.EXTRA_RFB_PASSWORD);

        m_workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                workerLoop(rfbHost, rfbPort, rfbPass);
            }
        });
        m_workerThread.start();

        ApplicationList.registerRemoteFramebufferActivity(this);
    }

    @Override
    protected void onDestroy () {
        ApplicationList.unregisterRemoteFramebufferActivity(this);

        // Close connection
        m_closeRequested = true;
        try {
            // Wait max 500 ms for worker to terminate. Cleaning resources
            // before destroy is nice thing to do, but not at the expense of
            // whole app's responsivity.
            // \todo[jarkko]: Consider lowering timeout
            int maxJoinTimeMs = 500; // ms
            m_workerThread.join(maxJoinTimeMs);
        } catch (InterruptedException ex) {
            m_workerThread.interrupt();
            Thread.currentThread().interrupt();
        }
        super.onDestroy();
    }

    @Override
    protected void onStart () {
        super.onStart();
        // Enable screen fetching
        m_activityRunning = true;
    }

    @Override
    protected void onStop () {
        // Disable screen fetching, just keep conn alive
        m_activityRunning = false;
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, ApplicationList.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    private void workerLoop (String host, int port, String password) {

        // Util tasks (lambdas are not supported)
        class PostImageTask implements Runnable {
            private final Bitmap m_nextFrameBitmap;

            private PostImageTask (Bitmap nextFrameBitmap) {
                m_nextFrameBitmap = nextFrameBitmap;
            }

            @Override
            public void run() {
                ImageView dst = (ImageView)findViewById(R.id.remoteFBImage);
                if (dst == null)
                    return;
                if (m_nextFrameBitmap != null)
                    dst.setImageBitmap(m_nextFrameBitmap);
                else
                    dst.setImageResource(android.R.color.transparent);
            }
        }
        class PostConnectingStatusTask implements Runnable {
            private final boolean m_status;

            private PostConnectingStatusTask (boolean status) {
                m_status = status;
            }

            @Override
            public void run() {
                ProgressBar dst = (ProgressBar)findViewById(R.id.connectingSpinner);
                if (dst != null)
                    dst.setVisibility(m_status ? View.VISIBLE : View.INVISIBLE);
            }
        }
        class PostStatusTask implements Runnable {
            private final int m_resId;

            private PostStatusTask (int resId) {
                m_resId = resId;
            }

            @Override
            public void run() {
                TextView text = (TextView)findViewById(R.id.statusText);
                if (text != null)
                    text.setText(m_resId);
            }
        }

        RFBClient cl;
        Future<Bitmap> nextFrame = null;

        runOnUiThread(new PostStatusTask(R.string.activity_remote_app_status_connecting));

        try {
            cl = new RFBClient(host, port, password);
        } catch (IOException ex) {
            Log.d(LOG_TAG, "failed to connect");

            runOnUiThread(new PostStatusTask(R.string.activity_remote_app_status_error));
            runOnUiThread(new PostConnectingStatusTask(false));
            return;
        }

        synchronized (m_touchQueue) {
            m_touchQueue.clear();
        }

        boolean firstBitmap = true;
        runOnUiThread(new PostStatusTask(R.string.activity_remote_app_status_syncing));

        for (;;) {
            // Kill if requested
            if (m_closeRequested)
                break;
            if (cl.isClosed())
                break;

            // If frame is completed, post it to screen
            if (nextFrame != null && nextFrame.isDone()) {

                // \note: Using outdated "J"DK -> need to use temp var to assign to final
                Bitmap nextFrameBitmapTmp;
                final Bitmap nextFrameBitmap;

                try {
                    nextFrameBitmapTmp = nextFrame.get();
                } catch (ExecutionException|InterruptedException ex) {
                    Log.d(LOG_TAG, "Image fetch failed");
                    nextFrameBitmapTmp = null;
                }
                nextFrameBitmap = nextFrameBitmapTmp;

                if (nextFrameBitmap != null)
                    runOnUiThread(new PostImageTask(nextFrameBitmap));

                nextFrame = null;

                // Mixing some UI logic to transport logic, yay
                if (firstBitmap) {
                    firstBitmap = false;
                    runOnUiThread(new PostConnectingStatusTask(false));
                    runOnUiThread(new PostStatusTask(R.string.activity_remote_app_status_connected));
                }
            }

            if (nextFrame == null && m_activityRunning)
                nextFrame = cl.enqueueFetchScreen();

            ArrayList<VNCTouchEvent> pendingTouches;
            synchronized (m_touchQueue) {
                pendingTouches = (ArrayList<VNCTouchEvent>) m_touchQueue.clone();
                m_touchQueue.clear();
            }
            for (VNCTouchEvent touch : pendingTouches)
                cl.enqueueMouseClick(touch.button, touch.relativeX, touch.relativeY);

            cl.updateEventLoop();
        }
        cl.close();

        // Show error if unscheduled teardown
        if (!m_closeRequested) {
            runOnUiThread(new PostImageTask(null));
            runOnUiThread(new PostStatusTask(R.string.activity_remote_app_status_error));
        }
    }
}
