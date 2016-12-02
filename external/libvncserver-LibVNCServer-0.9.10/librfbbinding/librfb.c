/*
 * LibRFBClient - wrapper over libvnc to encapsulate the cancer.
 */

#include <jni.h>
#include <errno.h>
#include <stdint.h>
#include <stdarg.h>
#include <android/log.h>

#include "rfbclient.h"

#define JNI_EXPORT
#define nullptr NULL
#define LOG_TAG "librfb"
#define LOG_EXTERN_PREFIX "[librfbbind-vnc] "
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[librfbbind] " __VA_ARGS__)

static int RFBTAG_CONTEXTPTR = 0;

struct rfbContext
{
	char* password;
	int framebufferTimestamp;
	int closed;
};

/* Utils */

static char* copyJavaStringToCString (JNIEnv* env, jobject string)
{
	if (string == nullptr)
		return nullptr;

	const char* jniStringPtr = (*env)->GetStringUTFChars(env, string, JNI_FALSE);
	char* cString = strdup(jniStringPtr);
	(*env)->ReleaseStringUTFChars(env, string, jniStringPtr);

	return cString;
}

/* Callbacks */

static char* rfb_cb_GetPassword (rfbClient* cl)
{
	if (!cl)
		return nullptr;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx)
		return nullptr;
	if (!ctx->password)
		return nullptr;
	return strdup(ctx->password);
}

static void rfb_cb_FinishedFrameBufferUpdate (rfbClient* cl)
{
	if (!cl)
		return;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx)
		return;

	ctx->framebufferTimestamp++;
}

/* Hook logging to be android compatible */
extern rfbClientLogProc rfbClientLog;
extern rfbClientLogProc rfbClientErr;

static void rfb_log_vnc (int logClass, const char *format, va_list args)
{
	char* logFormat = malloc(strlen(LOG_EXTERN_PREFIX) + strlen(format) + 1);
	if (!logFormat)
		return;
	memcpy(logFormat, LOG_EXTERN_PREFIX, strlen(LOG_EXTERN_PREFIX));
	memcpy(logFormat + strlen(LOG_EXTERN_PREFIX), format, strlen(format) + 1);

	__android_log_vprint(logClass, LOG_TAG, logFormat, args);

	free(logFormat);
}

static void rfb_hook_log (const char *format, ...)
{
	va_list args;
	va_start(args, format);
	rfb_log_vnc(ANDROID_LOG_INFO, format, args);
	va_end(args);
}

static void rfb_hook_logerr (const char *format, ...)
{
	va_list args;
	va_start(args, format);
	rfb_log_vnc(ANDROID_LOG_ERROR, format, args);
	va_end(args);
}

static void initLib (void)
{
	rfbClientLog = rfb_hook_log;
	rfbClientErr = rfb_hook_logerr;
}

/* LibRFBClient::open */
JNI_EXPORT jlong Java_fi_hut_niksula_librfbclient_LibRFBClient_open (JNIEnv* env, jclass klass, jobject host, jint port, jobject pass)
{
	static int libInitialized = 0;
	if (!libInitialized) {
		initLib();
		libInitialized = 1;
	}

	struct rfbContext* ctx = nullptr;
	rfbClient* cl = nullptr;

	ctx = malloc(sizeof(struct rfbContext));
	if (!ctx)
		goto err;

	ctx->password = copyJavaStringToCString(env, pass);
	ctx->framebufferTimestamp = 0;
	ctx->closed = 0;

	cl = rfbGetClient(8, 3, 4);
	if (!cl)
		goto err;

	LOG("Created empty handle");

	cl->programName = "librfbclient";
	cl->serverHost = copyJavaStringToCString(env, host);
	cl->serverPort = port;
	cl->GetPassword = rfb_cb_GetPassword;
	cl->FinishedFrameBufferUpdate = rfb_cb_FinishedFrameBufferUpdate;

	/* XRGB format */
	cl->format.redShift = 16;
	cl->format.greenShift = 8;
	cl->format.blueShift = 0;

	rfbClientSetClientData(cl, &RFBTAG_CONTEXTPTR, ctx);

	LOG("Begin connect to server");
	if (!cl->serverHost || !ConnectToRFBServer(cl, cl->serverHost, cl->serverPort))
		goto err;

	LOG("Connected, handshaking");
	if (!InitialiseRFBConnection(cl))
		goto err;

	cl->width=cl->si.framebufferWidth;
	cl->height=cl->si.framebufferHeight;

	LOG("Initialized, remote framebuffer size is %d x %d", cl->width, cl->height);
	if (!cl->MallocFrameBuffer(cl))
		goto err;

	LOG("Negotiate format");
	if (!SetFormatAndEncodings(cl))
		goto err;

	cl->updateRect.x = 0;
	cl->updateRect.y = 0;
	cl->updateRect.w = cl->width;
	cl->updateRect.h = cl->height;

	LOG("Handle opened successfully.");
	return (jlong)(intptr_t)cl;

err:
	if (ctx)
	{
		free(ctx->password);
		free(ctx);
	}
	if (cl)
		rfbClientCleanup(cl);

	LOG("Failed to open handle");
	return 0;
}

