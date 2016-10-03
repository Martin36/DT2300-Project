/*
  traceserv by Gerhard Eckel, May 2014, Jan 2016
  version with incremental recording
  based on example_server.c from liblo        
*/
 
// previous: "Sep 27 2014, "Jan 06 2016", "Feb 01 2016", "Feb 10 2016", "Aug 15,22 2016", "Sep 1 2016"
#define TRACESERV_VERSION "Sep 18 2016"

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>

#include <stdlib.h> 
#include <stdio.h>
#include <unistd.h>
#include <stdbool.h>
#include <assert.h>


#include "lo/lo.h"

int done = 0;

void error(int num, const char *m, const char *path);

int generic_handler(const char *path, const char *types, lo_arg ** argv,
                    int argc, void *data, void *user_data);

int quit_handler(const char *path, const char *types, lo_arg ** argv,
                 int argc, void *data, void *user_data);

/* traceserv */

void init(void);

int verbose = 0;

typedef struct _vec3 {
  float x;
  float y;
  float z;
} vec3_t;

typedef struct _sample {
  float t;  // trajectory time
  vec3_t p; // trajectory point
  vec3_t f; // flow vector (vector from previous trajectory point)
  float d;  // flow vector magnitude
} sample_t;

typedef struct _trace {
  int size;   // allocated space in number of samples
  int length; // current number of samples (<= size)
  sample_t *samples;
} trace_t;

typedef struct _result {
  int i;     // index of g1 in trajectory
  int mark;  // mark for sorting
  vec3_t p;  // intersection point on trajectory
  float d;   // distance from trajectory segment
  float t;   // time in sound 
  float dir; // direction of movement for sound playback
  float a;   // cosine of angle between segment and movement vector
} result_t;

#define MAXTRACES 1024 		// max number of traces the server can handle
#define MAXRESULTS 128 		// max number of results that can be found
#define MAXLINELENGTH 128	// max line length to read trajectory text file
#define RESIZE 8192			// number of samples to add when trajectory runs out of space

trace_t *traces[MAXTRACES];
result_t results[MAXRESULTS];

int reset_handler(const char *path, const char *types, lo_arg ** argv,
                  int argc, void *data, void *user_data);

int set_verbose_handler(const char *path, const char *types, lo_arg ** argv,
                        int argc, void *data, void *user_data);

int add_trace_handler(const char *path, const char *types, lo_arg ** argv,
                      int argc, void *data, void *user_data);

int create_trace_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data);

int delete_trace_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data);

int append_sample_handler(const char *path, const char *types, lo_arg ** argv,
                          int argc, void *data, void *user_data);

int print_trace_handler(const char *path, const char *types, lo_arg ** argv,
                        int argc, void *data, void *user_data);

int print_traces_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data);

int compute2_handler(const char *path, const char *types, lo_arg ** argv,
                     int argc, void *data, void *user_data);

int alive_handler(const char *path, const char *types, lo_arg ** argv,
                  int argc, void *data, void *user_data);

int intersect_segment_plane(vec3_t* p0, vec3_t* u, float u_mag, vec3_t* v0, vec3_t* norm, float norm_mag, vec3_t* result, float* angle);

// memory allocation routines

trace_t* allocate_trace(int size)
{
  trace_t *trace;
  sample_t *samples;
  
  if ((trace = malloc(sizeof(trace_t))) == NULL) {
    return NULL;
  }
  if ((samples = malloc(sizeof(sample_t) * size)) == NULL) {
    free(trace);
    return NULL;
  }
  trace->size = size;
  trace->length = 0;
  trace->samples = samples;
  return trace;
}

trace_t* resize_trace(trace_t *trace, int size)
{
  sample_t *samples = trace->samples;
  
  if ((samples = realloc(samples, sizeof(sample_t) * size)) == NULL) {
  	// is trace->samples still valid here? what to do?
    return NULL;
  }
  trace->samples = samples;
  trace->size = size;
  if (trace->length > size) { // we lost some samples when shrinking the trace
  	trace->length = size;
  }
  return trace;
}

void free_trace(trace_t *trace)
{
	free(trace->samples);
	free(trace);
}

