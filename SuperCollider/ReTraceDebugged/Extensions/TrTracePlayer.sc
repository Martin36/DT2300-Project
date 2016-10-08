TrTracePlayer {
	var traceServer;
	var trackingInput;
	var <trace;

	var isPlaying;
	var trackingCB;
	var traceservCB;
	var traceservCB2;
	var lastPos;
	var lastPosRel;
	var synth;
	var synthGroupPlay;
	var outputBus;
	var feedbackBus;
	var watchDog;
	var counter;
	var lastTime;

	var <pTarget;
	var <pOn;
	var <>pDist;
	var <>pPoly;
	var <pSynth;
	var <>pOutputs;
	var <>pLevel;
	var <>pGrainDur;
	var <>pAttack;
	var <>pHold;
	var <>pDecay;
	var <>pDistExpon;
	var <>pMaxSpeed;
	var <>pMinSpeedAmp;
	var <>pMinRate;
	var <>pMaxRate;
	var <>pDownSample;
	var <>pMinAge;
	var <>pMaxAge;
	var <>pAlignment;

	var quatConOp;
	var quatRotOp;

	var speedOp;
	var speedOpRel;

	var vizSphere;

	var spatCoords;

	*makeSynths {

		SynthDef(\trAsync, { arg out, fb, rate, graindur, buf, pos, amp, transp = 1;
			var output = LeakDC.ar(TGrains.ar(2, Impulse.ar(rate), buf, transp, pos, graindur, -1, amp.lag, 4)[0]);
			OffsetOut.ar(fb, output);
			OffsetOut.ar(out, output);
		}).add;

		SynthDef(\trSync, { arg out, fb, rate, amp, graindur, buf, pos, transp = 1, attack = 1, hold = 0, decay = 1;
			var sum = attack + hold + decay;
			var env = Env.linen(attack / sum , hold / sum, decay / sum, 1, 'sine');
			var start = (pos - (graindur / 2)) * BufSampleRate.kr(buf);
			var playbuf = PlayBuf.ar(1, buf, transp * BufRateScale.kr(buf), startPos: start, loop: 1);
			var output = EnvGen.kr(env, 1, levelScale: amp, timeScale: graindur, doneAction:2) * playbuf;
			OffsetOut.ar(fb, output);
			OffsetOut.ar(out, output);
		}).add;

		SynthDef(\trTape, { arg out, fb, buf, pos, amp, gate = 1, transp = 1;
			var env = Env.asr(1.0, 1.0, 0.0001);
			var output = BufRd.ar(1, buf,
				LPF.ar(K2A.ar(pos) * Server.default.sampleRate, 3),
				0, 4) * EnvGen.ar(env, gate) * amp.lag;
			OffsetOut.ar(fb, output);
			OffsetOut.ar(out, output);
		}).add;

	}

	*new { arg trace, trackingInput, traceServer;
		^super.new.init(trace, trackingInput, traceServer);
	}

	init { arg argTrace, argTrackingInput, argTraceServer;
		trace = argTrace;
		traceServer = argTraceServer;
		trackingInput = argTrackingInput;
		outputBus = trace.track.scene.main.masterBus;
		isPlaying = false;
		trackingCB = { arg index, channel, time, pos;
			this.trackingInput(pos.keep(3));};
		traceservCB = { arg nth, more, speed, time, distance;
			this.soundOutput(nth, more, speed, time, distance); };
		traceservCB2 = { arg nth, more, speed, time, distance;
			this.soundOutput(nth, more, speed, time, distance); };
		watchDog = TrWatchDog(0.1, { if (synth.notNil) { synth.set(\amp, 0) } });
		lastPos = [0, 0, 0];
		lastPosRel = [0, 0, 0];
		pTarget = 0;
		pOn = false;
		pDist = 10; // in cm
		pPoly = 1;
		synthGroupPlay = trace.track.scene.main.synthGroupPlay;
		this.pSynth_(\async);
		pOutputs = [0];
		pLevel = 0;
		pGrainDur = 0.124;
		pAttack = 1;
		pHold = 90;
		pDecay = 1;
		pDistExpon = 1;
		pMaxSpeed = 0.001;
		pMinSpeedAmp = 0.001;
		pMinRate = 20;
		pMaxRate = 50;
		pDownSample = 1;
		pAlignment = 0;
		pMinAge = 0;
		pMaxAge = 3600;

		counter = 0;
		lastTime = 0;

		quatConOp = EGM_quatCon.new();
		quatRotOp = EGM_quatRot.new();

		speedOp = EGM_velocity.new(3, trace.speedMedian, trace.speedMean);
		speedOpRel = EGM_velocity.new(3, trace.speedMedian, trace.speedMean);

/*		vizSphere = VSphere(trackingInput.scene, 0.3!3, [255, 255, 255, 255], 0!3, 0!3);
		vizSphere.trans_([0.0, 0.0, 0.0]);
		vizSphere.visible = false;*/

		spatCoords = [0, 0];

	}

	free {
		if (pOn) {
			this.disconnect;
		};
		watchDog.stop;
	}

	pOn_ { arg flag;
		pOn = flag;
		pMinAge = ~defaultMinAge;
		if (flag) {
			this.connect;
			vizSphere = VSphere(trackingInput.scene, 0.3!3, [255, 255, 255, 255], 0!3, 0!3);
			vizSphere.trans_([0.0, 0.0, 0.0]);
			vizSphere.visible = true;
		} {
			this.disconnect;
			vizSphere.delete();
		}
	}

	pTarget_ { arg value;
		if (pOn) {
			this.disconnect;
			pTarget = value;
			this.connect;
		} {
			pTarget = value;
		}
	}

	connect {
		trackingInput.connect(pTarget, trackingCB);
		traceServer.connect(pTarget, trace.id, traceservCB);
		if (trace.relativeTo != 0) {
			traceServer.connect(trace.relativeTo, trace.id2, traceservCB2);
		}
	}

	disconnect {
		trackingInput.disconnect(trackingCB);
		traceServer.disconnect(traceservCB);
		if (trace.relativeTo != 0) {
			traceServer.disconnect(traceservCB2);
		}
	}

	trackingInput { arg pos;
		var maxage = pMaxAge.clip(pMinAge, trace.sound.duration);

		spatCoords = pos.keep(2);
		if (trace.recordSpeed && (trace.relativeTo == 0)) {
			var speed = speedOp.(pos)*trace.speedScale;
			traceServer.compute(trace.id, pTarget, lastPos, speed, pDist / 100, pPoly, pMinAge, maxage, pAlignment);
			lastPos = speed;
			vizSphere.visible = true;
			vizSphere.trans_(speed*5.0);
		} {
			vizSphere.visible = false;
			traceServer.compute(trace.id, pTarget, lastPos, pos, pDist / 100, pPoly, pMinAge, maxage, pAlignment);
			lastPos = pos;
		};
		if (trace.relativeTo != 0) {
			var rel = trackingInput.getTarget(trace.relativeTo);
			var posRel = pos - rel.keep(3);
			var quat = rel.drop(3);
			quat = quatConOp.(quat);
			posRel = quatRotOp.(posRel,quat).drop(1);
			vizSphere.visible = true;
			if (trace.recordSpeed) {
				var speed = speedOpRel.(posRel)*trace.speedScale;
				vizSphere.trans_(speed*5.0);
				traceServer.compute(trace.id2, trace.relativeTo,
					lastPosRel, speed, pDist / 100, pPoly, pMinAge, maxage, pAlignment);
				lastPosRel = speed;
			} {
				vizSphere.trans_(posRel*5.0);
				traceServer.compute(trace.id2, trace.relativeTo,
					lastPosRel, posRel, pDist / 100, pPoly, pMinAge, maxage, pAlignment);
				lastPosRel = posRel;
			};
		};
	}

	getParameters {
		^IdentityDictionary[
			\target -> pTarget,
			\on -> false, // don't save this one
			\dist -> pDist,
			\poly -> pPoly,
			\synth -> pSynth,
			\outputs -> pOutputs,
			\level -> pLevel,

			\graindur -> pGrainDur,
			\attack -> pAttack,
			\hold -> pHold,
			\decay -> pDecay,
			\distexpon -> pDistExpon,
			\maxspeed -> pMaxSpeed,
			\minspeedamp -> pMinSpeedAmp,
			\minrate -> pMinRate,
			\maxrate -> pMaxRate,
			\downsample -> pDownSample,
			\alignment -> pAlignment,
			\minage -> pMinAge
		]
	}

	setParameters { arg d;

		pTarget = d[\target];
		pOn = d[\on];
		pDist = d[\dist];
		pPoly = d[\poly];
		pSynth = d[\synth];
		pOutputs = d[\outputs];
		pLevel = d[\level];

		pGrainDur = d[\graindur];
		pAttack = d[\attack];
		pHold = d[\hold];
		pDecay = d[\decay];
		pDistExpon = d[\distexpon];
		pMaxSpeed = d[\maxspeed];
		pMinSpeedAmp = d[\minspeedamp];
		pMinRate = d[\minrate];
		pMaxRate = d[\maxrate];
		pDownSample = d[\downsample];
		pAlignment = d[\alignment];
		pMinAge = d[\minage];

		// backwards compatibility
		if (pGrainDur.isNil) { pGrainDur = 0.3 };
		if (pAttack.isNil) { pAttack = 1 };
		if (pHold.isNil) { pHold = 0 };
		if (pDecay.isNil) { pDecay = 1 };
		if (pDistExpon.isNil) { pDistExpon = 1 };
		if (pMaxSpeed.isNil) { pMaxSpeed = 0.001 };
		if (pMinSpeedAmp.isNil) { pMinSpeedAmp = 0.001 };
		if (pMinRate.isNil) { pMinRate = 20 };
		if (pMaxRate.isNil) { pMaxRate = 50 };
		if (pDownSample.isNil) { pDownSample = 1 };
		if (pAlignment.isNil) { pAlignment = 0 };
		if (pMinAge.isNil) { pMinAge = 1 };
		this.changed(\parameters);
	}

	pSynth_ { arg symbol;
		switch (symbol,
			\async, {
				synth.free;
				synth = Synth(\trAsync, target:synthGroupPlay);
				pSynth = 0;
			},
			\sync, {
				synth.free;
				synth = nil;
				pSynth = 1;
			},
			\tape, {
				synth.free;
				synth = Synth(\trTape, target:synthGroupPlay);
				pSynth = 2;
			},
		);
	}

	soundOutput { arg nth, more, speed, time, distance;
		var transp;

		if (~verbose.notNil && ~verbose) { [nth, more, speed, time, distance].postln };

		if (time < lastTime && ~reverseWhenPlayingBackwards) {
			transp = -1;
		} {
			transp = 1;
		};

		switch (pSynth,
			0, {
				watchDog.alive_(true); // why only here and not in next clause
				synth.set(
					\fb, trace.feedbackBus,
					\out, outputBus.index + pOutputs[0],
					\buf, trace.sound,
					\pos, time,
					\amp, pow(distance.linlin(0, pDist / 100, 1, 0), pDistExpon)
					* speed.linexp(0, pMaxSpeed, pMinSpeedAmp, 1)
					* pLevel.value.dbamp,
					\rate, rrand(pMinRate, pMaxRate),
					\graindur, pGrainDur,
					\transp, transp
				)
			},
			1, {
				var amp = pow(distance.linlin(0, pDist / 100, 1, 0), pDistExpon)
					* speed.linexp(0, pMaxSpeed, pMinSpeedAmp, 1)
					* pLevel.value.dbamp;
				if (amp > 0.001 && ((counter % pDownSample) == 0)) {
					Synth(\trSync, [
						\fb, trace.feedbackBus,
						\out, outputBus.index + pOutputs.choose,
						// spatialisation for Every Move a Sound
						// \out, outputBus.index + ~nearestOutputs.(2, spatCoords).choose,
						\buf, trace.sound,
						\pos, time,
						\amp, amp,
						\graindur, pGrainDur,
						\attack, pAttack,
						\hold, pHold,
						\decay, pDecay,
						\transp, transp
					], target:synthGroupPlay)
				};
				counter = counter + 1;
				if (counter == pDownSample) {
					counter = 0;
				}
			},
			2, {
				watchDog.alive_(true); // why only here and not in next clause
				synth.set(
					\fb, trace.feedbackBus,
					\out, outputBus.index + pOutputs[0],
					\buf, trace.sound,
					\pos, time,
					\amp, pow(distance.linlin(0, pDist / 100, 1, 0), pDistExpon)
					* speed.linexp(0, pMaxSpeed, pMinSpeedAmp, 1)
					* pLevel.value.dbamp,
					\gate, (if (abs(time - lastTime) > ~maxDt) {0.0} {1.0}),
					\transp, transp
				)
			}
		);
		lastTime = time;
	}
}
