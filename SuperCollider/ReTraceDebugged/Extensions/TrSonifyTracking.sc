TrSonifyTracking {
	var <synth;
	var outputBus;
	var <trackingInput;
	var trackingInputCallback;
	var lastTime;
	var lastPos;
	var target; // for messages only
	var <>srate;
	var <>maxDelta;
	var samePos;
	var <>maxSpeed;
	var <>minMapSpeed; // m/s
	var <>maxMapSpeed; // m/s
	var <level;
	var justConnected;
	var window;
	var <>post;
	var frames;
	var lastFrames;
	var <scope;

	*new { arg trackingInput, target, bus, out = 0, srate = 180;
		^super.new.init(trackingInput, target, bus, out, srate);
	}

	init { arg argTrackingInput, target, bus, out, argSrate;
		outputBus = bus;
		srate = argSrate;
		trackingInput = argTrackingInput;
		trackingInputCallback = { arg input, channel, time, pos; this.sonify(pos.keep(3)); };
		maxSpeed = 0;
		minMapSpeed = 0.001;
		maxMapSpeed = 15;
		lastTime = SystemClock.seconds;
		lastPos = [0, 0, 0];
		samePos = 0;
		maxDelta = 1/100;
		level = -20;
		post = false;
		lastFrames = nil;
		scope = nil;
		trackingInput.main.sonifyers.add(this);
		Server.default.waitForBoot({
			{
				SynthDef(\sonifyTracking, { arg out = 0, amp = 0, freq = 1000, samp = 1, namp = 0;
					var noise = WhiteNoise.ar() * namp;
					var sine = SinOsc.ar(freq) * samp;
					Out.ar(out, (sine + noise) * amp);
				}).add;
				Server.default.sync;
				synth = Synth(\sonifyTracking, [\out, outputBus.index + out]);
				Server.default.sync;
				window = TrSonifyTrackingWindow(this).front;
			}.fork(AppClock)
		});
	}

	free {
		this.disconnect;
		synth.free;
		trackingInput.main.sonifyers.remove(this);
	}

	connect { arg argTarget;
		this.disconnect;
		target = argTarget;
		justConnected = true;
		trackingInput.connect(target, trackingInputCallback);
		this.setLevel(level);
	}

	disconnect {
		if (trackingInput.isConnected(trackingInputCallback)) {
			trackingInput.disconnect(trackingInputCallback);
		};
		synth.set(\amp, 0);
		synth.set(\freq, 0);
	}

	sonify { arg pos;
		var now = SystemClock.seconds;
		var delta = now - lastTime;
		var speed = (pos - lastPos).squared.sum.sqrt / srate.reciprocal;

		lastTime = now;

		if (justConnected) {
			justConnected = false;
			lastPos = pos;
			samePos = 0;
			maxSpeed = 0;
			lastFrames = now;
			frames = 0;
			^false;
		};

		frames = frames + 1;
		if (now >= (lastFrames + 1)) {
			var copy = frames;
			{ window.avgRateN.value_(copy) }.defer;
			frames = 0;
			lastFrames = now;
		};

		if (delta > maxDelta) {
			if (post) {
				("TrSonifyTracking: [" ++ target ++ "] rate = " ++ delta.reciprocal.round(1)).postln;
			};
			{ window.rateN.value_(delta.reciprocal.round(1)) }.defer;

		};

		if (samePos == 0) {
			synth.set(\freq,
				speed.clip(0.0, maxMapSpeed).expexp(minMapSpeed, maxMapSpeed, 250.0, 8000.0));
		};

		{ window.speedN.value_(speed) }.defer;

		if (scope.notNil) {
			scope.addData([speed]);
		};

		if (speed > maxSpeed) {
			maxSpeed = speed;
			{ window.maxSpeedN.value_(maxSpeed) }.defer;
			if (post) {
				("TrSonifyTracking: [" ++ target ++ "] max speed = " ++ maxSpeed).postln;
			}
		};

		if (pos == [0, 0, 0]) {
			if (post) {
				("TrSonifyTracking: [" ++ target ++ "] pos = [0, 0, 0]").postln;
			}
		};

		if (lastPos == pos) {
			samePos = samePos + 1;
			synth.set(\namp, 1.0);
			synth.set(\famp, 0.0);
		} {
			if (samePos != 0) {
				synth.set(\namp, 0.0);
				synth.set(\famp, 1.0);
				if (post) {
					("TrSonifyTracking: [" ++ target ++ "] " ++ (samePos + 1) ++ " times same pos " ++ lastPos).postln;
				}
			};
			samePos = 0;
		};
		lastPos = pos;
	}

	setLevel { arg argLevel;
		level = argLevel;
		if (trackingInput.isConnected(trackingInputCallback)) {
			synth.set(\amp, level.dbamp);
		}
	}

	setOutput { arg channel;
		synth.set(\out, outputBus.index + channel);
	}

	setScope { arg on;
		if (on.not && scope.notNil) {
			scope.close;
			scope = nil;
		} {
			if (scope.isNil) {
				scope = TrScope(bounds:Rect(100, 100, 600, 50), pixelsPerSample:1, maxVal:maxMapSpeed).front;
			};
		}
	}
}