int main(int argc, char *argv[])
{
  lo_server_thread st;
  lo_address t;
  char *ts_port = "7770", *sc_port = "57120";

  if (argc > 1) {
    ts_port = argv[1];
  }
  if (argc > 2) {
    sc_port = argv[2];
  }
        
  init();
  
  st = lo_server_thread_new(ts_port, error);
  t = lo_address_new(NULL, sc_port);

  /* add method that will match any path and args */
  /* lo_server_thread_add_method(st, NULL, NULL, generic_handler, NULL); */

  /* add method that will match the path /quit with no args */
  lo_server_thread_add_method(st, "/quit", "", quit_handler, NULL);

  lo_server_thread_add_method(st, "/reset", "", reset_handler, NULL);
  lo_server_thread_add_method(st, "/setverbose", "i", set_verbose_handler, NULL);
  lo_server_thread_add_method(st, "/addtrace", "iis", add_trace_handler, NULL);
  lo_server_thread_add_method(st, "/createtrace", "ii", create_trace_handler, NULL);
  lo_server_thread_add_method(st, "/deletetrace", "i", delete_trace_handler, NULL);
  lo_server_thread_add_method(st, "/appendsample", "iffff", append_sample_handler, NULL);
  lo_server_thread_add_method(st, "/printtrace", "i", print_trace_handler, NULL);
  lo_server_thread_add_method(st, "/printtraces", "", print_traces_handler, NULL);
  lo_server_thread_add_method(st, "/compute2", "iiiffffffffff", compute2_handler, t);
  lo_server_thread_add_method(st, "/alive", "", alive_handler, t);

  lo_server_thread_start(st);

  printf("traceserv %s %s started (version %s)\n", ts_port, sc_port, TRACESERV_VERSION);
  fflush(stdout);
        
  while (!done) {
    usleep(1000);
  }

  lo_server_thread_free(st);

  return 0;
}

void error(int num, const char *msg, const char *path)
{
  printf("liblo server error %d in path %s: %s\n", num, path, msg);
  fflush(stdout);
}

/* catch any incoming messages and display them. returning 1 means that the
 * message has not been fully handled and the server should try other methods */
int generic_handler(const char *path, const char *types, lo_arg ** argv,
                    int argc, void *data, void *user_data)
{
  int i = 0;

  printf("path: <%s>\n", path);
  for (i = 0; i < argc; i++) {
    printf("arg %d '%c' ", i, types[i]);
    lo_arg_pp((lo_type)types[i], argv[i]);
    printf("\n");
  }
  printf("\n");
  fflush(stdout);

  return 1;
}

int quit_handler(const char *path, const char *types, lo_arg ** argv,
                 int argc, void *data, void *user_data)
{
  done = 1;
  printf("quiting\n\n");
  fflush(stdout);

  return 0;
}

void init(void) {
  int i = 0;
  for (i = 0; i < MAXTRACES; i++) {
    traces[i] = NULL;
  }
};

int reset_handler(const char *path, const char *types, lo_arg ** argv,
                  int argc, void *data, void *user_data)
{
  int i = 0;

  if (verbose) {
    printf("%s\n", path); fflush(stdout);
  }
        
  for (i = 0; i < MAXTRACES; i++) {
    if (traces[i] != NULL) {
      free(traces[i]->samples);
      free(traces[i]);
    }
    traces[i] = NULL;
  }

  return 0;
}

int set_verbose_handler(const char *path, const char *types, lo_arg ** argv,
                        int argc, void *data, void *user_data)
{
  verbose = argv[0]->i;
  if (verbose) {
    printf("%s %d\n", path, verbose); fflush(stdout);
  }
  return 0;
}

// traceserv specific handlers

/*
 creates and reads a trace from a file
*/

int add_trace_handler(const char *path, const char *types, lo_arg ** argv,
                      int argc, void *data, void *user_data)
{
  int id = argv[0]->i, size = argv[1]->i;
  char *file = &(argv[2]->s);
  FILE *fp;
  char line[MAXLINELENGTH];
  sample_t *samples;
  trace_t *trace;
  int i;
        
  if (verbose) {
    printf("%s %d %d %s\n", path, id, size, file); fflush(stdout);
  }
  if (id < 0 || id >= MAXTRACES) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }
  if (size < 0) {
    printf("%s: size (%d) out of range\n", path, size); fflush(stdout);
    return 0;
  }
  if ((fp = fopen(file, "r")) == NULL) {
    printf("%s: cannot open file %s\n", path, file); fflush(stdout);
    return 0;
  }
  if ((trace = allocate_trace(size)) == NULL) {
    printf("%s: cannot allocate trace\n", path); fflush(stdout);
    fclose(fp);
    return 0;
  }
  if (traces[id] != NULL) {
    free(traces[id]->samples);
    free(traces[id]);
  }
  
  i = 0;
  while (!feof(fp) && i < trace->size) {
    float t, x, y, z;
    sample_t *s = &(trace->samples[i]);
  	if (fscanf(fp, "%f %f %f %f", &t, &x, &y, &z) != 4) {
   	  printf("%s: fscanf failed\n", path); fflush(stdout);
   	  return 0;
  	}
    if (verbose) {
      printf("%d %f %f %f %f\n", i, t, x, y, z); fflush(stdout);
    }
    s->t = t;
    s->p.x = x;
    s->p.y = y;
    s->p.z = z;
    s->f.x = 0;
    s->f.y = 0;
    s->f.y = 0;
    s->d = 0;
    i++;
    if (i > 1) {
      sample_t* p = s - 1;
      p->f.x = s->p.x - p->p.x; 
      p->f.y = s->p.y - p->p.y; 
      p->f.z = s->p.z - p->p.z;
      p->d = sqrt(p->f.x * p->f.x + p->f.y * p->f.y + p->f.z * p->f.z);
    }
  }
  
  trace->length = i;
  traces[id] = trace;

  fclose(fp);
  unlink(file);
  
  return 0;
}

