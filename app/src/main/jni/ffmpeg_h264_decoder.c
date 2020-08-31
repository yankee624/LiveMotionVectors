
#include "android_log.h"
#include "ffmpeg_h264_decoder.h"

FFmpegDecodeContext* alloc_ffmpeg_decode_context() {
    FFmpegDecodeContext *c;

    // c = reinterpret_cast<FFmpegDecodeContext*>(malloc(sizeof(FFmpegDecodeContext)));
    c = (FFmpegDecodeContext*) malloc(sizeof(FFmpegDecodeContext));

    return c;
}

int init_ffmpeg_h264_decode_context(FFmpegDecodeContext *c, int width, int height) {

    AVDictionary *opts = NULL;

    // Set video width and height
    c->video_width  = width;
    c->video_height = height;

    // Initialize current buffer pointer and size
    c->cur_ptr  = NULL;
    c->cur_size = 0;

    // Initialize got frame count
    c->got_frame_count = 0;

    // Initialize codec
    c->codec = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!c->codec) {
        printf("Codec not found\n");
        return -1;
    }

    c->codec_ctx = avcodec_alloc_context3(c->codec);
    if (!c->codec_ctx) {
        printf("Fail to allocate codec context\n");
        return -1;
    }

    c->codec_parser_ctx = av_parser_init(AV_CODEC_ID_H264);
    if (!c->codec_parser_ctx) {
        printf("Fail to initialize parser context\n");
        return -1;
    }

    // Initialize frame
    c->frame = av_frame_alloc();

    // Initialize packet
    // c->packet = reinterpret_cast<AVPacket*>(malloc(sizeof(AVPacket)));
    c->packet = (AVPacket*) malloc(sizeof(AVPacket));
    av_init_packet(c->packet);

    // Initialize input buffer
    // c->in_buffer = reinterpret_cast<uint8_t*>(
    //     malloc(INPUT_BUFFER_SIZE+FF_INPUT_BUGGER_PADDING_SIZE));
    c->in_buffer = (uint8_t*) malloc(INPUT_BUFFER_SIZE+FF_INPUT_BUGGER_PADDING_SIZE);
    memset(c->in_buffer+INPUT_BUFFER_SIZE, 0, FF_INPUT_BUGGER_PADDING_SIZE);

    // Open codec
    av_dict_set(&opts, "flags2", "+export_mvs", 0);

    if (avcodec_open2(c->codec_ctx, c->codec, &opts) < 0) {
        printf("Fail to open codec\n");
        return -1;
    }

    return 0;
}

void free_ffmpeg_decode_context(FFmpegDecodeContext *c) {
    if (c) {
        free(c->in_buffer);
        free(c->packet);
        av_frame_free(&c->frame);
        avcodec_close(c->codec_ctx);

        free(c);
    }
}

int decode_frame(FFmpegDecodeContext *c) {

    int ret, len;

    while (c->cur_size > 0) {
        len = av_parser_parse2(c->codec_parser_ctx, c->codec_ctx,
                               &c->packet->data, &c->packet->size,
                               c->cur_ptr, c->cur_size,
                               AV_NOPTS_VALUE, AV_NOPTS_VALUE, AV_NOPTS_VALUE);

        c->cur_ptr  += len;
        c->cur_size -= len;

        if (!c->packet->size)  // NOTE: Not a full packet yet
            continue;

        ret = avcodec_send_packet(c->codec_ctx, c->packet);
        if (ret < 0) {
            printf("Send packet error\n");
            return -1;
        }

        av_packet_unref(c->packet);

        ret = avcodec_receive_frame(c->codec_ctx, c->frame);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF || ret < 0) {
            printf("Receive frame error (err=%d)\n", ret);
            return -1;
        }

        c->got_frame = 1;
        c->got_frame_count ++;
    }

    return 0;
}

MotionVectorMap* get_motion_vector_map(FFmpegDecodeContext *c) {

    AVFrameSideData *sd;
    
    sd = av_frame_get_side_data(c->frame, AV_FRAME_DATA_MOTION_VECTORS);
    //LOGD("Have side data: %d", sd != NULL);
    if (sd)
        return av_motion_vector_to_motion_vector_map(
                    (AVMotionVector*) sd->data,
                    sd->size / sizeof(AVMotionVector),
                    c->video_width, c->video_height);
    else
        return NULL;
}

int get_motion_vector_list_count(FFmpegDecodeContext *c) {
    AVFrameSideData *sd;
    
    sd = av_frame_get_side_data(c->frame, AV_FRAME_DATA_MOTION_VECTORS);
    if (sd)
        return sd->size / sizeof(AVMotionVector);
    else
        return 0;
}

void get_motion_vector_list(FFmpegDecodeContext *c, int *mv_list_data) {

    AVFrameSideData *sd;

    sd = av_frame_get_side_data(c->frame, AV_FRAME_DATA_MOTION_VECTORS);
    if (sd)
        av_motion_vector_to_motion_vector_item_list(
            (AVMotionVector*) sd->data,
            sd->size / sizeof(AVMotionVector),
            mv_list_data);
}
