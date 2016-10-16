/*
TrScene is a document holding a collection of TrTracks
*/

TrScene {
	var <tracks; // list of TrTracks
	var <view;   // the view of the scene
	var <edited; // if scene is edited
	var <path;   // a directory where the TrTrack files are stored (4 per track)
	var <main;   // the application containing a list of TrScenes

	*new { arg main;
		^super.new.init(main);
	}

	init { arg argMain;
		main = argMain;
		main.documents.add(this);
		view = TrSceneView.new(bounds:Rect(0, 0, 1382, 225), scene:this)
		.name_("Untitled")
		.userCanClose_(false)
		.front;
		this.addDependant(view);
		tracks = List.new;
		path = nil;
		^this;
	}

	free {
		// main.documents.remove(this);
		// view.vMCloseB.valueAction_(1);

		// if (edited) {
		// 	"saving scene on free".postln;
		// 	this.save({
		// 		"scene saved".postln;
		// 		main.documents.remove(this);
		// 		tracks.do(_.free);
		// 		view.close;
		// 	});
		// } {
		main.documents.remove(this);
		tracks.do(_.free);
		// 	view.close;
		// }
	}

	changed { arg what ... moreArgs; // every change also sets edited flag
		super.changed(*([what] ++ moreArgs));
		this.edited(true);
	}

	path_ { arg string; // setting path als sets the view's name
		path = string;
		view.name_(path.basename);
	}

	addTrack { arg track;
		var name = track.name;
		if (tracks.collect(_.name).includes(name)) {
			("TrScene::addTrack: track " ++ name ++ " exists already").warn;
			^false;
		} {
			tracks.add(track);
			this.changed(\trackAdded, track);
			^true;
		}
	}

	removeTrack { arg name;
		var track = tracks.select{ arg t; t.name == name.asSymbol }; // should make up my mind when to use symbols and when strings!
		if (track.isEmpty) {
			"TrScene::removeTrack: track " ++ name ++ " is not part of scene".warn;
			^false;
		} {
			track = track[0];
			("track" + track + track.class).postln;
			tracks.remove(track);
			this.edited_(true);
			^true;
		}
	}

	isFile { arg path;
		^path.pathMatch[0].last.isPathSeparator.not
	}

	isFolder { arg path;
		^path.pathMatch[0].last.isPathSeparator
	}

	removeAllFilesInPath { arg path; // what about deleteFilesInPath ???
		(path +/+ "*").pathMatch.do{ arg entry;
			if (this.isFile(entry)) {
				File.delete(entry);
				(entry + "deleted").postln;
			}
		}
	}

	save { arg action = nil;
		if (path.isNil) {
			Dialog.savePanel(
				{ arg p;
					if (File.exists(p).not) {
						File.mkdir(p);
					};
					if (this.isFolder(p)) {
						this.deleteFilesInPath(p);
						tracks.do{ arg t, i; t.save(p, i) };
						this.path_(p);
						edited = false;
						action.value;
					} {
						"path is not a folder".postln;
					}
				},
				{
					"save canceled".postln;
				}
			);
		} {
			this.deleteFilesInPath(path);
			tracks.do{ arg t, i; t.save(path, i) };
			if (edited) {
				this.edited_(false);
			};
			action.value;
		}
	}

	deleteFilesInPath { arg path;
		(path +/+ "*").pathMatch.do{ arg file;
			// ("delete" + file).postln;
			File.delete(file)
		};
	}

	saveAs { arg action;
		path = nil;
		this.save(action);
	}

	revertToSaved {
		"removing all tracks".postln;
		tracks.copy.do{ arg track;
			track.name.postln;
			this.removeTrack(track.name);
			track.free;
		};
		this.load(path, true);
	}

	addIn { arg p;
		this.load(p, true);
		this.edited_(true);
	}

	load { arg p, add = false;
		var names = (p +/+ "*.tpar").pathMatch;
		if (names.size == 0) {
			("TrScene:load: no scene files found at" + p).warn;
			^false;
		} {
			if (add.not) { // remove all if add is false (default)
				this.path_(p);
			};
			names.do { arg entry;
				var fullName = entry.splitext[0];
				var name = fullName.basename.drop(3);
				var track;
				// var twoTrajectories = (p +/+ "*_2.tpbd").pathMatch.size == 1;
				// ("twoTrajectories = "++ twoTrajectories).postln;

				while ({tracks.collect(_.name).includes(name.asSymbol)}, {
					name = name ++ "a";
				});
				track = TrTrack.new(this, name);
				("TrScene:load: loading" + name).postln;
				track.load(fullName, { { this.addTrack(track) }.defer });
			};
			this.edited_(false);
			^true;
		}
	}

	edited_ { arg bool;
		var old = edited;
		var name = view.name;

		edited = bool;
		if (old != edited) {
			if (edited) {
				view.name_(name + "(*)");
			} {
				view.name_(name.copyRange(0, name.size - 5));
			}
		}
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

	activeTargets { arg targets;
		tracks.copy.do(_.activeTargets(targets))
	}

	activeChannels { arg channels;
		tracks.copy.do(_.activeChannels(channels))
	}
}

// VLayout
//  HLayout Menu
//   items
//  line
//  HLayout Tracks
//   GridLayout Trace
//    [header items]
//    [line]
//    [track1]
//    [track2]
//    ...
//   GridLayout Player
//    [header items]
//    [line]
//    [track1]
//    [track2]
//    ...

TrSceneView : QScrollView {

	var <scene;
	// menu views
	var vMSceneST, vMSaveB, vMSaveAsB, vMAddB, vMRevertB, <vMCloseB, vMMasterST, vMMuteB, vMMasterS;
	// trace header views
	var vTNameST, <vTAddB, vTIdST, vTDurST, vTRecST, vTIncrementalST, vTSpeedST, vTInputST, vTTargetST, vTOriginST, vTPlayST, vTTraceST, vTDisplayST;
	// player header views
	var vPPlayerST, vPTargetST, vPOnST, vPDistST, vPPolyST, vPSynthST, vPOutputsST, vPLevelST;
	// line views
	var vHLine1, vHLine2, vHLine3, vVLine;
	var <tracksLayout;
	var canvas;

	*new { arg parent, bounds, scene;
		^super.new(parent, bounds).init(scene);
	}

	init { arg s;
		var mkHLine = { arg height; QUserView().fixedHeight_(height).drawFunc_({|v|
			Pen.line(0@v.bounds.height / 2, Point(v.bounds.width, v.bounds.height / 2)); Pen.stroke; })};
		var font = if (~guiFont.notNil) { ~guiFont } { Font.default.boldVariant};

		scene = s;
		tracksLayout = VLayout.new.spacing_(0).margins_(0);
		canvas = View();

		this.canFocus_(false);
		this.canvas = canvas;
		this.minHeight_(170);

		// menu line
		vMSceneST = StaticText().string_("Scene").font_(font);

		vMSaveB = Button().states_([ [ "save" ] ])
		.action_({
			scene.save
		});

		vMSaveAsB = Button().states_([ [ "save as" ] ])
		.action_({
			scene.saveAs
		});

		vMRevertB = Button().states_([ [ "revert" ] ])
		.action_({
			if (scene.edited.notNil) {
				if (scene.edited) {
					scene.alert("Alert", "Document has changes. Do you want to revert to saved version?",
						[
							["Cancel", { "reverting to saved document canceled".postln }],
							["Cancel", { "reverting to saved document canceled".postln }],
							["Revert", { scene.revertToSaved }]
						]
					);
				} {
					scene.alert("Alert", "Document has no changes. Reverting to saved version not necessary.",
						[
							["OK", { "reverting to saved document canceled".postln }],
							["Cancel", { "reverting to saved document canceled".postln }],
							["Cancel", { "reverting to saved document canceled".postln }]
						]
					);
				}
			}
		});


		vMAddB = Button().states_([ [ "merge" ] ])
		.action_({
			QFileDialog(
				{ arg path;
					("merging" + path).postln;
					if (scene.addIn(path)) {
						(path + "added in").postln;
					};
				},
				{
					"merge canceled".postln;
				},
				2,   // directories
				0,   // open
				true // stripResult
			);
		});

		vMCloseB = Button().states_([ [ "close" ] ])
		.action_({
			if (scene.edited.notNil) {
				if (scene.edited) {
					scene.alert("Alert", "Document to be closed has changes. Do you want to save them?",
						[
							["Cancel", { "closing document canceled".postln }],
							["Save", { scene.save({scene.free; this.close}) }],
							["Discard", {scene.free; this.close}]
						]
					);
				} {
					scene.free;
					this.close;
				}
			} {
				scene.free;
				this.close;
			}
		});

		vMMasterST = StaticText().string_("Master").font_(font);
		vMMuteB = Button().states_([ [ "mute" ], ["mute", Color.red] ]);
		vMMasterS = Slider().orientation_(\horizontal);

		// trace
		vTNameST = StaticText().minWidth_(60).string_("Track").font_(font).align_(\left);

		vTAddB = Button().fixedWidth_(20).fixedHeight_(20).states_([["+"]])
		.action_({
			var new = TrTrack.new(scene);

			if (scene.tracks.size > 0) {
				var model = scene.tracks.last;
				new.setParameters(model.getParameters);
				new.players.first.setParameters(model.players.first.getParameters);
				model.players.drop(1).do{ arg player;
					var np = TrTracePlayer(model.trace, scene.main.trackingInput, scene.main.traceServer);
					np.setParameters(player.getParameters);
					new.addPlayer(np);
				};
			};
			scene.addTrack(new)
		});

		vTIdST = StaticText().fixedWidth_(25).string_("ID").font_(font).align_(\center);
		vTDurST = StaticText().fixedWidth_(40).string_("Dur").font_(font).align_(\right);
		vTRecST = StaticText().fixedWidth_(30).string_("Rec").font_(font).align_(\left);
		vTIncrementalST = StaticText().fixedWidth_(30).string_("Inc").font_(font).align_(\left);
		vTSpeedST = StaticText().fixedWidth_(30).string_("Vel").font_(font).align_(\left);
		vTInputST = StaticText().fixedWidth_(35).string_("Chan").font_(font).align_(\left);
		vTTargetST = StaticText().fixedWidth_(40).string_("Target").font_(font).align_(\left);
		vTOriginST = StaticText().fixedWidth_(40).string_("Origin").font_(font).align_(\left);
		vTPlayST = StaticText().fixedWidth_(28).string_("Play").font_(font).align_(\left);
		vTTraceST = StaticText().fixedWidth_(35).string_("Trace").font_(font).align_(\left);
		vTDisplayST = StaticText().fixedWidth_(92).string_("Display").font_(font).align_(\left);

		// player
		vPPlayerST = StaticText().fixedWidth_(84).string_("Player").font_(font).align_(\left);
		vPTargetST = StaticText().fixedWidth_(40).string_("Target").font_(font).align_(\left);
		vPOnST = StaticText().fixedWidth_(20).string_("On").font_(font).align_(\left);
		vPDistST = StaticText().fixedWidth_(30).string_("Dist").font_(font).align_(\left);
		vPPolyST = StaticText().fixedWidth_(30).string_("Poly").font_(font).align_(\left);
		vPSynthST = StaticText().fixedWidth_(65).string_("Synth").font_(font).align_(\left);
		vPOutputsST = StaticText().minWidth_(60).string_("Outputs").font_(font).align_(\left);
		vPLevelST = StaticText().minWidth_(100).string_("Level").font_(font).align_(\left);

		// lines
		vHLine1 = mkHLine.value(3);
		vHLine2 = mkHLine.value(3);

		canvas.layout_(
			VLayout(
				HLayout(vMSceneST, vMSaveB, vMSaveAsB, vMAddB, vMRevertB, vMCloseB, 150, vMMasterST, vMMuteB, vMMasterS).spacing_(10),
				5,
				vHLine1,
				4,
				HLayout(
					vTNameST, vTAddB, vTIdST, vTDurST, vTRecST, vTIncrementalST, vTSpeedST, vTTargetST, vTOriginST, vTInputST, vTPlayST, vTTraceST, vTDisplayST,
					vPPlayerST, vPTargetST, vPOnST, vPDistST, vPPolyST, vPSynthST, vPOutputsST, vPLevelST
				).spacing_(10),
				3,
				vHLine2,
				3,
				tracksLayout,
				nil
			).spacing_(0)
		);

		this.bounds_(this.bounds.center_(Window.availableBounds.center));

		this.keyDownAction_({ arg view, char, modifiers, unicode, keycode, key;
			// [key, modifiers].postln;
			if (key == 83) { // S
				if (modifiers == 1048576) { // cmd
					vMSaveB.valueAction_(1);
				};
				if (modifiers == 1179648) { // cmd + shift
					vMSaveAsB.valueAction_(1);
				};
			};
			if (key == 87 && modifiers == 1048576) { // cmd-W
					vMCloseB.valueAction_(1);
			};
		});

	}

	update { arg sender, action, track;
		switch (action,
			\trackAdded, {
				\trackAdded.postln;
				tracksLayout.add(track.view);
				track.changed(\trackAdded);
			},
		);
	}
}