/* LibRFBClient::close */
JNI_EXPORT void Java_fi_hut_niksula_librfbclient_LibRFBClient_close (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (ctx)
	{
		free(ctx->password);
		free(ctx);
	}
	rfbClientCleanup(cl);

	LOG("Handle closed.");
}

/* LibRFBClient::enqueueMouseClick */
JNI_EXPORT void Java_fi_hut_niksula_librfbclient_LibRFBClient_enqueueMouseClick (JNIEnv* env, jclass klass, jlong handle, jint b, jint x, jint y)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx || ctx->closed)
		return;

	if (!SendPointerEvent(cl, x, y, (1 << b)) || !SendPointerEvent(cl, x, y, 0))
		ctx->closed = 1;
}

/* LibRFBClient::enqueueFetchScreen */
JNI_EXPORT void Java_fi_hut_niksula_librfbclient_LibRFBClient_enqueueFetchScreen (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx || ctx->closed)
		return;

	if (!SendFramebufferUpdateRequest(cl, 0, 0, cl->width, cl->height, TRUE))
		ctx->closed = 1;
}

/* LibRFBClient::pumpEventLoop */
JNI_EXPORT void Java_fi_hut_niksula_librfbclient_LibRFBClient_pumpEventLoop (JNIEnv* env, jclass klass, jlong handle, jlong pollTimeoutUs)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx || ctx->closed)
		return;

	int selectResult;
	selectResult = WaitForMessage(cl, pollTimeoutUs);

	if (selectResult == 0)
	{
		/* timeout, that is fine. No need to continue */
		return;
	}
	else if (selectResult < 0)
	{
		/* error, allow only interrupt */
		if (errno == EINTR)
			return;

		ctx->closed = 1;
		return;
	}

	/* handle message */

	if (!HandleRFBServerMessage(cl))
		ctx->closed = 1;
}

/* LibRFBClient::getError */
JNI_EXPORT jboolean Java_fi_hut_niksula_librfbclient_LibRFBClient_getError (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return JNI_TRUE;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx || ctx->closed)
		return JNI_TRUE;

	return JNI_FALSE;
}

/* LibRFBClient::getFramebufferTimestamp */
JNI_EXPORT jint Java_fi_hut_niksula_librfbclient_LibRFBClient_getFramebufferTimestamp (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return 0;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx)
		return 0;
	return ctx->framebufferTimestamp;
}

/* LibRFBClient::getFramebufferWidth */
JNI_EXPORT jint Java_fi_hut_niksula_librfbclient_LibRFBClient_getFramebufferWidth (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return 0;
	return cl->width;
}

/* LibRFBClient::getFramebufferHeight */
JNI_EXPORT jint Java_fi_hut_niksula_librfbclient_LibRFBClient_getFramebufferHeight (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return 0;
	return cl->height;
}

/* LibRFBClient::copyFramebufferARGB8 */
JNI_EXPORT jintArray Java_fi_hut_niksula_librfbclient_LibRFBClient_copyFramebufferARGB8 (JNIEnv* env, jclass klass, jlong handle)
{
	rfbClient* cl = (rfbClient*)(intptr_t)handle;
	if (!cl)
		return nullptr;

	struct rfbContext* ctx = rfbClientGetClientData(cl, &RFBTAG_CONTEXTPTR);
	if (!ctx)
		return nullptr;

	jintArray dataArray = (*env)->NewIntArray(env, cl->width * cl->height);
	if (!dataArray)
		return nullptr;

	uint32_t* storage = (*env)->GetIntArrayElements(env, dataArray, nullptr);
	if (!storage)
	{
		(*env)->DeleteLocalRef(env, dataArray);
		return nullptr;
	}

	memcpy(storage, cl->frameBuffer, cl->width * cl->height * 4);

	/* Convert XRGB to ARGB */
	int ndx;
	for (ndx = 0; ndx < cl->width * cl->height; ++ndx)
		storage[ndx] |= 0xFF000000u;

	(*env)->ReleaseIntArrayElements(env, dataArray, storage, 0);
	return dataArray;
}
