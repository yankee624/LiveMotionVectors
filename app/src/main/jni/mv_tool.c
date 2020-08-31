
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "libavutil/motion_vector.h"
#include "mv_tool.h"

MVOutputContext* alloc_mv_output_context(const char* filename) {

    // MVOutputContext *c = reinterpret_cast<MVOutputContext*>(malloc(sizeof(MVOutputContext)));
    MVOutputContext *c = (MVOutputContext*) malloc(sizeof(MVOutputContext));

    // Open file
    c->fp = fopen(filename, "wb");
    if (!c->fp) {
        printf("Fail to open file\n");
        return NULL;
    }

    return c;
}

void free_mv_output_context(MVOutputContext *c) {
    if (c) {
        if (c->fp)
            fclose(c->fp);
        free(c);
    }
}

void print_motion_vector(MVOutputContext *c, AVMotionVector *mvs, int mv_count, int frame_id) {

    int i;
    int val_x, val_y;
    AVMotionVector *mv;

    fprintf(c->fp, "===== Frame %d =====\n", frame_id);

    for (i = 0; i < mv_count; i ++) {
        mv = mvs + i;
        assert(mv->source == -1);

        if ((mv->dst_y - (mv->h >> 1)) >  0 &&
            (mv->dst_x - (mv->w >> 1)) == 0)
            fprintf(c->fp, "\n");

        val_x = mv->dst_x - mv->src_x;
        val_y = mv->dst_y - mv->src_y;

        fprintf(c->fp, "(%d,%d)\t", val_x, val_y);
    }

    fprintf(c->fp, "\n");
}

MotionVectorMap* alloc_motion_vector_map(int w, int h) {
    size_t data_size;
    MotionVectorMap *map;

    data_size = w * h * 2 * sizeof(uint32_t);

    // map = reinterpret_cast<MotionVectorMap*>(malloc(sizeof(MotionVectorMap)));
    map = (MotionVectorMap*) malloc(sizeof(MotionVectorMap));

    map->width  = w;
    map->height = h;
    // map->data   = reinterpret_cast<int32_t*>(malloc(data_size));
    map->data   = (int32_t*) malloc(data_size);

    memset((uint8_t*) map->data, 0, data_size);

    return map;
}

void free_motion_vector_map(MotionVectorMap *map) {
    if (map) {
        free(map->data);
        free(map);
    }
}

MotionVectorMap* av_motion_vector_to_motion_vector_map(
    AVMotionVector *mvs,
    int mv_count,
    int map_width,
    int map_height)
{
    int i, x, y;
    int bw, bh;
    int val_x, val_y;
    int dst_pix_x, dst_pix_y;
    int mv_stride;
    int mv_pos_v;
    AVMotionVector *mv;
    MotionVectorMap *mv_map;

    mv_map = alloc_motion_vector_map(map_width, map_height);
    mv_stride = map_width<<1;

    for (i = 0; i < mv_count; i ++) {
        mv = mvs + i;
        assert(mv->source == -1);

        val_x = mv->dst_x - mv->src_x;
        val_y = mv->dst_y - mv->src_y;

        if (val_x || val_y) {
            dst_pix_x = mv->dst_x - (mv->w >> 1);
            dst_pix_y = mv->dst_y - (mv->h >> 1);

            assert(dst_pix_x >= 0 && dst_pix_y >= 0);

            if (dst_pix_x >= map_width || dst_pix_y >= map_height) {
#ifdef DEBUG_INFO_ENABLE
                fprintf(stderr, "DEBUG: MV block (%d,%d) is out of map\n",
                                    dst_pix_x, dst_pix_y);
#endif
                continue;
            }

            bw = mv->w;
            bh = mv->h;

            // If part of block is of map
            if (dst_pix_x + bw >= map_width)
                bw = map_width - dst_pix_x;
            if (dst_pix_y + bh >= map_height)
                bh = map_height - dst_pix_y;

            mv_pos_v = (dst_pix_y * map_width + dst_pix_x) << 1;

#define SET_MV_DATA(off_x, off_y) \
    mv_map->data[mv_pos_v+off_x] = val_x; \
    mv_map->data[mv_pos_v+off_y] = val_y;

            if (bw == 16) {
                for (y = dst_pix_y; y < (dst_pix_y + bh); y ++) {
                    SET_MV_DATA(0, 1)
                    SET_MV_DATA(2, 3)
                    SET_MV_DATA(4, 5)
                    SET_MV_DATA(6, 7)
                    SET_MV_DATA(8, 7)
                    SET_MV_DATA(10, 11)
                    SET_MV_DATA(12, 13)
                    SET_MV_DATA(14, 15)
                    SET_MV_DATA(16, 17)
                    SET_MV_DATA(18, 19)
                    SET_MV_DATA(20, 21)
                    SET_MV_DATA(22, 23)
                    SET_MV_DATA(24, 25)
                    SET_MV_DATA(26, 27)
                    SET_MV_DATA(28, 29)
                    SET_MV_DATA(30, 31)

                    mv_pos_v += mv_stride;
                }
            } else if (bw == 8) {
                for (y = dst_pix_y; y < (dst_pix_y + bh); y ++) {
                    SET_MV_DATA(0, 1)
                    SET_MV_DATA(2, 3)
                    SET_MV_DATA(4, 5)
                    SET_MV_DATA(6, 7)
                    SET_MV_DATA(8, 7)
                    SET_MV_DATA(10, 11)
                    SET_MV_DATA(12, 13)
                    SET_MV_DATA(14, 15)

                    mv_pos_v += mv_stride;
                }
            } else {
                for (y = dst_pix_y; y < (dst_pix_y + bh); y ++) {
                    for (x = dst_pix_x; x < (dst_pix_x + bw); x ++) {
                        SET_MV_DATA((x<<1), ((x<<1)+1))
                    }

                    mv_pos_v += mv_stride;
                }
            }
        }
    }

    return mv_map;
}

void av_motion_vector_to_motion_vector_item_list(
    AVMotionVector *mvs, int mv_count,
    int *mv_item_list_data) {

    int i;
    int item_size;
    int *item;
    AVMotionVector *mv;

    item = mv_item_list_data;
    item_size = 6;

    for (i = 0; i < mv_count; i ++) {
        mv = mvs + i;
        assert(mv->source == -1);

        item[0] = mv->dst_x - mv->src_x;
        item[1] = mv->dst_y - mv->src_y;
        item[2] = mv->dst_x - (mv->w >> 1);
        item[3] = mv->dst_y - (mv->h >> 1);
        item[4] = mv->w;
        item[5] = mv->h;

        item += item_size;
    }
}

void print_motion_vector_map(MVOutputContext *c, MotionVectorMap *map, int fid) {

    int x, y;
    int mid;
    int val_x, val_y;

    fprintf(c->fp, "===== Frame %d =====\n", fid);

    for (y = 0; y < map->height; y ++) {
        for (x = 0; x < map->width; x ++) {

            mid = (y * map->width + x) << 1;

            val_x = map->data[mid];
            val_y = map->data[mid+1];

            fprintf(c->fp, "(%d,%d)\t", val_x, val_y);

        }
        fprintf(c->fp, "\n");
    }

}