/*
 create or replace trace with id to be filled incrementally
 */
 
int create_trace_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data)
{
  int id = argv[0]->i, size = argv[1]->i;
  trace_t *trace;
        
  if (verbose) {
    printf("%s %d %d\n", path, id, size); fflush(stdout);
  }
  if (id < 0 || id >= MAXTRACES) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }
  if (size < 0) {
    printf("%s: size (%d) out of range\n", path, size); fflush(stdout);
    return 0;
  }
  if ((trace = allocate_trace(size)) == NULL) {
    printf("%s: cannot allocate trace\n", path); fflush(stdout);
    return 0;
  }
  if (traces[id] != NULL) {
	free_trace(traces[id]);
  }  
  traces[id] = trace;
  return 0;
}

/*
 delete trace with id
 */
 
int delete_trace_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data)
{
  int id = argv[0]->i;
  trace_t *trace;
        
  if (verbose) {
    printf("%s %d\n", path, id); fflush(stdout);
  }
  if (id < 0 || id >= MAXTRACES) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }
  if (traces[id] != NULL) {
	free_trace(traces[id]);
  } else {
    printf("%s: trace with id %d does not exist\n", path, id); fflush(stdout);
    return 0;
  }
  traces[id] = NULL;
  return 0;
}


int append_sample_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data)
{
  int id = argv[0]->i;
  float t = argv[1]->f, x = argv[2]->f, y = argv[3]->f, z = argv[4]->f;
  trace_t *trace;
  sample_t* s;
        
  if (verbose) {
    printf("%s %d %f %f %f %f\n", path, id, t, x, y, z); fflush(stdout);
  }
  if (id < 0 || id >= MAXTRACES) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }
  if (traces[id] == NULL) {
    printf("%s: no trace with id %d\n", path, id); fflush(stdout);
    return 0;
  }  
  trace = traces[id];
  if ((trace->length + 1) > trace->size) {
  	if ((trace = resize_trace(trace, trace->size + RESIZE)) == NULL) {
      printf("%s: cannot resize trace to (%d)\n", path, trace->size + RESIZE); fflush(stdout);
      return 0;
  	}
  }
  traces[id] = trace;
  s = traces[id]->samples + traces[id]->length;
  s->t = t;
  s->p.x = x;
  s->p.y = y;
  s->p.z = z;
  s->f.x = 0;
  s->f.y = 0;
  s->f.y = 0;
  s->d = 0;
  traces[id]->length++;
  if (traces[id]->length > 1) {
    sample_t* p = s - 1;
    p->f.x = s->p.x - p->p.x; 
    p->f.y = s->p.y - p->p.y; 
    p->f.z = s->p.z - p->p.z;
    p->d = sqrt(p->f.x * p->f.x + p->f.y * p->f.y + p->f.z * p->f.z);
  }
  return 0;
}

int print_traces_handler(const char *path, const char *types, lo_arg ** argv,
                         int argc, void *data, void *user_data)
{
  int i = 0;
  int j = 0;
  if (verbose) {
    printf("%s\n", path); fflush(stdout);
  }
  for (i = 0; i < MAXTRACES; i++) {
    if (traces[i] != NULL) {
      printf("trace %d, size = %d, length = %d\n", i, traces[i]->size, traces[i]->length);
//      for (j = 0; j < traces[i]->size; j++) {
//        sample_t *s = traces[i]->samples + j;
//        printf(" %4d: %10.3f %10.3f %10.3f %10.3f\n", j, s->t, s->p.x, s->p.y, s->p.z);
//      }
      fflush(stdout);
    }
  }
  return 0;
}

