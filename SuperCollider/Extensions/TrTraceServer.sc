/*
SC side representation of the traceserv program, communicated with via OSC
*/

TrTraceServer {

	var <serverAddr;
	var serverPort;
	var serverExe;
	var serverPath;
	var <oscFunc;
	var callbacks;
	var inTerminal;

	var aliveOscFunc;
	var aliveFlag;
	var aliveTask;
	var >aliveCallback;

	*new { arg aliveCallback, serverPath, runInTerminal = false;
		^super.new.init(aliveCallback, serverPath, runInTerminal);
	}

	init { arg argAliveCallback, argServerPath, runInTerminal;

		aliveCallback = argAliveCallback;
		serverPath = argServerPath;
		serverPort = 7770;
		serverAddr = NetAddr("localhost", serverPort);
		if (thisProcess.platform.name == \osx) {
			serverExe = "traceserv-mac";
		} {
			serverExe = "traceserv-linux";
		};
		if (serverPath.isNil){
			serverPath = "/Users/eckel/ownCloud/CoS/Threads/201406/DtV/traceserv";
		};

		oscFunc = OSCFunc({ arg msg, time, addr, recvPort;
			this.dispatch(msg);
		}, '/result2', serverAddr).permanent_(true);

		callbacks = IdentityDictionary.new;

		aliveOscFunc = nil;
		aliveTask = nil;
		inTerminal = runInTerminal;
		this.startAliveChecking;
	}

	finish {
		aliveOscFunc.free;
		oscFunc.free;
		aliveTask.stop;
		aliveCallback = nil;
	}

	start {
		{
			("killall -9 " ++ serverExe).postln.systemCmd; // is this actually blocking?
			1.0.wait;
			if (inTerminal) {
				("\"" ++ serverPath +/+ serverExe ++ "\"" + serverPort + NetAddr.langPort).postln.runInTerminal;
			} {
				("\"" ++ serverPath +/+ serverExe ++ "\"" + serverPort + NetAddr.langPort).postln/*.runInTerminal*/.unixCmd;
			};
			"TrTraceServer: server started".postln;
		}.fork
		^this;
	}

	stop {
		("killall -9 " ++ serverExe).systemCmd;
		"TrTraceServer: server stopped".postln;
		^this;
	}

	// send a message to server, which the server echoes if alive
	// check after a second if the echo arrived and print a warning if it didn't
	// is Cmd-. save

	startAliveChecking {
		if (aliveOscFunc.notNil) {
			aliveOscFunc.free;
		};
		aliveOscFunc = OSCFunc({ aliveFlag = true }, '/alive', serverAddr).permanent_(true);
		if (aliveTask.notNil) {
			aliveTask.stop;
		};
		aliveTask = Task{
			loop {
				aliveFlag = false;
				serverAddr.sendMsg("/alive");
				1.wait;
				aliveCallback.value(aliveFlag);
				if (aliveFlag.not) {
					"TrTraceServer: server didn't respond".warn;
				}
			}
		};
		aliveTask.start;
	}

	stopAliveChecking {
		if (aliveTask.notNil) {
			aliveTask.stop;
		};
	}

	createTrajectory { arg id, size = 8192;
		serverAddr.sendMsg("/createtrace", id, size);
	}

	deleteTrajectory { arg id;
		serverAddr.sendMsg("/deletetrace", id);
	}

	appendSample { arg id, time, x, y, z;
		serverAddr.sendMsg("/appendsample", id, time, x, y, z);
	}

	// IMPORTANT: traceserv has problems with repeated values (zero-length segments)
	// make sure trajectory doesn't contain them (TrackingInput does filter them)

	addTrajectory { arg id, trajectory; // trajectory: [[t1, [x1, y1, z1]], [t2, [...], ...]
		var tmp = if (~tempFilesDir.notNil) { ~tempFilesDir } { "/tmp" };
		var name = tmp +/+ "_trajectory_" ++ id;
		var file = File(name, "w");

		trajectory.do{|s| file.write(s[0].asString + s[1][0] + s[1][1] + s[1][2] ++ "\n")};
		file.close;
		serverAddr.sendMsg("/addtrace", id, trajectory.size, name);
		// serverAddr.sendMsg("/addtrace", id, trajectory.size);
		// trajectory.do{|s, i| serverAddr.sendMsg("/setsample", id, i, s[0], s[1][0], s[1][1], s[1][2])};
	}

	compute { arg traceId, target, lastPos, pos, maxdist, maxres, minage, maxage, minalign;
		// serverAddr.sendMsg("/compute2", *([traceId, target, maxres] ++ lastPos ++ pos ++ maxdist ++ ~defaultMinAge));
		serverAddr.sendMsg("/compute2", *([traceId, target, maxres] ++ lastPos ++ pos ++ maxdist ++ minage ++ maxage ++ minalign));
	}

	dispatch { arg msg;
		var nthResult = msg[1];
		var channel = msg[2];
		var trace = msg[3];
		var moreResults = msg[4];
		var speed = msg[5];
		var time = msg[6];
		var distance = msg[7];

		// ("dispatch " ++ channel).postln;

		callbacks[channel][trace].do{ arg callback;
			callback.value(nthResult, moreResults, speed, time, distance)
		};
	}

	// the callbacks are stored per channel (i.e. target) and trace
	// as there can be several callback registered for the same target/trace combination
	// the callbacks are stored in a list for each combination
	connect { arg channel, trace, callback;
		if (callbacks[channel].isNil) {
			callbacks.put(channel, IdentityDictionary.new);
		};
		if (callbacks[channel][trace].isNil) {
			callbacks[channel].put(trace, List.new);
		};
		callbacks[channel][trace].add(callback);
	}

	disconnect { arg callback;
		callbacks.do{ arg channel; channel.do{ arg trace; trace.remove(callback) } };
	}

	sendMsg { arg msg ... args;
		serverAddr.sendMsg(msg, *args);
	}

}
