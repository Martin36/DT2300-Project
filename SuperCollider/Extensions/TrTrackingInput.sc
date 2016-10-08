/*
TrTrackingInput provides tracking data coming from OSC to subscribers.
Tracking data can also be polled from a cache holding the last input (-getTarget)
A set of channels to listen to has to be specified.
Each subscriber chooses a channel and provides a callback (-connect).
Channels can be disabled and enabled individually. Input can also be simulated (-input)
The activity of all channels can be probed (-activeChannels)
Tracking data can be recorded, saved, loaded, and played back (disables live input)
*/

TrTrackingInput {
	var <oscPath;     // what to listen for (OSC message)
	var <channels;    // channels to listen to (first argument of OSC message)
	var <scene;       // for visualisation
	var <main;        // backpointer always useful

	var <visuals;     // VServer objects
	var <enabled;     // enabled flag
	var <lastPos;     // last position of channel (to filter repetitions)
	var callbacks;    // array (per channel) of arrays of callbacks
	var respFunc;     // function called by responder or playback task
	var responder;    // OSCFunc
	var lastTime;     // time of last activity in channel (to detect activity)
	var <>blockOSC;   // block OSC input

	var <>recording;  // recorded data
	var <isRecording; // flag
	var <isPlaying;   // flag
	var <>recIsSaved; // if recording is saved on disk (i.e. loaded from or saved to disk)
	var <>window;     // recording GUI
	var <playIndex;   // playback index in recording
	var <>looping;    // flag for playback looping
	var player;       // playback task
	var lastLocUpdate;// time of last recording/playback time update for window
	var prevTime;     // last time in playback task

	var maxTargets;   // maximal number of targets for cache
	var <inputCache;  // remember tracking inputs
	var <>transInCB;  // transform input (in: this, channel, time, data; ret: data)
	var <filters;     // lowpass filters for positions

	var <>vicon;

	*new { arg oscPath, channels, scene, main;
		^super.newCopyArgs(oscPath, channels, scene, main).init;
	}

	init {
		callbacks = Array.fill(channels.size, { Array.new });
		this.makeVisuals;
		enabled = Array.fill(channels.size, { true });
		lastTime = Array.fill(channels.size, { 0 });
		lastPos = Array.fill(channels.size, { nil });
		blockOSC = false;
		maxTargets = 32;
		transInCB = nil;
		inputCache = Array.fill(maxTargets, { [0, 0, 0, 0, 0, 0, 1] }); // receiving 7 values (pos + quart)
		filters = Array.fill(maxTargets, { Array.fill(3, { EGM_lpf() }) }); // initially neutral

		if (~viconStyle.isNil) {
			vicon = false;
		} {
			if (~viconStyle) {
				vicon = true;
			} {
				vicon = false;
			}
		};

		if (vicon.not) {
			responder = OSCFunc({ arg msg, time, addr, recvPort;
				var channel = msg[1].asInteger;
				inputCache[channel] = [
					filters[channel][0].(msg[2].neg),    // X -> -x,
					filters[channel][1].(msg[4]),        // Y -> z,
					filters[channel][2].(msg[3]),        // Z -> y, Z pointing to wall
					msg[8], msg[5].neg, msg[7], msg[6]]; // angle as quarternion
				if (isPlaying.not && blockOSC.not) {
					var index = channels.indexOf(channel); // do this once for all callbacks registered to this channel
					if (index.notNil) { // if we are listening to this channel
						if (enabled[index]) { // if the channel is enabled
							var pos = inputCache[channel].keep(3);
							var quart = inputCache[channel].drop(3);
							if (pos != lastPos[index]) { // filter repeated values
								// time this thread started to run (triggered by OSC input), should be OSC time-tag
								var now = thisThread.seconds;
								lastTime[index] = now; // we are only active, if we actually dispatch
								this.dispatch(index, channel, now, pos ++ quart);
								if (isRecording) {
									recording = recording.add([now, channel, pos ++ quart]);
									if (window.notNil && (now > (lastLocUpdate + 0.25))) {
										var seconds = now - recording[0][0];
										{ window.setDur(seconds) }.defer;
										lastLocUpdate = now;
									};
								};
								lastPos[index] = pos;
							}
						}
					}
				}
			}, oscPath).permanent_(true);
		} {
			responder = Array.fill(~viconTargets, {arg i;
				OSCFunc({ arg msg, time, addr, recvPort;
					var channel = (i+1).asInteger;
					var pos = msg.drop(1)/1000;
					// [channel, "arrived", pos].postln;
					inputCache[channel] = [
						filters[channel][0].(pos[0]),        //
						filters[channel][1].(pos[1]),        //
						filters[channel][2].(pos[2]),        //
					1.0, 0.0, 0.0, 0.0];     // 0 for Vicon!
					if (isPlaying.not && blockOSC.not) {
						var index = channels.indexOf(channel); // do this once for all callbacks registered to this channel
						if (index.notNil) { // if we are listening to this channel
							if (enabled[index]) { // if the channel is enabled
								var pos = inputCache[channel].keep(3);
								var quart = inputCache[channel].drop(3);
								if (pos != lastPos[index]) { // filter repeated values
									// time this thread started to run (triggered by OSC input), should be OSC time-tag
									var now = thisThread.seconds;
									lastTime[index] = now; // we are only active, if we actually dispatch
									this.dispatch(index, channel, now, pos ++ quart);
									if (isRecording) {
										recording = recording.add([now, channel, pos ++ quart]);
										if (window.notNil && (now > (lastLocUpdate + 0.25))) {
											var seconds = now - recording[0][0];
											{ window.setDur(seconds) }.defer;
											lastLocUpdate = now;
										};
									};
									lastPos[index] = pos;
								}
							}
						}
					}
				}, (oscPath ++ (i+1) ++ oscPath ++ (i+1) ++ "/T"))
			})
		};

		// recording

		recording = nil;
		isRecording = false;
		isPlaying = false;
		looping = false;
		recIsSaved = true;
	}

	finish {
		responder.free;
	}

	enable {
		responder.enable;
	}

	disable {
		responder.disable;
	}

	enableChannel { arg channel;
		var index = channels.indexOf(channel);
		if (index.notNil) {
			enabled[index] = true;
			^true;
		} {
			("TrTrackingInput::enableChannel: illegal channel number").warn;
			^false;
		}
	}

	disableChannel { arg channel;
		var index = channels.indexOf(channel);
		if (index.notNil) {
			enabled[index] = false;
			^true;
		} {
			("TrTrackingInput::disableChannel: illegal channel number").warn;
			^false;
		}
	}

	channels_ { arg array;
		if (channels.size != array.size) {
			("TrTrackingInput::channels_: number of channels does not match").warn;
			^false;
		};
		channels = array;
		^true;
	}

	connect { arg channel, callback;
		var index = channels.indexOf(channel);
		if (index.isNil) {
			("TrTrackingInput::connect: channel not found (" ++ channel ++ ")").warn;
			^false;
		} {
			callbacks[index] = callbacks[index].add(callback);
			^true;
		}
	}

	disconnect { arg callback;
		if (this.isConnected(callback).not) {
			"TrTrackingInput::disconnect: callback not found".warn;
			^false;
		};
		callbacks = callbacks.collect{ arg callbacks; callbacks.reject{ arg test; test == callback }};
		^true;
	}

	isConnected { arg callback;
		^(callbacks.collect{ arg callbacks; callbacks.includes(callback).binaryValue }.sum != 0)
	}

	dispatch { arg index, channel, time, data;
		if (transInCB.notNil) {
			data = transInCB.value(this, channel, time, data);
		};
		visuals[index].trans = (data.keep(3) * 5);
		callbacks[index].do{ arg callback; callback.value(this, channel, time, data)};
	}

	input { arg channel, time, data;
		var index = channels.indexOf(channel);

		if (index.notNil) {
			lastTime[index] = Main.elapsedTime;
			this.dispatch(index, channel, time, data);
		}
	}

	activeChannels { arg interval;
		var now = Main.elapsedTime;
		^lastTime.collect{ arg time, index;
			if (enabled[index] && ((now - time) < interval)) { channels[index] } { nil } }
		.reject(_.isNil)
	}

	makeVisuals {
		visuals = Array.fill(channels.size, { arg i;
			var color = Color.hsv(i / channels.size, 1, 1, 1).asArray * 255;
			VSphere(scene, 0.3!3, color, 0!3, 0!3) });
	}

	updateVizualisation {
		visuals.do(_.delete);
		this.makeVisuals;
	}

	// recording

	showRecorderGUI {
		if (window.isNil) {
			window = TrTrackingRecorderGUI(this).front;
		} {
			window.front;
		}
	}

	startRecording {
		if (isPlaying.not) {
			recording = Array.new;
			isRecording = true;
			recIsSaved = false;
			lastLocUpdate = 0;
			this.changed(\recorderRecording);
		}
	}

	stopRecording {
		if (isRecording) {
			isRecording = false;
			this.changed(\recorderStopped);
		}
	}

	saveRecording { arg path;
		recording.writeBinaryArchive(path);
		recIsSaved = true;
	}

	loadRecording { arg path;
		if (recIsSaved || recording.isNil) {
			recording = Object.readBinaryArchive(path);
			window.setDur(this.recordingDuration);
		}
	}

	clearRecording {
		recording = nil;
	}

	startPlaying {
		if (recording.notNil && (recording.size > 1) && isRecording.not) {
			var size = recording.size;

			prevTime = recording[0][0];
			lastLocUpdate = 0;
			isPlaying = true;
			playIndex = 0;

			player = Task{
				var sample, time, channel, pos, index, delta;
				loop {
					sample = recording[playIndex];
					time = sample[0];
					channel = sample[1];
					pos = sample[2];
					index = channels.indexOf(channel);

					playIndex = playIndex + 1;

					// tell window where we are
					if (time > (lastLocUpdate + 0.1)) {
						var seconds = time - recording[0][0];
						if (window.notNil) {
							{ window.setLoc(seconds); window.setSliderLoc(seconds) }.defer;
						};
						lastLocUpdate = time;
					};

					if (playIndex >= size) {
						if (looping) {
							playIndex = 0;
							delta = 0.01;
							prevTime = recording[0][0];
							lastLocUpdate = 0;
						} {
							var seconds = time - recording[0][0];
							player.stop;
							if (window.notNil) {
								{
									window.stopB.valueAction_(1);
									window.setLoc(seconds);
									window.setSliderLoc(seconds);

								}.defer;
							}
						}
					} {
						delta = time - prevTime;
						prevTime = time;
					};

					if (index.notNil) {
						if (enabled[index]) {
							lastTime[index] = Main.elapsedTime;
							this.dispatch(index, channel, time, pos);
						}
					};

					if (delta < 0 || delta > 1) {
						("delta = " ++ delta).postln;
					};
					delta.wait;
				}
			};
			player.start;
			this.changed(\recorderPlaying);
		}
	}

	pausePlaying {
		if (isPlaying) {
			player.pause;
			this.changed(\recorderPaused);
		}
	}

	resumePlaying {
		if (isPlaying) {
			player.resume;
			this.changed(\recorderPlaying);
		}
	}

	stopPlaying {
		if (isPlaying) {
			player.stop;
			isPlaying = false;
			this.changed(\recorderStopped);
		}
	}

	setLoc { arg seconds;
		var index = this.indexInRecording(seconds);
		if (index.notNil) {
			playIndex = index;
			prevTime = recording[index][0];
			lastLocUpdate = 0;
		}
	}

	indexInRecording { arg relTime;
		var start = recording[0][0];
		recording.do{ arg sample, index;
			if ((sample[0] - start) >= relTime) {
				^index;
			}
		};
		^nil
	}

	recordingDuration {
		if (recording.notNil) {
			if (recording.size >= 2) {
				^(recording.last[0] - recording.first[0])
			}
		}
		^100;
	}

	getTarget { arg index;
		if (index < 0 || index >= maxTargets) {
			("TrTrackingInput::getTarget: index out of rage (" ++ index ++ ")").postln;
			^[0, 0, 0, 0, 0, 0, 1];
		} {
			^inputCache[index];
		}
	}
}

