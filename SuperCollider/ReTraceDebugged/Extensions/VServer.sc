VServer {
	var <host;
	var <port;
	var <tcp;
	var <netAddr;
	var <responder;
	var <>verbose;
	var lastID;
	var <>enabled;
	var <objects;

	*basicNew { arg host, port, tcp;
		^super.newCopyArgs(host, port, tcp);
	}

	*new { arg host = "localhost", port = 10000, tcp = true;
		^this.basicNew(host, port, tcp).init;
	}

	init {
		netAddr = NetAddr.new(host, port);
		this.connect;
		lastID = 0;
		enabled = true;
		verbose = false;
		objects = Array.new;
		^this;
	}

	initnetAddr {
		this.disconnect;
		netAddr = NetAddr.new(host, port);
		this.connect;
		^this;
	}

	host_ { arg value;
		host = value;
		^this.initServer();
	}

	port_ { arg value;
		port = value;
		^this.initServer();
	}

	connect {
		if (tcp) {
			netAddr.connect({"VServer: has been disconnected".postln});
		}
		^this;
	}

	disconnect {
		if (tcp) {
			netAddr.disconnect;
		}
		^this;
	}

	send { arg ... args;
		if (verbose) {
			( "VServer::send: " ++ args).postln;
		};
		if (enabled) {
			netAddr.sendMsg(*args);
		};
		^this;
	}

	nextID {
		lastID = lastID + 1;
		^lastID;
	}

	add { arg object;
		objects = objects.add(object);
		^this;
	}

	clear {
		this.send("/clear");
		objects = Array.new;
		^this;
	}

	setCamera { arg x, y, z, h, p, r;
		^this.send("/setCamera", *[x, y, z, h, p, r].asFloat);
	}

	setCameraViewDirection { arg x, y, z;
		^this.send("/setCameraViewDirection", *[x, y, z].asFloat);
	}

	setCameraUpVector { arg x, y, z;
		^this.send("/setCameraUpVector", *[x, y, z].asFloat)
	}

	setCameraPosition { arg x, y, z;
		^this.send("/setCameraPosition", *[x, y, z].asFloat);
	}

	setCameraOrientation { arg y, x;
		^this.send("/setCameraOrientation", *[y, x].asFloat);
	}

	setScale { arg s;
		^this.send("/setScale", s.asFloat);
	}

	setDraw { arg flag;
		^this.send("/setDraw", flag.asInteger);
	}

	setLight { arg flag;
		^this.send("/setLight", flag.asInteger);
	}

	setBackground { arg r, g, b;
		^this.send("/setBackground", r.asInteger, g.asInteger, b.asInteger);
	}

	setFrameRate { arg r;
		^this.send("/setFrameRate", r.asInteger);
	}

	setReplyPort { arg p;
		^this.send("/setReplyPort", p.asInteger);
	}

	// backwards compatibility

	server { // act as scene
		^this;
	}

	frameRate { arg r;
		^this.send("/setFrameRate", r.asInteger);
	}

	replyPort { arg p;
		^this.send("/setReplyPort", p.asInteger);
	}
}

// functionality has been collapped into VServer
// remains for backwards compatibility

VScene {
	var <server;
	var <objects;

	*basicNew { arg server;
		^super .newCopyArgs(server);
	}

	*new { arg server;
		^this.basicNew(server).init;
	}

	init {
		objects = Array .new;
		^this;
	}

	nextID {
		^server.nextID;
	}

	add { arg object;
		objects = objects.add(object);
		^this;
	}

	clear {
		server.send("/clear");
		^this.init;
	}

	camera { arg x, y, z, h, p, r;
		server.send("/setCamera", *[x, y, z, h, p, r].asFloat)
	}

	cameraViewDirection { arg x, y, z;
		server.send("/setCameraViewDirection", *[x, y, z].asFloat)
	}

	cameraUpVector { arg x, y, z;
		server.send("/setCameraUpVector", *[x, y, z].asFloat)
	}

	cameraPosition { arg x, y, z;
		server.send("/setCameraPosition", *[x, y, z].asFloat)
	}

	cameraOrientation { arg y, x;
		server.send("/setCameraOrientation", *[y, x].asFloat)
	}

	scale { arg s;
		server.send("/setScale", s.asFloat)
	}
}

VObject {
	var <scene;
	var <>id;

	*basicNew { arg scene;
		^super .newCopyArgs(scene, 0);
	}

	*new { arg scene;
		^this.basicNew(scene).init;
	}

	init {
		id = scene.server.nextID;
		^this;
	}

	delete {
		scene.server.send("/delete", id);
		id = 0;
		^this;
	}

}

