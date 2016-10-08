// -------------------------------------------------------------------------------------------------------

// set paths to helper apps

~vServerPath = "~/Library/Application Support/SuperCollider/VServer.app".standardizePath; // path to app
~traceServerPath = "~/Library/Application Support/SuperCollider".standardizePath;         // path to directory with executable

// set configuration variables

~runTraceServerInTerminal = true;             // if false, trace server runs invisibly
~recCompSlope = 2.0.reciprocal;               // slope of compressor used for recording
~recCompThresh = -50;                         // threshold of compressor used for recording
~reverseWhenPlayingBackwards = true;          // set to false for backwards compatibility (sounds worse)
~outputChannelMap = [0, 1, 0, 1, 0, 1, 0, 1]; // mapping of logical to physical output channels
~inputChannelMap = [0, 1];                    // mapping of logical to physical input channels
~recordingChannels = [2, 3];                  // physical input channel to be recorded in addition to ~inputChannelMap
~mainWindowBounds = Rect(0, 590, 567, 266);   // screen coordinates
~trackingInputTargets = (0..8);               // list of allowed targets (including 0 for internal use)
~defaultMinAge = 0;                           // minimum age of sample in trace when playing a trace
~defaultMinAlignment = cos(90.degrad);        // cosine of minimum aligment angle (90 = no alignment, 0 = perfect alignment)
~verbose = false;                             // debugging
~maxDt = 1.0;                                 // prevent jumps in tape mode
~defaultMidiBindings = false;                 // set to true for
~trackingSrate = 120;                         // tracking rate in frames per second
~distThres = 0;                               // distance threshold in m for skipping frames while recording (0.005)
~guiFont = Font("Helvetica", 10).boldVariant; // font for GUI (try size 10 on Windows)

//CHANGE THIS TO CORRECT PATH!
~tempFilesDir = "C:/Users/martin/AppData/Local/SuperCollider/tmp";                      // directory for temporary files (absolute path, no backslashes on Windows)

~viconStyle = false;                          // set Vicon tracking input style: NO ROTATION
~viconTargets = 4;                            // number of targets from Vicon2OSC


// start program and bind main object to global variable m

Server.default.waitForBoot({
	m = TrMain.new(outputs:~outputChannelMap, inputs:~inputChannelMap, room:~recordingChannels, bounds:~mainWindowBounds).front
});

// configure fader box
(thisProcess.nowExecutingPath.dirname +/+ "nano2.scd").load;

// spatialisation support for Every Move a Sound

~loudspeakers = [[-1787, -2604], [-3470, -195], [-1450, 2515], [1310, 2570], [3440, 1930], [4105, 25], [3533, -1296], [3533, -2296]];
~nearestOutputs = {arg n, p;
	~loudspeakers.collect{arg item, i; [(p-(item/1000)).squared.sum.sqrt, i]}.sort({arg a, b; a[0] < b[0]}).keep(n).collect{arg x; x[1]};
};


// -------------------------------------------------------------------------------------------------------

