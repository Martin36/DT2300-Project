// TODO
//
// OSC monitor
// colors for targets
// record/playback tracking data (performances), select targets, as OSC messages

TrMain : QWindow {

	var <wVolume, <wVolumeSlider, <wScene, <wServer, <wMeters, <wPost, <wOscPath, <wMonitorOsc;
	var <wCameraPosition, wCameraOrientation, <wRecorder, wPresets, wSonifyer, wFramerate, wMaxRecDur;
	var serverMeter, serverWindow, oscMonitor;
	var serverAmpSliderSpec;
	var <traceServer;
	var <bufferPool;
	var <maxRecDur;
	var <documents;
	var <trackingInput;
	var activeTargetsTask;

	var <vServer;
	var <vScene;
	var vServerWatchDog;
	var vServerResponder;
	var vServerStartedCounter;
	var vServerStartDelay;
	var cameraPosition;
	var cameraOrientation;
	var <cameraPresets;

	var <masterBus;
	var masterSynth;
	var masterOutputChannels;
	var <inputChannels;
	var roomChannels;
	var recBuffer;
	var recSynth;
	var <synthGroupPlay; // players
	var <synthGroupRecM; // recorders and master

	var <sonifyers;

	*new { arg name = "ReTrace", bounds = Rect(0, 0, 0, 0), resizable = true, border = true, server,
		scroll = false, outputs = [0, 1], inputs = [0, 1], room = [];

		^super.new(name, bounds, resizable, border, server, scroll).init(outputs, inputs, room);
	}

	init { arg outputs, inputs, room;
		masterOutputChannels = outputs;
		inputChannels = inputs;
		roomChannels = room;
		documents = Set.new;
		sonifyers = List.new;

		// allocate application resources
		traceServer = TrTraceServer.new(nil, ~traceServerPath, ~runTraceServerInTerminal).start;
		TrTrace.makeSynths;
		TrTracePlayer.makeSynths;

		// setup window
		serverAmpSliderSpec = ControlSpec.new(-90.0, 6.0, \lin, 0.01);
		wVolume = [
			QStaticText.new.string_("volume"),
			nil,
			QNumberBox.new.clipLo_(-90.0).clipHi_(6.0).decimals_(1)
			.step_(1.0).scroll_step_(1.0).shift_scale_(0.1).alt_scale_(3.0)
			.align_(\right).value_(0.0).action_({ arg number;
				this.serverVolumeA(number.value);
				wVolumeSlider[0][0].value_(serverAmpSliderSpec.unmap(number.value))
			}),
			QButton.new.states_([ [ "mute" ], [ "mute", Color.red ] ]).action_({ arg button;
				this.serverMuteA(button.value.booleanValue);
			})
		];
		wVolumeSlider = [ [
			QSlider.new.orientation_(\horizontal)
			.step_(0.01).shift_scale_(0.1).alt_scale_(3.0)
			.value_(serverAmpSliderSpec.unmap(0)).action_({ arg slider;
				var db = serverAmpSliderSpec.map(slider.value);
				this.serverVolumeA(db);
				wVolume[2].value_(db);
			}), columns: 4
		] ];
		wScene = [
			QStaticText.new.string_("scene"),
			QButton.new.states_([ [ "new" ] ])
			.action_({ arg button;
				TrScene.new(this);
			}),
			QButton.new.states_([ [ "open" ] ])
			.action_({ arg button;
				QFileDialog(
					{ arg path;
						var scene = TrScene.new(this);
						if (scene.load(path)) {
							(path + "loaded").postln;
						};
					},
					{
						"open canceled".postln;
					},
					2,   // directories
					0,   // open
					true // stripResult
				);
			}),
			QButton.new.states_([ [ "sync viz" ] ])
			.action_({
				vScene.clear;
				vServer.replyPort(NetAddr.langPort);
				documents./*copy.*/do{ arg scene; scene.tracks.do{ arg track; track.updateVizualisation }};
				trackingInput.updateVizualisation;
			})
		];
		wServer = [
			QStaticText.new.string_("server window"),
			QButton.new.states_([ [ "show" ], [ "hide" ] ]).action_({ arg button;
				this.serverWindowA(button.value.booleanValue);
				if (button.value.booleanValue) {{
					this.serverVolumeA(wVolume[2].value);
					this.serverMuteA(true);
					this.serverMuteA(wVolume[3].value.booleanValue);
				}.defer(0.1)}
			})
		];
		wPost = [
			QStaticText.new.string_("3D window").minWidth_(90),
			QButton.new.states_([ [ "show" ], [ "hide" ] ])
			.action_({ arg b;
				if (b.value.booleanValue) {
					var vServerPath = if (~vServerPath.isNil) {
						"/Users/eckel/ownCloud/TP/apps/VServer/VServer.app"
					} {
						~vServerPath
					};
					("open" + "\"" ++ vServerPath ++ "\"").unixCmd;
					vServerStartedCounter = vServerStartDelay;
					{
						vServer.enabled = true;
						vServer.frameRate(wFramerate[1].value);
						vServer.replyPort(NetAddr.langPort);
						documents.copy.do{ arg scene; scene.tracks.do{ arg track; track.updateVizualisation }};
						trackingInput.updateVizualisation;
					}.defer(vServerStartDelay)
				} {
					wPost[0].stringColor_(Color.red(0.5));
					vServer.enabled = false;
					"killall -9 VServer".unixCmd;
				}
			})
		];
		wMeters = [
			QStaticText.new.string_("server meters"),
			QButton.new.states_([ [ "show" ], [ "hide" ] ]).action_({ arg button;
				this.serverMeterA(button.value.booleanValue);
			})
		];
		wOscPath = [
			QStaticText.new.string_("OSC path").minWidth_(90),
			QTextField.new.string_("/RigidBody"),
			QStaticText.new.string_("trace server").minWidth_(90),
			QStaticText.new.string_("starting...").background_(Color.white).align_(\center),
		];
		traceServer.aliveCallback_({ arg alive;
			{
				if (alive) {
					wOscPath[3].string_("running").stringColor_(Color.green(0.7));
				} {
					wOscPath[3].string_("not running").stringColor_(Color.red(0.7));
				}
			}.defer
		});

		wMonitorOsc = [
			QStaticText.new.string_("monitor OSC").minWidth_(90),
			// QButton.new.states_([ [ "show" ], [ "hide" ] ]).action_({ arg button;
			// 	if (button.value.booleanValue) {
			// 		oscMonitor = TrTrackingMonitor.new.front;
			// 	} {
			// 		oscMonitor.close;
			// 	}
			// })
			QButton.new.states_([ [ "on" ], [ "off" ] ]).action_({ arg button;
				OSCFunc.trace(button.value.booleanValue);
			})
		];

		cameraPosition = Array.fill(3, 0);
		wCameraPosition = [
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-1000).clipHi_(1000).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraPosition[0] = nb.value;
				this.sendCamera;
			}),
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-1000).clipHi_(1000).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraPosition[1] = nb.value;
				this.sendCamera;
			}),
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-1000).clipHi_(1000).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraPosition[2] = nb.value;
				this.sendCamera;
			}),
		];

		cameraOrientation = Array.fill(3, 0);
		wCameraOrientation = [
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-360).clipHi_(360).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraOrientation[0] = nb.value;
				this.sendCamera;
			}),
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-360).clipHi_(360).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraOrientation[1] = nb.value;
				this.sendCamera;
			}),
			QNumberBox().value_(1).align_(\center)
			.decimals_(2).clipLo_(-360).clipHi_(360).step_(0.1).increment(1).decrement(1)
			.action_({ arg nb;
				cameraOrientation[2] = nb.value;
				this.sendCamera;
			}),
		];

		wRecorder = [
			QStaticText.new.string_("recorder").minWidth_(90),
			QButton.new.states_([ [ "open" ], [ "close" ] ])
			.action_({ arg b;
				if (b.value.booleanValue) {
					trackingInput.showRecorderGUI;
				} {
					trackingInput.window.close;
				}
			})
		];

		wSonifyer = [
			QStaticText.new.string_("sonifyer").minWidth_(90),
			QButton.new.states_([ [ "new" ] ])
			.action_({
				TrSonifyTracking(trackingInput, 0, masterBus, 0, ~trackingSrate);
			})
		];

		cameraPresets = Array.fill(15, { Array.fill(6, 0) });

		wPresets = Array.fill(cameraPresets.size, { arg i;
			QButton.new.states_([ [ (i + 1).asString ] ]).minWidth_(20)
			.action_({ arg but, mod;
				if (mod & 131072 == 131072) { // shift pressed
					var preset = cameraPresets[i];
					preset[0] = cameraPosition[0];
					preset[1] = cameraPosition[1];
					preset[2] = cameraPosition[2];
					preset[3] = cameraOrientation[0];
					preset[4] = cameraOrientation[1];
					preset[5] = cameraOrientation[2];
					but.states_([ [ (i + 1).asString, Color.red ] ]);
					// ("stored preset " ++ (i + 1) ++ ", " ++ cameraPresets[i]).postln;
				} {
					var preset = cameraPresets[i];
					cameraPosition[0] = preset[0];
					cameraPosition[1] = preset[1];
					cameraPosition[2] = preset[2];
					cameraOrientation[0] = preset[3];
					cameraOrientation[1] = preset[4];
					cameraOrientation[2] = preset[5];
					this.update(this, \camera);
					this.sendCamera;
					// ("recalled preset " ++ (i + 1) ++ ", " ++ cameraPresets[i]).postln;
				}
			});
		});

		wFramerate = [
			QStaticText.new.string_("frame rate"),
			QNumberBox.new.clipLo_(1).clipHi_(60).decimals_(0)
			.step_(1.0).scroll_step_(1.0).shift_scale_(1).alt_scale_(3.0)
			.align_(\right).value_(20).action_({ arg number;
				vServer.frameRate(number.value)
			}),
		];

		wMaxRecDur = [
			QStaticText.new.string_("max rec (min)"),
			QNumberBox.new.clipLo_(1).clipHi_(60).decimals_(0)
			.step_(1.0).scroll_step_(1.0).shift_scale_(1).alt_scale_(3.0)
			.align_(\right).action_({ arg number;
				maxRecDur = number.value * 60;
				bufferPool.free;
				bufferPool = TrBufferPool.new(12, maxRecDur);
			}).valueAction_(60),
		];

		this.layout_(
			// QGridLayout.rows(
			VLayout(
				[QGridLayout.columns(
					[QGridLayout.rows(
						wPost,
						wServer,
						wMeters,
						wMonitorOsc,
						wRecorder,
						wSonifyer
					)],
					[QGridLayout.rows(
						wVolume,
						wVolumeSlider,
						wScene,
						wOscPath,
						wFramerate,
						wMaxRecDur
					)],
				)],
				HLayout(*(
					[QStaticText.new.string_("POS")] ++ wCameraPosition ++ [20, nil] ++
					[QStaticText.new.string_("ROT")] ++ wCameraOrientation)),
				HLayout(*([QStaticText.new.string_("Presets")] ++ wPresets))
			)
		);
		this.view.keyDownAction_({ arg view, char, modifiers, unicode, keycode, key;
			// [key, modifiers].postln;
			if (key == 78 && modifiers == 1048576) { // cmd-n
				wScene[1].valueAction_(1);
			};
			if (key == 79 && modifiers == 1048576) { // cmd-o
				wScene[2].valueAction_(1);
			};
		});

		// VServer handling

		vServer = VServer(tcp:false).verbose_(false);
		vScene = VScene(vServer);
		vScene.clear;
		vServer.enabled_(false);
		vServer.replyPort(NetAddr.langPort);
		vServerStartDelay = 4;
		vServerStartedCounter = vServerStartDelay;
		vServerWatchDog = TrWatchDog(1, { {
			wPost[0].stringColor_(Color.red(0.5));
			if (vServerStartedCounter > 0) {
				vServerStartedCounter = vServerStartedCounter - 1;
			} {
				if (wPost[1].value == 1) {
					wPost[1].value_(0);
				}
			};
		}.defer });
		vServerResponder = OSCFunc({ arg msg;
			cameraPosition[0] = msg[1];
			cameraPosition[1] = msg[2];
			cameraPosition[2] = msg[3];
			cameraOrientation[0] = msg[4];
			cameraOrientation[1] = msg[5];
			cameraOrientation[2] = msg[6];
			this.update(this, \camera);
			vServerWatchDog.alive_(true);
			vServer.enabled_(true);
			{
				if (wPost[1].value == 0) {
					wPost[1].value_(1);
				};
				wPost[0].stringColor_(Color.green(0.5))
			}.defer
		}, '/VServerCamera');

		// tracking input

		trackingInput = TrTrackingInput(this.oscPath, ~trackingInputTargets, vScene, this);
		trackingInput.addDependant(this);
		activeTargetsTask = Task{
			loop {
				this.documents.copy.do{ arg scene, index;
					scene.activeTargets(trackingInput.activeChannels(0.2));
					scene.activeChannels(inputChannels + 1); // channels start with 0, in the GUI we count from 1
				};
				0.2.wait;
			}
		}.start;

		("\n*** LANGPORT = " ++ NetAddr.langPort ++ "\n").postln;

		this.onClose_({
			trackingInput.finish;
			traceServer.finish;
			traceServer.stop;
			vServerResponder.free;
			activeTargetsTask.stop;
			vServerWatchDog.stop;
			documents.do(_.free);
			"CLOSING MAIN WINDOW".postln;
		});

		Server.default.waitForBoot { // wait until Server is booted, then
			{
				SynthDef(\master, { arg in, amp, mono = 0;
					var outputs = Control.names([\outputs]).kr(masterOutputChannels);
					var n = masterOutputChannels.size;
					var monol = mono.lag;
					var sum = In.ar(in, n).sum / n * monol;
					var one_mono = 1 - monol;
					masterOutputChannels.size.do{ arg i;
						Out.ar(outputs[i], ((In.ar(in + i) * one_mono) + sum) * amp.lag);
					}
				}).add;

				SynthDef(\record, { arg out, buf;
					DiskOut.ar(buf, (In.ar(out, masterOutputChannels.size) ++
						(inputChannels ++ roomChannels).postln.collect{|c| SoundIn.ar(c)}).postln);
				}).add;
				recBuffer = nil;

				Server.default.sync;

				synthGroupPlay = Group.new();
				synthGroupRecM = Group.new(synthGroupPlay, \addAfter);

				masterBus = Bus.audio(Server.default, masterOutputChannels.size);
				masterSynth = Synth(\master, [\in, masterBus], synthGroupRecM);
				wVolume[2].valueAction_(-20);

			}.fork(AppClock)
		}


		^this;

	} // END INIT

	startRecord { arg path;
		if (recBuffer.isNil) {
			{ // is the buffer big enough?
				recBuffer = Buffer.alloc(Server.default, 65536 * 4, masterOutputChannels.size + inputChannels.size + roomChannels.size);
				Server.default.sync;
				recBuffer.write(path, "W64", "int24", 0, 0, true);
				Server.default.sync;
				recSynth = Synth(\record, [\out, masterBus, \buf, recBuffer], addAction:'addToTail');
				("TrMain::startRecord:\n path = " ++ path ++
					"\n output channels = " ++ masterOutputChannels ++
					"\n input channels  = " ++ (inputChannels ++ roomChannels)).postln;
			}.fork(AppClock);
		} {
			"TrMain::startRecord: already recording".warn;
		};
	}

	stopRecord {
		if (recBuffer.isNil) {
			"TrMain::stopRecord: not recording".warn;
		} {
			recSynth.free;
			recBuffer.close;
			recBuffer.free;
			recBuffer = nil; // used as flag
			"TrMain::stopRecord: recording stopped".postln;

		}

	}

	oscPath {
		^wOscPath[1].string;
	}

	serverWindowA { arg bool;
		if (bool) {
			Server.default.makeWindow;
			Server.default.window.userCanClose_(false);
		} {
			Server.default.window.close;
		}
	}

	serverMeterA { arg bool;
		if (bool) {
			serverMeter = Server.default.meter;
			serverMeter.window.userCanClose_(false);
		} {
			serverMeter.window.close;
		}
	}

	serverMuteA { arg bool;
		if (bool) {
			Server.default.mute;
		} {
			Server.default.unmute;
		}
	}

	serverVolumeA { arg db;
		masterSynth.set(\amp, db.dbamp);
		// Server.default.volume.volume = db;
	}

	setMono { arg v;
		masterSynth.set(\mono, v);
	}

	framerateA { arg val;

	}

	sendCamera {
		vScene.camera(*(cameraPosition ++ cameraOrientation));
	}

	update { arg sender, action ... args;
		switch (action,
			\camera, {
				{
					if (wCameraPosition[0].hasFocus.not) { wCameraPosition[0].value_(cameraPosition[0]) };
					if (wCameraPosition[1].hasFocus.not) { wCameraPosition[1].value_(cameraPosition[1]) };
					if (wCameraPosition[2].hasFocus.not) { wCameraPosition[2].value_(cameraPosition[2]) };
					if (wCameraOrientation[0].hasFocus.not) { wCameraOrientation[0].value_(cameraOrientation[0]) };
					if (wCameraOrientation[1].hasFocus.not) { wCameraOrientation[1].value_(cameraOrientation[1]) };
					if (wCameraOrientation[2].hasFocus.not) { wCameraOrientation[2].value_(cameraOrientation[2]) };
				}.defer
			},
			\recorderClosed, { wRecorder[1].value_(0)},
			\recorderStopped, { wRecorder[0].stringColor = Color.black },
			\recorderPlaying, { wRecorder[0].stringColor = Color.green(0.75) },
			\recorderRecording, { wRecorder[0].stringColor = Color.red(0.75) },
			\recorderPaused, { wRecorder[0].stringColor = Color.yellow(0.75) },
		);
	}
}

