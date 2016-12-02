LOCAL_PATH := $(call my-dir)/..
include $(CLEAR_VARS)

LOCAL_MODULE := libvncclient-native
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/common \
	$(LOCAL_PATH)/rfb \
	$(LOCAL_PATH)/../jpeg-9b
LOCAL_SRC_FILES := \
	libvncclient/cursor.c \
	libvncclient/rfbproto.c \
	libvncclient/sockets.c \
	libvncclient/tls_none.c \
	libvncclient/vncviewer.c \
	common/minilzo.c \
	librfbbinding/librfb.c \
	librfbbinding/librfb_workarounds_listen.c

# \note: For some reason libvnc uses -Wall but it doesn't actually build cleanly with it.
#        Work around by disabling some warnings.

LOCAL_CFLAGS := -O2 -W -Wall -Wno-unused-parameter -Wno-sign-compare -Wno-pointer-sign
LOCAL_LDLIBS := -lz -llog
LOCAL_STATIC_LIBRARIES = jpeg-native

TARGET_PLATFORM := android-24
include $(BUILD_SHARED_LIBRARY)

# \todo: Can't be arsed to configure NDK_MODULE_PATH so just refer to modules directly
include $(LOCAL_PATH)/../jpeg-9b/jni/Android.mk
