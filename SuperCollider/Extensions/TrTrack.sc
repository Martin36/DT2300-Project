/*
A TrTrack contains a TrTrace, a list of TrTracePlayers and a view
*/

TrTrack {

	var <scene;   // back pointer to scene
	var <trace;   // the trace
	var <players; // list of players
	var <view;    // view and controller

	// parameters

	var <>pTarget;
	var <>pOrigin;
	var <>pChannel;
	var <pColor;
	var <pViz;
	var <pIncremental;
	var <pVelocity;

	var <>tracePlayer;
	var tracingTarget;
	var loopTrace;

	var <levelSliderSpec;
	var <buffer3D;
	var finished;



	*new { arg scene, name = nil;
		^super.new.init(scene, name);
	}

	init { arg argScene, argName;
		var main = argScene.main;
		scene = argScene;
		trace = TrTrace.new(this, main.traceServer, main.bufferPool, main.trackingInput);
		trace.addDependant(this);
		players = List.new;
		this.addPlayer(TrTracePlayer.new(trace, main.trackingInput, main.traceServer).pTarget_(0));
		view = TrTrackView.new(this, if (argName.isNil) {"trace" ++ trace.id} {argName});
		this.addDependant(view);
		levelSliderSpec = ControlSpec.new(-60.0, 0.0, \lin, 0.01);
		loopTrace = true;

		pTarget = 0;
		pOrigin = 0;
		pChannel = 1;
		pColor = 'red';
		pViz = false;
		pIncremental = false;
		pVelocity = false;

		this.changed(\trackAdded);
		finished = false;

		^this;
	}

	free {
		finished = true;
		if (tracePlayer.notNil) {
			tracePlayer.stop;
		};
		trace.free;
		players.do(_.free);
		if (buffer3D.notNil) {
			buffer3D.delete;
		};
		view.close;
	}

	pColor_ { arg symbol;
		pColor = symbol;
		if (buffer3D.notNil) {
			buffer3D.color_(Color.perform(symbol).asArray * 255);
		};
		this.changed(\parameters);
	}

	pViz_ { arg flag;
		pViz = flag;
		if (buffer3D.notNil) {
			buffer3D.visible_(flag);
		};
		this.changed(\parameters);
	}

	pIncremental_ { arg flag;
		pIncremental = flag;
		trace.recordIncrementally = pIncremental;
		this.changed(\parameters);
		scene.edited_(true);
	}

	pVelocity_ { arg flag;
		pVelocity = flag;
		trace.recordSpeed = pVelocity;
		this.changed(\parameters);
		scene.edited_(true);
	}

	addPlayer { arg player;
		players.add(player);
		this.changed(\playerAdded);
		scene.edited_(true);
	}

	removePlayer { arg index;
		players.removeAt(index);
		scene.edited_(true);
	}

	name {
		^view.vTrNameTF.string.asSymbol; // as symbol to compare easier
	}

	save { arg path, index;
		var number = if (index < 10) { "0" ++ index.asString } { index.asString }; // work only up to 99
		var name = path +/+ number ++ "_" ++ this.name;

		trace.save(name);
		File.use(name ++ ".ppar", "w", { arg file;
			file.write(players.collect(_.getParameters).asCompileString) });
		File.use(name ++ ".tpar", "w", { arg file;
			file.write(this.getParameters.asCompileString) });
	}

	load { arg path, action;
		var ppars;
		File.use(path ++ ".tpar", "r", { arg file; this.setParameters(file.readAllString.interpret)});
		File.use(path ++ ".ppar", "r", { arg file; ppars = file.readAllString.interpret});
		ppars.do{ arg ppar, index;
			if (index != 0) { // there is always at least one player
				view.vPlAddB.valueAction_(1);
			};
			players[index].setParameters(ppar);
		};
		trace.load(path, action);
	}

	getParameters {
		^IdentityDictionary[
			\target -> pTarget,
			\origin -> pOrigin,
			\channel -> pChannel,
			\color -> pColor,
			\viz -> pViz,
			\incremental -> pIncremental,
			\velocity -> pVelocity;
		]
	}

	setParameters { arg d;
		pTarget = d[\target];
		if (d[\origin].isNil) { // backwards compatibility
			pOrigin = 0;
		} {
			pOrigin = d[\origin];
		};
		pChannel = d[\channel];
		pColor = d[\color];
		pViz = d[\viz];
		pIncremental = d[\incremental];
		pVelocity = d[\velocity];

		// backwards compatibility
		if (pIncremental.isNil) {
			pIncremental = false;
		};
		if (pVelocity.isNil) {
			pVelocity = false;
		};
		this.changed(\parameters);
	}

	activeTargets { arg targets;
		if (finished.not) {
			view.activeTargets(targets);
		}
	}

	activeChannels { arg channels;
		if (finished.not) {
			view.activeChannels(channels);
		}
	}

	startTracing { arg target;
		var trackingInput = scene.main.trackingInput;

		tracingTarget = target;
		trackingInput.disableChannel(tracingTarget);
		("TrTrack::sartTracing: disabled input from target" + tracingTarget).warn;

		tracePlayer = Task{
			loop {
				var trajectory = trace.trajectory;
				var last = trajectory[0];

				trajectory.do{ arg entry;
					var delta = entry[0] - last[0];
					var frames = (delta * ~trackingSrate + 0.5).asInteger;
					var slopeP = ((entry[1] - last[1]) / frames);
					var slopeT = (delta / frames);

					frames.do{|i|
						trackingInput.input(tracingTarget, last[0] + (slopeT * i), last[1] + (slopeP * i));
						if (slopeT < 0.0002){
							("TrTrack::startTracing: delta too small:" + slopeT + ", set to 1s").warn;
							1.0.wait;
						} {
							slopeT.wait;
						}
					};
					last = entry;
				};
				if (loopTrace.not) {
					{ view.vTrTraceB.valueAction_(0) }.defer;
				} {
					view.timeDisplayTask.reset;
					// view.timeDisplayTask.play(AppClock);
				};
			};
		}.start;

	}

	stopTracing {
		tracePlayer.stop;
		scene.main.trackingInput.enableChannel(tracingTarget);
		("TrTrack::stopTracing: reenabled input from target" + tracingTarget).warn;
	}

	updateVizualisation {
		if (buffer3D.notNil) {
			buffer3D.delete;
			buffer3D = nil;
		};
		if (trace.trajectory.notNil) {
			buffer3D = VBuffer3D(scene.main.vScene,
				Color.perform(view.vTrColorP.item).asArray * 255, 1, 2.0)
			.visible_(pViz);
			buffer3D.setPoints(trace.trajectory.collect(_.at(1) * 5));
				// var coord = entry[1];
				// [coord[0], coord[2].neg, coord[1]] * 5});
			buffer3D.addPoints;
		};
		if (trace.trajectory2.notNil) {
			buffer3D = VBuffer3D(scene.main.vScene,
				Color.perform(view.vTrColorP.item).asArray * 255, 1, 2.0)
			.visible_(pViz);
			buffer3D.setPoints(trace.trajectory2.collect(_.at(1) * 5));
				// var coord = entry[1];
				// [coord[0], coord[2].neg, coord[1]] * 5});
			buffer3D.addPoints;
		};
	}

	update { arg sender, action;
		switch (action,
			\traceChanged, {
				\traceChanged.postln;
				this.updateVizualisation;
			},
		);
	}

}