TrTrackingMonitor : QWindow {
	var <wHeader, wChannels, wDownsample;
	var trackingChannels;
	var hLine1, hLine2;

	*new { arg name = "Tracking Monitor", bounds = Rect(0, 0, 0, 0), resizable = true,
		border = true, server, scroll = false;

		^super.new(name, bounds, resizable, border, server, scroll).init;
	}

	init {
		hLine1 = QUserView().fixedHeight_(10).drawFunc_({|v|
			Pen.line(0@v.bounds.height / 2, Point(v.bounds.width, v.bounds.height / 2));
			Pen.stroke;
		});
		hLine2 = QUserView().fixedHeight_(10).drawFunc_({|v|
			Pen.line(0@v.bounds.height / 2, Point(v.bounds.width, v.bounds.height / 2));
			Pen.stroke;
		});
		wDownsample = [
			QStaticText.new.string_("downsample").fixedWidth_(80),
			QNumberBox.new.clipLo_(1).clipHi_(100).decimals_(0)
			.step_(1.0).scroll_step_(1.0).shift_scale_(5.0).alt_scale_(10.0)
			.align_(\right).value_(10.0).fixedWidth_(30).action_({ arg number;
				number.value.postln;
			}),
			StaticText().minWidth_(300) // fix minimum width of window
		];
		wHeader = [
			StaticText().string_("ID"),
			StaticText().string_("X [m]"),
			StaticText().string_("Y [m]"),
			StaticText().string_("Z [m]"),
			StaticText().string_("SR [Hz]"),
			StaticText().string_("V [m/s]"),
			[StaticText().string_("sonify"), align:\center],
			[StaticText().string_("output"), align:\center]
		];
		trackingChannels = Array.new;
		this.setLayout;

	}

	setLayout {
		this.layout_(
			QGridLayout.rows(
				[QGridLayout.rows(
					wDownsample
				)],
				[hLine1],
				[QGridLayout.rows(
					*([wHeader, [[hLine2, columns:8]]] ++ trackingChannels)
				)]
			)
		)
	}

	mkTrackingChannel {
		^[
			StaticText().string_("10"),
			StaticText().string_("10.123"),
			StaticText().string_("10.123"),
			StaticText().string_("10.123"),
			StaticText().string_("120"),
			StaticText().string_("1.123"),
			[CheckBox(), align:\center],
			[QNumberBox.new.clipLo_(1).clipHi_(8).decimals_(0)
				.step_(1.0).scroll_step_(1.0).shift_scale_(2.0).alt_scale_(3.0)
				.align_(\right).value_(1.0).maxWidth_(20).action_({ arg number;
					number.value.postln;
			}), align:\center]
		]
	}

	setTrackingChannels { arg n;
		trackingChannels.do{|c| c[(0..5)].do(_.close); c[6][0].close; c[7][0].close};
		trackingChannels = Array.fill(n, { this.mkTrackingChannel });
		this.setLayout;
	}

}
