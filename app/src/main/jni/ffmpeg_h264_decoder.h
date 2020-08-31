
#ifndef FFMPEG_H264_DECODER_H_
#define FFMPEG_H264_DECODER_H_

#include <stdint.h>

#include "libavutil/motion_vector.h"
#include "libavformat/avformat.h"
#include "libavutil/pixfmt.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"

#include "mv_tool.h"

#define INPUT_BUFFER_SIZE 4096
#define FF_INPUT_BUGGER_PADDING_SIZE 32

typedef struct {

    size_t video_width;
    size_t video_height;

    int     got_frame_count;
    int     got_frame;

    int     cur_size;
    uint8_t *cur_ptr;

    uint8_t *in_buffer;

    AVCodec              *codec;
    AVCodecContext       *codec_ctx;
    AVCodecParserContext *codec_parser_ctx;

    AVPacket             *packet;
    AVFrame              *frame;

} FFmpegDecodeContext;

FFmpegDecodeContext* alloc_ffmpeg_decode_context();
int init_ffmpeg_h264_decode_context(FFmpegDecodeContext *c, int width, int height);
void free_ffmpeg_decode_context(FFmpegDecodeContext *c);
int decode_frame(FFmpegDecodeContext *c);
MotionVectorMap* get_motion_vector_map(FFmpegDecodeContext *c);
int get_motion_vector_list_count(FFmpegDecodeContext *c);
void get_motion_vector_list(FFmpegDecodeContext *c, int *mv_list_data);

#endif