TrTrackView : QView {
	var track; // model
	// trace-related views
	var <vTrNameTF, vTrDeleteB, vTrIdST, vTrDurST, <vTrRecB, <vTrRecChannelNB;
	var <vTrRecTargetNB, <vTrRecOriginNB, vTrPlayB, <vTrTraceB, <vTrColorP, vTrVizB, vTrIncrementalB, vTrVelocityB;
	// player-related views
	var <vPlId, <vPlAddB, vPlDeleteB, <vPlTargetNB, <vPlOnB, <vPlDistNB;
	var <vPlPolyNB, vPlSynthP, <vPlOutputsTF, <vPlLevelS;
	var <timeDisplayTask;

	*new { arg track, name = "trace";
		^super.new.init(track, name);
	}

	init { arg argTrack, argName;
		var font = if (~guiFont.notNil) { ~guiFont } { Font.default.boldVariant};

		track = argTrack;

		// trace-related views

		vTrNameTF = TextField().minWidth_(60).string_(argName)
		.action_({ arg tf;
			var newName = tf.value;
			var otherTracks = track.scene.tracks.reject{ arg t; t == track }.collect(_.name);

			track.scene.edited_(true);
			if (otherTracks/*.collect(_.asSymbol)*/.includes(newName.asSymbol)) {
				tf.stringColor = Color.red;
				track.scene.alert("Alert",
					"There is already a track named \"" ++ newName ++ "\". Choose another name.",
					[["Cancel", {}], ["Cancel", {}], ["OK", {}]]
				);
			} {
				tf.stringColor = Color.black;
				tf.focus(false);
			}
		});

		vTrDeleteB = Button().fixedWidth_(20).fixedHeight_(20).states_([["-"]])
		.action_({
			var name = vTrNameTF.string;
			track.scene.alert("Alert", "Sure to remove track \"" ++
				name ++ "\"? This cannot be undone.",
				[
					["Cancel", { "removing track canceled".postln }],
					["Cancel", { "removing track canceled".postln }],
					["Confirm", {
						track.scene.removeTrack(name);
						track.free; // closes view
						// this.close; // was remove
					}]
				]
			)
		});

		vTrIdST = StaticText().fixedWidth_(25).string_("100").align_(\center);
		vTrDurST = StaticText().fixedWidth_(40).string_("1112.5").align_(\right);

		vTrRecB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			if (b.value.booleanValue) {
				// record only if there is tracking data coming in
				if (track.scene.main.trackingInput.activeChannels(1).includes(vTrRecTargetNB.value.asInteger)) {
					var doit = {
						if (track.trace.startRecording(track.pTarget, track.pChannel - 1).not)
						{ b.value_(0); }
						{
							track.scene.edited_(true);
							timeDisplayTask.reset;
							timeDisplayTask.play(AppClock);
						} // started successfully
					};

					// no allert when overwriting recording
					//
					// if (track.trace.trajectory.isNil) {
					// 	"trajectory is empty".postln;
					// 	doit.value
					// } {
					// 	"trajectory is not empty".postln;
					// 	track.scene.alert("Alert", "Overwrite existing recording? This cannot be undone.",
					// 		[
					// 			["Cancel", { b.value_(0) }],
					// 			["Confirm", { doit.value }]
					// 		]
					// 	)
					// }

					doit.value;

				} {
					b.value_(0); // no tracking input
				}
			} {
				timeDisplayTask.stop;
				track.trace.stopRecording(track);
			}
		});


		vTrRecChannelNB = NumberBox().fixedWidth_(25).value_(1).align_(\center)
		.decimals_(0).clipLo_(1).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			track.pChannel_(nb.value.asInteger);
			track.scene.edited_(true);
			if (track.trace.playSynth.isPlaying) {
				track.trace.playSynth.set(\out, nb.value.asInteger - 1);
			}
		});

		vTrRecTargetNB = NumberBox().fixedWidth_(25).value_(1).align_(\center)
		.decimals_(0).clipLo_(0).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			track.pTarget_(nb.value.asInteger);
			track.scene.edited_(true);
		});

		vTrRecOriginNB = NumberBox().fixedWidth_(25).value_(1).align_(\center)
		.decimals_(0).clipLo_(0).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			track.pOrigin_(nb.value.asInteger);
			track.trace.relativeTo = track.pOrigin;
			track.scene.edited_(true);
		});

		vTrPlayB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			if (b.value.booleanValue) {
				if (track.trace.startPlayingSound(track.pChannel - 1, track.levelSliderSpec.map(vPlLevelS.value).dbamp).not) {
					b.value_(0);
				} {
					timeDisplayTask.reset;
					timeDisplayTask.play(AppClock);
					{
						b.value_(0);
						timeDisplayTask.stop;
						// vTrDurST.string_(track.trace.sound.duration.round(0.1).asString);
						vTrDurST.string_(track.trace.trajectory.last[0].round(0.1).asString);
					}.defer(track.trace.sound.duration + 0.1);
				};
			} {
				track.trace.stopPlayingSound();
				timeDisplayTask.stop;
				// vTrDurST.string_(track.trace.sound.duration.round(0.1).asString);
				vTrDurST.string_(track.trace.trajectory.last[0].round(0.1).asString);
			}
		});

		vTrTraceB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			if (b.value.booleanValue) {
				track.startTracing(vTrRecTargetNB.value.asInteger);
				timeDisplayTask.reset;
				timeDisplayTask.play(AppClock);
			} {
				track.stopTracing();
				timeDisplayTask.stop;
				// vTrDurST.string_(track.trace.sound.duration.round(0.1).asString);
				vTrDurST.string_(track.trace.trajectory.last[0].round(0.1).asString);
			}
		});

		vTrIncrementalB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			track.pIncremental_(b.value.booleanValue);
		});


		vTrVelocityB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			track.pVelocity_(b.value.booleanValue);
		});

		vTrColorP = PopUpMenu().fixedWidth_(70).items_([\white, \grey, \red, \green, \blue, \cyan, \magenta, \yellow])
		.action_({ arg p;
			track.pColor_(p.item);
			track.scene.edited_(true);
		});

		vTrVizB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x"]])
		.action_({ arg b;
			track.pViz_(b.value.booleanValue);
			track.scene.edited_(true);
		});

		// player-related views

		vPlId = PopUpMenu().fixedWidth_(40).items_([1]).action_({ arg popup;
			this.updatePlayer(track.players[popup.value]);
		});

		vPlAddB = Button().fixedWidth_(20).fixedHeight_(20).states_([["+"]])
		.action_({
			var new = TrTracePlayer(track.trace, track.scene.main.trackingInput, track.scene.main.traceServer);
			var model = track.players[vPlId.value];
			new.setParameters(model.getParameters);
			track.addPlayer(new.pTarget_(model.pTarget + 1));
		});

		vPlDeleteB = Button().fixedWidth_(20).fixedHeight_(20).states_([["-"]])
		.font_(font)

		.action_({
			var players = vPlId.items.size;
			if (players > 1) {
				track.removePlayer(vPlId.value);
				vPlId.items_((1..(players - 1)));
				vPlId.value_(players - 2);
				this.updatePlayer(track.players[vPlId.value]);
			}
		});

		vPlTargetNB = NumberBox().fixedWidth_(25).value_(1).align_(\center)
		.decimals_(0).clipLo_(0).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			track.players[vPlId.value].pTarget_(nb.value.asInteger);
			track.scene.edited_(true);
		});

		vPlOnB = Button().fixedWidth_(20).fixedHeight_(20).states_([[""], ["x", Color.red]])
		.action_({ arg b;
			// track.players[vPlId.value].pOn_(b.value.booleanValue);
			track.players.do{ arg item; item.pOn_(b.value.booleanValue);};
			track.scene.edited_(true);
		});

		vPlDistNB = NumberBox().fixedWidth_(30).align_(\center)
		.decimals_(0).clipLo_(1).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			// var player = track.players[vPlId.value];
			// var index = track.scene.main.trackingInput.channels.indexOf(player.pTarget);
			// player.pDist_(nb.value);
			// track.scene.edited_(true);
			// if (index.notNil) {
			// 	track.scene.main.trackingInput.visuals[index].size = (player.pDist / 100 * 5) ! 3
			// };
			track.players.do{ arg item, index;
				item.pDist_(nb.value);
				track.scene.main.trackingInput.visuals[index].size = (item.pDist / 100 * 5) ! 3
			};
			track.scene.edited_(true);
		});

		vPlPolyNB = NumberBox().fixedWidth_(30).align_(\center)
		.decimals_(0).clipLo_(1).clipHi_(100).step_(1).increment(1).decrement(1)
		.action_({ arg nb;
			// track.players[vPlId.value].pPoly_(nb.value.asInteger);
			track.players.do{ arg item, index;
				item.pPoly_(nb.value.asInteger);
			};
			track.scene.edited_(true);
		});

		vPlSynthP = PopUpMenu().fixedWidth_(65).items_([\async, \sync, \tape])§
		.action_({ arg p;
			track.players[vPlId.value].pSynth_(p.item);
			track.scene.edited_(true);
		});

		vPlOutputsTF = TextField().minWidth_(60).string_("1 2 3 4").align_(\center)
		.action_({ arg tf;
			tf.value;
			tf.focus(false);
			track.players[vPlId.value].pOutputs_(("[" ++
				tf.value.collect{ arg char; if (char == $\ ) { $, } { char }} ++
				"]").interpret - 1);
			track.scene.edited_(true);
		});
		vPlLevelS = Slider().minWidth_(100).orientation_(\horizontal)
		.action_({ arg s;
			// track.players[vPlId.value].pLevel_(track.levelSliderSpec.map(s.value));
			track.players.do({arg item, i; item.pLevel_(track.levelSliderSpec.map(s.value))});
			track.scene.edited_(true);
			if (track.trace.playSynth.isPlaying) {
				track.trace.playSynth.set(\amp, track.levelSliderSpec.map(s.value).dbamp);
			}
		});

		this.layout_(
			HLayout(
				vTrNameTF,
				vTrDeleteB,
				vTrIdST,
				vTrDurST,
				vTrRecB, 10,
				vTrIncrementalB, 10,
				vTrVelocityB, 10,
				vTrRecTargetNB, 15,
				vTrRecOriginNB, 15,
				vTrRecChannelNB, 10,
				vTrPlayB, 8,
				vTrTraceB, 15,
				vTrColorP,
				-8,
				vTrVizB,

				vPlId,
				-8,
				vPlAddB,
				-8,
				vPlDeleteB,
				vPlTargetNB, 15,
				vPlOnB,
				vPlDistNB,
				vPlPolyNB,
				vPlSynthP,
				vPlOutputsTF,
				vPlLevelS,
			).margins_(3).spacing_(10)
		);

		timeDisplayTask = Task{
			var start = SystemClock.seconds;
			loop{
				var now = SystemClock.seconds - start;
				var rounded = now.round(0.1);
				var string = rounded.asString;
				if (rounded == rounded.asInteger) {
					string = string ++ ".0";
				};
				vTrDurST.string_(string);
				0.1.wait;
			}
		};

		if (~defaultMidiBindings.notNil && ~defaultMidiBindings && (track.trace.id < 9)) {
			"track id ".post;
			track.trace.id.postln;
			MIDIdef.cc((argName ++ "_rec").asSymbol, { arg val;
				if (val == 127) {
					("Rec ON on track " ++ argName.asSymbol).postln;
					{this.vTrRecB.valueAction_(1)}.defer;
				} {
					("Rec OFF on track " ++ argName.asSymbol).postln;
					{this.vTrRecB.valueAction_(0)}.defer;
				};
			}, (100 + track.trace.id) );

			MIDIdef.cc((argName ++ "_play").asSymbol, { arg val;
				if (val == 127) {
					("Play ON on track " ++ argName.asSymbol).postln;
					{this.vPlOnB.valueAction_(1)}.defer;
				} {
					("Play OFF on track " ++ argName.asSymbol).postln;
					{this.vPlOnB.valueAction_(0)}.defer;
				};
			}, (109 + track.trace.id) );

			MIDIdef.cc((argName ++ "_gain").asSymbol,
					{ arg val;
					var exponent = 1.5;
					var value = val / 127;
					var level = (pow(value, exponent));
					if (val == 0) { level = 0 };
					{ this.vPlLevelS.valueAction_(level) }.defer;
					("gain " ++ argName.asSymbol ++ " to " ++ level).postln;
			}, (21 + track.trace.id) );

		} {
			"Track ID is bigger then 8: No midi control".postln;
		};

	} // end init

	name {
		^vTrNameTF.string;
	}

	name_ { arg name;
		vTrNameTF.string_(name);
	}

	updateTrace { arg trace;
		var sound = trace.sound;
		var colors = vTrColorP.items.collect(_.asSymbol);

		"TrTrackView:updateTrace".postln;
		// sound.postln;

		vTrIdST.string_(trace.id.asString);
		if (sound.notNil) {
			vTrDurST.stringColor = Color.black;
			// vTrDurST.string_(trace.sound.duration.round(0.1).asString);
			vTrDurST.string_(track.trace.trajectory.last[0].round(0.1).asString);
		} {
			vTrDurST.stringColor = Color.red(1, 0.5);
			vTrDurST.string_("empty");
		};
		vTrRecChannelNB.value_(track.pChannel);
		vTrRecTargetNB.value_(track.pTarget);
		vTrRecOriginNB.value_(track.pOrigin);
		vTrColorP.value_(colors.indexOf(track.pColor));
		track.pColor_(vTrColorP.item); // trigger update of visualization
		vTrVizB.value_(track.pViz);
		vTrIncrementalB.value_(track.pIncremental);
		vTrVelocityB.value_(track.pVelocity);
	}

	updatePlayer { arg player;
		vPlTargetNB.value_(player.pTarget);
		vPlOnB.value_(player.pOn.booleanValue);
		vPlDistNB.value_(player.pDist);
		vPlPolyNB.value_(player.pPoly);
		vPlSynthP.value_(player.pSynth);
		vPlOutputsTF.string_((player.pOutputs + 1).inject("", { arg x, n; x + n}).drop(1));
		vPlLevelS.value_(track.levelSliderSpec.unmap(player.pLevel));
	}

	update { arg sender, action;
		switch (action,
			\trackAdded, {
				this.updateTrace(sender.trace);
				this.updatePlayer(sender.players[vPlId.value]);
			},
			\playerAdded, {
				vPlId.items_(sender.players.collect{ arg player, index; index + 1});
				vPlId.value_(sender.players.size - 1);
				this.updatePlayer(sender.players[vPlId.value]);
			},
			\recordingFinished, {
				// \recordingFinished.postln;
				this.updateTrace(sender.trace);
			},
		);
	}

	activeTargets { arg targets;
		{
			if (targets.includes(vTrRecTargetNB.value.asInteger)) {
				vTrRecTargetNB.background_(Color.green(1, 0.1));
			} {
				vTrRecTargetNB.background_(Color.red(1, 0.1));
			};
			if (targets.includes(vTrRecOriginNB.value.asInteger)) {
				vTrRecOriginNB.background_(Color.green(1, 0.1));
			} {
				vTrRecOriginNB.background_(Color.red(1, 0.1));
			};
			if (targets.includes(vPlTargetNB.value.asInteger)) {
				vPlTargetNB.background_(Color.green(1, 0.1));
			} {
				vPlTargetNB.background_(Color.red(1, 0.1));
			};
		}.defer;
	}

	activeChannels { arg channels;
		{
			if (channels.includes(vTrRecChannelNB.value.asInteger)) {
				vTrRecChannelNB.background_(Color.green(1, 0.1));
			} {
				vTrRecChannelNB.background_(Color.red(1, 0.1));
			};
		}.defer;
	}


}
