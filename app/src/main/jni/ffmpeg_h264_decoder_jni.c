
#include <jni.h>
#include "android_log.h"
#include "ffmpeg_h264_decoder.h"

#define __FUNC_NAME_LINK__(a, b, c) a##b##c
#define FUNC_NAME_LINK(a, b)        __FUNC_NAME_LINK__(a, _, b)

#define JAVA_PACKAGE_NAME Java_com_example_androidh264codecproject_decoder_FFmpegAVCDecoder
#define JNI_FUNC(a) FUNC_NAME_LINK(JAVA_PACKAGE_NAME, a)

#define EXTERN_C extern "C"

// For C, undefine EXTERN_C
#undef  EXTERN_C
#define EXTERN_C

EXTERN_C jlong JNI_FUNC(nativeInit)
(
    JNIEnv *env,
    jobject thisObject,
    jint    video_width,
    jint    video_height
)
{
    FFmpegDecodeContext *c;

    c = alloc_ffmpeg_decode_context();
    init_ffmpeg_h264_decode_context(c, video_width, video_height);

    LOGD("Init ffmpeg h264 decoder success");

    // return reinterpret_cast<jlong>(c);
    return (jlong) c;
}

EXTERN_C void JNI_FUNC(nativeFree)
(
    JNIEnv *env,
    jobject thisObject,
    jlong   handle
)
{
    FFmpegDecodeContext *c;

    // c = reinterpret_cast<FFmpegDecodeContext*>(handle);
    c = (FFmpegDecodeContext*) handle;

    free_ffmpeg_decode_context(c);
}

EXTERN_C jboolean JNI_FUNC(nativeDecodeFrame)
(
    JNIEnv *env,
    jobject thisObject,
    jlong   handle,
    jbyteArray packetData
)
{
    jint packetLength;
    int cur_pkg_len, cp_len;
    int ret;
    uint8_t *pkg_data, *cur_pkg_ptr;
    FFmpegDecodeContext *c;
    // c = reinterpret_cast<FFmpegDecodeContext*>(handle);
    c = (FFmpegDecodeContext*) handle;

    packetLength = (*env)->GetArrayLength(env, packetData);
    // pkg_data     = reinterpret_cast<uint8_t*>(malloc(packetLength));
    pkg_data     = (uint8_t*) malloc(packetLength);
    (*env)->GetByteArrayRegion(env, packetData,
                               0, packetLength,
                               (jbyte*) pkg_data);
    cur_pkg_ptr = pkg_data;
    cur_pkg_len = packetLength;

    //LOGD("Packet receive");

    c->got_frame = 0;

    do {
        //LOGD("Packet length %d", cur_pkg_len);

        cp_len = (cur_pkg_len >= INPUT_BUFFER_SIZE) ?
                    INPUT_BUFFER_SIZE : cur_pkg_len;

        memset(c->in_buffer, 0, INPUT_BUFFER_SIZE);
        memcpy(c->in_buffer, cur_pkg_ptr, cp_len);

        cur_pkg_ptr += cp_len;
        cur_pkg_len -= cp_len;

        c->cur_size = cp_len;
        c->cur_ptr  = c->in_buffer;

        ret = decode_frame(c);
        if (ret < 0) {
            LOGE("Fail to decode frame\n");
            break;
        }
    } while (cur_pkg_len);

    /**
    if (c->got_frame) {
        LOGD("Got a decoded frame, frame count %d\n", c->got_frame_count);
    } else {
        LOGD("Not got a decoded frame\n");
    }**/

    free(pkg_data);

    return (jboolean) c->got_frame;
}

EXTERN_C jboolean JNI_FUNC(nativeGetMotionVectorMapData)
(
    JNIEnv  *env,
    jobject thisObject,
    jlong   handle,
    jintArray mvArrayData
)
{
    FFmpegDecodeContext *c;
    MotionVectorMap     *mv_map;

    //c = reinterpret_cast<FFmpegDecodeContext*>(handle);
    c = (FFmpegDecodeContext*) handle;

    mv_map = get_motion_vector_map(c);

    //LOGD("Have mv map: %d", mv_map != NULL);

    if (mv_map) {
        (*env)->SetIntArrayRegion(
            env, mvArrayData, 0, (*env)->GetArrayLength(env, mvArrayData), mv_map->data);

        free_motion_vector_map(mv_map);

        return (jboolean) 1;
    } else {
        return (jboolean) 0;
    }
}

EXTERN_C jint JNI_FUNC(nativeGetMotionVectorListCount)
(
    JNIEnv  *env,
    jobject thisObject,
    jlong   handle
)
{
    return ((jint) get_motion_vector_list_count((FFmpegDecodeContext*) handle));
}

EXTERN_C void JNI_FUNC(nativeGetMotionVectorList)
(
    JNIEnv  *env,
    jobject thisObject,
    jlong   handle,
    jintArray mvListData
)
{
    jsize  data_length;
    size_t data_size;
    int    *mv_list_data;
    FFmpegDecodeContext *c;

    c = (FFmpegDecodeContext*) handle;

    data_length = (*env)->GetArrayLength(env, mvListData);
    data_size   = data_length * sizeof(int);

    mv_list_data = (int*) malloc(data_size);

    get_motion_vector_list(c, mv_list_data);

    (*env)->SetIntArrayRegion(
            env, mvListData, 0, data_length, mv_list_data);

    free(mv_list_data);
}

EXTERN_C void JNI_FUNC(nativeGetResidualMapData)
(
    JNIEnv  *env,
    jobject thisObject,
    jlong   handle,
    jbyteArray resMapData
)
{
    // Nothing to do
}

EXTERN_C void JNI_FUNC(nativeGetYUVFrameData)
(
    JNIEnv  *env,
    jobject thisObject,
    jlong   handle,
    jbyteArray yuvFrameData
)
{
    // Nothing to do
}

#undef JNI_FUNC
#undef JAVA_PACKAGE_NAME
