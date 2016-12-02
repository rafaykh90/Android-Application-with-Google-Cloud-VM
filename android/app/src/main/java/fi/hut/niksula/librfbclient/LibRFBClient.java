package fi.hut.niksula.librfbclient;

import java.lang.String;


/* package */ class LibRFBClient
{
	static
	{
		System.loadLibrary("vncclient-native");
	}

	static native long open (String host, int port, String password);
	static native void close (long handle);
	static native void enqueueMouseClick (long handle, int b, int x, int y);
	static native void enqueueFetchScreen (long handle);
	static native void pumpEventLoop (long handle, long pollTimeUs);
	static native boolean getError (long handle);

	static native int getFramebufferTimestamp (long handle);
	static native int getFramebufferWidth (long handle);
	static native int getFramebufferHeight (long handle);
	static native int[] copyFramebufferARGB8 (long handle);
};