TrSonifyTrackingWindow : QWindow {
	var model;
	var ampSliderSpec;
	var volumeS;
	var targetT, targetN, connectB;
	var speedT, <speedN, maxSpeedT, <maxSpeedN, resetMaxSpeedB;
	var rateT, <rateN, minRateT, minRateN, avgRateT, <avgRateN;
	var channelT, channelN, postB;
	var scopeB;

	*new { arg sonifyer;

		^super.new("Sonify Tracking", Rect(100, 100, 340, 160)).init(sonifyer);
	}

	init { arg sonifyer;

		model = sonifyer;
		ampSliderSpec = ControlSpec.new(-90.0, 6.0, \lin, 0.01);

		volumeS = QSlider.new.orientation_(\horizontal).maxHeight_(20)
		.step_(0.01).shift_scale_(0.1).alt_scale_(3.0)
		.action_({ arg slider;
			var db = ampSliderSpec.map(slider.value);
			model.setLevel(db);
		}).value_(ampSliderSpec.unmap(-20));

		targetT = QStaticText.new.string_("target");

		targetN = QNumberBox.new.clipLo_(0).clipHi_(6).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1)
		.align_(\center).value_(0)
		.action_({ arg number;
			if (connectB.value == 1) {
				model.disconnect;
				model.connect(number.value.asInteger);
			}
		});

		connectB = QButton.new.states_([ [ "connect" ], [ "disconnect", Color.red ] ])
		.action_({ arg button;
			if (button.value.booleanValue) {
				model.connect(targetN.value.asInteger);
			} {
				model.disconnect;
			}
		});

		speedT = QStaticText.new.string_("speed");
		speedN = QNumberBox.new.decimals_(3).align_(\center).value_(0).minWidth_(50);
		maxSpeedT = QStaticText.new.string_("max");
		maxSpeedN = QNumberBox.new.decimals_(3).align_(\center).value_(0).minWidth_(50);
		resetMaxSpeedB = QButton.new.states_([ [ "reset" ] ]).action_({ model.maxSpeed_(0) });

		rateT = QStaticText.new.string_("rate");
		rateN = QNumberBox.new.align_(\center).value_(0).minWidth_(50);

		minRateT = QStaticText.new.string_("min");
		minRateN = QNumberBox.new.clipLo_(0).clipHi_(200).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(5).alt_scale_(20).minWidth_(50)
		.align_(\center).action_({ arg number;
			model.maxDelta_(number.value.reciprocal);
		});

		minRateN.valueAction_(100);

		avgRateT = QStaticText.new.string_("avg");
		avgRateN = QNumberBox.new.align_(\center).value_(0).minWidth_(50);

		channelT = QStaticText.new.string_("output");
		channelN = QNumberBox.new.clipLo_(1).clipHi_(8).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1)
		.align_(\center).action_({ arg number;
			model.setOutput((number.value - 1).asInteger);
		});

		postB = QButton.new.states_([ [ "post" ], [ "quiet", Color.red ] ]).value_(0)
		.action_({ arg button;
			model.post_(button.value.booleanValue);
		});

		scopeB = QButton.new.states_([ [ "scope on" ], [ "scope off", Color.red ] ]).value_(0)
		.action_({ arg button;
			model.setScope(button.value.booleanValue);
		});


		this.layout_(VLayout(volumeS,
			HLayout(channelT, channelN, targetT, targetN, connectB),
			HLayout(speedT, speedN, maxSpeedT, maxSpeedN, resetMaxSpeedB),
			HLayout(rateT, rateN, minRateT, minRateN, avgRateT, avgRateN, postB, scopeB)
		));

		this.onClose_({
			model.free;
		});
	}
}