int print_trace_handler(const char *path, const char *types, lo_arg ** argv,
                        int argc, void *data, void *user_data)
{
  int id = argv[0]->i;
  int i = 0;
  if (verbose) {
    printf("%s %i\n", path, id); fflush(stdout);
  }
  if (id < 0 || id >= MAXTRACES || traces[id] == NULL) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }
  printf("trace %d (size = %d, length = %d):\n", id, traces[id]->size, traces[id]->length);
  for (i = 0; i < traces[id]->length; i++) {
    sample_t *s = traces[id]->samples + i;
    printf(" %4d: %10.3f %10.3f %10.3f %10.3f\n", i, s->t, s->p.x, s->p.y, s->p.z);
    fflush(stdout);
  }
  return 0;
}


// from: http://geomalgorithms.com/a05-_intersect-1.html
// segment defined by p0 and p1
// plane defined by v0 and norm
// result contains intersection point (valid if 1 is returned)
// angle contains cosine of angle between segment and norm
// return 0 if no intersection and 2 if segment lies on plane
int intersect_segment_plane(vec3_t* p0, vec3_t* u, float u_mag, vec3_t* v0, vec3_t* norm, float norm_mag, vec3_t* result, float* angle)
{
  float ux = u->x, uy = u->y, uz = u->z;
  float wx = p0->x - v0->x, wy = p0->y - v0->y, wz = p0->z - v0->z;
  float d =   (norm->x * ux) + (norm->y * uy) + (norm->z * uz);
  float n = -((norm->x * wx) + (norm->y * wy) + (norm->z * wz));
  float mag;
  float si;
        
  if (fabs(d) < 0.00000001) {
    if (n == 0)
      return 2;
    else
      return 0;
  }
        
  si = n / d;
        
  if (si < 0 || si > 1)
    return 0;
        
  result->x = p0->x + ux * si;
  result->y = p0->y + uy * si;
  result->z = p0->z + uz * si;
  mag = u_mag * norm_mag;
  if (mag > 0) {
  	*angle = d / mag;
  } else {
    *angle = 0;
    return 0;
  }
        
  return 1;
}

int alive_handler(const char *path, const char *types, lo_arg ** argv,
                  int argc, void *data, void *user_data)
{
  lo_address t = (lo_address) user_data;

  if (lo_send(t, "/alive", "") == -1) {
    printf("OSC error %d: %s\n", lo_address_errno(t), lo_address_errstr(t));
  }
  return 0;
}

// receives:
//   id of trace <int>
//   id of target (passed through, not used) <int>
//   max number of results <int>
//   last 3D position <float> <float> <float>
//   current 3D position <float> <float> <float>
//   maximum distance <float>
//   minimum age in seconds <float>
//   maximum age in seconds <float>
//   cosine of minimum aligment angle (90 = no alignment, 0 = perfect alignment) <float>
//
// returns (for each result):
//   index of result / solution <int>
//   id of target (passed through) <int>
//   id of trace (passed through) <int>
//   how many more results were possible <int>
//   speed <float>
//   time in sound <float>
//   distance from trace <float>
//   index in trace <int>
//   direction of movement
//