TrTrackingRecorderGUI : QWindow {
	var model;
	var posS;
	var nameT, pathTF, recB, playB, pauseB, <stopB, loopB, muteB;
	var locT, minLocT, secLocT, minLocN, secLocN;
	var durT, minDurT, secDurT, minDurN, secDurN;
	var loadT, loadB, saveT, saveB;
	var recDisable, playDisable;

	*new { arg trackingInput;

		^super.new("Tracking Recorder", Rect(100, 100, 340, 160)).init(trackingInput);
	}

	init { arg argTrackingInput;
		model = argTrackingInput;

		posS = QSlider.new.orientation_(\horizontal).maxHeight_(20)
		.value_(0).action_({ arg slider;
			var duration = model.recordingDuration;
			if (duration > 0) {
				var location = slider.value * duration;
				model.setLoc(location);
				this.setLoc(location);
			};
		});

		pathTF = QTextField.new.string_("Untitled").enabled_(false);

		muteB = QButton.new.states_([ [ "mute" ], [ "live", Color.black, Color.magenta ] ]).value_(0)
		.action_({ arg button;
			if (button.value.booleanValue) {
				model.blockOSC_(true);
			} {
				model.blockOSC_(false);
			}
		});


		recB = QButton.new.states_([ [ "rec" ], [ "rec", Color.white, Color.red ] ]).value_(0)
		.action_({ arg button;
			if (button.value.booleanValue) {
				if (model.activeChannels(1).size > 0) { // start only if there is something to record
					if (model.recIsSaved.not) {
						this.alert("Alert", "Last recording has not been saved. Do you want to overwrite or save it?",
							[
								["Cancel", { recB.value_(0) }],
								["Overwrite", {
									recDisable.do(_.enabled_(false));
									pathTF.value_("Untitled");
									this.setLoc(0);
									this.setSliderLoc(0);
									model.startRecording;
								}],
								["Save", { saveB.valueAction_(1); recB.value_(0) }]
							]
						)
					} {
						recDisable.do(_.enabled_(false));
						pathTF.value_("Untitled");
						this.setLoc(0);
						this.setSliderLoc(0);
						model.startRecording;
					}
				} {
					recB.value_(0);
				}
			} {
				recDisable.do(_.enabled_(true));
				model.stopRecording;
			}
		});

		playB = QButton.new.states_([ [ "play" ], [ "play", Color.black, Color.green ] ]).value_(0)
		.action_({ arg button;
			if (button.value.booleanValue) {
				if (model.recording.notNil && (model.recording.size > 1)) {
					playDisable.do(_.enabled_(false));
					model.startPlaying;
				} {
					playB.value_(0);
				}
			} {
				playDisable.do(_.enabled_(true));
				model.stopPlaying;
				pauseB.value_(0);
			}
		});

		pauseB = QButton.new.states_([ [ "pause" ], [ "resume", Color.black, Color.yellow ] ]).value_(0)
		.action_({ arg button;
			if (model.isPlaying) {
				if (button.value.booleanValue) {
					model.pausePlaying;
				} {
					model.resumePlaying;
				}
			} {
				pauseB.value_(0)
			}
		});

		stopB = QButton.new.states_([ [ "stop" ] ]).value_(0)
		.action_({
			if (recB.value == 1) {
				recB.valueAction_(0);
			};
			if (playB.value == 1) {
				playB.valueAction_(0);
			};
			pauseB.value_(0);
		});

		loopB = QButton.new.states_([ [ "loop" ], [ "loop", Color.white, Color.blue ] ]).value_(0)
		.action_({ arg button; model.looping_(button.value.booleanValue) });

		locT = QStaticText.new.string_("LOC");
		minLocT = QStaticText.new.string_("min");
		secLocT = QStaticText.new.string_("sec");

		minLocN = QNumberBox.new.align_(\center).clipLo_(0).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1).value_(0).fixedWidth_(30)
		.action_({ arg number;
			var seconds = number.value * 60 + secLocN.value;
			model.setLoc(seconds);
			this.setSliderLoc(seconds);
		});

		secLocN = QNumberBox.new.align_(\center).clipLo_(0).clipHi_(59).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1).value_(0).fixedWidth_(30)
		.action_({ arg number;
			var seconds = minLocN.value * 60 + number.value;
			model.setLoc(seconds);
			this.setSliderLoc(seconds);
		});

		durT = QStaticText.new.string_("DUR");
		minDurT = QStaticText.new.string_("min");
		secDurT = QStaticText.new.string_("sec");

		minDurN = QNumberBox.new.align_(\center).clipLo_(0).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1).value_(0).fixedWidth_(30).enabled_(false);
		secDurN = QNumberBox.new.align_(\center).clipLo_(0).clipHi_(59).decimals_(0)
		.step_(1).scroll_step_(1).shift_scale_(1).alt_scale_(1).value_(0).fixedWidth_(30).enabled_(false);

		loadB = QButton.new.states_([ [ "load" ] ]).value_(0)
		.action_({
			if (model.recIsSaved.not) {
				this.alert("Alert", "Last recording has not been saved. Do you want to overwrite or save it?",
					[
						["Cancel", { "loading was canceled" }],
						["Overwrite", {
							Dialog.openPanel(
								{ arg p;
									model.recIsSaved_(true);
									model.loadRecording(p);
									pathTF.value_(p);
								},
								{
									"open canceled".postln;
								}
							)
						}],
						["Save", { saveB.valueAction_(1); recB.value_(0) }]
					]
				)
			} {
				Dialog.openPanel(
					{ arg p;
						model.loadRecording(p);
						pathTF.value_(p);
					},
					{
						"open canceled".postln;
					}
				)
			}
		});

		saveB = QButton.new.states_([ [ "save" ] ]).value_(0)
		.action_({
			Dialog.savePanel(
				{ arg p;
					model.saveRecording(p);
					pathTF.value_(p);
				},
				{
					"save canceled".postln;
				}
			);
		});

		this.layout_(VLayout(posS,
			HLayout(locT, minLocN, -5, minLocT, secLocN, -5, secLocT, 20, nil, durT, minDurN, -5, minDurT, secDurN, -5, secDurT),
			HLayout(playB, pauseB, stopB, recB),
			HLayout(loopB, saveB, loadB, muteB),
			HLayout(pathTF)
		));

		recDisable = [posS, minLocN, secLocN, playB, saveB, loadB, loopB, muteB, pauseB];
		playDisable = [recB, saveB, loadB];

		this.onClose = {
			if (model.isPlaying) {
				model.stopPlaying;
			};
			if (model.isRecording) {
				model.stopRecording;
			};
			model.clearRecording;
			model.window = nil;
			model.blockOSC_(false);
			model.changed(\recorderClosed);
			model.looping_(false);
		};

	} // end init

	setLoc { arg seconds;
		var minutes = (seconds / 60).asInteger;
		minLocN.value_(minutes);
		secLocN.value_(seconds - (minutes * 60));
	}

	setSliderLoc { arg seconds;
		var minutes = (seconds / 60).asInteger;
		var duration = model.recordingDuration;
		if (duration > 0) {
			posS.value_(seconds / duration);
		}
	}

	setDur { arg seconds;
		var minutes = (seconds / 60).asInteger;
		minDurN.value_(minutes);
		secDurN.value_(seconds - (minutes * 60));
	}

	alert { arg title, message, choices;
		var text = StaticText().string_(message);
		var view = View().name_(title).alwaysOnTop_(true).minHeight_(100).userCanClose_(false);

		view.background_(Color.new(1, 0.5, 0.5, 1));
		view.layout_(
			VLayout(
				text,
				nil,
				HLayout(*choices.collect{ arg choice;
					Button().states_([[choice[0]]]).action_({choice[1].value; view.close})
					}
				)
			)
		);
		view.bounds_(view.bounds.center_(Window.availableBounds.center));
		view.front;
	}

	save {
		Dialog.savePanel(
			{ arg p;
				model.saveRecording(p);
			},
			{
				"save canceled".postln;
			}
		);
	}


}
