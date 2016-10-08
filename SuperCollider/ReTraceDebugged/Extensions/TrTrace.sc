TrTrace {
	classvar lastId;

	var <id;
	var <id2;
	var <>trajectory;
	var <>trajectory2;
	var <>relativeTo; // if not 0, process absolute and relative trajectories

	var <>sound;
	var <recBuffer;
	var <recSynth;
	var recLevel;
	var <playSynth;
	var <feedbackBus;
	var <isRecording;
	var <recData;
	var recData2;
	var recStartTime;
	var <>fadeTime;
	var quatConOp;
	var quatRotOp;

	var <track;
	var traceServer;
	var bufferPool;
	var trackingInput;
	var trackingInputCallback;
	var trackingInputCallbackIncremental;
	var <>recordIncrementally;
	var timeFirstFrame;

	var <>speedMedian;
	var <>speedMean;
	var speedOp;
	var speedOpRel;
	var <>speedScale;
	var <>recordSpeed;

	var lastPos;
	var distThres;
	var skipCount;

	*initClass {
		lastId = 0;
	}

	*makeSynths {

		SynthDef(\trRecord, { arg in, fb, buf, amp = 1, fbamp = 0, gate = 1, fade = 0.1, thresh = -6, slope = 1, out = 7;
			var input = LeakDC.ar(SoundIn.ar(in) * amp.lag);
			var feedback = LeakDC.ar(SoundIn.ar(fb) * fbamp.lag);
			var env = Env.asr(fade, 1, fade, \sine);
			var ampThresh = thresh.dbamp;
			var max = ampThresh + ((1-ampThresh)*slope);
			// var rec = Compander.ar(input, input, ampThresh, 1.0, slope) * EnvGen.ar(env, gate) * (1/max);
			var rec = input * EnvGen.ar(env, gate);
			RecordBuf.ar(rec + feedback, buf);
		}).add;

		SynthDef(\trPlay, { arg out, buf, amp = 1;
			Out.ar(out, PlayBuf.ar(1, buf, doneAction:2) * amp);
		}).add;
	}

	*new { arg track, traceServer, bufferPool, trackingInput;
		^super.new.init(track, traceServer, bufferPool, trackingInput);
	}

	init { arg argTrack, argTraceServer, argBufferPool, argTrackingInput;

		track = argTrack;
		traceServer = argTraceServer;
		bufferPool = argBufferPool;
		trackingInput = argTrackingInput;

		quatConOp = EGM_quatCon.new();
		quatRotOp = EGM_quatRot.new();

		lastId = lastId + 1;
		id = lastId;
		lastId = lastId + 1;
		id2 = lastId;
		isRecording = false;
		recLevel = 1.0;
		sound = nil;
		playSynth = nil;
		fadeTime = 0.1;
		trajectory = nil;
		trajectory2 = nil;
		relativeTo = 0;
		recordIncrementally = false;
		recBuffer = nil;

		speedMedian = 3;
		speedMean = 4;
		speedOp = EGM_velocity.new(3, speedMedian, speedMean);
		speedOpRel = EGM_velocity.new(3, speedMedian, speedMean);
		speedScale = 100.0;
		recordSpeed = false;

		trackingInputCallback = { arg input, channel, time, posQuat;
			var pos = posQuat.keep(3);
			var speed = speedOp.(pos);
			var dist = (pos - lastPos).squared.sum.sqrt;

			if ((dist > ~distThres) || timeFirstFrame.isNil) {
				if (timeFirstFrame.isNil) {
					timeFirstFrame = time;
				};
				lastPos = pos;
				if (recordSpeed) {
					recData = recData.add([time, speed*speedScale]);
				} {
					recData = recData.add([time, pos]);
				};
				if (relativeTo != 0) {
					var rel = trackingInput.getTarget(relativeTo);
					var posRel = pos - rel.keep(3);
					var quat = rel.drop(3);
					// ["quaternion", quat.round(0.01)].postln;
					// ["translated", posRel.round(0.01)].postln;
					quat = quatConOp.(quat);
					posRel = quatRotOp.(posRel,quat).drop(1);
					// ["relative", posRel.round(0.01)].postln;
					if (recordSpeed) {
						recData2 = recData2.add([time, speedOpRel.(posRel)*speedScale]);
					} {
						recData2 = recData2.add([time, posRel]);
					};
				};
				// ("==================").postln;

			} {
				// ("skipping " ++ time.round(0.01)).postln;
				skipCount = skipCount + 1;
			};
		};

		trackingInputCallbackIncremental = { arg input, channel, time, posQuat; // refactor later!
			var pos = posQuat.keep(3);
			var speed = speedOp.(pos);
			var dist = (pos - lastPos).squared.sum.sqrt;

			if ((dist > ~distThres) || timeFirstFrame.isNil) {
				if (timeFirstFrame.isNil) {
					timeFirstFrame = time;
				};
				lastPos = pos;
				if (recordSpeed) {
					var sSpeed = speed*speedScale;
					recData = recData.add([time, speed]);
					traceServer.appendSample(id, time - timeFirstFrame, sSpeed[0], sSpeed[1], sSpeed[2]);
				} {
					recData = recData.add([time, pos]);
					traceServer.appendSample(id, time - timeFirstFrame, pos[0], pos[1], pos[2]);
				};
				if (relativeTo != 0) {
					var rel = trackingInput.getTarget(relativeTo);
					var posRel = posQuat.keep(3) - rel.keep(3);
					var quat = rel.drop(3);
					quat = quatConOp.(quat);
					posRel = quatRotOp.(posRel,quat).drop(1);
					if (recordSpeed) {
						var speedRel = speedOpRel.(posRel)*speedScale;
						recData2 = recData2.add([time, speedRel]);
						traceServer.appendSample(id2, time - timeFirstFrame, speedRel[0], speedRel[1], speedRel[2]);
					} {
						recData2 = recData2.add([time, posRel]);
						traceServer.appendSample(id2, time - timeFirstFrame, posRel[0], posRel[1], posRel[2]);
					};
				};
				// ("==================").postln;
			} {
				// ("skipping " ++ time).postln;
				skipCount = skipCount + 1;
			};
		};
		feedbackBus = Bus.audio(Server.default);
		recSynth = nil;
		^this;
	}

	free {
		// "free trace".postln;
		if (sound.notNil) {
			sound.free;
		};
		if (recBuffer.notNil) {
			bufferPool.release(recBuffer);
		};
		if (playSynth.isPlaying) {
			playSynth.free;
		};
		if (trackingInput.isConnected(trackingInputCallback)) {
			trackingInput.disconnect(trackingInputCallback);
		};
		if (trackingInput.isConnected(trackingInputCallbackIncremental)) {
			trackingInput.disconnect(trackingInputCallbackIncremental);
		};
		feedbackBus.free;
		if (trajectory.notNil) {
			traceServer.deleteTrajectory(id);
			trajectory = nil;
		};
		if (trajectory2.notNil) {
			traceServer.deleteTrajectory(id2);
			trajectory2 = nil;
		};
	}

	startRecording { arg target, inchannel;
		var channel = track.scene.main.inputChannels[inchannel];
		["recording channel", channel].postln;
		if (channel.isNil) {
			"TrTrace::startRecording: invalid channel number".warn;
			^false;
		};
		if (bufferPool.isNil) {
			"TrTrace::startRecording: no buffer pool".warn;
			^false;
		};
		if (traceServer.isNil) {
			"TrTrace::startRecording: no trace server".warn;
			^false;
		};
		if (isRecording) {
			"TrTrace::startRecording: is already recording".warn;
			^false;
		};
		if (recBuffer.isNil) {
			recBuffer = bufferPool.book;
		} {
			"TrTrace::startRecording: reuse booked buffer".warn;
		};
		if (recBuffer.isNil) {
			"TrTrace::startRecording: cannot book buffer".warn;
			^false;
		};
		if (~distThres.notNil) {
			distThres = ~distThres;
		} {
			distThres = 0;
		};
		recData = Array.new;
		if (relativeTo != 0) {
			recData2 = Array.new;
		};
		if (recordIncrementally == true) {
			traceServer.createTrajectory(id, track.scene.main.maxRecDur * 128); // assuming a typical tracking rate of 120 Hz
			if (relativeTo != 0) {
				traceServer.createTrajectory(id2, track.scene.main.maxRecDur * 128); // assuming a typical tracking rate of 120 Hz
			};
		};

		recSynth = Synth(\trRecord,
			[\in, channel, \amp, recLevel.dbamp, \fb, feedbackBus, \buf, recBuffer,
				\fade, fadeTime, \slope, ~recCompSlope, \thresh, ~recCompThresh].postln,
			track.scene.main.synthGroupRecM);
		recStartTime = SystemClock.seconds;
		lastPos = [0, 0, 0];
		timeFirstFrame = nil;
		skipCount = 0;
		if (recordIncrementally == true) {
			trackingInput.connect(target, trackingInputCallbackIncremental);
			sound = recBuffer;
		} {
			trackingInput.connect(target, trackingInputCallback);
			sound = recBuffer;
		};
		isRecording = true;
		^true;
	}

	setRecordingLevel { arg level;
		recLevel = level;
		if (isRecording) {
			recSynth.set(\amp, recLevel.dbamp);
		};
	}

	stopRecording { arg track;
		if (isRecording.not) {
			"TrTrace::stopRecording: is not recording".warn;
			^false;
		};
		if (recData.size == 0) {
			"TrTrace::stopRecording: trajectory is empty".warn;
			^false;
		};
		if (relativeTo != 0 && recData2.size == 0) {
			"TrTrace::stopRecording: trajectory 2 is empty".warn;
			^false;
		};
		{ // this should be done after fadeTime + 0.01 seconds
			var soundDur = SystemClock.seconds - recStartTime + fadeTime;
			var frames = soundDur * Server.default.sampleRate;
			var firstTime = recData.first[0];
			var trajectoryDur = recData.last[0] - firstTime;
			var newSound;

			recSynth.set(\gate, 0); // start fade out
			fadeTime.wait;          // wait for fade out
			if (recordIncrementally) {
				trackingInput.disconnect(trackingInputCallbackIncremental);
			} {
				trackingInput.disconnect(trackingInputCallback);
			};
			0.01.wait;              // add a few zeros to buffer
			recSynth.free;          // stop recorder
			recSynth = nil;

			trajectory = recData.collect{|x| [x[0] - firstTime, x[1]]}; // let times start at 0
			if (recordIncrementally.not) {
				traceServer.addTrajectory(id, trajectory);
			};
			if (relativeTo != 0) {
				trajectory2 = recData2.collect{|x| [x[0] - firstTime, x[1]]}; // let times start at 0
				if (recordIncrementally.not) {
					traceServer.addTrajectory(id2, trajectory2);
				};
			};

			// newSound = Buffer.alloc(Server.default, frames.asInteger, 1);
			// Server.default.sync;
			// recBuffer.copyData(newSound, 0, 0, frames);
			// Server.default.sync;
			// sound = newSound;
			// bufferPool.release(recBuffer);
			// recBuffer = nil;
			("TrTrace::stopRecording: completed (id = " ++ id ++
				", sound = " ++ soundDur.round(0.001) ++
				", trajectory = " ++ trajectoryDur.round(0.001) ++
				", skipped " ++ ((skipCount / (recData.size + skipCount)) * 100).round(1) ++
				" %)").postln;
			isRecording = false;
			{
				track.changed(\recordingFinished);
				this.changed(\traceChanged);
			}.defer;
		}.fork;
		^true;
	}

	startPlayingSound { arg channel, amp;
		if (isRecording) {
			"TrTrace::startPlaying: cannot play while recording".warn;
			^false;
		};
		if (sound.isNil) {
			"TrTrace::startPlaying: no sound recorded".warn;
			^false;
		};
		if (playSynth.isPlaying) {
			"TrTrace::startPlaying: is already playing".warn;
			^false;
		};
		playSynth = Synth(\trPlay, [\out, track.scene.main.masterBus.index + channel, \buf, sound, \amp, amp], track.scene.main.synthGroupPlay);
		NodeWatcher.register(playSynth, true); // for isPlaying flag
		^true;
	}

	stopPlayingSound {
		if (playSynth.isPlaying) {
			playSynth.free;
		};
		^true;
	}

	setRecAmp { arg amp;
		if (isRecording && recSynth.notNil) {
			recSynth.set(\amp, amp);
			^true;
		} {
			^false;
		}
	}

	setFeedbackAmp { arg amp;
		if (isRecording && recSynth.notNil) {
			recSynth.set(\fbamp, amp);
			^true;
		} {
			^false;
		}
	}

	save { arg path; // writes two or three files (path ++ ".tpbd{_2}", path ++ ".aif")
		if (trajectory.notNil) {
			this.prWriteTPBD([\timetagged, true], trajectory, path ++ ".tpdb");
		};
		if (trajectory2.notNil) {
			this.prWriteTPBD([\timetagged, true], trajectory2, path ++ "_2.tpdb");
		};
		if (sound.notNil) {
			var frames = trajectory.last[0] * sound.sampleRate;
			if (frames > sound.numFrames) {
				frames = sound.numFrames;
				"TrTrace::save: not enough frames in sound".warn;
			};
			sound.write(path ++ ".aif", sampleFormat:"float", numFrames:frames);
		};
	}

	load { arg path, action;
		var snd = path ++ ".aif";

		if (File.exists((path ++ ".tpbd").postln).postln) {
			trajectory = this.prReadTPBD(path ++ ".tpbd")[1].postln; // skip header
			traceServer.addTrajectory(id, trajectory);
		} {
			trajectory = nil;
		};
		if (File.exists(path ++ "_2.tpbd")) {
			trajectory2 = this.prReadTPBD(path ++ "_2.tpbd")[1]; // skip header
			traceServer.addTrajectory(id2, trajectory2);
		} {
			trajectory2 = nil;
		};
		this.changed(\traceChanged);
		if (File.exists(snd)) {
			sound = Buffer.read(Server.default, snd, action:action);
		} {
			sound = nil;
			action.value;
		};
	}

	prWriteTPBD { arg header, data, path;

		if (header.isNil) {
			header = Dictionary.new;
		};

		if (header.isArray) { // make dictionary from collection
			header = Dictionary.newFrom(header);
		};

		if (header[\timetagged].isNil) { // assume no time tags
			header[\timetagged] = false;
		};

		if (header[\shape].isNil) { // add shape
			if (header[\timetagged]) {
				header[\shape] = data[0][1].shape;
			} {
				header[\shape] = data[0].shape;
			};
		};

		if (header[\frames].isNil) { // add frames if timetagged
			if (header[\timetagged]) {
				header[\frames] = data.size;
			};
		};

		if (path.splitext[1].isNil) { // add extension if missing
			path = path ++ ".tpbd";
		};

		if (path.splitext[1] != "tpbd") { // change extension if wrong
			path = path.splitext[0] ++ ".tpbd";
		};

		[header, data].writeBinaryArchive(path);
	}

	prReadTPBD { arg path;

		if (path.splitext[1].isNil) {  // add extension if missing
			path = path ++ ".tpbd";
		};

		if (path.splitext[1] != "tpbd") { // change extension if wrong
			path = path.splitext[0] ++ ".tpbd";
		};

		^Object.readBinaryArchive(path);
	}
}