int compute2_handler(const char *path, const char *types, lo_arg ** argv,
                     int argc, void *data, void *user_data)
{
  int index = -1, nres = 0, more = 0;
  int id = argv[0]->i;
  int target = argv[1]->i;
  int maxres = argv[2]->i;
  vec3_t last, current, norm, inter, result;
  float minDist = 1e10, maxDistSq, minAge, maxAge, minAlignment = 0.7, speed;
  trace_t *trace;
  lo_address t = (lo_address) user_data;
  int i = 0;
  int j = 0;
  float traceLength;

  // get and check arguments
  
  if (id < 0 || id >= MAXTRACES || traces[id] == NULL) {
    printf("%s: id (%d) out of range\n", path, id); fflush(stdout);
    return 0;
  }

  trace = traces[id];
  // we assume here that the first timetag is always 0
  // could ensure that when trace is received
  traceLength = trace->samples[trace->length - 1].t;

  if (maxres < 1 || maxres > MAXRESULTS) {
    printf("error: maxres out of range: %d\n", maxres); fflush(stdout);
    return 0;
  }
        
  last.x = argv[3]->f;
  last.y = argv[4]->f;
  last.z = argv[5]->f;
        
  current.x = argv[6]->f;
  current.y = argv[7]->f;
  current.z = argv[8]->f;
        
  maxDistSq = argv[9]->f * argv[9]->f;
  minAge = argv[10]->f;
  maxAge = argv[11]->f;
  minAlignment = argv[12]->f;
     
  // compute line between last and current (used as normal for "cutting plane") and compute "speed" (i.e. distance)
  norm.x = current.x - last.x;
  norm.y = current.y - last.y;
  norm.z = current.z - last.z;
  speed = sqrt(norm.x * norm.x + norm.y * norm.y + norm.z * norm.z);
        
  if (verbose) {
    printf("%s %d %d %d %f %f %f %f %f %f %f %f %f\n", path, id, target, maxres, last.x, last.y, last.z, current.x, current.y, current.z, maxDistSq, minAge, minAlignment); fflush(stdout);
  }

  // compute all results (constrained by maxDistSq and MAXRESULTS)
  for (i = 1; i < trace->length - 1; i++) { // length - 1 to avoid invalid last sample to be used in case minAge < frame interval
    sample_t *g1 = trace->samples + i - 1, *g2 = trace->samples + i;
    float angle;

	// if an intersection is found
    if (intersect_segment_plane(&g1->p, &g1->f, g1->d, &current, &norm, speed, &inter, &angle) == 1) {
      vec3_t diff;
      float d;
      
      // compute squared distance to intersection point                  
      diff.x = inter.x - current.x;
      diff.y = inter.y - current.y;
      diff.z = inter.z - current.z;
      d = diff.x * diff.x + diff.y * diff.y + diff.z * diff.z;
      
      if (verbose) {
	    printf("%s %d %f %f %f %f\n", path, id, d, traceLength, g2->t, minAge); fflush(stdout);
      }
	  // if it is close, old enough, and aligned enough register it as a result
      if (d < maxDistSq && (traceLength - g2->t) > minAge && fabs(angle) > minAlignment) {
        if (nres < MAXRESULTS) {
          result_t *r = &(results[nres++]);
          r->p = inter;		// intersection point on trajectory
          r->i = i - 1;		// index of g1 in trajectory
          r->d = sqrtf(d);	// distance to intersection point
          r->a = angle;		// cosine of angle between segment and movement vector
          r->mark = 0; 		// mark as unaccounted, for sorting later
        } else {
          more++; 			// number of results which where not recorded (due to MAXRESULTS)
        }
      }
    }
  }
  
  if (verbose) {
	printf("%s %d %d\n", path, id, nres); fflush(stdout);
  }

  // for all results compute time of intersection point in trajectory
  for (i = 0; i < nres; i++) {
    result_t *r = &(results[i]);
    sample_t *s1 = trace->samples + r->i;
    sample_t *s2 = s1 + 1;
    float t1 = s1->t;
    float t2 = s2->t;
    vec3_t *g1 = &(s1->p);
    vec3_t *g2 = &(s2->p);
    vec3_t g1_p, g1_g2;
    float direction;
                
    g1_p.x = g1->x - r->p.x;
    g1_p.y = g1->y - r->p.y;
    g1_p.z = g1->z - r->p.z;
                
    r->dir = -(s1->f.x * norm.x + s1->f.y * norm.y + s1->f.z * norm.z);
    r->t = t1 + (sqrt(g1_p.x * g1_p.x + g1_p.y * g1_p.y + g1_p.z * g1_p.z) / s1->d) * (t2 - t1);
  }

  // if there are less results than asked for, adjust the limit to avoid
  // unnecessary computation in the two loops below
  if (nres < maxres)
    maxres = nres;

  // sort results and report them sequentially (from closest to farthest)
  for (i = 0; i < maxres; i++) {
    float min = 1e10; // start with a very large value for the minimum so that first test will be successful
    int next = -1;
    for (j = 0; j < nres; j++) {
      if (results[j].mark == 0 && results[j].d < min) { // look for the result with the smallest distance
        min = results[j].d;
        next = j;
      }
    }

    if (next > -1) {
      results[next].mark = 1;
//       if (lo_send(t, "/result2", "iiiifffif", i, target, id, more, speed, results[next].t, results[next].d, results[next].i, results[next].dir) == -1) {
      if (lo_send(t, "/result2", "iiiifffiff", i, target, id, more, speed, results[next].t, results[next].d, results[next].i, results[next].dir, results[next].a) == -1) {
        printf("OSC error %d: %s\n", lo_address_errno(t), lo_address_errstr(t)); fflush(stdout);
      }
    }

  }
        
  return 0;
}
