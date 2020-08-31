LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Set your ffmpeg android library path.
# Including include and lib directory.
FFMPEG_PREFIX := ../../ffmpeg-android-library

FFMPEG_INC := ${FFMPEG_PREFIX}/$(TARGET_ARCH_ABI)/include
FFMPEG_LIB := ${FFMPEG_PREFIX}/$(TARGET_ARCH_ABI)/lib

LOCAL_CFLAGS    += -I${FFMPEG_INC}
LOCAL_MODULE    := ffmpeg_h264_decoder_jni
LOCAL_SRC_FILES := ffmpeg_h264_decoder_jni.c ffmpeg_h264_decoder.c mv_tool.c
LOCAL_LDFLAGS   += -llog -L${FFMPEG_LIB} -lavcodec -lavformat -lavutil -lswscale -lswresample

include $(BUILD_SHARED_LIBRARY)