{
	NetAddr.langPort;

	OSCFunc.trace(true);
	OSCFunc.trace(false);

	m.traceServer.stopAliveChecking;
	m.documents.asArray[0].tracks[0].players[0].pAlignment;

	~verbose = false;
	~verbose = true;

	// inspect trajectory

	m.documents.asArray[0].tracks[0].trace.trajectory;

	// create a tracking recording

	m.trackingInput.recording = 1200.collect{|i| [i * (1/120), 1, [i / 1200, i / 1200, 0]] };
	m.trackingInput.recording = 360.collect{|i| [i * (1/120), 1, [i / 180, 0, 0]] }; // file sweep.tracking (3s, 2m)
	m.trackingInput.recording.last;

	// recording

	m.startRecord("~/Desktop".standardizePath +/+ "retrace_20" ++ Date.getDate.stamp ++ ".wav");
	m.stopRecord;

	// data reduction

	{ var t = m.documents.asArray[0].tracks[2].trace.trajectory; ((1 - (t.size / (t.last[0]*120))) * 100).round(0.1); }.(); // percent saved

	// explore min age

	m.documents.asArray[0].tracks[0].players[0].pMinAge = 3;
	m.documents.asArray[0].tracks[0].players[0].pMinAge = 0;
	m.documents.asArray[0].tracks[0].players[0].pGrainDur = 0.1;
	~distThres = 0;
	m.documents.asArray[0].view.bounds;

	// debuging code

	m.documents.asArray[0].tracks[0].trace.recordIncrementally = true;
	m.documents.asArray[0].tracks[0].trace.recordSpeed = true;
	m.documents.asArray[0].tracks[0].trace.recordSpeed = false;
	m.documents.asArray[0].tracks[0].trace.setRecAmp(0.dbamp);
	m.documents.asArray[0].tracks[0].trace.setFeedbackAmp(0.dbamp);

	m.documents.asArray[0].tracks[0].relativeTo;
	m.documents.asArray[0].tracks[0].trace.recData.collect{|x, i| x[0]}.plot;
	m.documents.asArray[0].tracks[0].trace.trajectory = [[0, [0, 0, 0]], [1.5, [1, 0, 0]], [3, [2, 0, 0]]];


	m.documents.asArray[0].tracks[4].players;

	m.documents.asArray[0].tracks[2].players.do{|x| x.pAlignment = cos(90.degrad)};
	m.documents.asArray[0].tracks[3].players.do{|x| x.pAlignment = cos(20.degrad)};
	m.documents.asArray[0].tracks[4].players.do{|x| x.pAlignment = cos(20.degrad)};
	m.documents.asArray[0].tracks[5].players.do{|x| x.pAlignment = cos(20.degrad)};
	m.documents.asArray[0].tracks[6].players.do{|x| x.pAlignment = cos(20.degrad)};

	~alignSet = 90;
	m.documents.asArray[0].tracks[0].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[1].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[2].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[3].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[4].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[5].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};
	m.documents.asArray[0].tracks[6].players.do{|x| x.pAlignment = cos(~alignSet.degrad)};

	~sphereRel = VSphere(m.trackingInput.scene, 0.3!3, [255, 255, 255, 255], 0!3, 0!3);
	~sphereRel.trans_([5.0, 0.0, 2.0]);

	~finalHack = {

		m.documents.asArray[0].tracks.do{|track|
			var v = track.view;

			v.vPlAddB.valueAction_(0);
			if (track.players.size < 3) {
				v.vPlAddB.valueAction_(0);
			};

			v.vPlId.valueAction_(0);
			v.vPlTargetNB.valueAction_(1);
			v.vPlOutputsTF.string_("1");
			track.players[0].pOutputs_([0]);

			v.vPlId.valueAction_(1);
			v.vPlTargetNB.valueAction_(2);
			v.vPlOutputsTF.string_("2");
			track.players[1].pOutputs_([1]);

			v.vPlId.valueAction_(2);
			v.vPlTargetNB.valueAction_(3);
			v.vPlOutputsTF.string_("3 4");
			track.players[2].pOutputs_([2, 3]);

		}
	};


	// ~finalHack.();

	m.documents.asArray[0].tracks[0].trace.twoTrajectories;

	m.startRecord("/tmp/test_0.aiff");
	m.stopRecord;

	~recBuffer = Buffer.alloc(Server.default, 65536 * 4, 7);
	~recBuffer.write("/tmp/test.snd", "snd", "int24", 0, 0, true);

	~recSynth = Synth(\record7chan, [\out, m.masterBus, \buf, ~recBuffer], addAction:'addToTail');

	m.documents.asArray[0].tracks.do{|t| t.name.postln};


	m.documents.asArray[0].tracks.do{|t| t.players.do{|p| t.removePlayer(0)}};

	x.view.vPlAddB.valueAction_(0);
	~gerhard.players.do{|p| p};

	~gerhard.removePlayer(0);

	m.documents.asArray[0].tracks[2].trace.trajectory.
	collect{|x|x[0]}.differentiate.keep(100).drop(1).minItem;

	~addr.sendMsg("/DtV/fadeTo", 1.0, 5.0);
	~addr.sendMsg("/DtV/fadeTo", 0.0, 5.0);

	m.trackingInput.connect(1, ~christine);
	m.trackingInput.disconnect(~christine);

	"killall -9 scsynth".unixCmd;

	m.documents.asArray[0].tracks[6].players[0].getParameters;

	// set all

	m.documents.asArray[0].tracks.do{|x| x.players[0].pHold_(0).pGrainDur_(0.3)};

	// choir

	m.documents.asArray[0].tracks[4].players[0].pHold_(0).pGrainDur_(0.3);
	m.documents.asArray[0].tracks[5].players[0].pHold_(0).pGrainDur_(0.3);

	// manipulate recording

	m.trackingInput.setLoc(0);

	m.trackingInput.recording.size; // frames
	m.trackingInput.recording.last[0] - m.trackingInput.recording.first[0] / 60; // length
	m.trackingInput.recording.last[0] - m.trackingInput.recording.first[0] * 120 / m.trackingInput.recording.size; // savings

	~trimTo = { arg b, e;
		m.trackingInput.recording = m.trackingInput.recording.copyRange(b * 120, e * 120);
	};

	~trimTo.(3 * 60, 3 * 60 + 10);

	m.trackingInput.recording.size/100/60/5;
	m.trackingInput.recording.last[0] - m.trackingInput.recording.first[0] / 60;
	m.trackingInput.window.setDur(m.trackingInput.recordingDuration);
	m.trackingInput.recordingDuration;
	m.trackingInput.recording = m.trackingInput.recording.drop(m.trackingInput.playIndex).keep(1200*4);
	m.trackingInput.recording[m.trackingInput.playIndex];

	m.trackingInput.recording.clump(4).first;
	m.trackingInput.recording = m.trackingInput.recording.keep(m.trackingInput.recording.size-1);
	m.trackingInput.recording = ((0..(120*3-1))/120).collect{|x, i| [x, 1, [i / 360 * 2, 0, 0]]};

	~clumped = m.trackingInput.recording.clump(4);
	~reversed = ~clumped.reverse;
	~times = ~clumped.collect{|x| x[0][0]};
	~corrected = ~reversed.collect{|x, i| x.collect{|y| [~times[i] - 1828.679049953 + 1838.661647941 + 120.reciprocal, y[1], y[2]]}};
	~clumped.last;
	m.trackingInput.recording = m.trackingInput.recording ++ ~corrected.flatten;
	m.trackingInput.recording = m.trackingInput.recording.keep(4796);
	m.trackingInput.recording.last[0];
	~corrected.flatten.first[0];
	~corrected.flatten.first;
	m.trackingInput.recording = m.trackingInput.recording.drop(4796);
	m.trackingInput.recording = m.trackingInput.recording.drop(2398).keep(1199);

	m.trackingInput.recording.select{|x| x[1] == 3}.collect{|x| x[2].keep(3)}.flop.plot;
	~last = [0, 0, 0];
	m.trackingInput.recording.select{|x| x[1] == 3}.collect{|x| x[2].keep(3)}
	.collect{|x| var o = (x-~last).squared.sum.sqrt/120;~last = x;o}.drop(1).plot;

	// more debugging code

	m.documents.asArray[0].tracks[0].trace.trajectory
	.pDownSample_(2)
	.pGrainDur_(0.3)
	.pHold_(2);

	m.trackingInput.visuals[1].trans_([0, -9, 0]);
	m.trackingInput.visuals[1].size_(40 / 100 * 5 / 2 ! 3);

	m.documents.asArray[0].tracks[0].trace.trajectory = [[0, [-5.1, 0, 0]], [m.documents.asArray[0].tracks[0].trace.sound.duration/2, [0, 0, 0]], [m.documents.asArray[0].tracks[0].trace.sound.duration, [5.1, 0, 0]]];

	m.documents.asArray[0].tracks[0].trace.sound = ~sound;

	m.documents.asArray[0].tracks[0].trace.sound.duration;

	~sound = Buffer.read(s, "~/current/KTH/DtV2/video1/speaker01_TP13_left.wav".standardizePath);

	~verbose = true;
	~verbose = false;

	m.documents.asArray[0].tracks[1].players.first.pDownSample_(2);
	m.documents.asArray[0].tracks[1].players.first.pGrainDur_(0.3);
	m.documents.asArray[0].tracks[1].players.first.pHold_(2);


	~test = { Out.ar(m.masterBus, SinOsc.ar(1000)) }.play;
	~test.free;
	m.trackingInput.activeChannels(1).size;

	TrSonifyTracking(m.trackingInput, 2, m.masterBus, 0, 180);
	m.trackingInput.recording;
	m.cameraPresets;
	m.cameraPresets[0] === m.cameraPresets[1];
	m.trackingInput.showRecorderGUI;
	m.documents.asArray.collect(_.path);
	m.documents.asArray[0].tracks.collect(_.name);
	m.documents.asArray[0].tracks.collect{ arg x; [x.name, x.players]};

	m.vServer.enabled;
	m.vServer.frameRate(20);
	m.vServer.verbose_(false);
	m.vServer.replyPort(NetAddr.langPort);
	m.vServer.nextID;
	m.vScene.camera(0, 0, 20, 0, 0, 0);

	b = VBuffer3D(m.vScene, Color.green.asArray * 255, 1.5, 2.0).visible_(true);
	b.setPoints([[0, 0, 0], [10, 0, 0], [10, 10, 0]]);
	b.addPoints;
	b.color = [255, 255, 0, 255];
	b.visible = true;
	b.file;


	m.documents.asArray[0].tracks[1].trace.trajectory;

	m.bufferPool.pool[1].duration;

	x = m.documents.asArray[0];
	x.tracks;
	x.tracks[0].name.class;
	x.removeTrack(x.tracks[1].name);
	x.tracks.select{ arg t; t.name == x.tracks[0].name };

	// remove repeated positions in trajectory
	m.documents.asArray[0].tracks[2].trace.trajectory.slide(2, 1).clump(2).reject{|x| x[0][1] == x[1][1]};

	m.traceServer.serverAddr.sendMsg("/setverbose", 1);
	m.traceServer.serverAddr.sendMsg("/setverbose", 0);

	m.traceServer.dump;

	m.traceServer.serverAddr.sendMsg("/printtrace", 1);
	m.traceServer.serverAddr.sendMsg("/printtraces");

	m.traceServer.createTrajectory(2, 8);
	m.documents.asArray[0].tracks[0].updateVizualisation;

	m.documents.asArray[0].tracks[0].trace.trajectory.collect{|x| x[0]}.plot;
	m.documents.asArray[0].tracks.asArray.collect{|t| t.trace.trajectory.collect{|x| x[0]}}.plot;
	m.documents.asArray[0].tracks[0].trace.trajectory.last[0];

	~savings = { arg t; 100-(t.size/((t.last[0]-t.first[0])*120)*100) };
	~savings.(m.documents.asArray[0].tracks[0].trace.trajectory);
	m.documents.asArray[0].tracks.asArray.collect{|t| ~savings.(t.trace.trajectory)}.mean; // 85

	l = 0;
	(m.documents.asArray[0].tracks[0].trace.trajectory.collect{|e| e[0]}*120).round.collect{|x| var r = x-l; l=x; r}.mean;

	m.documents.asArray[0].tracks[0].trace.trajectory.slide(2).clump(2).collect{|p| (p[0][1]-p[1][1]).squared.sum.sqrt}.plot;
	m.documents.asArray[0].tracks[0].trace.trajectory.collect(_[0]).slide(2).clump(2).collect{|x| x[1]-x[0]};
	m.documents.asArray[0].tracks[0].trace.trajectory.do{|e| e[0] = e[0]+1};
	m.documents.asArray[0].tracks[0].trace.trajectory[0][0]=0;

	m.trackingInput.visuals[0].size;
	m.trackingInput.visuals.do(_.size_([0.03, 0.03, 0.03]));

	m.trackingInput.dump;

	m.documents.asArray[0].tracks[0].players[0].dump;

	m.trackingInput.recording.select{|x| x[1] == 1}.collect{|x| x[2].keep(3)}.keep(100).flop.plot;

	// look speed of one target in a recording

	~speeds = m.trackingInput.recording.select{|x| x[1] == 1}.collect{|x|
			x[2].keep(3)}.slide(2).clump(2).collect{|x| (x[1]-x[0]).squared.sum.sqrt};
	[~speeds.minItem, ~speeds.maxItem] * 120;
	~speeds.drop(30*120).keep(30*120).plot;
	~speeds.drop(25*120).keep(20*120).plot;
	~speeds.histo(1000).plot;

	(~halfbandDecimate.(~halfbandDecimate.(~halfbandDecimate.(~halfbandDecimate.(~halfbandDecimate.(~speeds.as(Signal)))))).keep(100)*120).plot;

	// sonifyer

	m.sonifyers;
	m.sonifyers[0].minMapSpeed = 0.001;
	m.traceServer.compute(1, 3, [0, 0, 0], [1,2,3], 0.2, 3);

	m.documents.asArray[0].tracks[2].players[0].pMinSpeedAmp = 1;
	m.documents.asArray[0].tracks[2].players[1].pMinSpeedAmp = 1;

}

