package fi.hut.niksula.librfbclient;

import java.lang.InterruptedException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import android.graphics.Bitmap;

/**
 * Implement promise manually as java.util.concurrent.CompletableFuture is unavailable.
 */

/* package */ class RFBBitmapPromise implements Future<Bitmap>
{
	private final Object m_lock = new Object();
	private boolean m_cancelled = false;
	private boolean m_completed = false;
	private Bitmap m_result = null;

	public boolean cancel (boolean mayInterruptIfRunning) {
		synchronized (m_lock) {
			m_cancelled = true;
			return !m_completed;
		}
	}

	public Bitmap get () throws InterruptedException, CancellationException {
		synchronized (m_lock) {
			while (!m_cancelled && !m_completed)
				m_lock.wait();
			if (m_completed)
				return m_result;
			throw new CancellationException();
		}
	}

	public Bitmap get (long timeout, TimeUnit unit) throws InterruptedException,
			CancellationException {
		long timeToWait = TimeUnit.MILLISECONDS.convert(timeout, unit);
		synchronized (m_lock) {
			for (;;) {
				if (m_cancelled ||  m_completed)
					break;
				if (timeToWait <= 0)
					break;

				long startTime = System.nanoTime();
				m_lock.wait(timeToWait);
				long waited = (System.nanoTime() - startTime + 999_999) / 1_000_000;
				timeToWait -= waited;
			}
			if (m_completed)
				return m_result;
			throw new CancellationException();
		}
	}

	public boolean isCancelled () {
		synchronized (m_lock) {
			return m_cancelled;
		}
	}

	public boolean isDone () {
		synchronized (m_lock) {
			return m_cancelled || m_completed;
		}
	}

	/* package */ void reject () {
		synchronized (m_lock) {
			m_cancelled = true;
			m_lock.notifyAll();
		}
	}

	/* package */ void resolve (Bitmap bitmap) {
		synchronized (m_lock) {
			m_completed = true;
			m_result = bitmap;
			m_lock.notifyAll();
		}
	}
}
