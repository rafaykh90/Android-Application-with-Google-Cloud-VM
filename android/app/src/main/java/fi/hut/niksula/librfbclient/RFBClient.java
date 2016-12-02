package fi.hut.niksula.librfbclient;

import java.io.IOException;
import java.lang.String;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import android.graphics.Bitmap;


public class RFBClient
{
	private static final int POLL_TIMEOUT_US = 200_000; // 200 ms
	private long m_handle;
	private int m_lastScreenFetchTimestamp = 0;
	private ArrayList<RFBBitmapPromise> m_pendingFetches = new ArrayList<>();

	public RFBClient (String host, int port, String password) throws IOException {
		m_handle = LibRFBClient.open(host, port, password);
		if (m_handle == 0)
			throw new IOException("cannot open RFB connection");
	}

	public void enqueueMouseClick (int button, float rx, float ry) {
		int x = (int)(rx * LibRFBClient.getFramebufferWidth(m_handle));
		int y = (int)(ry * LibRFBClient.getFramebufferHeight(m_handle));
		LibRFBClient.enqueueMouseClick(m_handle, button, x, y);
	}

	public Future<Bitmap> enqueueFetchScreen () {
		LibRFBClient.enqueueFetchScreen(m_handle);

		final RFBBitmapPromise fetch = new RFBBitmapPromise();
		m_pendingFetches.add(fetch);
		return fetch;
	}

	public void updateEventLoop () {
		LibRFBClient.pumpEventLoop(m_handle, POLL_TIMEOUT_US);
		if (LibRFBClient.getError(m_handle)) {
			// conn is dead
			close();
			return;
		}
		// poll if we have response to screen fetch
		int framebufferTime = LibRFBClient.getFramebufferTimestamp(m_handle);
		if (framebufferTime != m_lastScreenFetchTimestamp) {
			m_lastScreenFetchTimestamp = framebufferTime;
			resolvePendingFetches();
		}
	}

	public boolean isClosed () {
		return m_handle == 0;
	}

	public void close () {
		LibRFBClient.close(m_handle);
		m_handle = 0;
		rejectPendingFetches();
	}

	private void rejectPendingFetches () {
		ArrayList<RFBBitmapPromise> pendingFetches = m_pendingFetches;
		m_pendingFetches = new ArrayList<>();

		for (RFBBitmapPromise pendingFetch : pendingFetches)
			pendingFetch.reject();
	}

	private void resolvePendingFetches () {
		int remoteFBWidth = LibRFBClient.getFramebufferWidth(m_handle);
		int remoteFBHeight = LibRFBClient.getFramebufferHeight(m_handle);
		int[] remoteFBPixels = LibRFBClient.copyFramebufferARGB8(m_handle);

		if (remoteFBPixels == null) {
			rejectPendingFetches();
			return;
		}
		Bitmap remoteFB = Bitmap.createBitmap(remoteFBPixels, remoteFBWidth, remoteFBHeight,
				Bitmap.Config.ARGB_8888);

		ArrayList<RFBBitmapPromise> pendingFetches = m_pendingFetches;
		m_pendingFetches = new ArrayList<>();

		for (RFBBitmapPromise pendingFetch : pendingFetches)
			pendingFetch.resolve(remoteFB);
	}
};
