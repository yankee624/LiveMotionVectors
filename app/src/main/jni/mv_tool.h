
#ifndef MV_TOOLS_H_
#define MV_TOOLS_H_

#include <stdio.h>
#include <stdint.h>

typedef struct {
	FILE *fp;
} MVOutputContext;

typedef struct {
	size_t width;
	size_t height;
	int32_t *data;
} MotionVectorMap;

MVOutputContext* alloc_mv_output_context(const char* filename);
MotionVectorMap* alloc_motion_vector_map(int w, int h);

void free_mv_output_context(MVOutputContext *c);
void free_motion_vector_map(MotionVectorMap *map);

MotionVectorMap* av_motion_vector_to_motion_vector_map(
    AVMotionVector *mvs,
    int mv_count,
    int map_width,
    int map_height);
void av_motion_vector_to_motion_vector_item_list(
    AVMotionVector *mvs, int mv_count,
    int *mv_item_list_data);

void print_motion_vector(MVOutputContext *c, AVMotionVector *mvs,
	                     int mv_count, int frame_id);
void print_motion_vector_map(MVOutputContext *c, MotionVectorMap *map, int fid);

#endif
