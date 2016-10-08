// test audio output (piano note)
().play;

// load scene "sweep.retrace" into ReTrace
// IMPORTANT: switch on retracing (button "On" in track)

n = NetAddr("localhost", 7770); // UDP connection to traceServ

n.sendMsg("/setverbose", 1);    // switch into verbose mode

// traceServ:
// /setverbose 1

n.sendMsg("/printtraces");      // see which traces exist on the server

// traceServ:
// /printtraces
// trace 1, size = 269, length = 269

n.sendMsg("/printtrace", 1);

// traceServ:
// /printtrace 1
// trace 1 (size = 269, length = 269):
// 0:      0.000      0.344      0.000      0.000
// 1:      0.008      0.350      0.000      0.000
// 2:      0.017      0.356      0.000      0.000
// ...
// 268:      2.233      1.833      0.000      0.000

OSCdef(\result, { |msg| msg.postln }, '/result2'); // install OSC handler for result

n.sendMsg("/compute2", 1, 1, 1, 1, 0, 0, 1.01, 0, 0, 10, 0, 0, 0); // request tracing computation

// traceServ:
// /compute2 1 1 1 1.000000 0.000000 0.000000 1.010000 0.000000 0.000000 100.000000 0.000000 0.000000
// /compute2 1 0.000000 2.233333 1.000000 0.000000
// /compute2 1 1

// SuperCollider:
// [ /result2, 0, 1, 1, 0, 0.0099999904632568, 0.99833327531815, 0, 119, -5.5556243751198e-05, 0.99999994039536 ]
// AND: a brief sound!