VBox : VObject {
	var <size, <color, <trans, <rot;
	var <visible;

	*basicNew { arg scene, size, color, trans, rot;
		^super.newCopyArgs(scene, 0, size, color, trans, rot);
	}

	*new { arg scene, size, color, trans, rot;
		^this.basicNew(scene, size, color, trans, rot).init;
	}

	init {
		super.init;
		scene.add( this);
		this.create;
		^this;
	}

	create {
		scene.server.send( "/create" , id, "Box" ,
			*(size.asFloat ++ color.asInteger ++ trans.asFloat ++ rot.asFloat));
		this.visible = true;
		^this;
	}

	size_ { arg value;
		size = value;
		scene.server.send( "/setSize" , id, *(size.asFloat));
	}

	color_ { arg value;
		color = value;
		scene.server.send( "/setColor" , id, *(color.asInteger));
	}

	trans_ { arg value;
		trans = value;
		scene.server.send( "/setTrans" , id, *(trans.asFloat));
	}

	rot_ { arg value;
		rot = value;
		scene.server.send( "/setRot" , id, *(rot.asFloat));
	}

	visible_ { arg value;
		visible = value;
		scene.server.send( "/setVisible" , id, if (visible) { 1 } { 0 });
	}
}

VSphere : VBox {

	create {
		scene.server.send( "/create" , id, "Sphere" ,
			*(size.asFloat ++ color.asInteger ++ trans.asFloat ++ rot.asFloat));
		this.visible = true;
		^this;
	}
}

VTracedSphere : VBox {

	var <traceLen;

	create {
		scene.server.send( "/create" , id, "TracedSphere" ,
			*(size.asFloat ++ color.asInteger ++ trans.asFloat ++ rot.asFloat));
		this.visible = true;
		^this;
	}

	traceLen_ { arg value;
		traceLen = value;
		scene.server.send( "/setTraceLen" , id, traceLen.asInteger);
	}
}

VSpeaker : VBox {

	create {
		scene.server.send( "/create" , id, "Speaker" ,
			*(size.asFloat ++ color.asInteger ++ trans.asFloat ++ rot.asFloat));
		this.visible = true;
		^this;
	}
}


VLine3D : VObject {
	var <begin, <end, <color, <weight;
	var <visible;

	*basicNew { arg scene, begin, end, color, weight;
		^super .newCopyArgs(scene, 0, begin, end, color, weight);
	}

	*new { arg scene, begin, end, color, weight = 1;
		^this.basicNew(scene, begin, end, color, weight).init;
	}

	init {
		super .init;
		scene.add( this);
		this.create;
		^this;
	}

	create {
		scene.server.send( "/create" , id, "Line3D" ,
			*(begin.asFloat ++ end.asFloat ++ color.asInteger ++ weight.asFloat));
		this.visible = true;
		^this;
	}

	begin_ { arg value;
		begin = value;
		scene.server.send( "/setBegin" , id, *(begin.asFloat));
	}

	end_ { arg value;
		end = value;
		scene.server.send( "/setEnd" , id, *(end.asFloat));
	}

	color_ { arg value;
		color = value;
		scene.server.send( "/setColor" , id, *(color.asInteger));
	}

	weight_ { arg value;
		weight = value;
		scene.server.send( "/setWeight" , id, weight.asFloat);
	}

	visible_ { arg value;
		visible = value;
		scene.server.send( "/setVisible" , id, if (visible) { 1 } { 0 });
	}
}

VBuffer3D : VObject {
	var <color, <scale, <weight;
	var <visible;
	var <>file;

	*basicNew { arg scene, color, scale, weight;
		^super.newCopyArgs(scene, 0, color, scale, weight);
	}

	*new { arg scene, color, scale, weight = 1;
		^this.basicNew(scene, color, scale, weight).init;
	}

	init {
		var tmp = if (~tempFilesDir.notNil) { ~tempFilesDir } { "/tmp" };
		super.init;
		scene.add( this);
		this.create;
		file = tmp +/+ "_VBuffer3D_" ++ id ++ ".txt";
		("VBuffer3D::init: file = " ++ file).postln;
		^this;
	}

	create {
		scene.server.send( "/create" , id, "Buffer3D" ,
			*(color.asInteger ++ scale.asFloat ++ weight.asFloat));
		this.visible = true;
		^this;
	}

	scale_ { arg value;
		scale = value;
		scene.server.send( "/setScale" , id, scale.asFloat);
	}

	color_ { arg value;
		color = value;
		scene.server.send( "/setColor" , id, *(color.asInteger));
	}

	weight_ { arg value;
		weight = value;
		scene.server.send( "/setWeight" , id, weight.asFloat);
	}

	visible_ { arg value;
		visible = value;
		scene.server.send( "/setVisible" , id, if (visible) { 1 } { 0 });
	}

	setPoints { arg points;
		var f = File.new(file, "wb+");
		points.do{ arg coords;
			f.write(coords[0].asString + coords[1] + coords[2] ++ " ");
		};
		f.close;
	}

	addPoints {
		scene.server.send( "/addPoints" , id, file);
	}

}
